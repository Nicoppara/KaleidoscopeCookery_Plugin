package net.kaleidoscope.cookery.util;

import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

// 按世界加区块分桶的空间索引 登记时把覆盖半径内的每个区块都挂上
// 查询只看自己那个桶 不做实体扫描 代价压在低频的登记侧 两层表跨 region 线程共享
public final class ChunkIndex<T> {
    private final Map<UUID, Map<Long, Set<T>>> byWorld = new ConcurrentHashMap<>();
    // 摘除必须用登记时那批 key 对象被旋转或移位后按现位置反算会摘不掉 留下永久悬挂引用
    private final Map<T, Registration> registrations = new ConcurrentHashMap<>();

    public void register(T value, World world, int blockX, int blockZ, int radius) {
        unregister(value);
        UUID worldId = world.getUID();
        int minChunkX = (blockX - radius) >> 4;
        int maxChunkX = (blockX + radius) >> 4;
        int minChunkZ = (blockZ - radius) >> 4;
        int maxChunkZ = (blockZ + radius) >> 4;
        long[] chunks = new long[(maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1)];
        Map<Long, Set<T>> buckets = this.byWorld.computeIfAbsent(worldId, k -> new ConcurrentHashMap<>());
        int index = 0;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                long chunkKey = Chunk.getChunkKey(chunkX, chunkZ);
                chunks[index++] = chunkKey;
                buckets.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet()).add(value);
            }
        }
        this.registrations.put(value, new Registration(worldId, chunks));
    }

    public void unregister(T value) {
        Registration registration = this.registrations.remove(value);
        if (registration == null) {
            return;
        }
        Map<Long, Set<T>> buckets = this.byWorld.get(registration.worldId);
        if (buckets == null) {
            return;
        }
        for (long chunkKey : registration.chunks) {
            Set<T> bucket = buckets.get(chunkKey);
            if (bucket == null) {
                continue;
            }
            bucket.remove(value);
            if (bucket.isEmpty()) {
                buckets.remove(chunkKey, bucket);
            }
        }
    }

    // 遍历该坐标所在桶里的登记者 predicate 返回 false 视为该项已失效 顺手摘掉
    // 方块实体在区块卸载时不会有回调来注销 只能靠查询时惰性清理
    public void forEach(World world, int blockX, int blockZ, Predicate<T> action) {
        Map<Long, Set<T>> buckets = this.byWorld.get(world.getUID());
        if (buckets == null || buckets.isEmpty()) {
            return;
        }
        Set<T> bucket = buckets.get(Chunk.getChunkKey(blockX >> 4, blockZ >> 4));
        if (bucket == null) {
            return;
        }
        List<T> stale = null;
        for (T value : bucket) {
            if (!action.test(value)) {
                if (stale == null) {
                    stale = new ArrayList<>(2);
                }
                stale.add(value);
            }
        }
        if (stale != null) {
            stale.forEach(this::unregister);
        }
    }

    public boolean anyMatch(World world, int blockX, int blockZ, Predicate<T> predicate) {
        Map<Long, Set<T>> buckets = this.byWorld.get(world.getUID());
        if (buckets == null || buckets.isEmpty()) {
            return false;
        }
        Set<T> bucket = buckets.get(Chunk.getChunkKey(blockX >> 4, blockZ >> 4));
        if (bucket == null) {
            return false;
        }
        for (T value : bucket) {
            if (predicate.test(value)) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        this.byWorld.clear();
        this.registrations.clear();
    }

    private record Registration(UUID worldId, long[] chunks) {
    }
}
