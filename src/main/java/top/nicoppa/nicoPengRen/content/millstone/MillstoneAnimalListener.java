package top.nicoppa.nicoPengRen.content.millstone;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import top.nicoppa.nicoPengRen.NicoPengRen;

import java.util.UUID;

/**
 * 石磨生物监听器 拉磨的生物死亡时，调度到主线程让对应 {@link MillstoneController} 停止拉磨
 * 通过 {@link MillstoneController#ACTIVE_ANIMAL_PULLERS} 静态注册表定位对应石磨控制器
 */

// TODO：以后需要添加的功能 原版生物拉磨黑白名单、调用MythicMobsAPI，让自定义生物拉磨 所有生物均可被拴绳连接
public class MillstoneAnimalListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAnimalDeath(EntityDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        MillstoneController ctrl = MillstoneController.ACTIVE_ANIMAL_PULLERS.get(uuid);
        if (ctrl == null) return;
        org.bukkit.Bukkit.getScheduler().runTask(
                NicoPengRen.getPlugin(NicoPengRen.class),
                (Runnable) () -> ctrl.stopSpinning()
        );
    }

    // 禁止玩家骑乘正在拉磨的生物，以及罢工中的生物
    @EventHandler(ignoreCancelled = true)
    public void onMount(org.bukkit.event.entity.EntityMountEvent event) {
        if (MillstoneController.ACTIVE_ANIMAL_PULLERS.containsKey(event.getMount().getUniqueId())
                || MillstoneController.isStruck(event.getMount())) {
            event.setCancelled(true);
        }
    }

    /**
     * 拉磨中/罢工中的生物：拦截道具右键行为
     * 放行 驴/骡的所有交互 要能开箱子加料做自动化
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(org.bukkit.event.player.PlayerInteractEntityEvent event) {
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
    public void onLeash(org.bukkit.event.entity.PlayerLeashEntityEvent event) {
        if (MillstoneController.ACTIVE_ANIMAL_PULLERS.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
