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
        teleport(entity, location, () -> {});
    }

    public static void teleport(Entity entity, Location location, Runnable afterTeleport) {
        if (FOLIA) {
            runEntity(entity, () -> entity.teleportAsync(location).thenAccept(success -> {
                if (success) {
                    runEntity(entity, afterTeleport);
                }
            }));
        } else {
            if (entity.teleport(location)) {
                afterTeleport.run();
            }
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
