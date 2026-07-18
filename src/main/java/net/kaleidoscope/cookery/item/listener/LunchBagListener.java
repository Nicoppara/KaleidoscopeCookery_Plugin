package net.kaleidoscope.cookery.item.listener;

import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import net.kaleidoscope.cookery.item.LunchBagContents;
import net.kaleidoscope.cookery.item.LunchBagEating;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.ItemUtils;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
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

    // 原版收纳袋在背包里右键能塞进任意物品 绕过只收熟牛肉的限制 直接压掉
    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClick() != ClickType.RIGHT) {
            return;
        }
        if (isLunchBag(event.getCurrentItem()) || isLunchBag(event.getCursor())) {
            event.setCancelled(true);
        }
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
        LunchBagEating.dropContents(bukkitPlayer, BukkitAdaptor.adapt(bukkitPlayer), wrap(event.getItem()));
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
