package net.kaleidoscope.cookery.block.entity;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplayPackets;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplaySet;
import net.kaleidoscope.cookery.block.entity.render.PacketBundles;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.momirealms.craftengine.core.entity.furniture.element.FurnitureElement;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.MiscUtils;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacketProxy;
import net.momirealms.craftengine.bukkit.util.EntityUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class MillstoneElement implements FurnitureElement {
    private final MillstoneController controller;
    private final WorldPosition basePos;
    private final MillstoneModel model;

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

    // 源区块缓存 家具不动只算一次 用于动画视距过滤
    private final int srcChunkX;
    private final int srcChunkZ;

    public MillstoneElement(@NotNull MillstoneController controller, @NotNull WorldPosition position) {
        this.controller = controller;
        this.basePos = position;
        this.srcChunkX = ((int) Math.floor(position.x)) >> 4;
        this.srcChunkZ = ((int) Math.floor(position.z)) >> 4;
        this.model = new MillstoneModel(controller.behavior());

        this.stick1Id = EntityUtils.ENTITY_COUNTER.incrementAndGet();
        this.stick1Uuid = UUID.randomUUID();

        this.stick2Id = EntityUtils.ENTITY_COUNTER.incrementAndGet();
        this.stick2Uuid = UUID.randomUUID();

        this.stoneId = EntityUtils.ENTITY_COUNTER.incrementAndGet();
        this.stoneUuid = UUID.randomUUID();

        refreshPackets();
    }

    private float baseYawRad() {
        return MillstoneAnimation.baseYawRad(controller.furniture().position().yRot());
    }

    public void refreshPackets() {
        this.spawnPacket1 = model.spawn(basePos, stick1Id, stick1Uuid);
        this.spawnPacket2 = model.spawn(basePos, stick2Id, stick2Uuid);
        this.spawnPacket3 = model.spawn(basePos, stoneId, stoneUuid);
        invalidateMeta();
    }

    // 静态 meta 只有新观察者 show 时才用得上 动画每帧重建等于每帧白建三个包
    private void invalidateMeta() {
        this.metaPacket1 = null;
        this.metaPacket2 = null;
        this.metaPacket3 = null;
    }

    private void ensureMeta() {
        if (this.metaPacket1 != null) {
            return;
        }
        float yaw = baseYawRad();
        float angle = controller.currentAngle();
        this.metaPacket1 = model.stick1Meta(stick1Id, yaw, angle, 0);
        this.metaPacket2 = model.stick2Meta(stick2Id, yaw, angle, 0);
        this.metaPacket3 = model.stoneMeta(stoneId, yaw, angle, 0);
    }

    public void updateRotation(float targetAngle, int durationTicks) {
        float yaw = baseYawRad();
        Object bundlePacket = PacketBundles.of(List.of(
                model.stick1Meta(stick1Id, yaw, targetAngle, durationTicks),
                model.stick2Meta(stick2Id, yaw, targetAngle, durationTicks),
                model.stoneMeta(stoneId, yaw, targetAngle, durationTicks)));

        for (Player p : TrackedPlayers.snapshotInRange(controller.furniture().getTrackedBy(), srcChunkX, srcChunkZ, controller.behavior().animChunkRadius)) {
            p.sendPacket(bundlePacket, false);
        }
        invalidateMeta();
    }

    public void updateFinalRotation(float angle) {
        float yaw = baseYawRad();
        Object bundlePacket = PacketBundles.of(List.of(
                model.stick1Meta(stick1Id, yaw, angle, 0),
                model.stick2Meta(stick2Id, yaw, angle, 0),
                model.stoneMeta(stoneId, yaw, angle, 0)));
        TrackedPlayers.forEach(controller.furniture().getTrackedBy(),
                player -> player.sendPacket(bundlePacket, false));
        invalidateMeta();
    }

    private void buildGrindSlotPackets(int slot, Item item) {
        Object spawn = ItemDisplayPackets.at(basePos).spawn(grindDisplay.id(slot), grindDisplay.uuid(slot));
        grindDisplay.setPackets(slot, spawn, model.grindStaticMeta(grindDisplay.id(slot), item));
        grindRot[slot] = model.grindRotMeta(grindDisplay.id(slot), slot);
    }

    private void sendGrindSlot(Player p, int slot) {
        if (grindDisplay.spawn(slot) == null) {
            return;
        }
        List<Object> pk = new ArrayList<>();
        pk.add(grindDisplay.spawn(slot));
        pk.add(grindDisplay.meta(slot));
        if (grindRot[slot] != null) {
            pk.add(grindRot[slot]);
        }
        p.sendPacket(PacketBundles.of(pk), false);
    }

    private void sendAllGrind(Player p) {
        List<Object> all = new ArrayList<>();
        for (int i = 0; i < GRIND_SLOTS; i++) {
            if (!controller.grindItem(i).isEmpty() && grindDisplay.spawn(i) != null) {
                all.add(grindDisplay.spawn(i));
                all.add(grindDisplay.meta(i));
                if (grindRot[i] != null) {
                    all.add(grindRot[i]);
                }
            }
        }
        if (!all.isEmpty()) {
            p.sendPacket(PacketBundles.of(all), false);
        }
    }

    public void spawnGrindSlot(int slot, Item item) {
        buildGrindSlotPackets(slot, item);
        for (Player p : controller.furniture().getTrackedBy()) {
            sendGrindSlot(p, slot);
        }
    }

    public void removeGrindSlot(int slot) {
        for (Player p : controller.furniture().getTrackedBy()) {
            grindDisplay.removeSlot(p, slot);
        }
        grindDisplay.clear(slot);
        grindRot[slot] = null;
    }

    // 按槽差异分派 别整体 removeAll 再重建 那会把未变化槽的旋转插值一并清零
    public void refreshAllGrind() {
        for (int i = 0; i < GRIND_SLOTS; i++) {
            boolean spawned = grindDisplay.spawn(i) != null;
            if (controller.grindItem(i).isEmpty()) {
                if (spawned) {
                    removeGrindSlot(i);
                }
                continue;
            }
            buildGrindSlotPackets(i, controller.grindItem(i));
            for (Player p : controller.furniture().getTrackedBy()) {
                if (spawned) {
                    p.sendPacket(grindDisplay.meta(i), false);
                } else {
                    sendGrindSlot(p, i);
                }
            }
        }
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
            ensureMeta();
            PacketBundles.send(player, List.of(
                    spawnPacket1, metaPacket1,
                    spawnPacket2, metaPacket2,
                    spawnPacket3, metaPacket3));
        }
        sendAllGrind(player);
    }

    @Override
    public void hide(@NotNull Player player) {
        IntList ids = MiscUtils.init(new IntArrayList(), a -> {
            a.add(stick1Id);
            a.add(stick2Id);
            a.add(stoneId);
        });
        player.sendPacket(ClientboundRemoveEntitiesPacketProxy.INSTANCE.newInstance(ids), false);
        grindDisplay.removeAll(player);
    }

    @Override
    public void update(@NotNull Player player) {
        // 内容更新由控制器主动推送 不在此重建实体 避免丢旋转插值状态 参考 PotElement
    }
}
