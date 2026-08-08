package net.kaleidoscope.cookery.block.listener;

import net.kaleidoscope.cookery.block.entity.ScarecrowController;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;

// 稻草人范围内的耕地不会被踩坏
public final class ScarecrowTrampleListener implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onTrample(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.FARMLAND || event.getTo() != Material.DIRT) {
            return;
        }
        if (ScarecrowController.protects(block.getWorld(), block.getX(), block.getY(), block.getZ())) {
            event.setCancelled(true);
        }
    }
}
