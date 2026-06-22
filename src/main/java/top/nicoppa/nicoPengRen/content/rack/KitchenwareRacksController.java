package top.nicoppa.nicoPengRen.content.rack;

import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.Tag;
import org.jetbrains.annotations.NotNull;
import top.nicoppa.nicoPengRen.common.item.DropUtils;
import top.nicoppa.nicoPengRen.common.render.TrackedPlayers;

import java.util.function.Consumer;

/**
 * 厨具架方块实体 维护左右两槽物品及变更状态，按朝向计算展示位置并驱动渲染、负责 NBT 存读与移除掉落
 * 交互入口见 {@link KitchenwareRacksBehavior}，渲染见 {@link KitchenwareRacksElement}
 */
public final class KitchenwareRacksController extends BlockEntityController {
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
        this.lastItemLeft = itemLeft;
        this.lastItemRight = itemRight;
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
        // TODO 访问窜类 等重构时修改
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
        data.putInt("data_version", VersionHelper.WORLD_VERSION);
        if (!ItemUtils.isEmpty(itemLeft)) {
            data.put("item_left", ItemStackUtils.saveMinecraftItemStackAsTag(itemLeft.minecraftItem()));
        }
        if (!ItemUtils.isEmpty(itemRight)) {
            data.put("item_right", ItemStackUtils.saveMinecraftItemStackAsTag(itemRight.minecraftItem()));
        }
        tag.put(behavior.customDataKey, data);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        CompoundTag dataTag = tag.getCompound(behavior.customDataKey);
        if (dataTag == null) {
            this.itemLeft = Item.empty();
            this.itemRight = Item.empty();
            this.updateLastState();
            return;
        }

        int dataVersion = dataTag.getInt("data_version", VersionHelper.WORLD_VERSION);

        Tag leftTag = dataTag.get("item_left");
        if (leftTag != null) {
            this.itemLeft = ItemStackUtils.wrap(ItemStackUtils.parseMinecraftItem(leftTag, dataVersion));
        } else {
            this.itemLeft = Item.empty();
        }

        Tag rightTag = dataTag.get("item_right");
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
