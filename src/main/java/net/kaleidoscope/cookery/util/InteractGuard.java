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

    // 打开 UI 视作开容器 留言板这类用 OPEN_CONTAINER 权限
    public static boolean canOpenContainer(Player player, WorldPosition pos) {
        return test(player, Flag.OPEN_CONTAINER, (org.bukkit.World) pos.world().platformWorld(), pos.x, pos.y, pos.z);
    }

    private static boolean test(Player player, Flag flag, org.bukkit.World world, double x, double y, double z) {
        if (player == null) {
            return false;
        }
        Location loc = new Location(world, x, y, z);
        return KaleidoscopeCookeryPlugin.antiGrief().test(
                (org.bukkit.entity.Player) player.platformPlayer(), flag, loc);
    }
}
