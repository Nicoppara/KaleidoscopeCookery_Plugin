package top.nicoppa.nicoPengRen.content.barstool;

import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.bukkit.plugin.user.BukkitServerPlayer;
import net.momirealms.craftengine.bukkit.util.DirectionUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.EntityBlock;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.world.context.BlockPlaceContext;
import net.momirealms.craftengine.core.world.context.UseOnContext;

/**
 * 吧台凳方块行为
 * 数据状态机与 NBT 见 {@link BarStoolController}，渲染见 {@link BarStoolElement}
 */
public final class BarStoolBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<BarStoolBehavior> FACTORY = new Factory();

    private int controllerId;
    private Property<Direction> facingProperty;

    public BarStoolBehavior(BlockDefinition blockDefinition) {
        super(blockDefinition);
    }

    @Override
    public InteractionResult useWithoutItem(UseOnContext context, ImmutableBlockState state) {
        BukkitServerPlayer player = (BukkitServerPlayer) context.getPlayer();
        if (player == null || player.isSecondaryUseActive()) return InteractionResult.PASS;

        BlockEntity blockEntity = context.getLevel().storageWorld().getBlockEntityAtIfLoaded(context.getClickedPos());
        if (blockEntity == null) return InteractionResult.PASS;

        player.updateLastSuccessfulInteractionTick(player.gameTicks());
        // 延迟一 tick 再坐，确保坐骑实体未被占用
        BukkitCraftEngine.instance().scheduler().platform().runDelayed(() -> {
            blockEntity.controller.let(BarStoolController.class, this.controllerId, c -> {
                if (c.getSitEntity() == null || !c.getSitEntity().isValid() || c.getSitEntity().getPassengers().isEmpty()) {
                    c.sit(player);
                    player.swingHand(context.getHand());
                }
            });
        }, null, player.platformPlayer());
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    @Override
    public ImmutableBlockState updateStateForPlacement(BlockPlaceContext context, ImmutableBlockState state) {
        if (this.facingProperty != null) {
            Direction horizontal = DirectionUtils.fromNMSDirection(context.getHorizontalDirection());
            Direction facing = switch (horizontal) {
                case NORTH -> Direction.SOUTH;
                case SOUTH -> Direction.NORTH;
                case EAST -> Direction.WEST;
                case WEST -> Direction.EAST;
                default -> horizontal;
            };
            return state.with(this.facingProperty, facing);
        }
        return state;
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new BarStoolController(blockEntity, this);
    }

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    public Property<Direction> getFacingProperty() {
        return facingProperty;
    }

    private static class Factory implements BlockBehaviorFactory<BarStoolBehavior> {
        @Override
        public BarStoolBehavior create(BlockDefinition block, ConfigSection section) {
            BarStoolBehavior behavior = new BarStoolBehavior(block);
            behavior.facingProperty = BlockBehaviorFactory.getProperty(section.path(), block, "facing", Direction.class);
            return behavior;
        }
    }
}
