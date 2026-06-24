package net.kaleidoscope.cookery.block.entity;
import net.kaleidoscope.cookery.block.behavior.StockpotBehavior;
import net.kaleidoscope.cookery.block.entity.render.StockpotElement;

import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.block.entity.tick.BlockEntityTicker;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.food.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.food.FoodRecipeResult;
import net.kaleidoscope.cookery.util.HeatSourceUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

// 高汤锅方块实体：持有烹饪状态机（汤底/食材/锅盖/成品），按 tick 推进烹饪并刷新渲染，负责 NBT 存读
public class StockpotController extends BlockEntityController {
    public static final int MAX_INGREDIENTS = 8;

    private static final Key NO_RECIPE_RESULT = Key.of("cook:suspicious_stir_fry");
    private static final Key LAVA_BUCKET = Key.of("minecraft:lava_bucket");

    private StockpotStage stage = StockpotStage.PUT_SOUP_BASE;
    private int currentTick = -1;
    private int takeoutCount = 0;
    private int finishedMax = 0;
    private boolean heatedCache = false;
    private int heatCheckTick = 0;
    private final List<Item> ingredients = new ArrayList<>();
    private Item result = Item.empty();
    private final List<Key> lastCookedIngredients = new ArrayList<>();
    private final List<Item> ingredientsView = java.util.Collections.unmodifiableList(ingredients);
    private final List<Key> lastCookedIngredientsView = java.util.Collections.unmodifiableList(lastCookedIngredients);
    private final StockpotElement element;
    private final StockpotBehavior behavior;
    private Key soupBaseId = Key.of("minecraft:water");
    private Item lidItem = Item.empty();
    private long seed = System.currentTimeMillis();

    private final RenderTracker renderTracker = new RenderTracker();

    public static final class RenderTracker {
        public StockpotStage stage = StockpotStage.PUT_SOUP_BASE;
        public int ingredientCount = 0;
        public Key soupBaseId = Key.of("minecraft:water");

        void reset() {
            stage = StockpotStage.PUT_SOUP_BASE;
            ingredientCount = 0;
            soupBaseId = Key.of("minecraft:water");
        }
    }

    @Override
    public <C extends BlockEntityController> BlockEntityTicker<C> createBlockEntityTicker(
            CEWorld world, ImmutableBlockState blockState) {
        return createTickerHelper((w, pos, state, controller) -> this.tick());
    }

    public StockpotController(BlockEntity blockEntity, StockpotBehavior behavior) {
        super(blockEntity);
        this.behavior = behavior;
        this.element = new StockpotElement(this, new WorldPosition(
                null,
                (float) super.blockEntity.pos.x() + 0.5f,
                (float) super.blockEntity.pos.y() + 0.1f,
                (float) super.blockEntity.pos.z() + 0.5f
        ));
    }

    public void refreshDynamicElement(BiConsumer<StockpotElement, Player> consumer) {
        TrackedPlayers.forEach(super.blockEntity, player -> consumer.accept(this.element, player));
    }

