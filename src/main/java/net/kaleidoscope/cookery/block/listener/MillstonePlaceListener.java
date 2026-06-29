package net.kaleidoscope.cookery.block.listener;

import net.kaleidoscope.cookery.block.behavior.MillstoneBehavior;
import net.kaleidoscope.cookery.block.entity.MillstoneController;

import net.momirealms.craftengine.bukkit.api.event.FurnitureAttemptPlaceEvent;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureBehaviorTemplate;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

// 石磨只能放在占满整格的完整方块上 否则拉磨轨道高度不平整会导致推磨异常
public class MillstonePlaceListener implements Listener {

    @EventHandler
    public void onFurnitureAttemptPlace(FurnitureAttemptPlaceEvent event) {
        boolean isMillstone = false;
        for (FurnitureBehaviorTemplate b : event.furniture().behaviors()) {
            if (b instanceof MillstoneBehavior) {
                isMillstone = true;
                break;
            }
        }
        if (!isMillstone) return;

        Block clicked = event.clickedBlock();
        if (clicked == null) return;

        if (!MillstoneController.isFullCubeTop(clicked)) {
            event.setCancelled(true);
        }
    }
}
