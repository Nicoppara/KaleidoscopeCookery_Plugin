package top.nicoppa.nicoPengRen.content.millstone;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 拉磨受伤监听器 玩家在拉磨过程中受到伤害时，取消水平击退并让 {@link MillstoneController} 切换到加速模式
 * 通过 {@link MillstoneController#ACTIVE_PUSHERS} 静态注册表定位对应石磨控制器
 */
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
