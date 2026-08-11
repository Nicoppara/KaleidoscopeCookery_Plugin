package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.KitchenwareRacksBehavior;

import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;

import java.util.function.Consumer;

public final class KitchenwareRacksController extends BlockEntityController {
    private static final String DATA_KEY = "kaleidoscopecookery:kitchenware_racks";
    private static final String K_ITEM_LEFT = "item_left";
    private static final String K_ITEM_RIGHT = "item_right";

    private static final float ITEM_HEIGHT = 0.4375f;
    private static final float SIDE_OFFSET = 0.2f;
    private static final float DEPTH_OFFSET = 0.3f;

    private final KitchenwareRacksBehavior behavior;
    private final KitchenwareRacksElement element = new KitchenwareRacksElement(this);

    @NotNull
    private Item itemLeft = Item.empty();
    @NotNull
    private Item itemRight = Item.empty();

    private WorldPosition leftPosition;
    private WorldPosition rightPosition;
    private boolean positionsInitialized;

    public KitchenwareRacksController(BlockEntity blockEntity, KitchenwareRacksBehavior behavior) {
        super(blockEntity);
        this.behavior = behavior;
    }

    @Override
    public boolean hasElement() {
        return true;
    }

    @Override
    public void gatherElements(Consumer<BlockEntityElement> consumer) {
        consumer.accept(this.element);
    }

    @NotNull
    public Item getItemLeft() {
        return this.itemLeft;
    }

    @NotNull
    public Item getItemRight() {
        return this.itemRight;
    }

    public void putLeft(Item item) {
        this.itemLeft = item;
        refresh();
    }

    public void putRight(Item item) {
        this.itemRight = item;
        refresh();
    }

    public Item takeLeft() {
        Item taken = this.itemLeft;
        this.itemLeft = Item.empty();
        refresh();
        return taken;
    }

    public Item takeRight() {
        Item taken = this.itemRight;
        this.itemRight = Item.empty();
        refresh();
        return taken;
    }

    // 位置依赖 world 与方块状态 区块反序列化时 world 还没注入 只能推迟到第一次用
    void rebuildElement() {
        rebuildElement(super.blockEntity.blockState);
    }

    private void rebuildElement(ImmutableBlockState state) {
        if (!this.positionsInitialized && super.blockEntity.world != null) {
            this.leftPosition = itemPosition(state, true);
            this.rightPosition = itemPosition(state, false);
            this.positionsInitialized = true;
        }
        this.element.rebuild(this.leftPosition, this.rightPosition, facingOf(state));
    }

    private void redraw(ImmutableBlockState state) {
        rebuildElement(state);
        TrackedPlayers.forEach(super.blockEntity, this.element::update);
    }

    private void refresh() {
        redraw(super.blockEntity.blockState);
        super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
    }

    // 架子转向后挂件的位置与自身旋转都要跟着走 状态写进世界前先按新状态重画
    @Override
    public void preBlockStateChange(ImmutableBlockState newState) {
        if (super.blockEntity.world == null) {
            return;
        }
        this.leftPosition = itemPosition(newState, true);
        this.rightPosition = itemPosition(newState, false);
        this.positionsInitialized = true;
        redraw(newState);
    }

    private WorldPosition itemPosition(ImmutableBlockState state, boolean isLeft) {
        float x = (float) (super.blockEntity.pos.x + 0.5);
        float y = (float) (super.blockEntity.pos.y + ITEM_HEIGHT);
        float z = (float) (super.blockEntity.pos.z + 0.5);
        float side = isLeft ? -SIDE_OFFSET : SIDE_OFFSET;
        return switch (facingOf(state)) {
            case SOUTH -> new WorldPosition(super.blockEntity.world.world, x - side, y, z - DEPTH_OFFSET);
            case EAST -> new WorldPosition(super.blockEntity.world.world, x - DEPTH_OFFSET, y, z + side);
            case WEST -> new WorldPosition(super.blockEntity.world.world, x + DEPTH_OFFSET, y, z - side);
            default -> new WorldPosition(super.blockEntity.world.world, x + side, y, z + DEPTH_OFFSET);
        };
    }

    private Direction facingOf(ImmutableBlockState state) {
        return BlockStates.value(state, behavior.getFacingProperty(), Direction.NORTH);
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        CompoundTag data = BlockEntityNbt.newData();
        BlockEntityNbt.putItem(data, K_ITEM_LEFT, this.itemLeft);
        BlockEntityNbt.putItem(data, K_ITEM_RIGHT, this.itemRight);
        tag.put(DATA_KEY, data);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        this.itemLeft = Item.empty();
        this.itemRight = Item.empty();
        CompoundTag data = tag.getCompound(DATA_KEY);
        if (data == null) {
            return;
        }
        int dataVersion = BlockEntityNbt.dataVersion(data);
        this.itemLeft = BlockEntityNbt.getItem(data, K_ITEM_LEFT, dataVersion);
        this.itemRight = BlockEntityNbt.getItem(data, K_ITEM_RIGHT, dataVersion);
    }

    @Override
    public void onRemove() {
        DropUtils.dropOnRemove(super.blockEntity, itemLeft);
        DropUtils.dropOnRemove(super.blockEntity, itemRight);
    }
}
