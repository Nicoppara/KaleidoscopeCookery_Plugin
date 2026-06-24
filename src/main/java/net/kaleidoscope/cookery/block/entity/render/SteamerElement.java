package net.kaleidoscope.cookery.block.entity.render;
import net.kaleidoscope.cookery.block.entity.SteamerController;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.MiscUtils;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacketProxy;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplayPackets;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplaySet;

public final class SteamerElement implements BlockEntityElement {
    private static final int SLOTS = 8;

    private final SteamerController controller;
    private final WorldPosition basePos;
    private final ItemDisplaySet display = new ItemDisplaySet(SLOTS);

    private int lastVisibleCount = 0;
    private int currentVisibleCount = 0;

    public SteamerElement(@NotNull SteamerController controller, @NotNull WorldPosition position) {
        this.controller = controller;
        this.basePos = position;
        refreshMetaPackets();
    }

    private void refreshMetaPackets() {
        Item[] items = controller.getItems();
        for (int i = 0; i < SLOTS; i++) {
            if (i < controller.getItemCount()) {
                float xOffset = (i % 2) * 0.3f - 0.15f;
                float yOffset = (i / 4) * 0.5f + 0.15f + (i % 4) * 0.01f;
                float zOffset = ((i / 2) % 2) * 0.3f - 0.15f;

                ItemDisplayPackets packets = ItemDisplayPackets.at(basePos)
                        .item(items[i])
                        .scale(0.5f)
                        .translation(xOffset, yOffset, zOffset)
                        .leftRotation(new Quaternionf().rotateX((float) Math.toRadians(-90)))
                        .itemTransform((byte) 8);

                display.setPackets(i, packets.spawn(display.id(i), display.uuid(i)), packets.meta(display.id(i)));
            } else {
                display.clear(i);
            }
        }
    }

    public void prepareUpdate() {
        this.lastVisibleCount = this.currentVisibleCount;
        this.currentVisibleCount = controller.isCovered() ? 0 : controller.getItemCount();
        refreshMetaPackets();
    }

    @Override
    public void update(@NotNull Player player) {
        if (currentVisibleCount > lastVisibleCount) {
            for (int i = lastVisibleCount; i < currentVisibleCount; i++) {
                display.showSlot(player, i);
            }
        }
        else if (currentVisibleCount < lastVisibleCount) {
            IntList idsToRemove = MiscUtils.init(new IntArrayList(), a -> {
                for (int i = currentVisibleCount; i < lastVisibleCount; i++) {
                    a.add(display.id(i));
                }
            });
            player.sendPacket(ClientboundRemoveEntitiesPacketProxy.INSTANCE.newInstance(idsToRemove), false);
        }

        for (int i = 0; i < currentVisibleCount; i++) {
            if (display.meta(i) != null) {
                player.sendPacket(display.meta(i), false);
            }
        }
    }

    @Override
    public void show(@NotNull Player player) {
        for (int i = 0; i < currentVisibleCount; i++) {
            display.showSlot(player, i);
        }
    }

    @Override
    public void hide(@NotNull Player player) {
        display.removeAll(player);
    }
}
