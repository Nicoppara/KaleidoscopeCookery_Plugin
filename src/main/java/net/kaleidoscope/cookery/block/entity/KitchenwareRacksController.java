package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.KitchenwareRacksBehavior;

import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.Tag;
import org.jetbrains.annotations.NotNull;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;

import java.util.function.Consumer;

public final class KitchenwareRacksController extends BlockEntityController {
    private static final String DATA_KEY = "kaleidoscopecookery:kitchenware_racks";
    private static final String K_DATA_VERSION = "data_version";
    private static final String K_ITEM_LEFT = "item_left";
    private static final String K_ITEM_RIGHT = "item_right";

    private final KitchenwareRacksBehavior behavior;
    private final KitchenwareRacksElement element;

    @NotNull
    private Item itemLeft;
    @NotNull
    private Item itemRight;
    private Item lastItemLeft = Item.empty();
    private Item lastItemRight = Item.empty();

    private WorldPosition leftPosition;
    private WorldPosition rightPosition;
    private boolean positionsInitialized = false;

    public boolean hasLeftChanged() {
        return !itemLeft.isSimilar(lastItemLeft);
    }

    public boolean hasRightChanged() {
        return !itemRight.isSimilar(lastItemRight);
    }

    public void updateLastState() {
        this.lastItemLeft = this.itemLeft;
        this.lastItemRight = this.itemRight;
    }

    public KitchenwareRacksController(BlockEntity blockEntity, KitchenwareRacksBehavior behavior) {
        super(blockEntity);
        this.behavior = behavior;
        this.itemLeft = Item.empty();
        this.itemRight = Item.empty();

        this.element = new KitchenwareRacksElement(this, null, null);
    }

    public void ensurePositionsInitialized() {
        if (!positionsInitialized && super.blockEntity.world != null) {
            this.leftPosition = calculateItemPosition(super.blockEntity.blockState, true);
            this.rightPosition = calculateItemPosition(super.blockEntity.blockState, false);
            this.element.refreshPositions(this.leftPosition, this.rightPosition);
            this.positionsInitialized = true;
        }
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
        ensurePositionsInitialized();
        this.itemLeft = item;
        this.element.refreshLeftItem(item);
        this.refreshElement();
        this.updateLastState();
    }

    public void putRight(Item item) {
        ensurePositionsInitialized();
        this.itemRight = item;
        this.element.refreshRightItem(item);
        this.refreshElement();
        this.updateLastState();
    }

    public Item takeLeft() {
        ensurePositionsInitialized();
        Item temp = this.itemLeft;
        this.itemLeft = Item.empty();
        this.element.hideLeft();
        this.refreshElement();
        this.updateLastState();
        return temp;
    }

    public Item takeRight() {
        ensurePositionsInitialized();
        Item temp = this.itemRight;
        this.itemRight = Item.empty();
        this.element.hideRight();
        this.refreshElement();
        this.updateLastState();
        return temp;
    }

    @Override
    public void preBlockStateChange(ImmutableBlockState newState) {
        ensurePositionsInitialized();
        if (super.blockEntity.world != null) {
            this.leftPosition = calculateItemPosition(newState, true);
            this.rightPosition = calculateItemPosition(newState, false);
            this.element.refreshPositions(this.leftPosition, this.rightPosition);
            this.refreshElement();
        }
    }

    private WorldPosition calculateItemPosition(ImmutableBlockState state, boolean isLeft) {
        Direction facing = behavior.getFacingProperty() != null ? state.get(behavior.getFacingProperty()) : Direction.NORTH;
        float x = (float) (super.blockEntity.pos.x + 0.5);
        float y = (float) (super.blockEntity.pos.y + 0.4375);
        float z = (float) (super.blockEntity.pos.z + 0.5);

        float offset = isLeft ? -0.2f : 0.2f;
        float zOffset = 0.3f;

        return switch (facing) {
            case NORTH -> new WorldPosition(super.blockEntity.world.world, x + offset, y, z + zOffset);
            case SOUTH -> new WorldPosition(super.blockEntity.world.world, x - offset, y, z - zOffset);
            case EAST -> new WorldPosition(super.blockEntity.world.world, x - zOffset, y, z + offset);
            case WEST -> new WorldPosition(super.blockEntity.world.world, x + zOffset, y, z - offset);
            default -> new WorldPosition(super.blockEntity.world.world, x + offset, y, z + zOffset);
        };
    }

    private void refreshElement() {
        TrackedPlayers.forEach(super.blockEntity, this.element::update);
        super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        CompoundTag data = new CompoundTag();
        data.putInt(K_DATA_VERSION, VersionHelper.WORLD_VERSION);
        if (!ItemUtils.isEmpty(itemLeft)) {
            data.put(K_ITEM_LEFT, ItemStackUtils.saveMinecraftItemStackAsTag(itemLeft.minecraftItem()));
        }
        if (!ItemUtils.isEmpty(itemRight)) {
            data.put(K_ITEM_RIGHT, ItemStackUtils.saveMinecraftItemStackAsTag(itemRight.minecraftItem()));
        }
        tag.put(DATA_KEY, data);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        CompoundTag dataTag = tag.getCompound(DATA_KEY);
        if (dataTag == null) {
            this.itemLeft = Item.empty();
            this.itemRight = Item.empty();
            this.updateLastState();
            return;
        }

        int dataVersion = dataTag.getInt(K_DATA_VERSION, Config.itemDataFixerUpperFallbackVersion());

        Tag leftTag = dataTag.get(K_ITEM_LEFT);
        if (leftTag != null) {
            this.itemLeft = ItemStackUtils.wrap(ItemStackUtils.parseMinecraftItem(leftTag, dataVersion));
        } else {
            this.itemLeft = Item.empty();
        }

        Tag rightTag = dataTag.get(K_ITEM_RIGHT);
        if (rightTag != null) {
            this.itemRight = ItemStackUtils.wrap(ItemStackUtils.parseMinecraftItem(rightTag, dataVersion));
        } else {
            this.itemRight = Item.empty();
        }

        this.element.refreshLeftItem(this.itemLeft);
        this.element.refreshRightItem(this.itemRight);
        this.updateLastState();
    }

    @Override
    public void onRemove() {
        DropUtils.dropAtCenter(super.blockEntity, itemLeft);
        DropUtils.dropAtCenter(super.blockEntity, itemRight);
    }
}