    public void tick() {
        if (stage == StockpotStage.PUT_SOUP_BASE) {
            return;
        }

        if (heatCheckTick == 0) heatedCache = hasHeatSource();
        heatCheckTick = (heatCheckTick + 1) % 20;
        if (!heatedCache) {
            return;
        }

        boolean hasLid = hasLid();

        org.bukkit.World bWorld = (org.bukkit.World) super.blockEntity.world.world().platformWorld();
        if (bWorld.getGameTime() % 15 == 0) {
            float volume = hasLid ? 0.3f : 0.6f;
            float pitch = 0.9f + (float) ThreadLocalRandom.current().nextDouble() * 0.2f;
            super.blockEntity.world.world().playSound(
                    Vec3d.atCenterOf(super.blockEntity.pos),
                    Key.of("kaleidoscopecookery:stockpot_" + (int) (ThreadLocalRandom.current().nextDouble() * 7)),
                    volume, pitch, SoundSource.BLOCK);
        }

        if (!hasLid) {
            if (bWorld.getGameTime() % 8 == 0) {
                double bx = super.blockEntity.pos.x() + 0.3 + ThreadLocalRandom.current().nextDouble() * 0.4;
                double by = super.blockEntity.pos.y() + 0.4;
                double bz = super.blockEntity.pos.z() + 0.3 + ThreadLocalRandom.current().nextDouble() * 0.4;
                if (isLavaSoup()) {
                    bWorld.spawnParticle(org.bukkit.Particle.LAVA, bx, by, bz, 1, 0, 0, 0, 0);
                } else {
                    bWorld.spawnParticle(org.bukkit.Particle.SPLASH, bx, by, bz, 2, 0.05, 0.0, 0.05, 0.1);
                    bWorld.spawnParticle(org.bukkit.Particle.BUBBLE_POP, bx, by, bz, 1, 0.05, 0.0, 0.05, 0.02);
                }
            }
            refreshDynamicElement((element, p) -> element.updateAnimation(p));
            return;
        }

        if (ThreadLocalRandom.current().nextDouble() < 0.05) {
            if (stage == StockpotStage.FINISHED) {
                double bx = super.blockEntity.pos.x() + 0.5 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.6;
                double by = super.blockEntity.pos.y() + 0.85 + ThreadLocalRandom.current().nextDouble() * 0.25;
                double bz = super.blockEntity.pos.z() + 0.5 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.6;
                bWorld.spawnParticle(org.bukkit.Particle.CLOUD, bx, by, bz, 1, 0.05, 0.0, 0.05, 0.01);
            } else {
                double bx = super.blockEntity.pos.x() + 0.5 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.3;
                double by = super.blockEntity.pos.y() + 0.85;
                double bz = super.blockEntity.pos.z() + 0.5 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.3;
                org.bukkit.Particle p = isLavaSoup() ? org.bukkit.Particle.LARGE_SMOKE : org.bukkit.Particle.CAMPFIRE_COSY_SMOKE;
                bWorld.spawnParticle(p, bx, by, bz, 0, 0.0, 1.0, 0.0, 0.04);
            }
        }

        if (stage == StockpotStage.PUT_INGREDIENT && !ingredients.isEmpty() && bWorld.getGameTime() % 5 == 0) {
            stage = StockpotStage.COOKING;
            currentTick = behavior.cookingTime;
            this.refreshRendering();
            return;
        }

        if (stage == StockpotStage.COOKING) {
            if (currentTick > 0) {
                currentTick--;
                return;
            }

            stage = StockpotStage.FINISHED;
            currentTick = -1;

            List<Key> ids = ingredientIds();
            java.util.Optional<FoodRecipeResult> res = FoodRecipeRegistry.instance()
                    .cookFlex(ApplianceType.STOCKPOT, ids, this.soupBaseId);

            int servings;
            if (res.isPresent()) {
                FoodRecipeResult fr = res.get();
                this.result = fr.item().copyWithCount(1);
                servings = Math.max(1, fr.count());
            } else {
                Item suspense = BukkitItemManager.instance().createWrappedItem(NO_RECIPE_RESULT, null);
                this.result = suspense != null ? suspense.copyWithCount(1) : Item.empty();
                servings = 1;
            }
            servings = Math.min(servings, MAX_INGREDIENTS);
            this.takeoutCount = servings;
            this.finishedMax = servings;
            this.lastCookedIngredients.clear();
            this.lastCookedIngredients.addAll(ids);
            this.ingredients.clear();
            this.refreshRendering();
            refreshDynamicElement(StockpotElement::onFinished);
        }
    }

    private List<Key> ingredientIds() {
        List<Key> ids = new ArrayList<>(ingredients.size());
        for (Item item : ingredients) ids.add(item.id());
        return ids;
    }

    private boolean hasHeatSource() {
        Object level = super.blockEntity.world.world().minecraftWorld();
        Object blockPos = LocationUtils.toBlockPos(super.blockEntity.pos);
        Object belowPos = LocationUtils.below(blockPos);
        return HeatSourceUtils.isHeatSource(level, belowPos);
    }

    public boolean hasLid() {
        ImmutableBlockState state = super.blockEntity.blockState;
        StockpotBehavior behavior = state.behavior().getFirst(StockpotBehavior.class);
        if (behavior == null || behavior.getHasLidProperty() == null) return false;
        return state.get(behavior.getHasLidProperty());
    }

