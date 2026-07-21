package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.ShawarmaSpitBehavior;

import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacketProxy;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplayPackets;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplaySet;
import net.kaleidoscope.cookery.block.entity.render.PacketBundles;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;

import java.util.ArrayList;
import java.util.List;

public final class ShawarmaSpitElement implements BlockEntityElement {
    private static final int LAYERS = ShawarmaSpitController.LAYERS;
    private static final int SLOTS = ShawarmaSpitController.SLOTS;
    private static final int TOTAL = LAYERS * SLOTS;
    private static final float ITEM_SCALE = 0.65f;
    private static final byte ITEM_TRANSFORM_FIXED = 8;

    private final ShawarmaSpitController controller;
    private final ShawarmaSpitBehavior behavior;
    private final ItemDisplaySet display = new ItemDisplaySet(TOTAL);
    private final Object[] rotationPackets = new Object[TOTAL];

    public ShawarmaSpitElement(@NotNull ShawarmaSpitController controller, ShawarmaSpitBehavior behavior) {
        this.controller = controller;
        this.behavior = behavior;
    }

    private static int idx(int layer, int slot) {
        return layer * SLOTS + slot;
    }

    // 各层渲染高度 下层 0.875 上层 1.5
    private float yOffset(int layer) {
        return layer == 1 ? 1.5f : 0.875f;
    }

    private Object buildItemMeta(int id, Item item) {
        return ItemDisplayPackets.builder()
                .item(item)
                .scale(ITEM_SCALE)
                .itemTransform(ITEM_TRANSFORM_FIXED)
                .meta(id);
    }

    private Object buildRotation(int slot, int id, int interpDuration) {
        float radians = (float) Math.toRadians(slot * 45f + controller.getCurrentRotation());
        Vector3f translation = new Vector3f(-0.25f, 0f, -0.25f).rotateY(radians);
        Quaternionf leftRot = new Quaternionf().rotateY(radians);
        return ItemDisplayPackets.builder()
                .interpolation(interpDuration, 0)
                .translation(translation.x, translation.y, translation.z)
                .leftRotation(leftRot)
                .meta(id);
    }

    private void buildSlotPackets(int layer, int slot, Item item) {
        int id = idx(layer, slot);
        WorldPosition pos = new WorldPosition(
                null,
                (float) controller.getPos().x + 0.5f,
                (float) controller.getPos().y + yOffset(layer),
                (float) controller.getPos().z + 0.5f
        );
        Object spawn = ItemDisplayPackets.at(pos).spawn(display.id(id), display.uuid(id));
        display.setPackets(id, spawn, buildItemMeta(display.id(id), item));
        rotationPackets[id] = buildRotation(slot, display.id(id), 0);
    }

    private void sendSlot(Player player, int id) {
        if (display.spawn(id) == null) {
            return;
        }
        List<Object> packets = new ArrayList<>();
        packets.add(display.spawn(id));
        packets.add(display.meta(id));
        if (rotationPackets[id] != null) {
            packets.add(rotationPackets[id]);
        }
        player.sendPacket(PacketBundles.of(packets), false);
    }

    public void spawnSlot(int layer, int slot, Item item) {
        buildSlotPackets(layer, slot, item);
        int id = idx(layer, slot);
        TrackedPlayers.forEach(controller.blockEntity(), player -> sendSlot(player, id));
    }

    public void removeSlot(int layer, int slot) {
        int id = idx(layer, slot);
        TrackedPlayers.forEach(controller.blockEntity(), player -> display.removeSlot(player, id));
        display.clear(id);
        rotationPackets[id] = null;
    }

    public void updateSlotItem(int layer, int slot, Item item) {
        int id = idx(layer, slot);
        if (display.spawn(id) == null) {
            return;
        }
        Object meta = buildItemMeta(display.id(id), item);
        display.setPackets(id, display.spawn(id), meta);
        TrackedPlayers.forEach(controller.blockEntity(), player -> player.sendPacket(meta, false));
    }

    public void updateRotation() {
        if (!controller.isActive()) {
            return;
        }
        Item[][] items = controller.getItems();
        List<Object> updates = new ArrayList<>();
        for (int l = 0; l < LAYERS; l++) {
            for (int s = 0; s < SLOTS; s++) {
                int id = idx(l, s);
                if (!items[l][s].isEmpty() && display.spawn(id) != null) {
                    rotationPackets[id] = buildRotation(s, display.id(id), 20);
                    updates.add(rotationPackets[id]);
                }
            }
        }
        if (updates.isEmpty()) {
            return;
        }
        Object bundle = PacketBundles.of(updates);
        for (Player p : TrackedPlayers.snapshotInRange(controller.blockEntity(), behavior.animChunkRadius)) {
            p.sendPacket(bundle, false);
        }
    }

    public void refreshAllItems() {
        boolean worldReady = controller.getWorld() != null;
        if (worldReady) {
            TrackedPlayers.forEach(controller.blockEntity(), this::hide);
        }
        for (int i = 0; i < TOTAL; i++) {
            display.clear(i);
            rotationPackets[i] = null;
        }
        Item[][] items = controller.getItems();
        for (int l = 0; l < LAYERS; l++) {
            for (int s = 0; s < SLOTS; s++) {
                if (!items[l][s].isEmpty()) {
                    buildSlotPackets(l, s, items[l][s]);
                }
            }
        }
        if (worldReady) {
            TrackedPlayers.forEach(controller.blockEntity(), this::show);
        }
    }

    @Override
    public void show(@NotNull Player player) {
        Item[][] items = controller.getItems();
        List<Object> all = new ArrayList<>();
        for (int l = 0; l < LAYERS; l++) {
            for (int s = 0; s < SLOTS; s++) {
                int id = idx(l, s);
                if (!items[l][s].isEmpty() && display.spawn(id) != null) {
                    all.add(display.spawn(id));
                    all.add(display.meta(id));
                    if (rotationPackets[id] != null) {
                        all.add(rotationPackets[id]);
                    }
                }
            }
        }
        if (!all.isEmpty()) {
            player.sendPacket(PacketBundles.of(all), false);
        }
    }

    @Override
    public void hide(@NotNull Player player) {
        IntArrayList toRemove = new IntArrayList();
        for (int i = 0; i < TOTAL; i++) {
            if (display.spawn(i) != null) {
                toRemove.add(display.id(i));
            }
        }
        if (toRemove.isEmpty()) {
            return;
        }
        player.sendPacket(ClientboundRemoveEntitiesPacketProxy.INSTANCE.newInstance(toRemove), false);
    }

    @Override
    public void update(@NotNull Player player) {
        // 内容更新由控制器主动推送 不在此重建实体 避免丢旋转插值状态 参考 PotElement
    }
}