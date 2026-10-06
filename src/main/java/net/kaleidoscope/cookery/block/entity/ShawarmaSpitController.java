package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.block.behavior.ShawarmaSpitBehavior;

import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
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
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import net.kaleidoscope.cookery.api.event.ShawarmaExtractEvent;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.util.EventUtils;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.ApplianceFoodRegistry;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.CookingPlan;
import net.kaleidoscope.cookery.recipe.FoodRecipeResult;
import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;

public class ShawarmaSpitController extends BlockEntityController {
    // 0 下层 1 上层
    public static final int LAYERS = 2;
    // 每层槽数
    public static final int SLOTS = 8;
    private static final String DATA_KEY = "kaleidoscopecookery:shawarma_spit";
    private static final String K_DATA_VERSION = "data_version";
    private static final String K_ITEMS = "items";
    private static final String K_LAYER = "layer";
    private static final String K_SLOT = "slot";
    private static final String K_ITEM = "item";
    private static final String K_PROGRESS = "progress";
    private static final String K_TIME = "time";
    private static final String K_PLAN = "cooking_plan";
    private static final String K_BLOCKED = "completion_blocked";
    private static final String K_CURRENT_ROTATION = "current_rotation";

    private final ShawarmaSpitBehavior behavior;
    private final boolean lower;
    private final Item[][] items = new Item[LAYERS][SLOTS];
    private final int[][] cookingProgress = new int[LAYERS][SLOTS];
    private final int[][] cookingTime = new int[LAYERS][SLOTS];
    private final CookingPlan[][] cookingPlans = new CookingPlan[LAYERS][SLOTS];
    private final boolean[][] completionBlocked = new boolean[LAYERS][SLOTS];

    private float currentRotation = 0f;
    private int animationTick = 0;
    private boolean wasActive = false;
    private int dirtyTick;
    private boolean progressDirty;
    private boolean needsPlanHydration;

    private final ShawarmaSpitElement element;

    public ShawarmaSpitController(BlockEntity blockEntity, ShawarmaSpitBehavior behavior) {
        super(blockEntity);
        this.behavior = behavior;
        this.dirtyTick = Math.floorMod(blockEntity.pos.x() * 31 + blockEntity.pos.z(), 20);
        var halfProperty = behavior.getHalfProperty();
        this.lower = BlockStates.value(blockEntity.blockState, halfProperty, halfProperty.defaultValue())
                != DoubleBlockHalf.UPPER;
        for (Item[] layer : items) {
            Arrays.fill(layer, Item.empty());
        }
        this.element = lower ? new ShawarmaSpitElement(this, behavior) : null;
    }

    @Override
    public <C extends BlockEntityController> BlockEntityTicker<C> createBlockEntityTicker(CEWorld world, ImmutableBlockState blockState) {
        var halfProperty = behavior.getHalfProperty();
        if (BlockStates.value(blockState, halfProperty, halfProperty.defaultValue()) == DoubleBlockHalf.UPPER) {
            return null;
        }
        return createTickerHelper((w, pos, state, controller) -> this.tick());
    }

    private boolean isPowered() {
        return BlockStates.value(blockEntity.blockState, behavior.getPoweredProperty(), false);
    }