    public boolean addLid(Item lidItem) {
        if (hasLid()) return false;
        this.lidItem = lidItem.copyWithCount(1);
        this.refreshRendering();
        super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
        BukkitCraftEngine.instance().scheduler().platform()
                .runLater(() -> refreshDynamicElement(StockpotElement::hide),
                        2L, super.blockEntity.world.world(),
                        super.blockEntity.pos.x, super.blockEntity.pos.z);
        return true;
    }

    public Item removeLid() {
        if (!hasLid()) return null;
        Item lid = this.lidItem.isEmpty()
                ? BukkitItemManager.instance().createWrappedItem(behavior.lidItem, null)
                : this.lidItem.copy();
        this.lidItem = Item.empty();
        this.refreshRendering();
        super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
        refreshDynamicElement((element, p) -> {
            element.hide(p);
            element.forceShow(p);
        });
        return lid;
    }

    public Item extractSoupBase() {
        if (stage != StockpotStage.PUT_INGREDIENT) return null;
        if (hasLid()) return null;
        if (!ingredients.isEmpty()) return null;

        Key soupBaseId = this.soupBaseId;
        this.soupBaseId = Key.of("minecraft:water");
        this.stage = StockpotStage.PUT_SOUP_BASE;
        this.seed = System.currentTimeMillis();
        this.renderTracker.soupBaseId = this.soupBaseId;
        updateBlockState();
        this.refreshRendering();
        super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);

        refreshDynamicElement((element, p) -> {
            element.hide(p);
            element.show(p);
        });

