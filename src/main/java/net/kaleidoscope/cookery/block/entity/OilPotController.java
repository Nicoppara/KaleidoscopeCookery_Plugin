package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.OilPotBehavior;
import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;

// 放置的油壶 油量存在方块实体里 方块态只留 has_oil 供外观切换
// 与模组一致 破坏时把油量原样带回物品 而不是散一地油脂
public final class OilPotController extends BlockEntityController {
    private static final String DATA_KEY = "kaleidoscopecookery:oil_pot";
    private static final String K_OIL_COUNT = "oil_count";

    private final OilPotBehavior behavior;
    private int oilCount;

    public OilPotController(BlockEntity blockEntity, OilPotBehavior behavior) {
        super(blockEntity);
        this.behavior = behavior;
    }

    public int oilCount() {
        return this.oilCount;
    }

    public void setOilCount(int count) {
        int clamped = Math.max(0, Math.min(count, this.behavior.maxOil));
        if (clamped == this.oilCount) {
            return;
        }
        this.oilCount = clamped;
        updateBlockState();
        this.blockEntity.world.blockEntityChanged(this.blockEntity.pos);
    }

    // 返回实际加进去的量 调用方按这个数扣手中物品
    public int addOil(int amount) {
        int added = Math.min(amount, this.behavior.maxOil - this.oilCount);
        if (added <= 0) {
            return 0;
        }
        setOilCount(this.oilCount + added);
        return added;
    }

    public int removeOil(int amount) {
        int removed = Math.min(amount, this.oilCount);
        if (removed <= 0) {
            return 0;
        }
        setOilCount(this.oilCount - removed);
        return removed;
    }

    private void updateBlockState() {
        Property<Boolean> hasOil = this.blockEntity.blockState.getProperty("has_oil");
        if (hasOil == null) {
            return;
        }
        boolean expected = this.oilCount > 0;
        if (this.blockEntity.blockState.get(hasOil) == expected) {
            return;
        }
        ImmutableBlockState newState = this.blockEntity.blockState.with(hasOil, expected);
        BlockStates.sync(this.blockEntity, newState);
    }

    // 方块的 loot 段留空 掉落全由这里负责 否则破坏一次会掉两个壶
    // dropOnRemove 内部已挡掉放置被领地回滚时的那次移除 见 PlacementGuard
    @Override
    public void onRemove() {
        DropUtils.dropOnRemove(this.blockEntity, buildDrop());
        super.onRemove();
    }

    private Item buildDrop() {
        if (this.oilCount <= 0) {
            return InventoryUtils.createOrEmpty(this.behavior.emptyPotItem);
        }
        Item pot = InventoryUtils.createOrEmpty(this.behavior.potItem);
        if (!ItemUtils.isEmpty(pot)) {
            pot.damage(this.behavior.maxOil - this.oilCount);
        }
        return pot;
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        CompoundTag data = new CompoundTag();
        data.putInt(K_OIL_COUNT, this.oilCount);
        tag.put(DATA_KEY, data);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        CompoundTag data = tag.getCompound(DATA_KEY);
        if (data == null) {
            return;
        }
        this.oilCount = Math.max(0, Math.min(data.getInt(K_OIL_COUNT, 0), this.behavior.maxOil));
    }
}
