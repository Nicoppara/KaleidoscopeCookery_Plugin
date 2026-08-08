package net.kaleidoscope.cookery.item.listener;

import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.item.ItemMatch;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Chicken;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.concurrent.ThreadLocalRandom;

// 猪儿虫喂幼年鸡 立刻长大
// 原版喂小麦种子只是缩短一点成长时间 这里是直接成年 所以猪儿虫才有存在意义
public final class CaterpillarListener implements Listener {

    private static final int PARTICLE_COUNT = 5;
    private static final double PARTICLE_SPREAD = 0.2;
    private static final double PARTICLE_Y_OFFSET = 0.25;

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onFeedChicken(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Chicken chicken) || chicken.isAdult()) {
            return;
        }
        Player player = BukkitAdaptor.adapt(event.getPlayer());
        InteractionHand hand = event.getHand() == EquipmentSlot.OFF_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        Item held = player.getItemInHand(hand);
        if (!ItemMatch.is(held, ItemKeys.CATERPILLAR)) {
            return;
        }
        // 喂别人圈里的鸡也算改动实体 领地要拦
        if (!InteractGuard.canInteract(event.getPlayer(), chicken.getLocation())) {
            return;
        }

        chicken.setAdult();
        chicken.getWorld().spawnParticle(Particle.HEART,
                chicken.getX(), chicken.getY() + PARTICLE_Y_OFFSET, chicken.getZ(),
                PARTICLE_COUNT, PARTICLE_SPREAD, PARTICLE_SPREAD / 2, PARTICLE_SPREAD, 0.1);
        chicken.getWorld().playSound(chicken.getLocation(), Sound.ENTITY_PARROT_EAT,
                SoundCategory.NEUTRAL, 1.0f,
                1.0f + (ThreadLocalRandom.current().nextFloat() - ThreadLocalRandom.current().nextFloat()) * 0.2f);

        InventoryUtils.shrinkHeld(player, held, 1);
        player.swingHand(hand);
        event.setCancelled(true);
    }
}
