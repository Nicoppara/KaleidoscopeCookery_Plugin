package net.kaleidoscope.cookery.block.listener;

import net.kaleidoscope.cookery.api.MillstoneAnimals;
import net.kaleidoscope.cookery.block.entity.MillstoneController;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;

import java.util.UUID;

public class MillstoneAnimalListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAnimalDeath(EntityDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        MillstoneController ctrl = MillstoneController.ACTIVE_ANIMAL_PULLERS.get(uuid);
        if (ctrl == null) return;
        BukkitCraftEngine.instance().scheduler().platform().run(
                ctrl::stopSpinning, ctrl::stopSpinning, event.getEntity());
    }

    // 禁止玩家骑乘正在拉磨或罢工中的生物
    @EventHandler(ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (MillstoneController.ACTIVE_ANIMAL_PULLERS.containsKey(event.getMount().getUniqueId())
                || MillstoneController.isStruck(event.getMount())) {
            event.setCancelled(true);
        }
    }

    // 原版不能被拴的生物 配置允许拉磨且强制拴绳时 手持拴绳右键直接挂到玩家身上
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onForceLeash(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof LivingEntity living) || living.isLeashed()) {
            return;
        }
        // 正在拉磨或罢工中禁止再次拴绳 否则会被牵去同时拉多个磨 拉磨时拴绳已被取下所以这里要单独拦
        if (MillstoneController.ACTIVE_ANIMAL_PULLERS.containsKey(living.getUniqueId())
                || MillstoneController.isStruck(living)) {
            event.setCancelled(true);
            return;
        }
        MillstoneAnimals.Profile profile = MillstoneAnimals.instance().resolve(living);
        if (profile == null || !profile.allowed() || !profile.forceLeash()) {
            return;
        }
        ItemStack hand = event.getPlayer().getInventory().getItem(event.getHand());
        if (hand == null || hand.getType() != Material.LEAD) {
            return;
        }
        // TODO setLeashHolder 对原版不可拴生物可能不稳定 必要时改 NMS setLeashedTo
        living.setLeashHolder(event.getPlayer());
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }
        event.setCancelled(true);
    }

    // 拉磨中或罢工中的生物拦截道具右键 放行驴骡的交互以便开箱加料做自动化
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        boolean pulling = MillstoneController.ACTIVE_ANIMAL_PULLERS.containsKey(entity.getUniqueId());
        boolean struck = MillstoneController.isStruck(entity);
        if (!pulling && !struck) return;
        // 档案里关掉了右键禁用就放行交互
        MillstoneAnimals.Profile profile = MillstoneAnimals.instance().resolve(entity);
        if (profile != null && !profile.interactionDisabled()) return;
        if (entity instanceof ChestedHorse) return;
        ItemStack hand = event.getPlayer().getInventory().getItem(event.getHand());
        if (struck && !pulling && hand != null && hand.getType() == Material.LEAD) return;
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
