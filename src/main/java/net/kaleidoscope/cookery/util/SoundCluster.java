package net.kaleidoscope.cookery.util;

import org.bukkit.World;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

// 同类环境音的空间聚合 一片方块按网格合成一个代表声源
// 被压掉的次数按 log10 折成额外音量 声压本就是对数叠加 十个源响一档一百个响两档
public final class SoundCluster {
    private static final long NANOS_PER_TICK = 50L * 1_000_000L;
    // 超过冷却这么多倍没再响过就算这片田停了
    private static final int STALE_FACTOR = 4;

    private final int cellShift;
    private final long cooldownNanos;
    private final float volumeStep;
    private final float maxBonus;
    private final int maxCells;
    // folia 上随机 tick 跑在各 region 线程 这张表是跨线程共享的
    private final Map<UUID, Map<Long, Cell>> cells = new ConcurrentHashMap<>();

    public SoundCluster(int cellSize, int cooldownTicks, float volumeStep, float maxBonus, int maxCells) {
        this.cellShift = Math.max(0, Integer.numberOfTrailingZeros(Integer.highestOneBit(Math.max(1, cellSize))));
        this.cooldownNanos = cooldownTicks * NANOS_PER_TICK;
        this.volumeStep = volumeStep;
        this.maxBonus = maxBonus;
        this.maxCells = Math.max(1, maxCells);
    }

    // 记一次触发 返回该次应叠加的额外音量 返回负数表示这次被聚合掉了不发声
    public float accumulate(World world, int x, int y, int z) {
        Map<Long, Cell> byCell = this.cells.computeIfAbsent(world.getUID(), k -> new ConcurrentHashMap<>());
        Cell cell = byCell.computeIfAbsent(pack(x >> this.cellShift, y >> this.cellShift, z >> this.cellShift),
                k -> new Cell());
        long now = System.nanoTime();
        long last = cell.last.get();
        // 冷却内的一律记账不发声 同一格同时撞进来时让 CAS 赢的那个发
        if (now - last < this.cooldownNanos || !cell.last.compareAndSet(last, now)) {
            cell.pending.incrementAndGet();
            return -1f;
        }
        if (byCell.size() > this.maxCells) {
            purge(byCell, now);
        }
        return Math.min(this.maxBonus, this.volumeStep * (float) Math.log10(1 + cell.pending.getAndSet(0)));
    }

    // 先清停了很久的 全都新鲜时按时间戳中位数砍掉较旧的一半
    // 必须保证每次都真的清掉东西 否则超过上限后每次 accumulate 都要全表扫一遍 跑在 region 线程上
    private void purge(Map<Long, Cell> byCell, long now) {
        Iterator<Map.Entry<Long, Cell>> iterator = byCell.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().last.get() >= this.cooldownNanos * STALE_FACTOR) {
                iterator.remove();
            }
        }
        if (byCell.size() <= this.maxCells) {
            return;
        }
        long[] stamps = new long[byCell.size()];
        int size = 0;
        for (Cell cell : byCell.values()) {
            if (size == stamps.length) {
                break;
            }
            stamps[size++] = cell.last.get();
        }
        Arrays.sort(stamps, 0, size);
        long cutoff = stamps[size / 2];
        byCell.values().removeIf(cell -> cell.last.get() <= cutoff);
    }

    // x z 各 26 位 y 12 位 刚好铺满 long
    // 21 位装不下世界边界 负坐标取低位后会和远处的正坐标撞成同一格 两片田共用一个冷却
    private static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    private static final class Cell {
        private final AtomicLong last = new AtomicLong();
        private final AtomicInteger pending = new AtomicInteger();
    }
}
