package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.entity.render.ItemDisplayPackets;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplaySet;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.world.WorldPosition;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

public final class FruitBasketElement implements BlockEntityElement {
    private static final int SLOTS = 8;
    private static final float ITEM_SCALE = 0.375f;
    private static final byte FIXED_TRANSFORM = 8;

    private final FruitBasketController controller;
    private final ItemDisplaySet display = new ItemDisplaySet(SLOTS);
    private WorldPosition[] positions;
    private Quaternionf rotation = new Quaternionf();

    public FruitBasketElement(FruitBasketController controller) {
        this.controller = controller;
    }

    public void configure(WorldPosition[] positions, Quaternionf rotation) {
        this.positions = positions;
        this.rotation = rotation;
    }

    public void refreshItem(int slot, Item item) {
        if (item.isEmpty() || positions == null) {
            display.clear(slot);
            return;
        }
        ItemDisplayPackets packets = ItemDisplayPackets.at(positions[slot])
                .item(item)
                .scale(ITEM_SCALE)
                .leftRotation(rotation)
                .itemTransform(FIXED_TRANSFORM);
        display.setPackets(slot, packets.spawn(display.id(slot), display.uuid(slot)), packets.meta(display.id(slot)));
    }

    public void showSlot(Player player, int slot) {
        display.showSlot(player, slot);
    }

    public void removeSlot(Player player, int slot) {
        display.removeSlot(player, slot);
    }

    public void metaSlot(Player player, int slot) {
        Object meta = display.meta(slot);
        if (meta != null) {
            player.sendPacket(meta, false);
        }
    }

    @Override
    public void show(@NotNull Player player) {
        controller.ensurePositionsInitialized();
        for (int i = 0; i < SLOTS; i++) {
            display.showSlot(player, i);
        }
    }

    @Override
    public void hide(@NotNull Player player) {
        display.removeAll(player);
    }

    @Override
    public void update(@NotNull Player player) {
        controller.ensurePositionsInitialized();
        List<Object> packets = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            Object meta = display.meta(i);
            if (meta != null) {
                packets.add(meta);
            }
        }
        if (!packets.isEmpty()) {
            player.sendPackets(packets, false);
        }
    }
}
