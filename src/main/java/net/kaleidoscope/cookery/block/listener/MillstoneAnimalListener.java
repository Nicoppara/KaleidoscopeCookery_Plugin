package net.kaleidoscope.cookery.block.listener;
import net.kaleidoscope.cookery.block.entity.MillstoneController;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;

import java.util.UUID;

// TODO: 后续可加：原版生物拉磨黑白名单、接入 MythicMobs API 让自定义生物拉磨、所有生物均可被拴绳连接
public class MillstoneAnimalListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAnimalDeath(EntityDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        MillstoneController ctrl = MillstoneController.ACTIVE_ANIMAL_PULLERS.get(uuid);
        if (ctrl == null) return;
        org.bukkit.Bukkit.getScheduler().runTask(
                KaleidoscopeCookeryPlugin.getPlugin(KaleidoscopeCookeryPlugin.class),
                (Runnable) () -> ctrl.stopSpinning()
        );
    }

    // 禁止玩家骑乘正在拉磨或罢工中的生物
    @EventHandler(ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (MillstoneController.ACTIVE_ANIMAL_PULLERS.containsKey(event.getMount().getUniqueId())
                || MillstoneController.isStruck(event.getMount())) {
            event.setCancelled(true);
        }
    }

    // 拉磨中/罢工中的生物拦截道具右键；放行驴/骡的交互以便开箱加料做自动化
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        org.bukkit.entity.Entity entity = event.getRightClicked();
        boolean pulling = MillstoneController.ACTIVE_ANIMAL_PULLERS.containsKey(entity.getUniqueId());
        boolean struck = MillstoneController.isStruck(entity);
        if (!pulling && !struck) return;
        if (entity instanceof org.bukkit.entity.ChestedHorse) return;
        org.bukkit.inventory.ItemStack hand = event.getPlayer().getInventory().getItem(event.getHand());
        if (struck && !pulling && hand != null && hand.getType() == org.bukkit.Material.LEAD) return;
        event.setCancelled(true);
    }

    // 正在拉磨的生物禁止被拴绳牵走
    @EventHandler(ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (MillstoneController.ACTIVE_ANIMAL_PULLERS.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
