package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.SteamerBehavior;

import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.block.entity.tick.BlockEntityTicker;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.block.property.type.SlabType;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.ListTag;
import net.momirealms.craftengine.libraries.nbt.Tag;
import net.momirealms.craftengine.proxy.minecraft.world.level.BlockGetterProxy;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.ApplianceFoodRegistry;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.FoodRecipeResult;
import net.kaleidoscope.cookery.util.HeatSourceUtils;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.block.entity.render.Particles;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.Random;

public class SteamerController extends BlockEntityController {
    public static final String DATA_KEY = "kaleidoscopecookery:steamer";
    private static final String K_BLOCK_ENTITY_TAG = "BlockEntityTag";
    private static final String K_DATA_VERSION = "data_version";
    private static final String K_SEED = "seed";
    private static final String K_HAS_LID = "has_lid";
    private static final String K_LIT_LEVEL = "lit_level";
    private static final String K_ITEMS = "items";
    private static final String K_COOKING_PROGRESS = "cooking_progress";
    private static final String K_COOKING_TIME = "cooking_time";
    private static final int MAX_LIT_LEVEL = 4;
    private static final int SLOTS = 8;
    private final SteamerBehavior behavior;
    private final int[] cookingProgress = new int[SLOTS];
    private final int[] cookingTime = new int[SLOTS];
    private final Item[] items = new Item[SLOTS];
    private final Random random = new Random();
    private int itemCount = 0;
    private final SteamerElement element;
    private boolean hasLid = false;
    private boolean wasCovered = false;
    private long seed = System.currentTimeMillis();
    private int tickCounter = 0;
    private int particleTick = 0;
    private int litLevel = 0;
    private boolean fallingAway = false;
    private boolean skipFoodDrop = false;

    public SteamerController(BlockEntity blockEntity, SteamerBehavior behavior) {
        super(blockEntity);
        this.behavior = behavior;
        Arrays.fill(this.items, Item.empty());
        this.element = new SteamerElement(this, new WorldPosition(
                null, (float) super.blockEntity.pos.x() + 0.5f,
                (float) super.blockEntity.pos.y() + 0.1f,
                (float) super.blockEntity.pos.z() + 0.5f
        ));
    }

    @Override
    public <C extends BlockEntityController> BlockEntityTicker<C> createBlockEntityTicker(
            CEWorld world, ImmutableBlockState blockState) {
        return createTickerHelper((w, pos, state, controller) -> this.tick());
    }

    public void refreshDynamicElement(BiConsumer<SteamerElement, Player> consumer) {
        TrackedPlayers.forEach(super.blockEntity, trackedPlayer -> consumer.accept(this.element, trackedPlayer));
    }

    public void refreshElementState() {
        if (this.element != null) {
            this.element.prepareUpdate();
            refreshDynamicElement(SteamerElement::update);
        }
    }

    public void tick() {
        boolean aboveSteamer = isAboveSteamer();
        boolean currentlyCovered = hasLid || aboveSteamer;
        if (currentlyCovered != wasCovered) {
            wasCovered = currentlyCovered;
            refreshElementState();
        }

        particleTick++;
        if (++tickCounter >= 5) {
            tickCounter = 0;
            updateLitLevel();
        }
        if (particleTick % behavior.particleInterval == 0) {
            for (int i = 0; i < itemCount; i++) {
                if (cookingTime[i] == -1) {
                    makeRipeParticles();
                    break;
                }
            }
        }
        if (litLevel > 0) {
            cookingTick(aboveSteamer);
        } else {
            cooldownTick();
        }
    }

