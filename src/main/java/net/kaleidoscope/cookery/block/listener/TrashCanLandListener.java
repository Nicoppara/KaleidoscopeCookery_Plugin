package net.kaleidoscope.cookery.block.listener;

import net.kaleidoscope.cookery.block.entity.TrashCanController;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

// uspigot 专用的落地判定 只在 isUniverseSpigot 时注册
// 它的 disableWardenSpawnTracking 短路了 ServerLevel.gameEvent GenericGameEvent 收不到 HIT_GROUND
public final class TrashCanLandListener implements Listener {
    // 跳跃落地的落差约 1.25 故阈值取 1 与 TrashCanListener.onLand 一致
    static final float MIN_FALL = 1.0f;

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!player.isOnGround() || player.getVehicle() != null) {
            return;
        }
        Location to = event.getTo();
        // 该事件排在 doCheckFallDamage 之前 fallDistance 还差本次移动的下落量 补上才等于 HIT_GROUND 时的值
        double fall = player.getFallDistance() + Math.max(0.0, event.getFrom().getY() - to.getY());
        if (fall <= MIN_FALL) {
            return;
        }
        TrashCanController.tryEnterOnLand(player, to);
    }
}
