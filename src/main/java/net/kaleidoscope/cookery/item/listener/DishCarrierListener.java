package net.kaleidoscope.cookery.item.listener;

import net.kaleidoscope.cookery.recipe.DishCarriers;
import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;

// 普通物品菜吃完退还容器 家具菜走 ReturnCarrierFunction 两条路查同一张表
// 不用 use_remainder 是因为那是烤进物品的组件 改配方老物品不跟着变 这里每次现查
public final class DishCarrierListener implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (DishCarriers.isEmpty()) {
            return;
        }
        Item consumed = BukkitItemManager.instance().wrap(event.getItem());
        if (ItemUtils.isEmpty(consumed)) {
            return;
        }
        Key carrier = DishCarriers.of(consumed.id());
        if (carrier == null) {
            return;
        }
        Item container = InventoryUtils.createOrEmpty(carrier);
        if (ItemUtils.isEmpty(container)) {
            return;
        }
        // MONITOR 阶段物品还没被扣 这时动背包会和原版的扣除撞车 延到下一 tick
        // 玩家所属 region 线程 folia 上也安全
        Player player = BukkitAdaptor.adapt(event.getPlayer());
        event.getPlayer().getScheduler().run(
                KaleidoscopeCookeryPlugin.instance(),
                task -> DropUtils.dropAtPlayer(player, container.copyWithCount(1)),
                null);
    }
}
