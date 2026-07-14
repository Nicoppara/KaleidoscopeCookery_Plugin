package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.StoveBehavior;
import net.kaleidoscope.cookery.block.entity.render.Particles;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.tick.BlockEntityTicker;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.World;
import org.bukkit.Particle;

import java.util.concurrent.ThreadLocalRandom;

public final class StoveController extends BlockEntityController {
    private static final Key CRACKLE = Key.of("minecraft:block.campfire.crackle");

    private final StoveBehavior behavior;
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
        if (++controller.tickCount % controller.behavior.particleInterval != 0) {
            return;
        }
        controller.animateTick(ceWorld.world(), pos, state);
    }

    private void animateTick(World level, BlockPos pos, ImmutableBlockState state) {
        Property<Boolean> litProperty = behavior.getLitProperty();
        if (litProperty == null || !state.get(litProperty)) {
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double x = pos.x() + 0.5;
        double y = pos.y() + 0.5;
        double z = pos.z() + 0.5;

        boolean crackle = random.nextInt(10) == 0;
        float volume = 0.5f + random.nextFloat();
        float pitch = random.nextFloat() * 0.7f + 0.6f;

        double sx = x + random.nextDouble() / 3 * (random.nextBoolean() ? 1 : -1);
        double sy = y + 0.5 + random.nextDouble() / 3;
        double sz = z + random.nextDouble() / 3 * (random.nextBoolean() ? 1 : -1);

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
        double fx = x + xOffset;
        double fy = pos.y() + 0.25 + random.nextDouble() * 6.0 / 16.0;
        double fz = z + zOffset;

        CEWorld ceWorld = super.blockEntity.world();
        int count = behavior.particleCount;
        BukkitCraftEngine.instance().scheduler().platform().run(() -> {
            if (crackle) {
                level.playSound(new Vec3d(x, y, z), CRACKLE, volume, pitch, SoundSource.BLOCK);
            }
            Particles.emit(ceWorld, Particle.SMOKE, sx, sy, sz, count, 0.08, 0.12, 0.08, 0.02, null);
            Particles.emit(ceWorld, Particle.FLAME, fx, fy, fz, count, 0.05, 0.06, 0.05, 0.0, null);
        }, level, pos.x() >> 4, pos.z() >> 4);
    }
}
