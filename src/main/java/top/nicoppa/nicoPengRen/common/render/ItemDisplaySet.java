package top.nicoppa.nicoPengRen.common.render;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacketProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.EntityProxy;

import java.util.List;
import java.util.UUID;

/**
 * 展示实体槽集合 封装一组假 ItemDisplay 的 entityId/uuid 与已构建的生成/元数据包
 * 仅管理身份与收发，具体包内容由各 element 按自身样式构建后通过 {@link #setPackets} 存入
 */
public final class ItemDisplaySet {
    private final int[] entityIds;
    private final UUID[] uuids;
    private final Object[] spawnPackets;
    private final Object[] metaPackets;

    public ItemDisplaySet(int size) {
        this.entityIds = new int[size];
        this.uuids = new UUID[size];
        this.spawnPackets = new Object[size];
        this.metaPackets = new Object[size];
        for (int i = 0; i < size; i++) {
            this.entityIds[i] = EntityProxy.ENTITY_COUNTER.incrementAndGet();
            this.uuids[i] = UUID.randomUUID();
        }
    }

    public int size() {
        return entityIds.length;
    }

    public int id(int index) {
        return entityIds[index];
    }

    public UUID uuid(int index) {
        return uuids[index];
    }

    public void setPackets(int index, Object spawnPacket, Object metaPacket) {
        this.spawnPackets[index] = spawnPacket;
        this.metaPackets[index] = metaPacket;
    }

    public Object spawn(int index) {
        return spawnPackets[index];
    }

    public Object meta(int index) {
        return metaPackets[index];
    }

    public void clear(int index) {
        this.spawnPackets[index] = null;
        this.metaPackets[index] = null;
    }

    // 若该槽已构建生成包，则向玩家发送 生成包 + 元数据包
    public void showSlot(Player player, int index) {
        if (spawnPackets[index] != null) {
            player.sendPackets(List.of(spawnPackets[index], metaPackets[index]), false);
        }
    }

    public void removeSlot(Player player, int index) {
        IntArrayList ids = new IntArrayList(1);
        ids.add(entityIds[index]);
        player.sendPacket(ClientboundRemoveEntitiesPacketProxy.INSTANCE.newInstance(ids), false);
    }

    public void removeAll(Player player) {
        IntArrayList ids = new IntArrayList(entityIds.length);
        for (int id : entityIds) ids.add(id);
        player.sendPacket(ClientboundRemoveEntitiesPacketProxy.INSTANCE.newInstance(ids), false);
    }
}
