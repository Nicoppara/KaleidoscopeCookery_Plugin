package net.kaleidoscope.cookery.block.listener;

import net.kaleidoscope.cookery.block.entity.MillstoneController;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.util.Vector;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;

import java.util.UUID;

// 拉磨者状态 被打时加速 玩家额外取消击退避免被推离磨道 以及登录时还原被拉磨关掉的飞行权限
public class MillstoneDamageListener implements Listener {

    // 硬崩溃兜底 只清掉残留标记 飞行状态交给权限插件和游戏模式各自恢复
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        MillstoneController.clearFlightMarkOnJoin(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPullerDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        UUID id = entity.getUniqueId();

        MillstoneController ctrl = MillstoneController.ACTIVE_PUSHERS.get(id);
        if (ctrl == null) {
            ctrl = MillstoneController.ACTIVE_ANIMAL_PULLERS.get(id);
        }
        if (ctrl == null) {
            return;
        }

        if (entity instanceof Player player) {
            // CE 调度器 兼容 folia 在玩家所在区域线程取消击退
            BukkitCraftEngine.instance().scheduler().platform().run(
                    () -> {
                        Vector v = player.getVelocity();
                        v.setX(0).setZ(0);
                        player.setVelocity(v);
                    },
                    null,
                    player
            );
        }

        ctrl.onPullerDamaged();
    }
}
