package net.kaleidoscope.cookery.block.listener;

import net.kaleidoscope.cookery.block.entity.render.ItemDisplaySet;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

// CE 只在给客户端发忘记区块包时才走元素的 hide 玩家退出登录不发那个包
// 展示实体按玩家记的已发帧号就留在表里 区块不卸载就一直不回收 这里兜底清掉
public final class DisplayTrackingListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        ItemDisplaySet.forgetPlayer(event.getPlayer().getUniqueId());
    }
}
