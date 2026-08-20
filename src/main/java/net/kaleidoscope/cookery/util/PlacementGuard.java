package net.kaleidoscope.cookery.util;

import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.world.CEWorld;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// CE 在 BlockPlaceEvent 前写入方块；事件取消后的状态回滚会触发 onRemove
// 放置期间使用短期标记，避免回滚重复生成掉落物
public final class PlacementGuard {
    private PlacementGuard() {}

    private static final Set<PosKey> PENDING = ConcurrentHashMap.newKeySet();

    public static void beginPlace(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        PosKey key = new PosKey(world.getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        PENDING.add(key);
        // 回滚都发生在放置的同一 tick 内 下一 tick 清掉即可
        // 走 CE 调度器落到该坐标所属 region folia 上不能拿 Bukkit 调度器
        FoliaUtil.runLater(
                () -> PENDING.remove(key), 1L,
                BukkitAdaptor.adapt(world), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    // 该方块实体是不是正处在一次尚未落定的放置里
    public static boolean isPlacing(BlockEntity blockEntity) {
        // 绝大多数移除都不在放置窗口内 先用一次空表判断走掉
        if (PENDING.isEmpty()) {
            return false;
        }
        CEWorld world = blockEntity.world;
        if (world == null) {
            return false;
        }
        return PENDING.contains(new PosKey(world.world().uuid(),
                blockEntity.pos.x(), blockEntity.pos.y(), blockEntity.pos.z()));
    }

    public static void clear() {
        PENDING.clear();
    }

    private record PosKey(UUID world, int x, int y, int z) {
    }
}
