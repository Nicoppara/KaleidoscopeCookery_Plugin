package top.nicoppa.nicoPengRen.content.shawarma;

import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.block.entity.tick.BlockEntityTicker;
import net.momirealms.craftengine.core.block.property.type.DoubleBlockHalf;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.ListTag;
import net.momirealms.craftengine.libraries.nbt.Tag;
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.util.VersionHelper;
import top.nicoppa.nicoPengRen.common.item.DropUtils;
import top.nicoppa.nicoPengRen.common.item.InventoryUtils;
import top.nicoppa.nicoPengRen.recipe.ApplianceType;
import top.nicoppa.nicoPengRen.recipe.food.ApplianceFoodRegistry;
import top.nicoppa.nicoPengRen.recipe.food.FoodRecipeRegistry;
import top.nicoppa.nicoPengRen.recipe.food.FoodRecipeResult;

import java.util.Arrays;

/**
 * 烤肉串方块实体 功能控制器只挂在下半
 * 但内部按两层独立管理食材 layer 0=下层、layer 1=上层，各 {@link #SLOTS} 槽，互不串层
 * 红石通电且全空或仍有生料时整串旋转并烹饪 全是成品则停转
 * 交互/红石入口见 {@link ShawarmaSpitBehavior}，渲染见 {@link ShawarmaSpitElement}
 */
public class ShawarmaSpitController extends BlockEntityController {
    public static final int LAYERS = 2;   // 0=下层, 1=上层
    public static final int SLOTS = 8;    // 每层槽数

    private final ShawarmaSpitBehavior behavior;
    private final boolean lower;
    private final Item[][] items = new Item[LAYERS][SLOTS];
    private final int[][] cookingProgress = new int[LAYERS][SLOTS];
    private final int[][] cookingTime = new int[LAYERS][SLOTS];

    private float currentRotation = 0f;
    private int animationTick = 0;
    private boolean wasActive = false;

    private final ShawarmaSpitElement element;

    public ShawarmaSpitController(BlockEntity blockEntity, ShawarmaSpitBehavior behavior) {
        super(blockEntity);
        this.behavior = behavior;
        this.lower = blockEntity.blockState.get(behavior.getHalfProperty()) != DoubleBlockHalf.UPPER;
        for (Item[] layer : items) Arrays.fill(layer, Item.empty());
        this.element = lower ? new ShawarmaSpitElement(this, behavior) : null;
    }

    @Override
    public <C extends BlockEntityController> BlockEntityTicker<C> createBlockEntityTicker(CEWorld world, ImmutableBlockState blockState) {
        if (blockState.get(behavior.getHalfProperty()) == DoubleBlockHalf.UPPER) return null;
        return createTickerHelper((w, pos, state, controller) -> this.tick());
    }

    private boolean isPowered() {
        return blockEntity.blockState.get(behavior.getPoweredProperty());
    }

    private boolean hasRaw() {
        for (int l = 0; l < LAYERS; l++)
            for (int s = 0; s < SLOTS; s++)
                if (cookingTime[l][s] > 0) return true;
        return false;
    }

    public boolean isEmpty() {
        for (int l = 0; l < LAYERS; l++)
            for (int s = 0; s < SLOTS; s++)
                if (!items[l][s].isEmpty()) return false;
        return true;
    }

    public boolean isActive() {
        return isPowered() && (isEmpty() || hasRaw());
    }

    public void tick() {
        if (!isActive()) {
            wasActive = false;
            return;
        }
        if (!wasActive) {
            animationTick = 0;
            wasActive = true;
        }

        boolean changed = false;
        for (int l = 0; l < LAYERS; l++) {
            for (int s = 0; s < SLOTS; s++) {
                if (cookingTime[l][s] <= 0) continue;
                cookingProgress[l][s]++;
                if (cookingProgress[l][s] >= cookingTime[l][s]) {
                    items[l][s] = getRecipeResult(items[l][s]);
                    cookingTime[l][s] = -1;
                    cookingProgress[l][s] = 0;
                    element.updateSlotItem(l, s, items[l][s]);
                    changed = true;
                }
            }
        }
        if (changed) blockEntity.world.blockEntityChanged(blockEntity.pos);

        if (animationTick % 20 == 0) {
            currentRotation = (currentRotation + 45.0f) % 360.0f;
            element.updateRotation();
        }
        animationTick++;
    }

    private Item getRecipeResult(Item input) {
        return FoodRecipeRegistry.instance()
                .findAccurate(ApplianceType.SHAWARMA, input.id())
                .map(FoodRecipeResult::item)
                .orElse(input.copy());
    }

    // 该食材是否允许放入烤架（由 accurate_foods 中 cook=shawarma 的 require 控制）
    public boolean canCook(Item item) {
        return ApplianceFoodRegistry.instance().isAllowed(ApplianceType.SHAWARMA, item.id());
    }

    private int firstEmptySlot(int layer) {
        for (int s = 0; s < SLOTS; s++) if (items[layer][s].isEmpty()) return s;
        return -1;
    }

