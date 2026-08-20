package net.kaleidoscope.cookery.block.listener;

import net.kaleidoscope.cookery.util.FoliaUtil;
import net.kaleidoscope.cookery.api.MillstoneAnimals;
import net.kaleidoscope.cookery.block.entity.MillstoneController;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.item.ItemMatch;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.kaleidoscope.cookery.util.InventoryUtils;

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
import org.bukkit.inventory.EquipmentSlot;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;

import java.util.UUID;

public class MillstoneAnimalListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAnimalDeath(EntityDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        MillstoneController ctrl = MillstoneController.ACTIVE_ANIMAL_PULLERS.get(uuid);
        if (ctrl == null) return;
        // retired 表示实体已永久移除 只能做纯内存清理 stopSpinning 会写实体状态并掉落拴绳
        FoliaUtil.run(
                ctrl::stopSpinning, ctrl::releaseAnimalRefs, event.getEntity());
    }

    // 禁止玩家骑乘正在拉磨的生物
    @EventHandler(ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (MillstoneController.ACTIVE_ANIMAL_PULLERS.containsKey(event.getMount().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // 原版不能被拴的生物 配置允许拉磨且强制拴绳时 手持拴绳右键直接挂到玩家身上
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onForceLeash(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof LivingEntity living) || living.isLeashed()) {
            return;
        }
        // 正在拉磨禁止再次拴绳 否则会被牵去同时拉多个磨 拉磨时拴绳已被取下所以这里要单独拦
        if (MillstoneController.ACTIVE_ANIMAL_PULLERS.containsKey(living.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        MillstoneAnimals.Profile profile = MillstoneAnimals.instance().resolve(living);
        if (profile == null || !profile.allowed() || !profile.forceLeash()) {
            return;
        }
        Player cePlayer = BukkitAdaptor.adapt(event.getPlayer());
        Item lead = cePlayer.getItemInHand(event.getHand() == EquipmentSlot.OFF_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!ItemMatch.is(lead, ItemKeys.LEAD)) {
            return;
        }
        // 强拴绕开了原版的可拴判定 领地校验也要自己补上 否则别人圈里的动物照样能被牵走
        if (!InteractGuard.canInteract(event.getPlayer(), living.getLocation())) {
            return;
        }
        // Paper 对原版不可拴生物的 setLeashHolder 支持因实体类型而异
        living.setLeashHolder(event.getPlayer());
        // 走轮子 内部已判 canInstabuild 别手写 GameMode 判定 那样会漏掉旁观等情况
        InventoryUtils.shrinkHeld(cePlayer, lead, 1);
        event.setCancelled(true);
    }

    // 拉磨中的生物拦截道具右键 放行驴骡的交互以便开箱加料做自动化
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!MillstoneController.ACTIVE_ANIMAL_PULLERS.containsKey(entity.getUniqueId())) return;
        // 档案里关掉了右键禁用就放行交互
        MillstoneAnimals.Profile profile = MillstoneAnimals.instance().resolve(entity);
        if (profile != null && !profile.interactionDisabled()) return;
        if (entity instanceof ChestedHorse) return;
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
