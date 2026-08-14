package net.kaleidoscope.cookery.util;

import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

// 登记时按覆盖区块分桶 查询只读取当前区块
public final class ChunkIndex<T> {
    private static final BooleanSupplier ALWAYS_ALIVE = () -> true;

    private final Map<UUID, Map<Long, Set<T>>> byWorld = new ConcurrentHashMap<>();
    // 摘除必须用登记时那批 key 对象被旋转或移位后按现位置反算会摘不掉 留下永久悬挂引用
    private final Map<T, Registration> registrations = new ConcurrentHashMap<>();

    public void register(T value, World world, int blockX, int blockZ, int radius) {
        register(value, ALWAYS_ALIVE, world, blockX, blockZ, radius);
    }

    // alive 是漏掉注销时的兜底 跨 region 线程读 只能读普通字段 禁止碰 Bukkit 实体状态
    public void register(T value, BooleanSupplier alive, World world, int blockX, int blockZ, int radius) {
        unregister(value);
        UUID worldId = world.getUID();
        int minChunkX = (blockX - radius) >> 4;
        int maxChunkX = (blockX + radius) >> 4;
        int minChunkZ = (blockZ - radius) >> 4;
        int maxChunkZ = (blockZ + radius) >> 4;
        long[] chunks = new long[(maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1)];
        int index = 0;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunks[index++] = Chunk.getChunkKey(chunkX, chunkZ);
            }
        }
        // 登记必须先于填桶 反过来会有一段桶里有值却查不到登记 并发查询会当失效摘掉
        this.registrations.put(value, new Registration(worldId, chunks, alive));
        Map<Long, Set<T>> buckets = this.byWorld.computeIfAbsent(worldId, k -> new ConcurrentHashMap<>());
        for (long chunkKey : chunks) {
            buckets.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet()).add(value);
        }
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

    public void forEach(World world, int blockX, int blockZ, Consumer<T> action) {
        Set<T> bucket = bucket(world, blockX, blockZ);
        if (bucket == null) {
            return;
        }
        List<T> stale = null;
        for (T value : bucket) {
            if (!isAlive(value)) {
                stale = markStale(stale, value);
                continue;
            }
            action.accept(value);
        }
        dropStale(bucket, stale);
    }

    public boolean anyMatch(World world, int blockX, int blockZ, Predicate<T> predicate) {
        Set<T> bucket = bucket(world, blockX, blockZ);
        if (bucket == null) {
            return false;
        }
        List<T> stale = null;
        boolean matched = false;
        for (T value : bucket) {
            if (!isAlive(value)) {
                stale = markStale(stale, value);
                continue;
            }
            if (predicate.test(value)) {
                matched = true;
                break;
            }
        }
        dropStale(bucket, stale);
        return matched;
    }

    private Set<T> bucket(World world, int blockX, int blockZ) {
        Map<Long, Set<T>> buckets = this.byWorld.get(world.getUID());
        if (buckets == null || buckets.isEmpty()) {
            return null;
        }
        return buckets.get(Chunk.getChunkKey(blockX >> 4, blockZ >> 4));
    }

    private boolean isAlive(T value) {
        Registration registration = this.registrations.get(value);
        return registration != null && registration.alive.getAsBoolean();
    }

    private List<T> markStale(List<T> stale, T value) {
        List<T> out = stale == null ? new ArrayList<>(2) : stale;
        out.add(value);
        return out;
    }

    private void dropStale(Set<T> bucket, List<T> stale) {
        if (stale == null) {
            return;
        }
        for (T value : stale) {
            unregister(value);
            bucket.remove(value);
        }
    }

    public void clear() {
        this.byWorld.clear();
        this.registrations.clear();
    }

    private record Registration(UUID worldId, long[] chunks, BooleanSupplier alive) {
    }
}
