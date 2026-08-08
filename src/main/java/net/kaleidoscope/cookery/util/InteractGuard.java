package net.kaleidoscope.cookery.util;

import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.momirealms.antigrieflib.Flag;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.WorldPosition;
import org.bukkit.Location;

// 交互前置校验
public final class InteractGuard {
    private InteractGuard() {}

    // 玩家为空或没有该位置的 INTERACT 权限返回 false 调用方应直接 PASS 方块用
    public static boolean canInteract(Player player, World level, BlockPos pos) {
        return test(player, Flag.INTERACT, (org.bukkit.World) level.platformWorld(), pos.x(), pos.y(), pos.z());
    }

    // 家具用
    public static boolean canInteract(Player player, WorldPosition pos) {
        return test(player, Flag.INTERACT, (org.bukkit.World) pos.world().platformWorld(), pos.x, pos.y, pos.z);
    }

    // 自定义方块在写入世界前校验放置权限，避免先放置再由领地插件回滚。
    public static boolean canPlace(Player player, World level, BlockPos pos) {
        return test(player, Flag.PLACE, (org.bukkit.World) level.platformWorld(), pos.x(), pos.y(), pos.z());
    }

    // 打开 UI 视作开容器 用 OPEN_CONTAINER 权限
    public static boolean canOpenContainer(Player player, WorldPosition pos) {
        return test(player, Flag.OPEN_CONTAINER, (org.bukkit.World) pos.world().platformWorld(), pos.x, pos.y, pos.z);
    }

    // 范围破坏方块用 BREAK 权限 逐格校验 别整片一刀切
    public static boolean canBreak(Player player, World level, int x, int y, int z) {
        return test(player, Flag.BREAK, (org.bukkit.World) level.platformWorld(), x, y, z);
    }

    // CE 事件回调里拿到的是 Bukkit 对象 没有 CE Player 可用
    public static boolean canPlace(org.bukkit.entity.Player player, Location location) {
        return test(player, Flag.PLACE, location);
    }

    // 对实体动手也算交互 拴绳骑乘之类按实体所在位置判
    public static boolean canInteract(org.bukkit.entity.Player player, Location location) {
        return test(player, Flag.INTERACT, location);
    }

    private static boolean test(Player player, Flag flag, org.bukkit.World world, double x, double y, double z) {
        if (player == null) {
            return false;
        }
        return test((org.bukkit.entity.Player) player.platformPlayer(), flag, new Location(world, x, y, z));
    }

    private static boolean test(org.bukkit.entity.Player player, Flag flag, Location location) {
        if (player == null) {
            return false;
        }
        return KaleidoscopeCookeryPlugin.antiGrief().test(player, flag, location);
    }
}
