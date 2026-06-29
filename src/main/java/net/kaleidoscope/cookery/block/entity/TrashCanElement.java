package net.kaleidoscope.cookery.block.entity;

import net.momirealms.craftengine.core.entity.furniture.element.FurnitureElement;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.WorldPosition;
import org.jetbrains.annotations.NotNull;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.kaleidoscope.cookery.block.entity.render.DroppedItemDisplay;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplayPackets;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplaySet;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.util.InventoryUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class TrashCanElement implements FurnitureElement {
    private static final double BASE_X_OFFSET = 0.0;
    private static final double BASE_Y_OFFSET = 0.5;
    private static final double BASE_Z_OFFSET = 0.0;

    private static final int BODY_SLOT = 0;
    private static final int LID_SLOT = 1;
    private static final int EYE_SLOT = 2;
    private static final float LID_PIVOT_Y = 0.421875f;

    private static final int STORAGE_SLOTS = 3;
    // 三件掉落物的渲染位置 方块内相对坐标 0 到 1
    private static final float[][] ITEM_OFFSETS = {
            {0.50f, 0.95f, 0.42f},
            {0.42f, 1.00f, 0.55f},
            {0.58f, 0.97f, 0.52f},
    };

    private final TrashCanController controller;
    private final WorldPosition basePos;
    // 槽 0 桶身 槽 1 桶盖 槽 2 眼睛
    private final ItemDisplaySet display = new ItemDisplaySet(3);
    private final DroppedItemDisplay[] items = new DroppedItemDisplay[STORAGE_SLOTS];
    // 有玩家进入垃圾桶时隐藏桶内掉落物 物品仍然保存
    private boolean itemsHidden;

    public TrashCanElement(@NotNull TrashCanController controller, @NotNull WorldPosition position) {
        this.controller = controller;
        this.basePos = new WorldPosition(position.world(),
                position.x + BASE_X_OFFSET, position.y + BASE_Y_OFFSET, position.z + BASE_Z_OFFSET);
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            this.items[i] = new DroppedItemDisplay();
        }
        rebuildPackets();
    }

    private float yawRad() {
        return (float) Math.toRadians(-controller.facingDegrees());
    }

    private void refreshPart(Key key, int slot) {
        Item item = InventoryUtils.createOrEmpty(key);
        if (ItemUtils.isEmpty(item)) {
            display.clear(slot);
            return;
        }
        ItemDisplayPackets packets = ItemDisplayPackets.at(basePos)
                .item(item)
                .scale(1.0f)
                .itemTransform((byte) 0)
                .leftRotation(new Quaternionf().rotateY(yawRad()));
        display.setPackets(slot, packets.spawn(display.id(slot), display.uuid(slot)), packets.meta(display.id(slot)));
    }

    public void refreshItems() {
        Item[] s = controller.storage();
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            items[i].set(itemPos(i), s[i].isEmpty() ? s[i] : s[i].copyWithCount(1));
        }
    }

    public void refreshItemsAndBroadcast() {
        Item[] s = controller.storage();
        Iterable<Player> tracked = controller.furniture().getTrackedBy();
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            boolean was = items[i].present();
            items[i].set(itemPos(i), s[i].isEmpty() ? s[i] : s[i].copyWithCount(1));
            boolean now = items[i].present();
            if (!was && now) {
                for (Player p : tracked) {
                    items[i].show(p);
                }
            } else if (was && !now) {
                for (Player p : tracked) {
                    items[i].hide(p);
                }
            } else if (was) {
                for (Player p : tracked) {
                    items[i].updateData(p);
                }
            }
        }
    }

    public void rebuildPackets() {
        refreshPart(ItemKeys.TRASHCAN_BODY, BODY_SLOT);
        refreshPart(ItemKeys.TRASHCAN_LID, LID_SLOT);
        refreshPart(ItemKeys.TRASHCAN_EYE, EYE_SLOT);
        refreshItems();
    }

    private WorldPosition itemPos(int i) {
        float[] o = ITEM_OFFSETS[i];
        return new WorldPosition(basePos.world(),
                basePos.x - 0.5 + o[0], basePos.y - 0.5 + o[1], basePos.z - 0.5 + o[2]);
    }

    public Object openFrameMeta(float rotXDeg, float posYModel, int durationTicks) {
        float yaw = yawRad();
        float angle = (float) Math.toRadians(rotXDeg);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        Vector3f t = new Vector3f(0f, LID_PIVOT_Y * (1f - cos) + posYModel / 16f, -LID_PIVOT_Y * sin);
        t.rotateY(yaw);
        Quaternionf rot = new Quaternionf().rotateY(yaw).rotateX(angle);
        return ItemDisplayPackets.builder()
                .translation(t.x, t.y, t.z)
                .leftRotation(rot)
                .interpolation(durationTicks, 0)
                .meta(display.id(LID_SLOT));
    }

    @Override
    public void refresh(@NotNull Player player) {
        sendStaticMetas(player);
    }

    private void sendStaticMetas(Player player) {
        List<Object> packets = new ArrayList<>();
        if (display.meta(BODY_SLOT) != null) {
            packets.add(display.meta(BODY_SLOT));
        }
        if (display.meta(LID_SLOT) != null) {
            packets.add(display.meta(LID_SLOT));
        }
        if (display.meta(EYE_SLOT) != null) {
            packets.add(display.meta(EYE_SLOT));
        }
        if (!packets.isEmpty()) {
            player.sendPackets(packets, false);
        }
    }

    @Override
    public void gatherInteractableEntityId(Consumer<Integer> collector) {
        collector.accept(display.id(BODY_SLOT));
        collector.accept(display.id(LID_SLOT));
    }

    // 进入动画
    public Object bodyEnterFrameMeta(float rotYDeg, int durationTicks) {
        float yaw = yawRad();
        Quaternionf rot = new Quaternionf().rotateY(yaw + (float) Math.toRadians(rotYDeg));
        return ItemDisplayPackets.builder()
                .leftRotation(rot)
                .interpolation(durationTicks, 0)
                .meta(display.id(BODY_SLOT));
    }

    // 进入动画
    public Object lidEnterFrameMeta(float rotZDeg, float posYModel, int durationTicks) {
        float yaw = yawRad();
        float angle = (float) Math.toRadians(rotZDeg);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        Vector3f t = new Vector3f(LID_PIVOT_Y * sin, LID_PIVOT_Y * (1f - cos) + posYModel / 16f, 0f);
        t.rotateY(yaw);
        Quaternionf rot = new Quaternionf().rotateY(yaw).rotateZ(angle);
        return ItemDisplayPackets.builder()
                .translation(t.x, t.y, t.z)
                .leftRotation(rot)
                .interpolation(durationTicks, 0)
                .meta(display.id(LID_SLOT));
    }

    // 待机动画
    public Object eyeMeta(float posXModel, float posYModel, int durationTicks) {
        float yaw = yawRad();
        Vector3f t = new Vector3f(posXModel / 16f, posYModel / 16f, 0f);
        t.rotateY(yaw);
        Quaternionf rot = new Quaternionf().rotateY(yaw);
        return ItemDisplayPackets.builder()
                .translation(t.x, t.y, t.z)
                .leftRotation(rot)
                .interpolation(durationTicks, 0)
                .meta(display.id(EYE_SLOT));
    }

    public void setItemsHidden(boolean hidden) {
        if (this.itemsHidden == hidden) {
            return;
        }
        this.itemsHidden = hidden;
        for (Player p : controller.furniture().getTrackedBy()) {
            for (DroppedItemDisplay d : items) {
                if (hidden) {
                    d.hide(p);
                } else {
                    d.show(p);
                }
            }
        }
    }

    @Override
    public void show(@NotNull Player player) {
        display.showSlot(player, BODY_SLOT);
        display.showSlot(player, LID_SLOT);
        display.showSlot(player, EYE_SLOT);
        if (!itemsHidden) {
            for (DroppedItemDisplay d : items) {
                d.show(player);
            }
        }
    }

    @Override
    public void hide(@NotNull Player player) {
        display.removeAll(player);
        for (DroppedItemDisplay d : items) {
            d.hide(player);
        }
    }
}
