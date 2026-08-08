package net.kaleidoscope.cookery.block.behavior;

import net.kaleidoscope.cookery.util.InteractGuard;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.UpdateFlags;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelWriterProxy;

// 成熟作物右键收割
public final class CropHarvestBehavior extends BukkitBlockBehavior implements HarvestableCrop {
    public static final BlockBehaviorFactory<CropHarvestBehavior> FACTORY = new Factory();

    private static final int DEFAULT_RESET_AGE = 5;

    private final CropHarvestRules rules;

    private CropHarvestBehavior(BlockDefinition block, CropHarvestRules rules) {
        super(block);
        this.rules = rules;
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        if (player == null || player.isAdventureMode()) {
            return InteractionResult.PASS;
        }
        if (context.getHand() != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (this.rules.isBlocked(context.getItem())) {
            return InteractionResult.PASS;
        }
        if (!harvest(context.getLevel(), context.getClickedPos(), state, player)) {
            return InteractionResult.PASS;
        }
        player.swingHand(InteractionHand.MAIN_HAND);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 没熟就交给骨粉 熟了就只收割 两条路互斥
    @Override
    public boolean harvest(World world, BlockPos pos, ImmutableBlockState state, Player player) {
        if (!this.rules.isMature(state)) {
            return false;
        }
        if (!InteractGuard.canBreak(player, world, pos.x(), pos.y(), pos.z())) {
            return false;
        }
        this.rules.logBreak(world, pos, player);
        WorldPosition position = new WorldPosition(world, Vec3d.atCenterOf(pos));
        this.rules.dropLoot(world, position, state, player);
        LevelWriterProxy.INSTANCE.setBlock(world.minecraftWorld(), LocationUtils.toBlockPos(pos),
                state.with(this.rules.ageProperty(), this.rules.resetAge()).customBlockState().minecraftState(),
                UpdateFlags.UPDATE_CLIENTS);
        this.rules.playSound(world, position);
        return true;
    }

    private static class Factory implements BlockBehaviorFactory<CropHarvestBehavior> {
        @Override
        public CropHarvestBehavior create(BlockDefinition block, ConfigSection section) {
            return new CropHarvestBehavior(block, CropHarvestRules.fromConfig(block, section, DEFAULT_RESET_AGE));
        }
    }
}