    private void updateLitLevel() {
        Object level = super.blockEntity.world.world().minecraftWorld();
        Object belowPos = LocationUtils.below(LocationUtils.toBlockPos(super.blockEntity.pos));

        if (HeatSourceUtils.isHeatSource(level, belowPos)) {
            this.litLevel = MAX_LIT_LEVEL;
        } else {
            Object belowState = BlockGetterProxy.INSTANCE.getBlockState(level, belowPos);
            Optional<ImmutableBlockState> optionalCustomState = BlockStateUtils.getOptionalCustomBlockState(belowState);

            if (optionalCustomState.isPresent()) {
                ImmutableBlockState belowCustomState = optionalCustomState.get();
                if (belowCustomState.owner().value() == super.blockEntity.blockState.owner().value()) {
                    SteamerBehavior belowBehavior = belowCustomState.behavior().getFirst(SteamerBehavior.class);
                    if (belowBehavior != null) {
                        SlabType type = getSlabType(belowCustomState);

                        // 下层是双层蒸笼时 火力按层向上递减传导
                        if (type == SlabType.DOUBLE) {
                            BlockPos ceBelowPos = new BlockPos(
                                    super.blockEntity.pos.x, super.blockEntity.pos.y - 1, super.blockEntity.pos.z);
                            BlockEntity belowEntity = super.blockEntity.world.getBlockEntityAtIfLoaded(ceBelowPos);

                            if (belowEntity != null) {
                                SteamerController belowController = belowEntity.controller.get(SteamerController.class, belowBehavior.getControllerId());
                                if (belowController != null) {
                                    this.litLevel = Math.max(belowController.getLitLevel() - 1, 0);
                                    return;
                                }
                            }
                        }
                    }
                }
            }
            this.litLevel = 0;
        }
    }

    private void cookingTick(boolean aboveIsSteamer) {
        if (!aboveIsSteamer) {
            makeCookingParticles();
            if (!this.hasLid) {
                return;
            }
        }

        boolean stateChanged = false;
        for (int i = 0; i < itemCount; i++) {
            if (cookingTime[i] <= 0) {
                continue;
            }
            cookingProgress[i]++;
            if (cookingProgress[i] >= cookingTime[i]) {
                Item resultItem = getRecipeResult(items[i]);
                if (!resultItem.isEmpty()) {
                    items[i] = resultItem;
                    cookingTime[i] = -1;
                    cookingProgress[i] = 0;
                    stateChanged = true;
                    this.refreshElementState();
                }
            }
        }
        if (stateChanged) {
            super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
        }
    }

    private void cooldownTick() {
        for (int i = 0; i < itemCount; i++) {
            if (cookingProgress[i] > 0) {
                cookingProgress[i] = Math.max(0, cookingProgress[i] - 2);
            }
        }
    }

    private Item getRecipeResult(Item input) {
        Optional<FoodRecipeResult> result = FoodRecipeRegistry.instance().findAccurate(ApplianceType.STEAMER, input.id());
        // 份数由配方的 result_count 决定 只取 item 会把它吞掉
        return result.map(fr -> fr.item().count(fr.count())).orElse(input);
    }

    public int capacity() {
        SlabType type = getSlabType(super.blockEntity.blockState);
        return (type == SlabType.DOUBLE) ? SLOTS : SLOTS / 2;
    }

    public boolean hasSpace() {
        return itemCount < capacity();
    }

    public boolean canSteam(Item food) {
        return ApplianceFoodRegistry.instance().isAllowed(ApplianceType.STEAMER, food.id());
    }

    public boolean tryAddOne(Item food) {
        if (!hasSpace() || !canSteam(food)) {
            return false;
        }
        items[itemCount] = food.copyWithCount(1);
        cookingProgress[itemCount] = 0;
        cookingTime[itemCount] = behavior.cookingTime;
        itemCount++;
        refreshElementState();
        super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
        return true;
    }

