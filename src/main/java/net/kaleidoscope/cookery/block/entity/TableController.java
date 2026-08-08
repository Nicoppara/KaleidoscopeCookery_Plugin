package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.TableBehavior;
import net.kaleidoscope.cookery.item.CarpetColors;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

// 桌面铺一张桌布 颜色只存一个 Key 不占方块状态
// 桌布端型跟着方块状态的 line/position 走 拼接变化要重画 见 preBlockStateChange
public final class TableController extends BlockEntityController {
    private static final String DATA_KEY = "kaleidoscopecookery:table";
    private static final String K_CARPET = "carpet";

    private final TableBehavior behavior;
    private final TableElement element = new TableElement(this);
    private volatile Key carpet;

    public TableController(BlockEntity blockEntity, TableBehavior behavior) {
        super(blockEntity);
        this.behavior = behavior;
    }

    public int line(ImmutableBlockState state) {
        return this.behavior.line(state);
    }

    public int position(ImmutableBlockState state) {
        return this.behavior.position(state);
    }

    @Nullable
    public Key carpet() {
        return this.carpet;
    }

    // 换色返回旧桌布 没铺过返回空物品
    public Item putCarpet(Key id) {
        Key previous = this.carpet;
        this.carpet = id;
        refresh();
        return InventoryUtils.createOrEmpty(previous);
    }

    public Item takeCarpet() {
        Key previous = this.carpet;
        if (previous == null) {
            return Item.empty();
        }
        this.carpet = null;
        refresh();
        return InventoryUtils.createOrEmpty(previous);
    }

    // 走 TrackedPlayers 轮子只推给追踪该区块的玩家 再标记方块实体已变动
    private void refresh() {
        redraw(super.blockEntity.blockState());
        super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
    }

    private void redraw(ImmutableBlockState state) {
        this.element.rebuild(state);
        TrackedPlayers.forEach(super.blockEntity, this.element::update);
    }

    // 拼接变化会换端型与朝向 桌布模型跟着变 状态写进世界前先按新状态重画
    @Override
    public void preBlockStateChange(ImmutableBlockState newState) {
        if (super.blockEntity.world == null) {
            return;
        }
        ImmutableBlockState current = super.blockEntity.blockState();
        if (line(current) == line(newState) && position(current) == position(newState)) {
            return;
        }
        redraw(newState);
    }

    @Override
    public boolean hasElement() {
        return true;
    }

    @Override
    public void gatherElements(Consumer<BlockEntityElement> consumer) {
        consumer.accept(this.element);
    }

    @Override
    public void onRemove() {
        Key previous = this.carpet;
        if (previous != null) {
            this.carpet = null;
            DropUtils.dropOnRemove(super.blockEntity, InventoryUtils.createOrEmpty(previous));
        }
        super.onRemove();
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        Key current = this.carpet;
        if (current == null) {
            return;
        }
        CompoundTag data = BlockEntityNbt.newData();
        data.putString(K_CARPET, current.asString());
        tag.put(DATA_KEY, data);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        this.carpet = null;
        CompoundTag data = tag.getCompound(DATA_KEY);
        if (data == null) {
            return;
        }
        this.carpet = CarpetColors.parse(data.getString(K_CARPET));
    }
}
