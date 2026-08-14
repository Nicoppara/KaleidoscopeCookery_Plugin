package net.kaleidoscope.cookery.util;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 按区块惰性缓存指定实体的坐标
public final class ChunkEntityCache {
    private static final int MAX_TRACKED_PER_CHUNK = 64;
    private static final long NANOS_PER_TICK = 50L * 1_000_000L;

    private final Set<EntityType> types;
    private final long ttlNanos;
    private final int maxChunks;
    private final Map<UUID, Map<Long, Snapshot>> byWorld = new ConcurrentHashMap<>();

    public ChunkEntityCache(Set<EntityType> types, int ttlTicks, int maxChunks) {
        this.types = Set.copyOf(types);
        this.ttlNanos = ttlTicks * NANOS_PER_TICK;
        this.maxChunks = Math.max(1, maxChunks);
    }

    public int countAround(World world, int x, int y, int z, int radius) {
        if (this.types.isEmpty()) {
            return 0;
        }
        int minChunkX = (x - radius) >> 4;
        int maxChunkX = (x + radius) >> 4;
        int minChunkZ = (z - radius) >> 4;
        int maxChunkZ = (z + radius) >> 4;
        int count = 0;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                Snapshot snapshot = snapshot(world, chunkX, chunkZ);
                if (snapshot != null) {
                    count += count(snapshot.positions, x, y, z, radius);
                }
            }
        }
        return count;
    }

    private static int count(int[] positions, int x, int y, int z, int radius) {
        int count = 0;
        for (int i = 0; i < positions.length; i += 3) {
            int dy = positions[i + 1] - y;
            if (dy >= 0 && dy <= 1 && Math.abs(positions[i] - x) <= radius
                    && Math.abs(positions[i + 2] - z) <= radius) {
                count++;
            }
        }
        return count;
    }

    private Snapshot snapshot(World world, int chunkX, int chunkZ) {
        Map<Long, Snapshot> snapshots = this.byWorld.computeIfAbsent(world.getUID(), k -> new ConcurrentHashMap<>());
        long key = Chunk.getChunkKey(chunkX, chunkZ);
        long now = System.nanoTime();
        Snapshot cached = snapshots.get(key);
        if (cached != null && now - cached.stamp < this.ttlNanos) {
            return cached;
        }
        // 不归当前 region 就用旧快照 宁可少加速也不能抛 邻块常常属于别的 region
        if (!Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ)) {
            return cached;
        }
        // getChunkAt 会强制加载 未加载的区块里也不会有实体 直接丢掉旧快照
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            snapshots.remove(key, cached);
            return null;
        }
        Snapshot fresh = scan(world, chunkX, chunkZ, now);
        snapshots.put(key, fresh);
        if (snapshots.size() > this.maxChunks) {
            purge(snapshots, now);
        }
        return fresh;
    }

    private Snapshot scan(World world, int chunkX, int chunkZ, long now) {
        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        int[] buffer = new int[MAX_TRACKED_PER_CHUNK * 3];
        Location cursor = new Location(world, 0, 0, 0);
        int size = 0;
        for (Entity entity : chunk.getEntities()) {
            if (size >= buffer.length) {
                break;
            }
            if (!this.types.contains(entity.getType()) || entity.isDead()) {
                continue;
            }
            entity.getLocation(cursor);
            buffer[size++] = cursor.getBlockX();
            buffer[size++] = cursor.getBlockY();
            buffer[size++] = cursor.getBlockZ();
        }
        return new Snapshot(now, size == buffer.length ? buffer : Arrays.copyOf(buffer, size));
    }

    private void purge(Map<Long, Snapshot> snapshots, long now) {
        Iterator<Map.Entry<Long, Snapshot>> iterator = snapshots.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().stamp >= this.ttlNanos) {
                iterator.remove();
            }
        }
        if (snapshots.size() <= this.maxChunks) {
            return;
        }
        long[] stamps = new long[snapshots.size()];
        int size = 0;
        for (Snapshot snapshot : snapshots.values()) {
            if (size == stamps.length) {
                break;
            }
            stamps[size++] = snapshot.stamp;
        }
        if (size == 0) {
            return;
        }
        Arrays.sort(stamps, 0, size);
        long cutoff = stamps[size / 2];
        snapshots.values().removeIf(snapshot -> snapshot.stamp <= cutoff);
    }

    private record Snapshot(long stamp, int[] positions) {
    }
}
