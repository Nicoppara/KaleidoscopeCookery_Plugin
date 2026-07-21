package net.kaleidoscope.cookery.block.entity;
import net.kaleidoscope.cookery.block.behavior.EnamelBasinBehavior;

import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.util.DropUtils;

public class EnamelBasinController extends BlockEntityController {
    private static final String DATA_KEY = "kaleidoscopecookery:cooking_enamel_basin";
    private static final String K_OIL_COUNT = "oil_count";
    private static final String K_CLOSED = "closed";

    private int oilCount = 0;
    private boolean closed = true;
    private final EnamelBasinBehavior behavior;

    public EnamelBasinController(BlockEntity blockEntity, EnamelBasinBehavior behavior) {
        super(blockEntity);
        this.behavior = behavior;
    }

    public int getOilCount() {
        return oilCount;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        if (this.closed == closed) {
            return;
        }
        this.closed = closed;
        updateBlockState();
        this.blockEntity.world.blockEntityChanged(this.blockEntity.pos);
    }

    public void addOil(int amount) {
        if (oilCount >= behavior.maxOil) {
            return;
        }
        oilCount = Math.min(oilCount + amount, behavior.maxOil);
        updateBlockState();
        this.blockEntity.world.blockEntityChanged(this.blockEntity.pos);
    }

    public void removeOil(int amount) {
        if (oilCount <= 0) {
            return;
        }
        oilCount = Math.max(oilCount - amount, 0);
        updateBlockState();
        this.blockEntity.world.blockEntityChanged(this.blockEntity.pos);
    }

    private void updateBlockState() {
        int oilLevel;
        if (this.closed) {
            // 关闭时显示底座
            oilLevel = 0;
        } else {
            oilLevel = getOilLevelInt(this.oilCount);
        }

        Property<Integer> oilLevelProperty = this.blockEntity.blockState.getProperty("oil_level");
        Property<Direction> facingProperty = this.blockEntity.blockState.getProperty("facing");

        if (oilLevelProperty != null && facingProperty != null) {
            Direction currentFacing = this.blockEntity.blockState.get(facingProperty);
            ImmutableBlockState newState = this.blockEntity.blockState
                    .with(oilLevelProperty, oilLevel)
                    .with(facingProperty, currentFacing);

            BlockStates.sync(this.blockEntity, newState);
        }
    }

    // 按油量映射档位
    private int getOilLevelInt(int oilCount) {
        return switch (oilCount) {
            case 0 -> 1;
            case 1, 2, 3, 4, 5 -> 2;
            case 6, 7, 8, 9, 10 -> 3;
            default -> 4;
        };
    }

    @Override
    public void onRemove() {
        if (oilCount > 0) {
            Item oilItem = InventoryUtils.createOrEmpty(behavior.oilItem);
            if (!oilItem.isEmpty()) {
                oilItem.count(oilCount);
                DropUtils.dropAtCenter(super.blockEntity, oilItem);
            }
        }
        super.onRemove();
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        CompoundTag data = new CompoundTag();
        data.putInt(K_OIL_COUNT, oilCount);
        data.putBoolean(K_CLOSED, closed);
        tag.put(DATA_KEY, data);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        CompoundTag data = tag.getCompound(DATA_KEY);
        if (data == null) return;
        this.oilCount = data.getInt(K_OIL_COUNT, 0);
        this.closed = data.getBoolean(K_CLOSED, true);
    }
}
