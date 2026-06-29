package net.kaleidoscope.cookery.block.behavior;
import net.kaleidoscope.cookery.block.entity.BarStoolController;
import net.kaleidoscope.cookery.util.InteractGuard;

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
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.BlockPlaceContext;
import net.momirealms.craftengine.core.world.context.UseOnContext;

/**
 * 吧台凳行为处理 
 * 支持右键坐下 并处理放置时的朝向 
 * 待优化 非厨房内容 正式版删除
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
        if (player == null || player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }

        World world = context.getLevel();
        BlockPos pos = context.getClickedPos();

        BlockEntity blockEntity = world.storageWorld().getBlockEntityAtIfLoaded(pos);
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }

        if (!InteractGuard.canInteract(player, world, pos)) {
            return InteractionResult.PASS;
        }

        player.updateLastSuccessfulInteractionTick(player.gameTicks());
        scheduleSit(context, player, blockEntity);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 延迟 1tick 再坐 确保坐骑实体未被占用
    private void scheduleSit(UseOnContext context, BukkitServerPlayer player, BlockEntity blockEntity) {
        BukkitCraftEngine.instance().scheduler().platform().runDelayed(() -> {
            blockEntity.controller.let(BarStoolController.class, this.controllerId, c -> {
                // 没人的时候才能坐
                if (c.getSitEntity() == null || !c.getSitEntity().isValid() || c.getSitEntity().getPassengers().isEmpty()) {
                    c.sit(player);
                    player.swingHand(context.getHand());
                }
            });
        }, null, player.platformPlayer());
    }

    @Override
    public ImmutableBlockState updateStateForPlacement(BlockPlaceContext context, ImmutableBlockState state) {
        if (this.facingProperty == null) {
            return state;
        }
        // 自动调整朝向 让凳子正对着玩家
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
