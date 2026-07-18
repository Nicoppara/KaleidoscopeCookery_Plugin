package net.kaleidoscope.cookery.item.behavior;

import net.momirealms.craftengine.bukkit.plugin.user.BukkitServerPlayer;
import net.momirealms.craftengine.core.block.BlockStateWrapper;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.behavior.ItemBehavior;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.World;

import java.nio.file.Path;
import java.util.Set;

/** Breaks only grass-like plants in the original sickle's 5x5x2 area. */
public final class SickleRangeHarvestBehavior extends ItemBehavior {
    public static final ItemBehaviorFactory<SickleRangeHarvestBehavior> FACTORY = new Factory();

    private static final int HORIZONTAL_RADIUS = 2;
    private static final int HEIGHT = 2;
    private static final Set<Key> HARVESTABLE_GRASS = Set.of(
            Key.of("minecraft:grass"),
            Key.of("minecraft:short_grass"),
            Key.of("minecraft:tall_grass"),
            Key.of("minecraft:fern"),
            Key.of("minecraft:large_fern"),
            Key.of("minecraft:short_dry_grass"),
            Key.of("minecraft:tall_dry_grass"),
            Key.of("minecraft:seagrass"),
            Key.of("minecraft:tall_seagrass")
    );

    @Override
    public void onBreakBlock(World world, Player player, BlockPos pos) {
        BukkitServerPlayer serverPlayer = (BukkitServerPlayer) player;
        if (serverPlayer.isRangeMining() || !isHarvestableGrass(world.getBlockState(pos))) {
            return;
        }

        serverPlayer.setRangeMining(true);
        try {
            for (int x = -HORIZONTAL_RADIUS; x <= HORIZONTAL_RADIUS; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    for (int z = -HORIZONTAL_RADIUS; z <= HORIZONTAL_RADIUS; z++) {
                        if (x == 0 && y == 0 && z == 0) {
                            continue;
                        }

                        int targetX = pos.x() + x;
                        int targetY = pos.y() + y;
                        int targetZ = pos.z() + z;
                        BlockStateWrapper target = world.getBlockState(
                                new BlockPos(targetX, targetY, targetZ));
                        if (isHarvestableGrass(target)) {
                            player.breakBlock(targetX, targetY, targetZ);
                        }
                    }
                }
            }
        } finally {
            serverPlayer.setRangeMining(false);
        }
    }

    private static boolean isHarvestableGrass(BlockStateWrapper state) {
        return state != null && !state.isAir() && HARVESTABLE_GRASS.contains(state.ownerId());
    }

    private static final class Factory implements ItemBehaviorFactory<SickleRangeHarvestBehavior> {
        @Override
        public SickleRangeHarvestBehavior create(
                Pack pack, Path path, Key key, ConfigSection section) {
            return new SickleRangeHarvestBehavior();
        }
    }
}
