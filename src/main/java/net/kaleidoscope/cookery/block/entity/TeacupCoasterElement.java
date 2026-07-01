package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.entity.render.ItemDisplayPackets;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplaySet;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.world.WorldPosition;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

public final class TeacupCoasterElement implements BlockEntityElement {
    private static final int MAX_CUPS = TeacupCoasterController.MAX_CUPS;
    private static final float SPREAD = 0.2f;
    private static final float JITTER_DEG = 25f;

    private static final float[] CORNER_X = {-SPREAD, SPREAD, -SPREAD, SPREAD};
    private static final float[] CORNER_Z = {-SPREAD, SPREAD, SPREAD, -SPREAD};

    private final TeacupCoasterController controller;
    private final ItemDisplaySet display = new ItemDisplaySet(MAX_CUPS);
    private boolean built;

    public TeacupCoasterElement(@NotNull TeacupCoasterController controller) {
        this.controller = controller;
    }

    private WorldPosition basePos() {
        return new WorldPosition(controller.getWorld().world(),
                controller.getPos().x() + 0.5,
                controller.getPos().y() + controller.cupYOffset(),
                controller.getPos().z() + 0.5);
    }

    public void rebuildPackets() {
        built = true;
        int count = controller.cupCount();
        boolean full = count == MAX_CUPS;
        WorldPosition base = basePos();
        Direction facing = controller.facing();
        float yaw = controller.facingYaw();
        for (int i = 0; i < MAX_CUPS; i++) {
            if (i >= count) {
                display.clear(i);
                continue;
            }
            Item item = InventoryUtils.createOrEmpty(controller.cupModel(i));
            if (ItemUtils.isEmpty(item)) {
                display.clear(i);
                continue;
            }
            float ox = count == 1 ? 0f : CORNER_X[i];
            float oz = count == 1 ? 0f : CORNER_Z[i];
            float rx;
            float rz;
            switch (facing) {
                case EAST -> { rx = -oz; rz = ox; }
                case SOUTH -> { rx = -ox; rz = -oz; }
                case WEST -> { rx = oz; rz = -ox; }
                default -> { rx = ox; rz = oz; }
            }
            float rotDeg = yaw + (full ? 0f : jitter(i));
            ItemDisplayPackets packets = ItemDisplayPackets.at(base)
                    .item(item)
                    .scale(controller.cupScale())
                    .itemTransform((byte) 0)
                    .translation(rx, 0f, rz)
                    .leftRotation(new Quaternionf().rotateY((float) Math.toRadians(rotDeg)));
            display.setPackets(i, packets.spawn(display.id(i), display.uuid(i)), packets.meta(display.id(i)));
        }
    }

    private float jitter(int index) {
        int h = ((controller.getPos().x() * 31 + controller.getPos().z()) * 31 + controller.getPos().y()) * 31 + index;
        h ^= h >>> 16;
        h *= 0x7feb352d;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535f * (2f * JITTER_DEG) - JITTER_DEG;
    }

    public Object cupMeta(int index) {
        return display.meta(index);
    }

    public void broadcastChange(@NotNull Player player, int oldCount, int newCount) {
        for (int i = 0; i < newCount; i++) {
            if (i < oldCount) {
                player.sendPacket(display.meta(i), false);
            } else {
                display.showSlot(player, i);
            }
        }
        for (int i = newCount; i < oldCount; i++) {
            display.removeSlot(player, i);
        }
    }

    @Override
    public void show(@NotNull Player player) {
        if (!built) {
            rebuildPackets();
        }
        int count = controller.cupCount();
        for (int i = 0; i < count; i++) {
            display.showSlot(player, i);
        }
    }

    @Override
    public void hide(@NotNull Player player) {
        display.removeAll(player);
    }

    @Override
    public void update(@NotNull Player player) {
    }
}
