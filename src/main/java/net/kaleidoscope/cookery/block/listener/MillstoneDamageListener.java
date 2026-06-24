package net.kaleidoscope.cookery.block.listener;
import net.kaleidoscope.cookery.block.entity.MillstoneController;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class MillstoneDamageListener implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Player bukkitPlayer)) return;

        MillstoneController ctrl = MillstoneController.ACTIVE_PUSHERS.get(bukkitPlayer.getUniqueId());
        if (ctrl == null) return;

        // 取消击退
        org.bukkit.Bukkit.getScheduler().runTask(
                JavaPlugin.getProvidingPlugin(MillstoneDamageListener.class),
                () -> {
                    org.bukkit.util.Vector v = bukkitPlayer.getVelocity();
                    v.setX(0).setZ(0);
                    bukkitPlayer.setVelocity(v);
                }
        );

        // 切换到加速模式
        ctrl.onPlayerDamaged();
    }
}