        // 汤底物品本身就是对应的桶，取不到时回退到水桶
        Item bucket = BukkitItemManager.instance().createWrappedItem(soupBaseId, null);
        return bucket != null ? bucket
                : BukkitItemManager.instance().createWrappedItem(Key.of("minecraft:water_bucket"), null);
    }

    private boolean isLavaSoup() {
        return LAVA_BUCKET.equals(this.soupBaseId);
    }

    public boolean addSoupBase(Key soupBaseId, boolean hasHeatSource) {
        if (stage != StockpotStage.PUT_SOUP_BASE) return false;
        if (hasLid()) return false;

        this.soupBaseId = soupBaseId;
        this.stage = StockpotStage.PUT_INGREDIENT;
        this.seed = System.currentTimeMillis();
        this.renderTracker.soupBaseId = this.soupBaseId;
        this.refreshRendering();
        super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);

        refreshDynamicElement((element, p) -> {
            element.hide(p);
            element.show(p);
        });

        return true;
    }

    public boolean addIngredient(Item item) {
        if (hasLid()) return false;
        if (stage != StockpotStage.PUT_INGREDIENT && stage != StockpotStage.COOKING) return false;
        if (ingredients.size() >= MAX_INGREDIENTS) return false;

        ingredients.add(item);
        if (stage == StockpotStage.COOKING) {
            stage = StockpotStage.PUT_INGREDIENT;
            currentTick = -1;
        }
        this.refreshRendering();
        super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);

        final int index = ingredients.size() - 1;
        refreshDynamicElement((element, p) -> element.showIndex(p, index));
        return true;
    }

    public Item extractIngredient(Player player) {
        if (hasLid()) return null;
        if (ingredients.isEmpty()) return null;

        Item extracted = ingredients.remove(ingredients.size() - 1);

        if (stage == StockpotStage.COOKING) {
            if (player != null) player.damage(2, Key.of("minecraft:generic"), null);
            stage = StockpotStage.PUT_INGREDIENT;
            currentTick = -1;
        }

        this.refreshRendering();
        super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
        refreshDynamicElement((element, p) -> element.hideIndex(p, ingredients.size()));
        return extracted;
    }

    public Item takeOutResult() {
        if (stage != StockpotStage.FINISHED) return null;
        if (hasLid()) return null;
        if (takeoutCount <= 0 || result.isEmpty()) return null;

        Item toReturn = result.copyWithCount(1);
        takeoutCount--;

        if (takeoutCount <= 0) {
            resetStockpot();
        } else {
            this.refreshRendering();
            refreshDynamicElement(StockpotElement::refreshLiquidLevel);
        }

        return toReturn;
    }

    private void resetStockpot() {
        this.ingredients.clear();
        this.result = Item.empty();
        this.stage = StockpotStage.PUT_SOUP_BASE;
        this.currentTick = -1;
        this.takeoutCount = 0;
        this.soupBaseId = Key.of("minecraft:water");
        this.seed = System.currentTimeMillis();
        this.renderTracker.reset();
        updateBlockState();
        this.refreshRendering();
        refreshDynamicElement(StockpotElement::clearAll);
    }

    private void updateBlockState() {
        StockpotBehavior behavior = super.blockEntity.blockState.behavior().getFirst(StockpotBehavior.class);
        if (behavior == null) return;

        ImmutableBlockState newState = super.blockEntity.blockState
                .with(behavior.getHasLidProperty(), hasLid());

        BlockStates.sync(super.blockEntity, newState);
    }

    private void refreshRendering() {
        this.element.refreshPackets();
        renderTracker.stage = stage;
        renderTracker.ingredientCount = ingredients.size();
        renderTracker.soupBaseId = soupBaseId;
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
        if (!ingredients.isEmpty()) {
            for (Item item : ingredients) {
                DropUtils.dropAtCenter(super.blockEntity, item);
            }
            ingredients.clear();
        }
        if (!lidItem.isEmpty()) {
            DropUtils.dropAtCenter(super.blockEntity, lidItem);
        }
        super.onRemove();
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        tag.putInt("data_version", VersionHelper.WORLD_VERSION);
        tag.putInt("status", this.stage.ordinal());
        tag.putInt("current_tick", this.currentTick);
        tag.putInt("takeout_count", this.takeoutCount);
        tag.putInt("finished_max", this.finishedMax);
        if (!this.lastCookedIngredients.isEmpty()) {
            tag.putString("last_cooked", this.lastCookedIngredients.stream()
                    .map(Key::toString).collect(java.util.stream.Collectors.joining(",")));
        }
        tag.putLong("seed", this.seed);
        tag.putString("soup_base_id", this.soupBaseId.toString());

        tag.put("ingredients", BlockEntityNbt.saveItems(ingredients));
        if (!result.isEmpty()) {
            tag.put("result", ItemStackUtils.saveMinecraftItemStackAsTag(result.minecraftItem()));
        }

        if (!lidItem.isEmpty()) {
            tag.put("lid_item", ItemStackUtils.saveMinecraftItemStackAsTag(lidItem.minecraftItem()));
        }
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        int dataVersion = tag.getInt("data_version", VersionHelper.WORLD_VERSION);

        this.stage = StockpotStage.fromOrdinal(tag.getInt("status", 0));
        this.currentTick = tag.getInt("current_tick", -1);
        this.takeoutCount = tag.getInt("takeout_count", 0);
        this.finishedMax = tag.getInt("finished_max", this.takeoutCount);
        this.lastCookedIngredients.clear();
        String lc = tag.getString("last_cooked", "");
        if (!lc.isEmpty()) for (String s : lc.split(",")) if (!s.isEmpty()) this.lastCookedIngredients.add(Key.of(s));
        this.seed = tag.getLong("seed", System.currentTimeMillis());
        this.soupBaseId = Key.of(tag.getString("soup_base_id", "minecraft:water"));

        BlockEntityNbt.loadItems(tag, "ingredients", dataVersion, this.ingredients);

        this.result = Item.empty();
        if (tag.containsKey("result")) {
            Item r = ItemStackUtils.wrap(ItemStackUtils.parseMinecraftItem(tag.getCompound("result"), dataVersion));
            if (r != null) this.result = r;
        }

        if (tag.containsKey("lid_item")) {
            this.lidItem = ItemStackUtils.wrap(ItemStackUtils.parseMinecraftItem(tag.getCompound("lid_item"), dataVersion));
        }

        this.refreshRendering();
    }

    public StockpotStage stage() {
        return stage;
    }

    public int getCurrentTick() {
        return currentTick;
    }

    public int getTakeoutCount() {
        return takeoutCount;
    }

    public int getFinishedMax() {
        return finishedMax;
    }

    public List<Key> getLastCookedIngredients() {
        return lastCookedIngredientsView;
    }

    public List<Item> getIngredients() {
        return ingredientsView;
    }

    public Key getSoupBaseId() {
        return soupBaseId;
    }

    public Item getLidItem() {
        return lidItem;
    }

    public long getSeed() {
        return seed;
    }

    public RenderTracker renderTracker() {
        return renderTracker;
    }
}