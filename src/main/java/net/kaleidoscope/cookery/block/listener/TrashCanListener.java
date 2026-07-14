package net.kaleidoscope.cookery.block.listener;
import net.kaleidoscope.cookery.block.entity.TrashCanController;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import org.bukkit.GameEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.world.GenericGameEvent;

public class TrashCanListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onLand(GenericGameEvent event) {
        if (event.getEvent() != GameEvent.HIT_GROUND) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        // 已经在骑乘 或 落差不足 跳跃落地的落差约 1.25 故阈值取 1
        if (player.getVehicle() != null || player.getFallDistance() <= 1.0f) {
            return;
        }
        TrashCanController ctrl = TrashCanController.findUnder(player);
        if (ctrl == null || ctrl.isOccupied() || ctrl.isAnimating()) {
            return;
        }
        // 落地事件在移动处理中触发 延后一 tick 再切模式与传送 避免在下落步骤里传送玩家
        BukkitCraftEngine.instance().scheduler().platform().runLater(() -> {
            if (!ctrl.isOccupied() && !ctrl.isAnimating() && player.isOnline()) {
                ctrl.enter(player);
            }
        }, null, 1L, player);
    }

    // 玩家停止旁观相机 死亡触发的停止旁观交给重生处理 不在此处退出(避免传送/切模式死人)
    @EventHandler
    public void onStopSpectating(PlayerStopSpectatingEntityEvent event) {
        if (event.getPlayer().isDead()) {
            return;
        }
        TrashCanController ctrl = TrashCanController.byOccupant(event.getPlayer().getUniqueId());
        if (ctrl != null) {
            ctrl.exit();
        }
    }

    // 在桶里死亡后点重生 松开桶并还原生存模式 在正确的重生点重生
    // 潜行退出垃圾桶
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        TrashCanController ctrl = TrashCanController.byOccupant(event.getPlayer().getUniqueId());
        if (ctrl != null) {
            ctrl.exit();
        }
    }

    // 进入桶内的玩家退出登录时还原状态
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        TrashCanController ctrl = TrashCanController.byOccupant(event.getPlayer().getUniqueId());
        if (ctrl != null) {
            ctrl.exit();
        }
    }

    // 硬崩溃兜底 onDisable 没跑导致玩家卡在旁观加南瓜 登录时据持久化数据还原
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        TrashCanController.restoreIfCrashed(event.getPlayer());
    }
}
