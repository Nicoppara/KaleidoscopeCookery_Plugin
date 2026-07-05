package net.kaleidoscope.cookery.nms;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public interface NmsBridge {
    Object litBlockStateProperty();

    Object bukkitEntity(Object nmsEntity);

    Object nmsHandle(Entity entity);

    boolean isSpectateTeleportPacket(Object packet);

    boolean isPerformRespawnPacket(Object packet);

    default Player bukkitPlayer(Object nmsEntity) {
        Object bukkit = bukkitEntity(nmsEntity);
        return bukkit instanceof Player player ? player : null;
    }
}
