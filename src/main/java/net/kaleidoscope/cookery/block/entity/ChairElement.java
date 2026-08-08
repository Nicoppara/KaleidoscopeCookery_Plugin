package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.ChairBehavior;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplayPackets;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplaySet;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.core.entity.furniture.element.FurnitureElement;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.WorldPosition;
import org.joml.Quaternionf;

import java.util.function.Consumer;

// 椅面上的坐垫 没铺地毯时不生成任何实体
public final class ChairElement implements FurnitureElement {
    private static final int SLOT_CARPET = 0;
    private static final int DISPLAYS = 1;
    private static final byte ITEM_TRANSFORM_NONE = (byte) 0;

    private final ChairController controller;
    private final ChairBehavior behavior;
    private final ItemDisplaySet displays = new ItemDisplaySet(DISPLAYS);

    public ChairElement(ChairController controller, ChairBehavior behavior) {
        this.controller = controller;
        this.behavior = behavior;
    }

    public void rebuild() {
        Item item = InventoryUtils.createOrEmpty(this.controller.carpetModel());
        if (ItemUtils.isEmpty(item)) {
            this.displays.clear(SLOT_CARPET);
            return;
        }
        WorldPosition base = this.controller.furniture().position();
        Vec3d position = Furniture.getRelativePosition(base, this.behavior.carpetOffset);
        float yaw = (float) -Math.toRadians(base.yRot()) + (float) Math.toRadians(this.behavior.yawOffsetDegrees);
        this.displays.setPackets(SLOT_CARPET,
                ItemDisplayPackets.at(new WorldPosition(base.world(), position.x, position.y, position.z))
                        .spawn(this.displays.id(SLOT_CARPET), this.displays.uuid(SLOT_CARPET)),
                ItemDisplayPackets.builder()
                        .item(item)
                        .scale(this.behavior.carpetScale)
                        .leftRotation(new Quaternionf().rotateY(yaw))
                        .itemTransform(ITEM_TRANSFORM_NONE)
                        .meta(this.displays.id(SLOT_CARPET)));
    }

    @Override
    public void gatherInteractableEntityId(Consumer<Integer> collector) {
    }

    @Override
    public void show(Player player) {
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
