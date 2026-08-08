package net.kaleidoscope.cookery.block.listener;
import net.kaleidoscope.cookery.block.entity.TrashCanController;

import net.kaleidoscope.cookery.util.UniverseSpigotUtil;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent;
import org.bukkit.GameEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.world.GenericGameEvent;

public class TrashCanListener implements Listener {

    // 观察者左键实体最终会尝试切换旁观目标。Netty 层会直接丢弃对应数据包，
    // 这里再在 Paper 的状态切换入口兜底，避免管线结构变化或其它插件主动切换目标时脱离垃圾桶相机。
    @EventHandler
    public void onStartSpectating(PlayerStartSpectatingEntityEvent event) {
        TrashCanController ctrl = TrashCanController.byOccupant(event.getPlayer().getUniqueId());
        if (ctrl != null && !ctrl.isCamera(event.getNewSpectatorTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLand(GenericGameEvent event) {
        // uspigot 收不到 HIT_GROUND 改由 TrashCanLandListener 判定 见其类注释
        if (UniverseSpigotUtil.isUniverseSpigot()) {
            return;
        }
        if (event.getEvent() != GameEvent.HIT_GROUND) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        // 已经在骑乘 或 落差不足 跳跃落地的落差约 1.25 故阈值取 1
        if (player.getVehicle() != null || player.getFallDistance() <= TrashCanLandListener.MIN_FALL) {
            return;
        }
        TrashCanController.tryEnterOnLand(player, player.getLocation());
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

    // folia 清仇恨手段
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player
                && TrashCanController.byOccupant(player.getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }
}
