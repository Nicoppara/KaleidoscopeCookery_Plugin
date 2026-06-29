package net.kaleidoscope.cookery.block.entity.render;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.momirealms.craftengine.bukkit.entity.data.item.ItemEntityData;
import net.momirealms.craftengine.bukkit.util.PacketUtils;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.MiscUtils;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundAddEntityPacketProxy;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacketProxy;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundSetEntityDataPacketProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.EntityProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.EntityTypeProxy;
import net.momirealms.craftengine.proxy.minecraft.world.phys.Vec3Proxy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 掉落物渲染
public final class DroppedItemDisplay {
    private final int vehicleId;
    private final int passengerId;
    private final UUID vehicleUUID = UUID.randomUUID();
    private final UUID passengerUUID = UUID.randomUUID();
    private final Object ridePacket;
    private final Object despawnPacket;
    private Object spawnVehicle;
    private Object spawnPassenger;
    private Object itemDataPacket;
    private boolean present;

    public DroppedItemDisplay() {
        this.vehicleId = EntityProxy.ENTITY_COUNTER.incrementAndGet();
        this.passengerId = EntityProxy.ENTITY_COUNTER.incrementAndGet();
        this.ridePacket = PacketUtils.createClientboundSetPassengersPacket(vehicleId, passengerId);
        this.despawnPacket = ClientboundRemoveEntitiesPacketProxy.INSTANCE.newInstance(
                MiscUtils.init(new IntArrayList(), a -> {
                    a.add(vehicleId);
                    a.add(passengerId);
                }));
    }

    public boolean present() {
        return present;
    }

    // 设置展示位置与物品 空物品则标记为不展示
    public void set(WorldPosition pos, Item item) {
        if (ItemUtils.isEmpty(item)) {
            present = false;
            return;
        }
        this.spawnVehicle = ClientboundAddEntityPacketProxy.INSTANCE.newInstance(
                vehicleId, vehicleUUID, pos.x, pos.y, pos.z, 0, 0, EntityTypeProxy.ITEM_DISPLAY, 0, Vec3Proxy.ZERO, 0);
        this.spawnPassenger = ClientboundAddEntityPacketProxy.INSTANCE.newInstance(
                passengerId, passengerUUID, pos.x, pos.y, pos.z, 0, 0, EntityTypeProxy.ITEM, 0, Vec3Proxy.ZERO, 0);
        List<Object> data = new ArrayList<>();
        ItemEntityData.Item.addEntityData(item.minecraftItem(), data);
        this.itemDataPacket = ClientboundSetEntityDataPacketProxy.INSTANCE.newInstance(passengerId, data);
        this.present = true;
    }

    public void show(Player player) {
        if (present) {
            player.sendPackets(List.of(spawnVehicle, spawnPassenger, ridePacket, itemDataPacket), false);
        }
    }

    public void updateData(Player player) {
        if (present && itemDataPacket != null) {
            player.sendPacket(itemDataPacket, false);
        }
    }

    public void hide(Player player) {
        player.sendPacket(despawnPacket, false);
    }
}