    public boolean tryAddOne(int layer, Item item) {
        int s = firstEmptySlot(layer);
        if (s < 0) return false;
        items[layer][s] = item.copyWithCount(1);
        cookingProgress[layer][s] = 0;
        cookingTime[layer][s] = behavior.grillTime;
        element.spawnSlot(layer, s, items[layer][s]);
        blockEntity.world.blockEntityChanged(blockEntity.pos);
        return true;
    }

    private void clearSlot(int layer, int s) {
        items[layer][s] = Item.empty();
        cookingProgress[layer][s] = 0;
        cookingTime[layer][s] = 0;
        element.removeSlot(layer, s);
    }

    /**
     * 空手右键取出
     * 有成品 → 一键取走该层所有成品
     * 无成品 → 取走 1 个生料
     * 返回是否取走了任何东西
     */
    public boolean takeFromLayer(int layer, Player player, InteractionHand hand) {
        boolean hasCooked = false;
        for (int s = 0; s < SLOTS; s++) {
            if (!items[layer][s].isEmpty() && cookingTime[layer][s] == -1) { hasCooked = true; break; }
        }

        boolean took = false;
        if (hasCooked) {
            for (int s = 0; s < SLOTS; s++) {
                if (items[layer][s].isEmpty() || cookingTime[layer][s] != -1) continue;
                Item it = items[layer][s].copy();
                if (!InventoryUtils.hasSpaceFor(player, it)) break;
                InventoryUtils.give(player, it);
                clearSlot(layer, s);
                took = true;
            }
        } else {
            for (int s = 0; s < SLOTS; s++) {
                if (items[layer][s].isEmpty()) continue;
                InventoryUtils.giveOrHold(player, hand, items[layer][s].copy());
                clearSlot(layer, s);
                took = true;
                break;
            }
        }
        if (took) blockEntity.world.blockEntityChanged(blockEntity.pos);
        return took;
    }

    public boolean layerEmpty(int layer) {
        for (int s = 0; s < SLOTS; s++) if (!items[layer][s].isEmpty()) return false;
        return true;
    }

    public Item[][] getItems() { return items; }
    public float getCurrentRotation() { return currentRotation; }
    public BlockPos getPos() { return blockEntity.pos; }
    public ImmutableBlockState getBlockState() { return blockEntity.blockState; }
    public CEWorld getWorld() { return blockEntity.world; }

    @Override
    public boolean hasElement() { return lower; }

    @Override
    public void gatherElements(java.util.function.Consumer<BlockEntityElement> consumer) {
        if (element != null) consumer.accept(element);
    }

    @Override
    public void onRemove() {
        for (int l = 0; l < LAYERS; l++) {
            for (int s = 0; s < SLOTS; s++) {
                if (!items[l][s].isEmpty()) DropUtils.dropAtCenter(blockEntity, items[l][s]);
            }
        }
        super.onRemove();
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        if (!lower) return;
        tag.putInt("data_version", VersionHelper.WORLD_VERSION);
        ListTag itemsTag = new ListTag();
        for (int l = 0; l < LAYERS; l++) {
            for (int s = 0; s < SLOTS; s++) {
                if (items[l][s].isEmpty()) continue;
                CompoundTag entry = new CompoundTag();
                entry.putInt("layer", l);
                entry.putInt("slot", s);
                entry.put("item", ItemStackUtils.saveMinecraftItemStackAsTag(items[l][s].minecraftItem()));
                entry.putInt("progress", cookingProgress[l][s]);
                entry.putInt("time", cookingTime[l][s]);
                itemsTag.add(entry);
            }
        }
        tag.put("items", itemsTag);
        tag.putFloat("current_rotation", currentRotation);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        if (!lower) return;
        int dataVersion = tag.getInt("data_version", Config.itemDataFixerUpperFallbackVersion());
        for (int l = 0; l < LAYERS; l++) {
            Arrays.fill(items[l], Item.empty());
            Arrays.fill(cookingProgress[l], 0);
            Arrays.fill(cookingTime[l], 0);
        }

        ListTag itemsTag = tag.getList("items");
        if (itemsTag != null) {
            for (Tag t : itemsTag) {
                if (!(t instanceof CompoundTag entry)) continue;
                int l = entry.getInt("layer", 0);
                int s = entry.getInt("slot", -1);
                if (l < 0 || l >= LAYERS || s < 0 || s >= SLOTS) continue;
                Object nms = ItemStackUtils.parseMinecraftItem(entry.getCompound("item"), dataVersion);
                if (nms != null) {
                    items[l][s] = ItemStackUtils.wrap(nms);
                    cookingProgress[l][s] = entry.getInt("progress", 0);
                    cookingTime[l][s] = entry.getInt("time", behavior.grillTime);
                }
            }
        }
        currentRotation = tag.getFloat("current_rotation", 0f);

        element.refreshAllItems();
    }
}
