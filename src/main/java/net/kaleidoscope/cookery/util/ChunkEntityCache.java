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

// 按区块缓存指定类型实体的坐标 读时惰性刷新 没被问过的区块永远不扫
public final class ChunkEntityCache {
    private static final int MAX_TRACKED_PER_CHUNK = 64;
    private static final long NANOS_PER_TICK = 50L * 1_000_000L;

    private final Set<EntityType> types;
    private final long ttlNanos;
    private final int maxChunks;
    // 区块键只有坐标 不带世界 必须按世界分表 否则多世界同坐标的区块会互相覆盖快照
    private final Map<UUID, Map<Long, Snapshot>> byWorld = new ConcurrentHashMap<>();

    public ChunkEntityCache(Set<EntityType> types, int ttlTicks, int maxChunks) {
        this.types = Set.copyOf(types);
        this.ttlNanos = ttlTicks * NANOS_PER_TICK;
        this.maxChunks = Math.max(1, maxChunks);
    }

    // 数以 x y z 为中心 水平半径 radius 内 与该格同高的目标实体个数
    public int countAround(World world, int x, int y, int z, int radius) {
        if (this.types.isEmpty()) {
            return 0;
        }
        Snapshot snapshot = snapshot(world, x >> 4, z >> 4);
        if (snapshot == null) {
            return 0;
        }
        int[] positions = snapshot.positions;
        int count = 0;
        for (int i = 0; i < positions.length; i += 3) {
            int dy = positions[i + 1] - y;
            if (dy < 0 || dy > 1) {
                continue;
            }
            if (Math.abs(positions[i] - x) <= radius && Math.abs(positions[i + 2] - z) <= radius) {
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
        // 不归当前 region 就用旧快照 宁可少加速也不能抛
        if (!Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ)) {
            return cached;
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
        // 复用同一个 Location 一次扫描不为每个实体各建一个
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

    // 先清过期的 全都新鲜时按时间戳中位数一趟砍掉较旧的一半
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
        Arrays.sort(stamps, 0, size);
        long cutoff = stamps[size / 2];
        snapshots.values().removeIf(snapshot -> snapshot.stamp <= cutoff);
    }

    // 只存坐标不存实体引用 跨区域传送后非玩家实体的实例会被整个替换
    private record Snapshot(long stamp, int[] positions) {
    }
}
