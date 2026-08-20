package net.kaleidoscope.cookery.util;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.bukkit.plugin.scheduler.impl.AbstractBukkitExecutor;
import net.momirealms.craftengine.core.plugin.scheduler.SchedulerTask;
import net.momirealms.craftengine.core.world.World;

public final class FoliaUtil {
    private static final boolean FOLIA = detectFolia();
    // 插件禁用后已排队的任务不再执行 region/实体调度器没有批量取消入口
    // 一次性任务全都短延迟 与其维护任务集合逐个 cancel 不如让任务体自己空转
    private static volatile boolean disabled;

    private FoliaUtil() {}

    public static boolean isFolia() {
        return FOLIA;
    }

    public static void shutdown() {
        disabled = true;
    }

    private static AbstractBukkitExecutor scheduler() {
        return BukkitCraftEngine.instance().scheduler().platform();
    }

    private static Runnable guarded(Runnable task) {
        return () -> {
            if (!disabled) {
                task.run();
            }
        };
    }

    // 以下是 CE 调度器的直通封装 只多一道禁用门
    // 与 runEntity 不同，这些任务在 Paper 上同样入队以维持延迟语义
    public static void run(Runnable task, Location location) {
        scheduler().run(guarded(task), location);
    }

    public static void run(Runnable task, World world, int chunkX, int chunkZ) {
        scheduler().run(guarded(task), world, chunkX, chunkZ);
    }

    public static void run(Runnable task, Runnable retired, Entity entity) {
        scheduler().run(guarded(task), retired, entity);
    }

    public static SchedulerTask runLater(Runnable task, long delay, Location location) {
        return scheduler().runLater(guarded(task), delay, location);
    }

    public static SchedulerTask runLater(Runnable task, long delay, World world, int chunkX, int chunkZ) {
        return scheduler().runLater(guarded(task), delay, world, chunkX, chunkZ);
    }

    public static SchedulerTask runLater(Runnable task, Runnable retired, long delay, Entity entity) {
        return scheduler().runLater(guarded(task), retired, delay, entity);
    }

    // 本区域直接跑 否则投递到实体所属 region 与上面几个直通封装不同 paper 上是同步执行
    public static void runEntity(Entity entity, Runnable task) {
        runEntity(entity, task, () -> {});
    }

    public static void runEntity(Entity entity, Runnable task, Runnable retired) {
        if (FOLIA) {
            scheduler().run(guarded(task), retired, entity);
        } else {
            task.run();
        }
    }

    public static void teleport(Entity entity, Location location) {
        teleportThen(entity, location, null);
    }

    // 依赖新位置的后续设置必须走 after folia 下 teleportAsync 是异步的
    // 写在调用点后面会在落地前就执行 after 跑在目标 region 线程 传送被拒则不执行
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
