package net.kaleidoscope.cookery.nms.v1_21_R6;

import net.kaleidoscope.cookery.nms.NmsBridge;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.bukkit.craftbukkit.v1_21_R6.entity.CraftEntity;
import org.bukkit.entity.Entity;

public final class NmsV1_21_R6 implements NmsBridge {
    @Override
    public Object litBlockStateProperty() {
        return BlockStateProperties.LIT;
    }

    @Override
    public Object bukkitEntity(Object nmsEntity) {
        if (nmsEntity instanceof net.minecraft.world.entity.Entity entity) {
            return entity.getBukkitEntity();
        }
        return null;
    }

    @Override
    public Object nmsHandle(Entity entity) {
        if (entity instanceof CraftEntity craftEntity) {
            return craftEntity.getHandle();
        }
        return null;
    }

    @Override
    public boolean isSpectateTeleportPacket(Object packet) {
        return packet instanceof ServerboundTeleportToEntityPacket;
    }

    @Override
    public boolean isPerformRespawnPacket(Object packet) {
        return packet instanceof ServerboundClientCommandPacket command
                && command.getAction() == ServerboundClientCommandPacket.Action.PERFORM_RESPAWN;
    }
}
