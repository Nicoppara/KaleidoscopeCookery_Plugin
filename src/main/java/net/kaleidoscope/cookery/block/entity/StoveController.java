package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.StoveBehavior;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.tick.BlockEntityTicker;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.context.Context;
import net.momirealms.craftengine.core.plugin.context.ContextHolder;
import net.momirealms.craftengine.core.plugin.context.SimpleContext;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.particle.ParticleType;

import java.util.concurrent.ThreadLocalRandom;

public final class StoveController extends BlockEntityController {
    private static final Key FLAME = Key.of("minecraft:flame");
    private static final Key SMOKE = Key.of("minecraft:smoke");
    private static final Key CRACKLE = Key.of("minecraft:block.campfire.crackle");
    private static final int TICK_INTERVAL = 3;

    private final StoveBehavior behavior;
    private final Context context = SimpleContext.of(ContextHolder.empty());
    private ParticleType flameType;
    private ParticleType smokeType;
    private boolean particleResolved;
    private int tickCount;

    public StoveController(BlockEntity blockEntity, StoveBehavior behavior) {
        super(blockEntity);
        this.behavior = behavior;
    }

    @Override
    public <C extends BlockEntityController> BlockEntityTicker<C> createAsyncBlockEntityTicker(CEWorld world, ImmutableBlockState blockState) {
        return createTickerHelper(StoveController::tick);
    }

    private static void tick(CEWorld ceWorld, BlockPos pos, ImmutableBlockState state, StoveController controller) {
        if (++controller.tickCount % TICK_INTERVAL != 0) {
            return;
        }
        controller.animateTick(ceWorld.world(), pos, state);
    }

    private void animateTick(World level, BlockPos pos, ImmutableBlockState state) {
        Property<Boolean> litProperty = behavior.getLitProperty();
        if (litProperty == null || !state.get(litProperty)) {
            return;
        }
        if (!particleResolved) {
            flameType = CraftEngine.instance().platform().getParticleType(FLAME);
            smokeType = CraftEngine.instance().platform().getParticleType(SMOKE);
            particleResolved = true;
        }
        if (flameType == null || smokeType == null) {
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double x = pos.x() + 0.5;
        double y = pos.y() + 0.5;
        double z = pos.z() + 0.5;

        if (random.nextInt(10) == 0) {
            float volume = 0.5f + random.nextFloat();
            float pitch = random.nextFloat() * 0.7f + 0.6f;
            Vec3d soundPos = new Vec3d(x, y, z);
            BukkitCraftEngine.instance().scheduler().platform().runDelayed(
                    () -> level.playSound(soundPos, CRACKLE, volume, pitch, SoundSource.BLOCK),
                    level, pos.x(), pos.z());
        }

        level.spawnParticle(
                new Vec3d(
                        x + random.nextDouble() / 3 * (random.nextBoolean() ? 1 : -1),
                        y + 0.5 + random.nextDouble() / 3,
                        z + random.nextDouble() / 3 * (random.nextBoolean() ? 1 : -1)),
                smokeType, 0, 0.0, 1.0, 0.0, 0.02, null, context);

        double offsetRandom = random.nextDouble() * 0.6 - 0.3;
        double xOffset = offsetRandom;
        double zOffset = offsetRandom;
        Property<Direction> facingProperty = behavior.getFacingProperty();
        if (facingProperty != null) {
            Direction direction = state.get(facingProperty);
            Direction.Axis axis = direction.axis();
            xOffset = axis == Direction.Axis.X ? direction.stepX() * 0.52 : offsetRandom;
            zOffset = axis == Direction.Axis.Z ? direction.stepZ() * 0.52 : offsetRandom;
        }
        double yOffset = 0.25 + random.nextDouble() * 6.0 / 16.0;
        level.spawnParticle(
                new Vec3d(x + xOffset, pos.y() + yOffset, z + zOffset),
                flameType, 1, 0.0, 0.0, 0.0, 0.0, null, context);
    }
}
