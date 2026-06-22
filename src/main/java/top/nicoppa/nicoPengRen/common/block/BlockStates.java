package top.nicoppa.nicoPengRen.common.block;

import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.UpdateFlags;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelWriterProxy;

/**
 * 方块状态同步 与当前状态相同则跳过，否则写入世界并更新方块实体缓存
 */
public final class BlockStates {
    private BlockStates() {}

    public static void sync(BlockEntity blockEntity, ImmutableBlockState newState) {
        sync(blockEntity, newState, UpdateFlags.UPDATE_ALL);
    }

    public static void sync(BlockEntity blockEntity, ImmutableBlockState newState, int flags) {
        if (newState.equals(blockEntity.blockState)) return;
        LevelWriterProxy.INSTANCE.setBlock(
                blockEntity.world.world().minecraftWorld(),
                LocationUtils.toBlockPos(blockEntity.pos),
                newState.customBlockState().minecraftState(),
                flags
        );
        blockEntity.setBlockState(newState);
    }
}
