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

    public ChoppingBoardElement(@NotNull ChoppingBoardController controller, @NotNull WorldPosition position) {
        this.controller = controller;
        this.basePos = position;
        refreshPackets();
    }

    @Override
    public void activate() {
        refreshPackets();
    }

    // 配方给了分阶段模型就用模型 那些模型是照着躺在板上画的 原样摆
    // 没给模型的退回展示放上去的物品本身 物品默认是立着的 要放倒并缩小才像躺在板上
    private static final float RAW_SCALE = 0.5f;
    private static final float RAW_PITCH = (float) Math.toRadians(-90);
    // basePos 的高度是照立着的阶段模型定的 物品放倒后中心还留在那儿会悬空
    // 往下压回板面 展示实体的 translation 在旋转之外应用 不会被 pitch 带偏
    private static final float RAW_Y_OFFSET = -0.5f;

    public void refreshPackets() {
        String model = controller.currentStageModel();
        boolean raw = model == null;
        Item item = raw
                ? controller.placedItem().copy()
                : InventoryUtils.createOrEmpty(Key.of(model));
        if (ItemUtils.isEmpty(item)) {
            display.clear(0);
            this.currentVisible = false;
            return;
        }

        Quaternionf rotation = new Quaternionf().rotateY(controller.facingYawRadians());
        if (raw) {
            rotation.rotateX(RAW_PITCH);
        }
        ItemDisplayPackets packets = ItemDisplayPackets.at(basePos)
                .item(item)
                .scale(raw ? RAW_SCALE : 1.0f)
                .itemTransform((byte) 0)
                .leftRotation(rotation);
        if (raw) {
            packets.translation(0f, RAW_Y_OFFSET, 0f);
        }
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