    public Item takeFood(Player player) {
        if (itemCount == 0) {
            return Item.empty();
        }
        int target = -1;
        for (int i = 0; i < itemCount; i++) {
            if (items[i].isEmpty()) {
                continue;
            }
            if (cookingTime[i] == -1) {
                target = i;
                break;
            }
            if (target == -1) {
                target = i;
            }
        }
        if (target == -1) {
            return Item.empty();
        }

        Item taken = items[target].copyWithCount(1);
        for (int i = target; i < itemCount - 1; i++) {
            items[i] = items[i + 1];
            cookingProgress[i] = cookingProgress[i + 1];
            cookingTime[i] = cookingTime[i + 1];
        }
        items[itemCount - 1] = Item.empty();
        cookingProgress[itemCount - 1] = 0;
        cookingTime[itemCount - 1] = 0;
        itemCount--;

        refreshElementState();
        super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
        return taken;
    }

    private void makeCookingParticles() {
        if (particleTick % behavior.particleInterval != 0) {
            return;
        }
        SlabType type = getSlabType(super.blockEntity.blockState);
        double yOffset = (type != SlabType.DOUBLE) ? 0.5 : 1.0;
        double x = super.blockEntity.pos.x + 0.5 + (random.nextDouble() / 2) * (random.nextBoolean() ? 1 : -1);
        double y = super.blockEntity.pos.y + yOffset + random.nextDouble() / 2;
        double z = super.blockEntity.pos.z + 0.5 + (random.nextDouble() / 2) * (random.nextBoolean() ? 1 : -1);
        Particles.emit(super.blockEntity.world, Particle.CLOUD, x, y, z, behavior.particleCount, 0.05, 0.05, 0.05, 0.05, null);
    }

    private void makeRipeParticles() {
        SlabType type = getSlabType(super.blockEntity.blockState);
        double yOffset = (type != SlabType.DOUBLE) ? 0.25 : 0.75;
        double x = super.blockEntity.pos.x + 0.5 + (random.nextDouble() / 1.25) * (random.nextBoolean() ? 1 : -1);
        double y = super.blockEntity.pos.y + yOffset + random.nextDouble() / 2;
        double z = super.blockEntity.pos.z + 0.5 + (random.nextDouble() / 1.25) * (random.nextBoolean() ? 1 : -1);
        Particles.emit(super.blockEntity.world, Particle.CLOUD, x, y, z, behavior.particleCount, 0.05, 0.05, 0.05, 0.05, null);
    }

    private static SlabType getSlabType(ImmutableBlockState state) {
        Property<SlabType> property = state.getProperty("type");
        return property != null ? state.get(property, SlabType.BOTTOM) : SlabType.BOTTOM;
    }

    public boolean isCovered() {
        return this.hasLid || isAboveSteamer();
    }

    private boolean isAboveSteamer() {
        Object level = super.blockEntity.world.world().minecraftWorld();
        Object abovePos = LocationUtils.toBlockPos(
                super.blockEntity.pos.x, super.blockEntity.pos.y + 1, super.blockEntity.pos.z
        );
        Object aboveState = BlockGetterProxy.INSTANCE.getBlockState(level, abovePos);
        Optional<ImmutableBlockState> opt = BlockStateUtils.getOptionalCustomBlockState(aboveState);
        return opt.isPresent() && opt.get().owner().value() == super.blockEntity.blockState.owner().value();
    }

    @Override
    public boolean hasElement() {
        return true;
    }

    @Override
    public void gatherElements(Consumer<BlockEntityElement> consumer) {
        consumer.accept(element);
    }

    public void markFallingAway() {
        this.fallingAway = true;
    }

    public void clearFallingAway() {
        this.fallingAway = false;
    }

    public void markSkipFoodDrop() {
        this.skipFoodDrop = true;
    }

    // 蒸笼装满且全部成品熟透
    public boolean isFullOfFinishedProducts() {
        if (itemCount < capacity()) {
            return false;
        }
        for (int i = 0; i < itemCount; i++) {
            if (items[i].isEmpty() || cookingTime[i] != -1) {
                return false;
            }
        }
        return true;
    }

