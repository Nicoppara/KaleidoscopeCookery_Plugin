package net.kaleidoscope.cookery.util;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;

public final class FoliaUtil {
    private static final boolean FOLIA = detectFolia();

    private FoliaUtil() {}

    public static boolean isFolia() {
        return FOLIA;
    }

    public static void runEntity(Entity entity, Runnable task) {
        runEntity(entity, task, () -> {});
    }

    public static void runEntity(Entity entity, Runnable task, Runnable retired) {
        if (FOLIA) {
            BukkitCraftEngine.instance().scheduler().platform().run(task, retired, entity);
        } else {
            task.run();
        }
    }

    public static void teleport(Entity entity, Location location) {
        teleportThen(entity, location, null);
    }

    // 传送后依赖新位置的状态设置必须走 after folia 下 teleportAsync 是异步的
    // 直接写在调用点后面会在传送落地前就执行 被随后到达的传送覆盖
    // 在 folia 下运行于目标 region 线程 传送被拒绝时不执行
    public static void teleportThen(Entity entity, Location location, Runnable after) {
        if (FOLIA) {
            entity.teleportAsync(location).thenAccept(success -> {
                if (success && after != null) {
                    after.run();
                }
            });
            return;
        }
        entity.teleport(location);
        if (after != null) {
            after.run();
        }
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
