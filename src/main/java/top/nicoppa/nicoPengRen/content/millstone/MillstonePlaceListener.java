package top.nicoppa.nicoPengRen.content.millstone;

import net.momirealms.craftengine.bukkit.api.event.FurnitureAttemptPlaceEvent;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * 石磨放置监听 石磨只能放在占满整格的完整方块上
 * 非完整方块一律拦截，否则拉磨轨道高度不平整会导致推磨异常
 */
public class MillstonePlaceListener implements Listener {

    @EventHandler
    public void onFurnitureAttemptPlace(FurnitureAttemptPlaceEvent event) {
        boolean isMillstone = event.furniture().behaviors().stream()
                .anyMatch(b -> b instanceof MillstoneBehavior);
        if (!isMillstone) return;

        Block clicked = event.clickedBlock();
        if (clicked == null) return;

        if (!MillstoneController.isFullCubeTop(clicked)) {
            event.setCancelled(true);
        }
    }
}
