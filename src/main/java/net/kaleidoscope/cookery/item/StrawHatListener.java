package net.kaleidoscope.cookery.item;

import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

// 戴草帽打草掉种子 对应原模组的草帽效果
public final class StrawHatListener implements Listener {
    private static final Key STRAW_HAT = Key.of("kaleidoscopecookery:straw_hat");
    private static final Key STRAW_HAT_FLOWER = Key.of("kaleidoscopecookery:straw_hat_flower");

    // 每种种子各自掷一次骰子 命中的进候选池 最后从候选池里随机取一个
    // 所以一次打草最多掉一种 而不是所有命中的都掉
    private static final SeedDrop[] SEED_DROPS = {
            new SeedDrop(Key.of("kaleidoscopecookery:tomato_seed"), 0.125f),
            new SeedDrop(Key.of("kaleidoscopecookery:chili_seed"), 0.125f),
            new SeedDrop(Key.of("kaleidoscopecookery:lettuce_seed"), 0.125f),
            new SeedDrop(Key.of("kaleidoscopecookery:wild_rice"), 0.125f),
            new SeedDrop(Key.of("minecraft:beetroot_seeds"), 0.02f),
            new SeedDrop(Key.of("minecraft:pumpkin_seeds"), 0.02f),
            new SeedDrop(Key.of("minecraft:melon_seeds"), 0.02f)
    };

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakGrass(BlockBreakEvent event) {
        if (!event.isDropItems() || event.getBlock().getType() != Material.SHORT_GRASS) {
            return;
        }
        if (!isStrawHat(event.getPlayer().getInventory().getHelmet())) {
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        SeedDrop[] eligible = new SeedDrop[SEED_DROPS.length];
        int eligibleCount = 0;
        for (SeedDrop drop : SEED_DROPS) {
            if (random.nextFloat() < drop.chance()) {
                eligible[eligibleCount++] = drop;
            }
        }
        if (eligibleCount == 0) {
            return;
        }

        Item item = InventoryUtils.createOrEmpty(eligible[random.nextInt(eligibleCount)].item());
        if (ItemUtils.isEmpty(item)) {
            return;
        }

        // 时运提高掉落数量 与原模组一致
        int fortune = event.getPlayer().getInventory().getItemInMainHand()
                .getEnchantmentLevel(Enchantment.FORTUNE);
        int amount = 1 + random.nextInt(fortune * 2 + 1);

        // BlockBreakEvent 跑在方块所属 region 上 就地掉落无需再调度
        event.getBlock().getWorld().dropItemNaturally(
                event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                ItemStackUtils.getBukkitStack(item.copyWithCount(amount)));
    }

    private static boolean isStrawHat(ItemStack helmet) {
        if (helmet == null || helmet.getType().isAir()) {
            return false;
        }
        Item item = BukkitItemManager.instance().wrap(helmet);
        return ItemMatch.is(item, STRAW_HAT) || ItemMatch.is(item, STRAW_HAT_FLOWER);
    }

    private record SeedDrop(Key item, float chance) {}
}
