package top.nicoppa.nicoPengRen.content.millstone;

import it.unimi.dsi.fastutil.ints.IntList;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.core.entity.furniture.element.FurnitureElement;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.MiscUtils;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacketProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.EntityProxy;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import top.nicoppa.nicoPengRen.common.render.ItemDisplayPackets;
import top.nicoppa.nicoPengRen.common.render.ItemDisplaySet;
import top.nicoppa.nicoPengRen.common.render.PacketBundles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 石磨渲染元素 用三个 ItemDisplay 表现拉磨动画，
 * 按 controller 的角度构建旋转与位移数据包。数据来源见 {@link MillstoneController}
 */
public final class MillstoneElement implements FurnitureElement {
    private final MillstoneController controller;
    private final WorldPosition basePos;

    private final int stick1Id;
    private final UUID stick1Uuid;
    private final int stick2Id;
    private final UUID stick2Uuid;
    private final int stoneId;
    private final UUID stoneUuid;

    private Object spawnPacket1;
    private Object metaPacket1;
    private Object spawnPacket2;
    private Object metaPacket2;
    private Object spawnPacket3;
    private Object metaPacket3;

    private static final int GRIND_SLOTS = MillstoneController.GRIND_SLOTS;
    private final ItemDisplaySet grindDisplay = new ItemDisplaySet(GRIND_SLOTS);
    private final Object[] grindRot = new Object[GRIND_SLOTS];

    public MillstoneElement(@NotNull MillstoneController controller, @NotNull WorldPosition position) {
        this.controller = controller;
        this.basePos = position;

        this.stick1Id = EntityProxy.ENTITY_COUNTER.incrementAndGet();
        this.stick1Uuid = UUID.randomUUID();

        this.stick2Id = EntityProxy.ENTITY_COUNTER.incrementAndGet();
        this.stick2Uuid = UUID.randomUUID();

        this.stoneId = EntityProxy.ENTITY_COUNTER.incrementAndGet();
        this.stoneUuid = UUID.randomUUID();

        refreshPackets();
    }

    private float getBaseYawRad() {
        return (float) Math.toRadians(-controller.furniture().position().yRot() - 90);
    }

    public void refreshPackets() {
        this.spawnPacket1 = ItemDisplayPackets.at(basePos).spawn(stick1Id, stick1Uuid);
        this.spawnPacket2 = ItemDisplayPackets.at(basePos).spawn(stick2Id, stick2Uuid);
        this.spawnPacket3 = ItemDisplayPackets.at(basePos).spawn(stoneId, stoneUuid);

        this.metaPacket1 = buildStick1MetaPacket(controller.getCurrentAngle(), 0);
        this.metaPacket2 = buildStick2MetaPacket(controller.getCurrentAngle(), 0);
        this.metaPacket3 = buildStoneMetaPacket(controller.getCurrentAngle(), 0);
    }

    private Object buildStick1MetaPacket(float angleY, int durationTicks) {
        float heightOffset = 0.9f;
        float xOffset = 0f;
        float zOffset = 0f;

        Item stickItem = BukkitItemManager.instance().createWrappedItem(controller.behavior().stickItem, null);

        Vector3f translation = new Vector3f(xOffset, heightOffset, zOffset);

        translation.rotateY(getBaseYawRad());

        Quaternionf rotation = new Quaternionf()
                .rotateY(getBaseYawRad())
                .rotateY((float) Math.toRadians(angleY));

        ItemDisplayPackets packets = ItemDisplayPackets.builder()
                .item(stickItem)
                .scale(1.0f, 1.0f, 1.0f)
                .translation(translation.x, translation.y, translation.z)
                .leftRotation(rotation)
                .itemTransform((byte) 8);
        if (durationTicks > 0) {
            packets.interpolation(durationTicks, 0);
        }
        return packets.meta(stick1Id);
    }

    private Object buildStick2MetaPacket(float angleY, int durationTicks) {
        float xOffset = 0f;
        float heightOffset = 0.6f;
        float zOffset = 0f;

        Item stickItem = BukkitItemManager.instance().createWrappedItem(controller.behavior().stick2Item, null);

        float radians = (float) Math.toRadians(angleY);

        Vector3f rotatedTranslation = new Vector3f(xOffset, heightOffset, zOffset)
                .rotateY(radians)
                .rotateY(getBaseYawRad());

        Quaternionf rotation = new Quaternionf()
                .rotateY(getBaseYawRad())
                .rotateY(radians);

        ItemDisplayPackets packets = ItemDisplayPackets.builder()
                .item(stickItem)
                .translation(rotatedTranslation.x, rotatedTranslation.y, rotatedTranslation.z)
                .scale(1.0f, 1.0f, 1.0f)
                .leftRotation(rotation)
                .itemTransform((byte) 8);

        if (durationTicks > 0) {
            packets.interpolation(durationTicks, 0);
        }

        return packets.meta(stick2Id);
    }

    private Object buildStoneMetaPacket(float angleY, int durationTicks) {
        float xOffset = 0f;
        float heightOffset = 1.4f;
        float zOffset = -0.5f;
        float spinMultiplier = 6.0f;

        Item stoneItem = BukkitItemManager.instance().createWrappedItem(controller.behavior().stoneItem, null);

        float rad = (float) Math.toRadians(angleY);
        float spin = rad * spinMultiplier;

        Vector3f orbit = new Vector3f(xOffset, heightOffset, zOffset);
        orbit.rotateY(rad).rotateY(getBaseYawRad() + (float) Math.PI);

        Quaternionf combinedRot = new Quaternionf()
                .rotateY(-getBaseYawRad())
                .rotateY(rad)
                .rotateZ(spin);

        ItemDisplayPackets packets = ItemDisplayPackets.builder()
                .item(stoneItem)
                .translation(orbit.x, orbit.y, orbit.z)
                .leftRotation(combinedRot)
                .scale(1f, 1f, 1f)
                .itemTransform((byte) 8);

        if (durationTicks > 0) {
            packets.interpolation(durationTicks, 0);
        }

        return packets.meta(stoneId);
    }

