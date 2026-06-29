package net.kaleidoscope.cookery.block.behavior;
import net.kaleidoscope.cookery.block.entity.ShawarmaSpitController;

import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.EntityBlock;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.block.property.type.DoubleBlockHalf;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.BlockPlaceContext;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import net.momirealms.craftengine.proxy.minecraft.core.BlockPosProxy;
import net.momirealms.craftengine.proxy.minecraft.core.DirectionProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.BlockGetterProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelWriterProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.SignalGetterProxy;
import net.kaleidoscope.cookery.util.BehaviorConfig;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;

public final class ShawarmaSpitBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<ShawarmaSpitBehavior> FACTORY = new Factory();
    private int controllerId;
    private final Property<Boolean> poweredProperty;
    private final Property<DoubleBlockHalf> halfProperty;
    private final Property<Direction> facingProperty;
    public final int grillTime;
    public int animChunkRadius = TrackedPlayers.DEFAULT_ANIM_CHUNK_RADIUS;

    public ShawarmaSpitBehavior(BlockDefinition blockDefinition,
                                Property<Boolean> poweredProperty,
                                Property<DoubleBlockHalf> halfProperty,
                                Property<Direction> facingProperty,
                                int grillTime) {
        super(blockDefinition);
        this.poweredProperty = poweredProperty;
        this.halfProperty = halfProperty;
        this.facingProperty = facingProperty;
        this.grillTime = grillTime;
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        if (player == null || player.isSneaking()) {
            return InteractionResult.PASS;
        }
        World world = context.getLevel();
        if (!InteractGuard.canInteract(player, world, context.getClickedPos())) {
            return InteractionResult.PASS;
        }
        // 功能控制器只在下半 点上半时定位到下半的方块实体
        var lowerPos = state.get(this.halfProperty) == DoubleBlockHalf.UPPER
                ? context.getClickedPos().below() : context.getClickedPos();
        BlockEntity blockEntity = world.storageWorld().getBlockEntityAtIfLoaded(lowerPos);
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }
        ShawarmaSpitController controller = blockEntity.controller.get(ShawarmaSpitController.class, this.controllerId);
        if (controller == null) {
            return InteractionResult.PASS;
        }

        // 烤架操作不涉及工具 只认主手 副手触发直接放行
        if (context.getHand() == InteractionHand.OFF_HAND) {
            return InteractionResult.PASS;
        }
        InteractionHand hand = InteractionHand.MAIN_HAND;
        Item itemInHand = player.getItemInHand(hand);
        int layer = state.get(this.halfProperty) == DoubleBlockHalf.UPPER ? 1 : 0;

        // 手持食材则放入 空手则取出
        if (!itemInHand.isEmpty()) {
            return handlePlaceFood(controller, player, hand, itemInHand, layer);
        }
        return handleTakeProduct(controller, player, hand, layer);
    }

    // 把手中食材逐个放入该层空槽
    private InteractionResult handlePlaceFood(ShawarmaSpitController controller, Player player,
                                              InteractionHand hand, Item itemInHand, int layer) {
        if (!controller.canCook(itemInHand)) {
            return InteractionResult.PASS;
        }
        int held = itemInHand.count();
        int placed = 0;
        while (placed < held && controller.tryAddOne(layer, itemInHand)) {
            placed++;
        }
        if (placed > 0) {
            InventoryUtils.shrinkHeld(player, itemInHand, placed);
            player.swingHand(hand);
        }
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 空手取出该层成品或生料
    private InteractionResult handleTakeProduct(ShawarmaSpitController controller, Player player,
                                                InteractionHand hand, int layer) {
        if (controller.takeFromLayer(layer, player, hand)) {
            player.swingHand(hand);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        return InteractionResult.PASS;
    }

    @Override
    public ImmutableBlockState updateStateForPlacement(BlockPlaceContext context, ImmutableBlockState state) {
        Object level = context.getLevel().minecraftWorld();
        Object pos = LocationUtils.toBlockPos(context.getClickedPos());
        boolean powered = SignalGetterProxy.INSTANCE.hasNeighborSignal(level, pos);
        return state.with(this.poweredProperty, powered);
    }

    @Override
    public void neighborChanged(Object thisBlock, Object[] args) {
        Object blockState = args[0];
        Object world = args[1];
        Object blockPos = args[2];

        ImmutableBlockState customState = BlockStateUtils.getOptionalCustomBlockState(blockState).orElse(null);
        if (customState == null) {
            return;
        }

        DoubleBlockHalf half = customState.get(this.halfProperty);
        Object lowerPos = half == DoubleBlockHalf.LOWER ? blockPos : BlockPosProxy.INSTANCE.relative(blockPos, DirectionProxy.DOWN);
        Object upperPos = half == DoubleBlockHalf.UPPER ? blockPos : BlockPosProxy.INSTANCE.relative(blockPos, DirectionProxy.UP);

        boolean powered = SignalGetterProxy.INSTANCE.hasNeighborSignal(world, lowerPos) ||
                SignalGetterProxy.INSTANCE.hasNeighborSignal(world, upperPos);

        setHalfPowered(world, lowerPos, powered);
        setHalfPowered(world, upperPos, powered);
    }

    private void setHalfPowered(Object world, Object pos, boolean powered) {
        ImmutableBlockState s = BlockStateUtils.getOptionalCustomBlockState(
                BlockGetterProxy.INSTANCE.getBlockState(world, pos)).orElse(null);
        if (s == null || s.owner().value() != super.blockDefinition) {
            return;
        }
        if (s.get(this.poweredProperty) != powered) {
            LevelWriterProxy.INSTANCE.setBlock(world, pos,
                    s.with(this.poweredProperty, powered).customBlockState().minecraftState(), 3);
        }
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new ShawarmaSpitController(blockEntity, this);
    }

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    public Property<Boolean> getPoweredProperty() {
        return this.poweredProperty;
    }

    public Property<DoubleBlockHalf> getHalfProperty() {
        return this.halfProperty;
    }

    public Property<Direction> getFacingProperty() {
        return this.facingProperty;
    }

    private static class Factory implements BlockBehaviorFactory<ShawarmaSpitBehavior> {
        @Override
        public ShawarmaSpitBehavior create(BlockDefinition block, ConfigSection section) {
            ShawarmaSpitBehavior b = new ShawarmaSpitBehavior(
                    block,
                    BlockBehaviorFactory.getProperty(section.path(), block, "powered", Boolean.class),
                    BlockBehaviorFactory.getProperty(section.path(), block, "half", DoubleBlockHalf.class),
                    BlockBehaviorFactory.getProperty(section.path(), block, "facing", Direction.class),
                    BehaviorConfig.getInt(section, 300, "grill_time", "grill-time")
            );
            b.animChunkRadius = BehaviorConfig.getInt(section, b.animChunkRadius, "animation_view_distance", "animation-view-distance");
            return b;
        }
    }
}
