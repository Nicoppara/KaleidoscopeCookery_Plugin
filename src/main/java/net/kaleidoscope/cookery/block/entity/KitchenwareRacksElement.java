package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.entity.render.ItemDisplayPackets;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplaySet;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.world.WorldPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

// 厨具展示架左右各挂一件 空槽不生成实体
public final class KitchenwareRacksElement implements BlockEntityElement {
    private static final int SLOT_LEFT = 0;
    private static final int SLOT_RIGHT = 1;
    private static final int SLOTS = 2;

    private static final float ITEM_SCALE = 0.75f;
    // 展示框那套
    private static final byte ITEM_TRANSFORM_FIXED = 8;
    // 厨具是斜挂在架子上的 先翻面再侧倾
    private static final float TILT_YAW = 25;
    private static final float TILT_PITCH = -180;
    private static final float TILT_ROLL = 45;

    private final KitchenwareRacksController controller;
    private final ItemDisplaySet displays = new ItemDisplaySet(SLOTS);
    private volatile boolean built;

    public KitchenwareRacksElement(@NotNull KitchenwareRacksController controller) {
        this.controller = controller;
    }

    // 区块反序列化时 loadCustomData 早于 setWorld 位置算不出来 画面留空等 show 时再补
    public void rebuild(@Nullable WorldPosition left, @Nullable WorldPosition right, Direction facing) {
        if (left == null || right == null) {
            this.built = false;
            return;
        }
        buildSlot(SLOT_LEFT, this.controller.getItemLeft(), left, facing);
        buildSlot(SLOT_RIGHT, this.controller.getItemRight(), right, facing);
        this.built = true;
    }

    private void buildSlot(int slot, Item item, WorldPosition position, Direction facing) {
        if (ItemUtils.isEmpty(item)) {
            this.displays.clear(slot);
            return;
        }
        this.displays.setPackets(slot,
                ItemDisplayPackets.at(position).spawn(this.displays.id(slot), this.displays.uuid(slot)),
                ItemDisplayPackets.builder()
                        .item(item)
                        .scale(ITEM_SCALE)
                        .leftRotation(new Quaternionf()
                                .rotateY(yawOf(facing) + (float) Math.toRadians(TILT_YAW))
                                .rotateX((float) Math.toRadians(TILT_PITCH))
                                .rotateZ((float) Math.toRadians(TILT_ROLL)))
                        .itemTransform(ITEM_TRANSFORM_FIXED)
                        .meta(this.displays.id(slot)));
    }

    private static float yawOf(Direction facing) {
        return switch (facing) {
            case WEST -> (float) Math.toRadians(90);
            case SOUTH -> (float) Math.toRadians(180);
            case EAST -> (float) Math.toRadians(270);
            default -> 0f;
        };
    }

    private void ensureBuilt() {
        if (!this.built) {
            this.controller.rebuildElement();
        }
    }

    @Override
    public void show(@NotNull Player player) {
        ensureBuilt();
        this.displays.show(player);
    }

    @Override
    public void hide(@NotNull Player player) {
        this.displays.hide(player);
    }

    @Override
    public void update(@NotNull Player player) {
        ensureBuilt();
        this.displays.update(player);
    }
}
