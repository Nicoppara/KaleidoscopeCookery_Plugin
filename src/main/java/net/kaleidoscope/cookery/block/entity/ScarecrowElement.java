package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.ScarecrowBehavior;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplayPackets;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplaySet;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.core.entity.furniture.element.FurnitureElement;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.WorldPosition;
import org.joml.Quaternionf;

import java.util.function.Consumer;

// 稻草人本体与头颅 两手物品
// 戴上头颅时本体换成无头模型
public final class ScarecrowElement implements FurnitureElement {
    public static final int SLOT_HEAD = 0;
    public static final int SLOT_MAIN_HAND = 1;
    public static final int SLOT_OFF_HAND = 2;
    public static final int SLOTS = 3;

    private static final int DISPLAY_BODY = 3;
    private static final int DISPLAYS = 4;
    private static final byte ITEM_TRANSFORM_NONE = (byte) 0;

    private final ScarecrowController controller;
    private final ScarecrowBehavior behavior;
    private final ItemDisplaySet displays = new ItemDisplaySet(DISPLAYS);

    public ScarecrowElement(ScarecrowController controller, ScarecrowBehavior behavior) {
        this.controller = controller;
        this.behavior = behavior;
    }

    public void rebuild() {
        WorldPosition base = this.controller.furniture().position();
        double yaw = Math.toRadians(base.yRot());
        buildBody(base, yaw);
        for (int slot = 0; slot < SLOTS; slot++) {
            Item item = this.controller.item(slot);
            if (ItemUtils.isEmpty(item)) {
                this.displays.clear(slot);
                continue;
            }
            buildSlot(base, yaw, slot, item);
        }
    }

    private void buildBody(WorldPosition base, double yaw) {
        Item body = InventoryUtils.createOrEmpty(
                ItemUtils.isEmpty(this.controller.item(SLOT_HEAD)) ? ItemKeys.SHOW_SCARECROW : ItemKeys.SHOW_SCARECROW_HEADLESS);
        if (ItemUtils.isEmpty(body)) {
            this.displays.clear(DISPLAY_BODY);
            return;
        }
        this.displays.setPackets(DISPLAY_BODY,
                ItemDisplayPackets.at(offsetOf(base, this.behavior.bodyOffset))
                        .spawn(this.displays.id(DISPLAY_BODY), this.displays.uuid(DISPLAY_BODY)),
                ItemDisplayPackets.builder()
                        .item(body)
                        .leftRotation(new Quaternionf().rotateY((float) -yaw))
                        .itemTransform(ITEM_TRANSFORM_NONE)
                        .meta(this.displays.id(DISPLAY_BODY)));
    }

    private void buildSlot(WorldPosition base, double yaw, int slot, Item item) {
        ScarecrowBehavior.SlotConfig config = this.behavior.slots[slot];
        Item display = slot == SLOT_OFF_HAND ? lanternDisplay(item) : item;
        if (ItemUtils.isEmpty(display)) {
            this.displays.clear(slot);
            return;
        }
        float rotation = (float) -yaw + (float) Math.toRadians(config.yawOffsetDegrees());
        // 灯笼那格已经放了真的 light 方块 展示实体吃所在格的光照就够亮 不用再覆盖亮度
        this.displays.setPackets(slot,
                ItemDisplayPackets.at(offsetOf(base, config.position()))
                        .spawn(this.displays.id(slot), this.displays.uuid(slot)),
                ItemDisplayPackets.builder()
                        .item(display)
                        .scale(config.scale())
                        .leftRotation(new Quaternionf().rotateY(rotation))
                        .itemTransform(config.itemTransform())
                        .meta(this.displays.id(slot)));
    }

    // 偏移要跟着家具朝向转 交给 CE 算 自己拼三角函数容易和碰撞箱对不齐
    private static WorldPosition offsetOf(WorldPosition base, org.joml.Vector3f relative) {
        Vec3d position = Furniture.getRelativePosition(base, relative);
        return new WorldPosition(base.world(), position.x, position.y, position.z);
    }

    private static Item lanternDisplay(Item item) {
        Key id = item.vanillaId();
        if (ItemKeys.SOUL_LANTERN.equals(id)) {
            return InventoryUtils.createOrEmpty(ItemKeys.SHOW_SCARECROW_SOUL_LANTERN);
        }
        if (ItemKeys.LANTERN.equals(id)) {
            return InventoryUtils.createOrEmpty(ItemKeys.SHOW_SCARECROW_LANTERN);
        }
        return item;
    }

    @Override
    public void gatherInteractableEntityId(Consumer<Integer> collector) {
    }

    @Override
    public void show(Player player) {
        // 家具刚放下时 CE 可能先让玩家追踪再走 onLoad 这里兜底补建
        if (this.displays.spawn(DISPLAY_BODY) == null) {
            rebuild();
        }
        this.displays.show(player);
    }

    @Override
    public void hide(Player player) {
        this.displays.hide(player);
    }

    @Override
    public void update(Player player) {
        this.displays.update(player);
    }
}
