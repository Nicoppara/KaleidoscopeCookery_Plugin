package net.kaleidoscope.cookery.util;

import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

// 登记时按覆盖区块分桶 查询只读取当前区块
public final class ChunkIndex<T> {
    private final Map<UUID, Map<Long, Set<T>>> byWorld = new ConcurrentHashMap<>();
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

    public void forEach(World world, int blockX, int blockZ, Consumer<T> action) {
        Map<Long, Set<T>> buckets = this.byWorld.get(world.getUID());
        if (buckets == null || buckets.isEmpty()) {
            return;
        }
        Set<T> bucket = buckets.get(Chunk.getChunkKey(blockX >> 4, blockZ >> 4));
        if (bucket == null) {
            return;
        }
        for (T value : bucket) {
            action.accept(value);
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
