package net.kaleidoscope.cookery.item.listener;

import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import net.kaleidoscope.cookery.item.LunchBagContents;
import net.kaleidoscope.cookery.item.LunchBagEating;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.util.ItemUtils;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;


public class LunchBagListener implements Listener {

    // 原版收纳袋右键会把内容物扔出去 无条件压掉
    // 不能只靠物品行为返回 SUCCESS_AND_CANCEL 因为准星指着实体时 CE 的 onInteractAir 会被 lastInteractEntityCheck 挡掉不派发
    // 进食态是 paper 不在这里被拦 原版的 consumable 才能正常起效
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (isLunchBag(event.getItem())) {
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    // 进食态被原版吃完 扣内容物并换回收纳态 不走原版扣物品
    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Item eating = wrap(event.getItem());
        if (!LunchBagContents.isEatingForm(eating)) {
            return;
        }
        event.setCancelled(true);
        LunchBagEating.finish(event.getPlayer(), event.getHand(), eating);
    }

    // 没吃完就松手
    @EventHandler(ignoreCancelled = true)
    public void onStopUsing(PlayerStopUsingItemEvent event) {
        if (LunchBagContents.isEatingForm(wrap(event.getItem()))) {
            LunchBagEating.restore(event.getPlayer());
        }
    }

    // 吃到一半切物品栏 滚轮是高频事件 只看原来那一格别全背包扫
    @EventHandler(ignoreCancelled = true)
    public void onHeldChange(PlayerItemHeldEvent event) {
        ItemStack previous = event.getPlayer().getInventory().getItem(event.getPreviousSlot());
        if (LunchBagContents.isEatingForm(wrap(previous))) {
            LunchBagEating.restore(event.getPlayer());
        }
    }

    // 进食态一律不许离手 取消掉落让它退回背包再换回收纳态
    // 若改成把掉落物换成收纳态 会和 restore 各自生成一个 直接刷物品
    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!LunchBagContents.isEatingForm(wrap(event.getItemDrop().getItemStack()))) {
            return;
        }
        event.setCancelled(true);
        LunchBagEating.restore(event.getPlayer());
    }

    // 硬崩溃时进食态会跟着背包落盘 重进服换回收纳态 内容物在组件里不会丢
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        LunchBagEating.restore(event.getPlayer());
    }

    // 底材是原版 minecraft:bundle 原版右键既能往里塞也能往外取 用的是同一个操作
    // 只能拦"塞入不该收的东西" 不能无差别取消右键 否则玩家连自己的牛排都取不出来
    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean bagInSlot = isLunchBag(current);
        boolean bagOnCursor = isLunchBag(cursor);
        if (!bagInSlot && !bagOnCursor) {
            return;
        }

        // 光标空 = 从袋里取出 放行 光标有东西 = 往袋里塞 交给领域类判
        // 手持袋子点别的物品 也是塞入
        boolean inserting = (bagInSlot && LunchBagContents.rejectsInsert(cursor))
                || (bagOnCursor && LunchBagContents.rejectsInsert(current));
        if (event.getClick() == ClickType.RIGHT && inserting) {
            event.setCancelled(true);
            return;
        }
        scheduleSanitize(event.getWhoClicked());
    }

    // 右键拖拽也能往收纳袋里塞
    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isLunchBag(event.getOldCursor()) || isLunchBag(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        for (ItemStack slot : event.getNewItems().values()) {
            if (isLunchBag(slot)) {
                event.setCancelled(true);
                return;
            }
        }
        scheduleSanitize(event.getWhoClicked());
    }

    // 原版塞入路径不止一条 逐个事件拦容易漏 这里事后扫一遍把不该在袋里的挑出来还给玩家
    // 延后一 tick 等原版把本次改动落到物品上
    private void scheduleSanitize(HumanEntity human) {
        if (!(human instanceof org.bukkit.entity.Player bukkitPlayer)) {
            return;
        }
        BukkitCraftEngine.instance().scheduler().platform().runLater(
                () -> LunchBagContents.sanitizeInventory(bukkitPlayer), null, 1L, bukkitPlayer);
    }

    // 潜行左键倒出全部
    @EventHandler(ignoreCancelled = true)
    public void onSneakLeftClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        if (!event.getPlayer().isSneaking() || !isLunchBag(event.getItem())) {
            return;
        }
        org.bukkit.entity.Player bukkitPlayer = event.getPlayer();
        InteractionHand hand = event.getHand() == EquipmentSlot.OFF_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        LunchBagEating.dropContents(BukkitAdaptor.adapt(bukkitPlayer), hand, wrap(event.getItem()));
    }

    private boolean isLunchBag(ItemStack stack) {
        return LunchBagContents.isLunchBag(wrap(stack));
    }

    private Item wrap(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return Item.empty();
        }
        Item item = BukkitItemManager.instance().wrap(stack);
        return ItemUtils.isEmpty(item) ? Item.empty() : item;
    }
}
