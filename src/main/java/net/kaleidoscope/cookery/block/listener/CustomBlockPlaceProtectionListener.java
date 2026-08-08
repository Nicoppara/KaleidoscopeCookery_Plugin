package net.kaleidoscope.cookery.block.listener;

import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.kaleidoscope.cookery.util.PlacementGuard;
import net.momirealms.craftengine.bukkit.api.event.CustomBlockAttemptPlaceEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

// 被领地插件取消时客户端持续右键会反复预测再回滚 所以提前到写入前校验
public final class CustomBlockPlaceProtectionListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onAttemptPlace(CustomBlockAttemptPlaceEvent event) {
        // 只管本插件的方块 别影响其它 CE 内容
        if (!ItemKeys.NAMESPACE.equals(event.customBlock().id().namespace())) {
            return;
        }
        if (!InteractGuard.canPlace(event.player(), event.location())) {
            event.setCancelled(true);
            return;
        }
        // 桥接不到的领地插件仍会在随后的 BlockPlaceEvent 取消 那条路径会回滚并触发 onRemove
        // 打上标记让那次移除不掉落
        PlacementGuard.beginPlace(event.location());
    }
}