    private boolean hasRaw() {
        for (int l = 0; l < LAYERS; l++) {
            for (int s = 0; s < SLOTS; s++) {
                if (cookingTime[l][s] > 0 && !completionBlocked[l][s]) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isEmpty() {
        for (int l = 0; l < LAYERS; l++) {
            for (int s = 0; s < SLOTS; s++) {
                if (!items[l][s].isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean isActive() {
        return isPowered() && (isEmpty() || hasRaw());
    }

    public void tick() {
        hydrateLegacyPlans();
        if (!isActive()) {
            if (wasActive) {
                element.updateFinalRotation();
            }
            wasActive = false;
            flushProgress();
            return;
        }
        if (!wasActive) {
            animationTick = 0;
            wasActive = true;
        }

        boolean changed = false;
        for (int l = 0; l < LAYERS; l++) {
            for (int s = 0; s < SLOTS; s++) {
                if (cookingTime[l][s] <= 0 || completionBlocked[l][s]) {
                    continue;
                }
                CookingPlan plan = cookingPlans[l][s];
                if (plan == null || !plan.valid()) {
                    blockCompletion(l, s);
                    changed = true;
                    continue;
                }
                cookingProgress[l][s]++;
                progressDirty = true;
                if (cookingProgress[l][s] >= cookingTime[l][s]) {
                    Optional<FoodRecipeResult> recipeResult = plan.matched() ? plan.buildResult() : Optional.empty();
                    if (plan.matched() && recipeResult.isEmpty()) {
                        blockCompletion(l, s);
                        changed = true;
                        continue;
                    }
                    items[l][s] = recipeResult.map(fr -> fr.item().copyWithCount(fr.count())).orElse(items[l][s].copy());
                    cookingTime[l][s] = -1;
                    cookingProgress[l][s] = 0;
                    element.updateSlotItem(l, s, items[l][s]);
                    changed = true;
                }
            }
        }
        if (changed) {
            progressDirty = false;
            blockEntity.world.blockEntityChanged(blockEntity.pos);
        }
        if (++dirtyTick >= 20) {
            dirtyTick = 0;
            flushProgress();
        }

        if (animationTick % 20 == 0) {
            currentRotation = (currentRotation + 45.0f) % 360.0f;
            element.updateRotation();
        }
        animationTick++;
    }

    private void hydrateLegacyPlans() {
        if (!needsPlanHydration) return;
        needsPlanHydration = false;
        boolean changed = false;
        for (int l = 0; l < LAYERS; l++) {
            for (int s = 0; s < SLOTS; s++) {
                if (cookingTime[l][s] > 0 && cookingPlans[l][s] == null && !items[l][s].isEmpty()) {
                    cookingPlans[l][s] = FoodRecipeRegistry.instance()
                            .planAccurate(ApplianceType.SHAWARMA, items[l][s].id(), cookingTime[l][s])
                            .withWorkRequired(cookingTime[l][s]);
                    changed = true;
                }
            }
        }
        if (changed) blockEntity.world.blockEntityChanged(blockEntity.pos);
    }

    private void blockCompletion(int layer, int slot) {
        completionBlocked[layer][slot] = true;
        var plugin = KaleidoscopeCookeryPlugin.instance();
        if (plugin != null) plugin.getLogger().warning("Shawarma cooking paused at " + blockEntity.pos
                + " (layer " + layer + ", slot " + slot + "): saved recipe or result is unavailable; input retained.");
    }

    private void flushProgress() {
        if (progressDirty) {
            progressDirty = false;
            blockEntity.world.blockEntityChanged(blockEntity.pos);
        }
    }

    // 该食材是否允许放入烤架
    public boolean canCook(Item item) {
        return ApplianceFoodRegistry.instance().isAllowed(ApplianceType.SHAWARMA, item.id());
    }

    private int firstEmptySlot(int layer) {
        for (int s = 0; s < SLOTS; s++) {
            if (items[layer][s].isEmpty()) {
                return s;
            }
        }
        return -1;
    }

    public boolean tryAddOne(int layer, Item item) {
        return FoodRecipeRegistry.instance().readSnapshot(() -> tryAddOneInSnapshot(layer, item));
    }

    private boolean tryAddOneInSnapshot(int layer, Item item) {
        int s = firstEmptySlot(layer);
        if (s < 0) {
            return false;
        }
        items[layer][s] = item.copyWithCount(1);
        cookingProgress[layer][s] = 0;
        cookingPlans[layer][s] = FoodRecipeRegistry.instance()
                .planAccurate(ApplianceType.SHAWARMA, item.id(), behavior.grillTime);
        cookingTime[layer][s] = cookingPlans[layer][s].workRequired();
        completionBlocked[layer][s] = false;
        element.spawnSlot(layer, s, items[layer][s]);
        blockEntity.world.blockEntityChanged(blockEntity.pos);
        return true;
    }

    private void clearSlot(int layer, int s) {
        items[layer][s] = Item.empty();
        cookingProgress[layer][s] = 0;
        cookingTime[layer][s] = 0;
        cookingPlans[layer][s] = null;
        completionBlocked[layer][s] = false;
        element.removeSlot(layer, s);
    }

    // 空手右键取出 有成品则一键取走该层所有成品 否则取走 1 个生料
    public boolean takeFromLayer(int layer, Player player, InteractionHand hand) {
        boolean hasCooked = false;
        for (int s = 0; s < SLOTS; s++) {
            if (!items[layer][s].isEmpty() && cookingTime[layer][s] == -1) {
                hasCooked = true;
                break;
            }
        }

        boolean took;
        if (hasCooked) {
            took = takeCookedProducts(layer, player);
        } else {
            took = takeOneRaw(layer, player, hand);
        }
        if (took) {
            blockEntity.world.blockEntityChanged(blockEntity.pos);
        }
        return took;
    }

    // 取走该层全部成品 逐个触发 ShawarmaExtractEvent
    private boolean takeCookedProducts(int layer, Player player) {
        boolean took = false;
        for (int s = 0; s < SLOTS; s++) {
            if (items[layer][s].isEmpty() || cookingTime[layer][s] != -1) {
                continue;
            }
            Item it = items[layer][s].copy();
            ItemStack product = ItemStackUtils.getBukkitStack(it);
            ShawarmaExtractEvent event = new ShawarmaExtractEvent(
                    (org.bukkit.entity.Player) player.platformPlayer(), extractLocation(), product);
            if (EventUtils.fireAndCheckCancel(event)) {
                continue;
            }
            Item give = BukkitItemManager.instance().wrap(event.product());
            if (!InventoryUtils.hasSpaceFor(player, give)) {
                break;
            }
            InventoryUtils.give(player, give);
            clearSlot(layer, s);
            took = true;
        }
        return took;
    }

    // 取走该层第一个生料
    private boolean takeOneRaw(int layer, Player player, InteractionHand hand) {
        for (int s = 0; s < SLOTS; s++) {
            if (items[layer][s].isEmpty()) {
                continue;
            }
            InventoryUtils.giveOrHold(player, hand, items[layer][s].copy());
            clearSlot(layer, s);
            return true;
        }
        return false;
    }

    private Location extractLocation() {
        BlockPos pos = blockEntity.pos;
        return new Location((org.bukkit.World) blockEntity.world.world().platformWorld(), pos.x(), pos.y(), pos.z());
    }

    public boolean layerEmpty(int layer) {
        for (int s = 0; s < SLOTS; s++) {
            if (!items[layer][s].isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public Item[][] getItems() {
        return items;
    }

    public float getCurrentRotation() {
        return currentRotation;
    }

    public BlockPos getPos() {
        return blockEntity.pos;
    }

    public ImmutableBlockState getBlockState() {
        return blockEntity.blockState;
    }

    public CEWorld getWorld() {
        return blockEntity.world;
    }

    @Override
    public boolean hasElement() {
        return lower;
    }

    @Override
    public void gatherElements(Consumer<BlockEntityElement> consumer) {
        if (element != null) {
            consumer.accept(element);
        }
    }

    @Override
    public void onRemove() {
        for (int l = 0; l < LAYERS; l++) {
            for (int s = 0; s < SLOTS; s++) {
                if (!items[l][s].isEmpty()) {
                    DropUtils.dropOnRemove(blockEntity, items[l][s]);
                }
            }
        }
        super.onRemove();
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        if (!lower) {
            return;
        }
        CompoundTag data = new CompoundTag();
        data.putInt(K_DATA_VERSION, VersionHelper.WORLD_VERSION);
        ListTag itemsTag = new ListTag();
        for (int l = 0; l < LAYERS; l++) {
            for (int s = 0; s < SLOTS; s++) {
                Tag itemTag = BlockEntityNbt.itemTag(items[l][s]);
                if (itemTag == null) {
                    continue;
                }
                CompoundTag entry = new CompoundTag();
                entry.putInt(K_LAYER, l);
                entry.putInt(K_SLOT, s);
                entry.put(K_ITEM, itemTag);
                entry.putInt(K_PROGRESS, cookingProgress[l][s]);
                entry.putInt(K_TIME, cookingTime[l][s]);
                if (cookingPlans[l][s] != null) entry.put(K_PLAN, cookingPlans[l][s].save());
                entry.putBoolean(K_BLOCKED, completionBlocked[l][s]);
                itemsTag.add(entry);
            }
        }
        data.put(K_ITEMS, itemsTag);
        data.putFloat(K_CURRENT_ROTATION, currentRotation);
        tag.put(DATA_KEY, data);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        if (!lower) {
            return;
        }
        needsPlanHydration = false;
        progressDirty = false;
        for (int l = 0; l < LAYERS; l++) {
            Arrays.fill(items[l], Item.empty());
            Arrays.fill(cookingProgress[l], 0);
            Arrays.fill(cookingTime[l], 0);
            Arrays.fill(cookingPlans[l], null);
            Arrays.fill(completionBlocked[l], false);
        }

        CompoundTag data = tag.getCompound(DATA_KEY);
        if (data == null) return;
        int dataVersion = data.getInt(K_DATA_VERSION, Config.itemDataFixerUpperFallbackVersion());

        ListTag itemsTag = data.getList(K_ITEMS);
        if (itemsTag != null) {
            for (Tag t : itemsTag) {
                if (!(t instanceof CompoundTag entry)) {
                    continue;
                }
                int l = entry.getInt(K_LAYER, 0);
                int s = entry.getInt(K_SLOT, -1);
                if (l < 0 || l >= LAYERS || s < 0 || s >= SLOTS) {
                    continue;
                }
                Object nms = ItemStackUtils.parseMinecraftItem(entry.getCompound(K_ITEM), dataVersion);
                if (nms != null) {
                    items[l][s] = ItemStackUtils.wrap(nms);
                    cookingProgress[l][s] = entry.getInt(K_PROGRESS, 0);
                    cookingTime[l][s] = entry.getInt(K_TIME, behavior.grillTime);
                    if (entry.containsKey(K_PLAN)) cookingPlans[l][s] = CookingPlan.load(entry.getCompound(K_PLAN));
                    completionBlocked[l][s] = entry.getBoolean(K_BLOCKED, false);
                    if (cookingTime[l][s] > 0 && cookingPlans[l][s] == null) needsPlanHydration = true;
                }
            }
        }
        currentRotation = data.getFloat(K_CURRENT_ROTATION, 0f);

        element.refreshAllItems();
    }
}
