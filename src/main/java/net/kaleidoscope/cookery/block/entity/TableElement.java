package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.TableBehavior;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplayPackets;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplaySet;
import net.kaleidoscope.cookery.item.CarpetColors;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.WorldPosition;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

// 桌面上的桌布
public final class TableElement implements BlockEntityElement {
    private static final int DISPLAY_CARPET = 0;
    private static final int DISPLAYS = 1;

    private static final byte ITEM_TRANSFORM_NONE = (byte) 0;
    // 桌布得跟桌子模型同一套朝向 比模组渲染器再多转半圈 垂边才落在朝外那一侧
    private static final float ROTATION_LINE_X = 0;
    private static final float ROTATION_LINE_Z = (float) Math.toRadians(270);

    private final TableController controller;
    private final ItemDisplaySet displays = new ItemDisplaySet(DISPLAYS);
    private volatile boolean built;

    public TableElement(TableController controller) {
        this.controller = controller;
    }

    // 区块反序列化时 loadCustomData 早于 setWorld 此时碰 world 会 NPE 并让整块 CE 数据读不出来
    public void rebuild(ImmutableBlockState state) {
        BlockEntity blockEntity = this.controller.blockEntity();
        if (blockEntity.world == null) {
            this.built = false;
            return;
        }
        float angle = this.controller.line(state) == TableBehavior.LINE_X ? ROTATION_LINE_X : ROTATION_LINE_Z;
        Vec3d center = Vec3d.atCenterOf(blockEntity.pos);
        WorldPosition origin = new WorldPosition(blockEntity.world.world(), center.x(), center.y(), center.z());
        buildCarpet(origin, this.controller.position(state), new Quaternionf().rotateY(angle));
        this.built = true;
    }

    private void ensureBuilt() {
        if (!this.built) {
            rebuild(this.controller.blockEntity().blockState());
        }
    }

    // 展示实体一律生在方块正中 摆放偏移走 translation
    // translation 通过元数据包更新，调整布局时无需重建实体
    private void buildCarpet(WorldPosition origin, int position, Quaternionf rotation) {
        Item item = InventoryUtils.createOrEmpty(CarpetColors.tableModel(this.controller.carpet(), position));
        if (ItemUtils.isEmpty(item)) {
            this.displays.clear(DISPLAY_CARPET);
            return;
        }
        this.displays.setPackets(DISPLAY_CARPET,
                ItemDisplayPackets.at(origin).spawn(this.displays.id(DISPLAY_CARPET), this.displays.uuid(DISPLAY_CARPET)),
                ItemDisplayPackets.builder()
                        .item(item)
                        .leftRotation(rotation)
                        .itemTransform(ITEM_TRANSFORM_NONE)
                        .meta(this.displays.id(DISPLAY_CARPET)));
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
