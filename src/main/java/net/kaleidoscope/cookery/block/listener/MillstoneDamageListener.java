package net.kaleidoscope.cookery.block.listener;

import net.kaleidoscope.cookery.block.entity.MillstoneController;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;

import java.util.UUID;

// 拉磨者被打时加速 玩家额外取消击退避免被推离磨道
public class MillstoneDamageListener implements Listener {

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
