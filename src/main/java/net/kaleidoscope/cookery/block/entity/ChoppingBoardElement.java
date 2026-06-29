package net.kaleidoscope.cookery.block.entity;

import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.WorldPosition;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplayPackets;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplaySet;
import net.kaleidoscope.cookery.util.InventoryUtils;

public final class ChoppingBoardElement implements BlockEntityElement {

    private final ChoppingBoardController controller;
    private final WorldPosition basePos;
    private final ItemDisplaySet display = new ItemDisplaySet(1);

    private boolean lastVisible = false;
    private boolean currentVisible = false;
    private String currentModel = null;

    public ChoppingBoardElement(@NotNull ChoppingBoardController controller, @NotNull WorldPosition position) {
        this.controller = controller;
        this.basePos = position;
        refreshPackets();
    }

    @Override
    public void activate() {
        refreshPackets();
    }

    public void refreshPackets() {
        String model = controller.currentStageModel();
        this.currentModel = model;
        if (model == null) {
            display.clear(0);
            this.currentVisible = false;
            return;
        }
        Item item = InventoryUtils.createOrEmpty(Key.of(model));
        if (ItemUtils.isEmpty(item)) {
            display.clear(0);
            this.currentVisible = false;
            return;
        }

        ItemDisplayPackets packets = ItemDisplayPackets.at(basePos)
                .item(item)
                .scale(1.0f)
                .itemTransform((byte) 0)
                .leftRotation(new Quaternionf().rotateY(controller.facingYawRadians()));
        display.setPackets(0, packets.spawn(display.id(0), display.uuid(0)), packets.meta(display.id(0)));
        this.currentVisible = true;
    }

    public void prepareUpdate() {
        this.lastVisible = this.currentVisible;
        refreshPackets();
    }

    @Override
    public void update(@NotNull Player player) {
        if (currentVisible && !lastVisible) {
            display.showSlot(player, 0);
        } else if (!currentVisible && lastVisible) {
            display.removeSlot(player, 0);
        } else if (currentVisible) {
            Object meta = display.meta(0);
            if (meta != null) {
                player.sendPacket(meta, false);
            }
        }
    }

    @Override
    public void show(@NotNull Player player) {
        if (currentVisible) {
            display.showSlot(player, 0);
        }
    }

    @Override
    public void hide(@NotNull Player player) {
        display.removeAll(player);
    }
}