package top.nicoppa.nicoPengRen.common.render;

import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.world.chunk.CEChunk;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * 追踪玩家遍历工具 对正在跟踪某方块实体所在区块的玩家逐个回调（取副本避免并发修改）
 */
public final class TrackedPlayers {
    private TrackedPlayers() {}

    public static void forEach(BlockEntity blockEntity, Consumer<Player> action) {
        CEChunk chunk = blockEntity.world.getChunkAtIfLoaded(blockEntity.pos.x() >> 4, blockEntity.pos.z() >> 4);
        if (chunk == null) return;
        for (Player player : new ArrayList<>(chunk.getTrackedBy())) {
            action.accept(player);
        }
    }
}