    public void updateRotation(float targetAngle, int durationTicks) {
        Object packet1 = buildStick1MetaPacket(targetAngle, durationTicks);
        Object packet2 = buildStick2MetaPacket(targetAngle, durationTicks);
        Object packet3 = buildStoneMetaPacket(targetAngle, durationTicks);

        List<Object> packets = new ArrayList<>();
        packets.add(packet1);
        packets.add(packet2);
        packets.add(packet3);
        Object bundlePacket = PacketBundles.of(packets);

        for (Player p : (controller.furniture()).getTrackedBy()) {
            p.sendPacket(bundlePacket, false);
        }

        this.metaPacket1 = buildStick1MetaPacket(targetAngle, 0);
        this.metaPacket2 = buildStick2MetaPacket(targetAngle, 0);
        this.metaPacket3 = buildStoneMetaPacket(targetAngle, 0);
    }

    private Object buildGrindStatic(int slot, Item item) {
        return ItemDisplayPackets.builder()
                .item(item).scale(0.65f).itemTransform((byte) 8)
                .meta(grindDisplay.id(slot));
    }

    private Object buildGrindRot(int slot) {
        float rad = (float) Math.toRadians(slot * 45f);
        Vector3f tr = new Vector3f(0.6f, 0.9f, 0f).rotateY(rad);
        Quaternionf rot = new Quaternionf().rotateY(rad).rotateX((float) Math.toRadians(-80));
        return ItemDisplayPackets.builder()
                .translation(tr.x, tr.y, tr.z)
                .leftRotation(rot)
                .meta(grindDisplay.id(slot));
    }

    private void buildGrindSlotPackets(int slot, Item item) {
        Object spawn = ItemDisplayPackets.at(basePos).spawn(grindDisplay.id(slot), grindDisplay.uuid(slot));
        grindDisplay.setPackets(slot, spawn, buildGrindStatic(slot, item));
        grindRot[slot] = buildGrindRot(slot);
    }

    private void sendGrindSlot(Player p, int slot) {
        if (grindDisplay.spawn(slot) == null) return;
        List<Object> pk = new ArrayList<>();
        pk.add(grindDisplay.spawn(slot));
        pk.add(grindDisplay.meta(slot));
        if (grindRot[slot] != null) pk.add(grindRot[slot]);
        p.sendPacket(PacketBundles.of(pk), false);
    }

    private void sendAllGrind(Player p) {
        Item[] items = controller.getGrindItems();
        List<Object> all = new ArrayList<>();
        for (int i = 0; i < GRIND_SLOTS; i++) {
            if (!items[i].isEmpty() && grindDisplay.spawn(i) != null) {
                all.add(grindDisplay.spawn(i));
                all.add(grindDisplay.meta(i));
                if (grindRot[i] != null) all.add(grindRot[i]);
            }
        }
        if (!all.isEmpty()) p.sendPacket(PacketBundles.of(all), false);
    }

    public void spawnGrindSlot(int slot, Item item) {
        buildGrindSlotPackets(slot, item);
        for (Player p : controller.furniture().getTrackedBy()) sendGrindSlot(p, slot);
    }

    public void removeGrindSlot(int slot) {
        for (Player p : controller.furniture().getTrackedBy()) grindDisplay.removeSlot(p, slot);
        grindDisplay.clear(slot);
        grindRot[slot] = null;
    }

    public void refreshAllGrind() {
        for (Player p : controller.furniture().getTrackedBy()) grindDisplay.removeAll(p);
        Item[] items = controller.getGrindItems();
        for (int i = 0; i < GRIND_SLOTS; i++) {
            grindDisplay.clear(i);
            grindRot[i] = null;
            if (!items[i].isEmpty()) buildGrindSlotPackets(i, items[i]);
        }
        for (Player p : controller.furniture().getTrackedBy()) sendAllGrind(p);
    }

    @Override
    public void refresh(Player player) {
        List<Object> packets = new ArrayList<>();
        packets.add(metaPacket1);
        packets.add(metaPacket2);
        packets.add(metaPacket3);
        player.sendPacket(PacketBundles.of(packets), false);
        sendAllGrind(player);
    }

    @Override
    public void gatherInteractableEntityId(Consumer<Integer> collector) {
        collector.accept(stick1Id);
        collector.accept(stick2Id);
        collector.accept(stoneId);
    }

    @Override
    public void show(@NotNull Player player) {
        if (spawnPacket1 != null) {
            List<Object> packets = new ArrayList<>();
            packets.add(spawnPacket1);
            packets.add(metaPacket1);
            packets.add(spawnPacket2);
            packets.add(metaPacket2);
            packets.add(spawnPacket3);
            packets.add(metaPacket3);

            PacketBundles.send(player, packets);
        }
        sendAllGrind(player);
    }

    @Override
    public void hide(@NotNull Player player) {
        IntList ids = MiscUtils.init(new it.unimi.dsi.fastutil.ints.IntArrayList(), a -> {
            a.add(stick1Id);
            a.add(stick2Id);
            a.add(stoneId);
        });
        player.sendPacket(ClientboundRemoveEntitiesPacketProxy.INSTANCE.newInstance(ids), false);
        grindDisplay.removeAll(player);
    }

    public void update(@NotNull Player player) {
        hide(player);
        show(player);
    }
}
