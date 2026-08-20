package net.kaleidoscope.cookery.block.behavior;

import net.kaleidoscope.cookery.util.DropUtils;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.property.IntegerProperty;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.context.ContextHolder;
import net.momirealms.craftengine.core.plugin.context.parameter.DirectContextParameters;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.WorldPosition;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.Nullable;

// 没挂 crop_harvest 或 rice_crop 的第三方 CE 作物的兜底
// 只认 age 属性 收割后整株回到 age 最小值 这是镰刀在引入 HarvestableCrop 之前的老语义
public final class GenericAgeCrop implements HarvestableCrop {
    private static final String AGE_PROPERTY = "age";
    // 往上往下找同株其它段的最大层数 兜底防止异常状态下走满世界高度
    private static final int MAX_STALK_HEIGHT = 8;

    private final IntegerProperty ageProperty;

    private GenericAgeCrop(IntegerProperty ageProperty) {
        this.ageProperty = ageProperty;
    }

    @Nullable
    public static GenericAgeCrop of(ImmutableBlockState state) {
        Property<Integer> property = state.getProperty(AGE_PROPERTY);
        return property instanceof IntegerProperty age ? new GenericAgeCrop(age) : null;
    }

    @Override
    public BlockPos rootPos(World world, BlockPos pos, ImmutableBlockState state) {
        Block bottom = blockAt(world, pos);
        for (int depth = 0; depth < MAX_STALK_HEIGHT; depth++) {
            Block below = bottom.getRelative(BlockFace.DOWN);
            if (sameCrop(below, state) == null) {
                break;
            }
            bottom = below;
        }
        return new BlockPos(bottom.getX(), bottom.getY(), bottom.getZ());
    }

    @Override
    public boolean harvest(World world, BlockPos pos, ImmutableBlockState state, Player player) {
        BlockPos rootPos = rootPos(world, pos, state);
        Block root = blockAt(world, rootPos);
        ImmutableBlockState rootState = sameCrop(root, state);
        if (rootState == null || rootState.get(this.ageProperty) < this.ageProperty.max) {
            return false;
        }
        dropLoot(world, rootPos, rootState, player);
        resetStalk(root, rootState);
        return true;
    }

    // 创造模式不掉落
    private void dropLoot(World world, BlockPos pos, ImmutableBlockState state, Player player) {
        if (player.canInstabuild()) {
            return;
        }
        WorldPosition position = new WorldPosition(world, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5);
        Item itemInHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        ContextHolder.Builder builder = ContextHolder.builder()
                .withParameter(DirectContextParameters.POSITION, position)
                .withParameter(DirectContextParameters.CUSTOM_BLOCK_STATE, state)
                .withOptionalParameter(DirectContextParameters.PLAYER, player)
                .withOptionalParameter(DirectContextParameters.ITEM_IN_HAND,
                        ItemUtils.isEmpty(itemInHand) ? null : itemInHand);
        DropUtils.dropAll(world, position, state.getDrops(builder, world, player));
    }

    // 整株一起回到最小 age 多段作物只重置底部会让上面几段模型断层
    private void resetStalk(Block root, ImmutableBlockState rootState) {
        Block cursor = root;
        ImmutableBlockState cursorState = rootState;
        for (int height = 0; height < MAX_STALK_HEIGHT && cursorState != null; height++) {
            if (cursorState.get(this.ageProperty) != this.ageProperty.min) {
                cursor.setBlockData(
                        CraftEngineBlocks.getBukkitBlockData(cursorState.with(this.ageProperty, this.ageProperty.min)),
                        true);
            }
            cursor = cursor.getRelative(BlockFace.UP);
            cursorState = sameCrop(cursor, rootState);
        }
    }

    // 是同一种作物就返回它的状态 否则返回 null 顺带把状态给调用方省一次查询
    @Nullable
    private static ImmutableBlockState sameCrop(Block block, ImmutableBlockState reference) {
        ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
        if (state == null || state.isEmpty() || state.owner().value() != reference.owner().value()) {
            return null;
        }
        return state;
    }

    private static Block blockAt(World world, BlockPos pos) {
        return ((org.bukkit.World) world.platformWorld()).getBlockAt(pos.x(), pos.y(), pos.z());
    }
}