    // 即将掉落的成品
    public List<ItemStack> finishedProductStacks() {
        List<ItemStack> products = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            if (!items[i].isEmpty()) {
                products.add(ItemStackUtils.getBukkitStack(items[i]));
            }
        }
        return products;
    }

    @Override
    public void onRemove() {
        if (!fallingAway) {
            // 蒸笼方块物品本身的掉落交给方块 loot 创造爆炸等场景行为统一 这里只负责内容物
            // skipFoodDrop 在 SteamerBreakFullEvent 被取消时跳过成品掉落
            if (!skipFoodDrop) {
                for (int i = 0; i < itemCount; i++) {
                    if (!items[i].isEmpty()) {
                        DropUtils.dropAtCenter(super.blockEntity, items[i]);
                    }
                }
            }
        }
        int oldCount = this.itemCount;
        this.itemCount = 0;
        Arrays.fill(this.items, Item.empty());
        if (oldCount > 0) {
            this.refreshElementState();
        }

        super.onRemove();
    }

    @Override
    public void loadCustomDataFromItem(Item item) {
        Object nmsItem = item.minecraftItem();
        Tag tag = ItemStackUtils.saveMinecraftItemStackAsTag(nmsItem);
        if (tag instanceof CompoundTag compoundTag && compoundTag.containsKey(K_BLOCK_ENTITY_TAG)) {
            loadCustomData(compoundTag.getCompound(K_BLOCK_ENTITY_TAG));
        }
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        CompoundTag data = new CompoundTag();
        data.putInt(K_DATA_VERSION, VersionHelper.WORLD_VERSION);
        data.putLong(K_SEED, this.seed);
        data.putBoolean(K_HAS_LID, hasLid);
        data.putInt(K_LIT_LEVEL, litLevel);
        ListTag itemsTag = new ListTag();
        for (int i = 0; i < itemCount; i++) {
            itemsTag.add(ItemStackUtils.saveMinecraftItemStackAsTag(items[i].minecraftItem()));
        }
        data.put(K_ITEMS, itemsTag);
        data.putIntArray(K_COOKING_PROGRESS, cookingProgress);
        data.putIntArray(K_COOKING_TIME, cookingTime);
        tag.put(DATA_KEY, data);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        CompoundTag data = tag.getCompound(DATA_KEY);
        if (data != null) {
            this.seed = data.getLong(K_SEED, System.currentTimeMillis());
            this.hasLid = data.getBoolean(K_HAS_LID, false);
            this.litLevel = data.getInt(K_LIT_LEVEL, 0);
            ListTag itemsTag = data.getList(K_ITEMS);
            Arrays.fill(this.items, Item.empty());
            this.itemCount = 0;
            if (itemsTag != null) {
                int dataVersion = data.getInt(K_DATA_VERSION, Config.itemDataFixerUpperFallbackVersion());
                for (Tag itemTag : itemsTag) {
                    if (this.itemCount >= SLOTS) {
                        break;
                    }
                    Object nmsItem = ItemStackUtils.parseMinecraftItem(itemTag, dataVersion);
                    if (nmsItem != null) {
                        this.items[this.itemCount] = ItemStackUtils.wrap(nmsItem);
                        this.itemCount++;
                    }
                }
            }
            int[] progress = data.getIntArray(K_COOKING_PROGRESS);
            if (progress != null && progress.length == SLOTS) {
                System.arraycopy(progress, 0, this.cookingProgress, 0, SLOTS);
            }
            int[] time = data.getIntArray(K_COOKING_TIME);
            if (time != null && time.length == SLOTS) {
                System.arraycopy(time, 0, this.cookingTime, 0, SLOTS);
            }
        }
    }

    public boolean hasLid() {
        return hasLid;
    }

    public void setHasLid(boolean hasLid) {
        this.hasLid = hasLid;
        this.wasCovered = isCovered();
        refreshElementState();
    }

    public Item[] getItems() {
        return items;
    }

    public int getItemCount() {
        return itemCount;
    }

    public int[] getCookingProgress() {
        return cookingProgress;
    }

    public int[] getCookingTime() {
        return cookingTime;
    }

    public int getLitLevel() {
        return litLevel;
    }

    public long seed() {
        return seed;
    }
}
