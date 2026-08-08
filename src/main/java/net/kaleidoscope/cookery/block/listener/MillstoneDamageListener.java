package net.kaleidoscope.cookery.block.listener;

import net.kaleidoscope.cookery.block.entity.MillstoneController;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

// 拉磨生物被打时加速
public class MillstoneDamageListener implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPullerDamage(EntityDamageEvent event) {
        MillstoneController ctrl =
                MillstoneController.ACTIVE_ANIMAL_PULLERS.get(event.getEntity().getUniqueId());
        if (ctrl == null) {
            return;
        }
        ctrl.onPullerDamaged();
    }
}
