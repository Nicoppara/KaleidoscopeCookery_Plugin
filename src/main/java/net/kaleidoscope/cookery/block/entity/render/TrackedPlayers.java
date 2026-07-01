package net.kaleidoscope.cookery.block.entity.render;

import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.world.chunk.CEChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// 追踪玩家遍历工具 对跟踪某方块实体所在区块的玩家逐个回调
public final class TrackedPlayers {
    private TrackedPlayers() {}

    // 动画视距默认值 切比雪夫区块距离 远于此的玩家反正也不渲染动画 就不发动画包
    public static final int DEFAULT_ANIM_CHUNK_RADIUS = 1;

    public static void forEach(BlockEntity blockEntity, Consumer<Player> action) {
        CEChunk chunk = blockEntity.world.getChunkAtIfLoaded(blockEntity.pos.x() >> 4, blockEntity.pos.z() >> 4);
        if (chunk == null) return;
        for (Player player : new ArrayList<>(chunk.getTrackedBy())) {
            action.accept(player);
        }
    }

    // 玩家所在区块与源区块的切比雪夫距离都在 chunkRadius 内 才算动画可见
    public static boolean withinChunkRange(Player player, int srcChunkX, int srcChunkZ, int chunkRadius) {
        int pcx = ((int) Math.floor(player.x())) >> 4;
        int pcz = ((int) Math.floor(player.z())) >> 4;
        return Math.abs(pcx - srcChunkX) <= chunkRadius && Math.abs(pcz - srcChunkZ) <= chunkRadius;
    }

    // 把追踪该方块实体且在动画视距内的玩家快照成 list 供整段多帧动画复用 区块未加载返回空表
    public static List<Player> snapshotInRange(BlockEntity blockEntity, int chunkRadius) {
        int cx = blockEntity.pos.x() >> 4;
        int cz = blockEntity.pos.z() >> 4;
        CEChunk chunk = blockEntity.world.getChunkAtIfLoaded(cx, cz);
        if (chunk == null) {
            return List.of();
        }
        return snapshotInRange(chunk.getTrackedBy(), cx, cz, chunkRadius);
    }

    // 家具等自带追踪来源 srcChunkX srcChunkZ 为源方块所在区块
    public static List<Player> snapshotInRange(Iterable<Player> tracked, int srcChunkX, int srcChunkZ, int chunkRadius) {
        List<Player> out = new ArrayList<>();
        for (Player player : tracked) {
            if (withinChunkRange(player, srcChunkX, srcChunkZ, chunkRadius)) {
                out.add(player);
            }
        }
        return out;
    }
}
