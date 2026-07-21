package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.StockpotBehavior;

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
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.block.entity.render.Particles;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.FoodRecipeResult;
import net.kaleidoscope.cookery.util.HeatSourceUtils;
import net.kaleidoscope.cookery.item.ItemKeys;
import org.bukkit.Particle;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class StockpotController extends BlockEntityController {
    public static final int MAX_INGREDIENTS = 8;
    private static final int ANIM_INTERVAL = 4;
    private static final Key[] STOCKPOT_SOUNDS = {
            Key.of("kaleidoscopecookery:stockpot_0"),
            Key.of("kaleidoscopecookery:stockpot_1"),
            Key.of("kaleidoscopecookery:stockpot_2"),
            Key.of("kaleidoscopecookery:stockpot_3"),
            Key.of("kaleidoscopecookery:stockpot_4"),
            Key.of("kaleidoscopecookery:stockpot_5"),
            Key.of("kaleidoscopecookery:stockpot_6"),
    };

    private static final Key DAMAGE_GENERIC = Key.of("minecraft:generic");
    private static final String DATA_KEY = "kaleidoscopecookery:stockpot";
    private static final String K_DATA_VERSION = "data_version";
    private static final String K_STATUS = "status";
    private static final String K_CURRENT_TICK = "current_tick";
    private static final String K_TAKEOUT_COUNT = "takeout_count";
    private static final String K_FINISHED_MAX = "finished_max";
    private static final String K_LAST_COOKED = "last_cooked";
    private static final String K_SEED = "seed";
    private static final String K_SOUP_BASE_ID = "soup_base_id";
    private static final String K_INGREDIENTS = "ingredients";
    private static final String K_RESULT = "result";
    private static final String K_LID_ITEM = "lid_item";

    private StockpotStage stage = StockpotStage.PUT_SOUP_BASE;
    private int currentTick = -1;
    private int takeoutCount = 0;
    private int finishedMax = 0;
    private boolean heatedCache = false;
    private int heatCheckTick = 0;
    private final List<Item> ingredients = new ArrayList<>();
    private Item result = Item.empty();
    private final List<Key> lastCookedIngredients = new ArrayList<>();
    private final List<Item> ingredientsView = Collections.unmodifiableList(ingredients);
    private final List<Key> lastCookedIngredientsView = Collections.unmodifiableList(lastCookedIngredients);
    private final StockpotElement element;
    private final StockpotBehavior behavior;
    private Key soupBaseId = ItemKeys.WATER;
    private Item lidItem = Item.empty();
    private long seed = System.currentTimeMillis();

    private final RenderTracker renderTracker = new RenderTracker();

    // 上一次渲染时的状态快照 只由 controller 写 外部只读
    // 字段公开可变会让渲染方顺手改掉快照 下次差异比对就失真 该刷新的槽不刷新
    public static final class RenderTracker {
        private StockpotStage stage = StockpotStage.PUT_SOUP_BASE;
        private int ingredientCount = 0;
        private Key soupBaseId = ItemKeys.WATER;

        public StockpotStage stage() {
            return stage;
        }

        public int ingredientCount() {
            return ingredientCount;
        }

        public Key soupBaseId() {
            return soupBaseId;
        }

        void snapshot(StockpotStage stage, int ingredientCount, Key soupBaseId) {
            this.stage = stage;
            this.ingredientCount = ingredientCount;
            this.soupBaseId = soupBaseId;
        }

        void reset() {
            snapshot(StockpotStage.PUT_SOUP_BASE, 0, ItemKeys.WATER);
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

    // 连续动画只发视距内玩家 远处不渲染插值
    public void refreshAnimation(int interpDuration) {
        Object bundle = this.element.buildAnimationBundle(interpDuration);
        if (bundle == null) {
            return;
        }
        for (Player player : TrackedPlayers.snapshotInRange(super.blockEntity, behavior.animChunkRadius)) {
            player.sendPacket(bundle, false);
        }
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

        World bWorld = (World) super.blockEntity.world.world().platformWorld();
        long gameTime = bWorld.getGameTime();
        if (gameTime % 15 == 0) {
            float volume = hasLid ? 0.3f : 0.6f;
            float pitch = 0.9f + (float) ThreadLocalRandom.current().nextDouble() * 0.2f;
            super.blockEntity.world.world().playSound(
                    Vec3d.atCenterOf(super.blockEntity.pos),
                    STOCKPOT_SOUNDS[ThreadLocalRandom.current().nextInt(STOCKPOT_SOUNDS.length)],
                    volume, pitch, SoundSource.BLOCK);
        }

        if (!hasLid) {
            if (gameTime % behavior.particleInterval == 0) {
                double bx = super.blockEntity.pos.x() + 0.3 + ThreadLocalRandom.current().nextDouble() * 0.4;
                double by = super.blockEntity.pos.y() + 0.4;
                double bz = super.blockEntity.pos.z() + 0.3 + ThreadLocalRandom.current().nextDouble() * 0.4;
                int pc = behavior.particleCount;
                if (isLavaSoup()) {
                    Particles.emit(super.blockEntity.world, Particle.LAVA, bx, by, bz, pc, 0.05, 0.0, 0.05, 0, null);
                } else {
                    Particles.emit(super.blockEntity.world, Particle.SPLASH, bx, by, bz, pc, 0.05, 0.0, 0.05, 0.1, null);
                    Particles.emit(super.blockEntity.world, Particle.BUBBLE_POP, bx, by, bz, pc, 0.05, 0.0, 0.05, 0.02, null);
                }
            }
            if (gameTime % ANIM_INTERVAL == 0) {
                refreshAnimation(ANIM_INTERVAL);
            }
            return;
        }

        if (gameTime % behavior.particleInterval == 0) {
            int pc = behavior.particleCount;
            if (stage == StockpotStage.FINISHED) {
                double bx = super.blockEntity.pos.x() + 0.5 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.6;
                double by = super.blockEntity.pos.y() + 0.85 + ThreadLocalRandom.current().nextDouble() * 0.25;
                double bz = super.blockEntity.pos.z() + 0.5 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.6;
                Particles.emit(super.blockEntity.world, Particle.CLOUD, bx, by, bz, pc, 0.05, 0.0, 0.05, 0.01, null);
            } else {
                double bx = super.blockEntity.pos.x() + 0.5 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.3;
                double by = super.blockEntity.pos.y() + 0.85;
                double bz = super.blockEntity.pos.z() + 0.5 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.3;
                Particle p = isLavaSoup() ? Particle.LARGE_SMOKE : Particle.CAMPFIRE_COSY_SMOKE;
                Particles.emit(super.blockEntity.world, p, bx, by, bz, pc, 0.05, 0.1, 0.05, 0.02, null);
            }
        }

        if (stage == StockpotStage.PUT_INGREDIENT && !ingredients.isEmpty() && gameTime % 5 == 0) {
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
            Optional<FoodRecipeResult> res = FoodRecipeRegistry.instance()
                    .cookFlex(ApplianceType.STOCKPOT, ids, this.soupBaseId);

            int servings;
            if (res.isPresent()) {
                FoodRecipeResult fr = res.get();
                this.result = fr.item().copyWithCount(1);
                servings = Math.max(1, fr.count());
            } else {
                this.result = InventoryUtils.createOrEmpty(ItemKeys.SUSPICIOUS_STIR_FRY).copyWithCount(1);
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
            super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
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
                        super.blockEntity.pos.x >> 4, super.blockEntity.pos.z >> 4);
        return true;
    }

    public Item removeLid() {
        if (!hasLid()) return null;
        Item lid = this.lidItem.isEmpty()
                ? InventoryUtils.createOrEmpty(behavior.lidItem)
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
        this.soupBaseId = ItemKeys.WATER;
        this.stage = StockpotStage.PUT_SOUP_BASE;
        this.seed = System.currentTimeMillis();
        this.renderTracker.snapshot(this.stage, this.ingredients.size(), this.soupBaseId);
        updateBlockState();
        this.refreshRendering();
        super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);

        refreshDynamicElement((element, p) -> {
            element.hide(p);
            element.show(p);
        });

        // 汤底物品本身就是对应的桶 取不到时回退到水桶
        // createOrEmpty 无效 key 返回空物品不返回 null 所以只能用 isEmpty 判
        Item bucket = InventoryUtils.createOrEmpty(soupBaseId);
        return ItemUtils.isEmpty(bucket) ? InventoryUtils.createOrEmpty(ItemKeys.WATER_BUCKET) : bucket;
    }

    private boolean isLavaSoup() {
        return ItemKeys.LAVA_BUCKET.equals(this.soupBaseId);
    }

    public boolean addSoupBase(Key soupBaseId, boolean hasHeatSource) {
        if (stage != StockpotStage.PUT_SOUP_BASE) return false;
        if (hasLid()) return false;

        this.soupBaseId = soupBaseId;
        this.stage = StockpotStage.PUT_INGREDIENT;
        this.seed = System.currentTimeMillis();
        this.renderTracker.snapshot(this.stage, this.ingredients.size(), this.soupBaseId);
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
        if (hasLid()) return Item.empty();
        if (ingredients.isEmpty()) return Item.empty();

        Item extracted = ingredients.remove(ingredients.size() - 1);

        if (stage == StockpotStage.COOKING) {
            if (player != null) player.damage(2, DAMAGE_GENERIC, null);
            stage = StockpotStage.PUT_INGREDIENT;
            currentTick = -1;
        }

        this.refreshRendering();
        super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
        refreshDynamicElement((element, p) -> element.hideIndex(p, ingredients.size()));
        return extracted;
    }

    // 只看不扣 供调用方在发可取消事件前预览 事件取消时不能已经扣掉份数
    public Item peekResult() {
        if (stage != StockpotStage.FINISHED || hasLid() || takeoutCount <= 0 || result.isEmpty()) {
            return Item.empty();
        }
        return result.copyWithCount(1);
    }

    public Item takeOutResult() {
        if (stage != StockpotStage.FINISHED) return Item.empty();
        if (hasLid()) return Item.empty();
        if (takeoutCount <= 0 || result.isEmpty()) return Item.empty();

        Item toReturn = result.copyWithCount(1);
        takeoutCount--;

        if (takeoutCount <= 0) {
            resetStockpot();
        } else {
            this.refreshRendering();
            refreshDynamicElement(StockpotElement::refreshLiquidLevel);
            // 不标脏则区块卸载后份数回滚 玩家舀完走开再回来能无限舀
            super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
        }

        return toReturn;
    }

    private void resetStockpot() {
        this.ingredients.clear();
        this.result = Item.empty();
        this.stage = StockpotStage.PUT_SOUP_BASE;
        this.currentTick = -1;
        this.takeoutCount = 0;
        this.soupBaseId = ItemKeys.WATER;
        this.seed = System.currentTimeMillis();
        this.renderTracker.reset();
        updateBlockState();
        this.refreshRendering();
        refreshDynamicElement(StockpotElement::clearAll);
        super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
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
        renderTracker.snapshot(stage, ingredients.size(), soupBaseId);
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
        CompoundTag data = new CompoundTag();
        data.putInt(K_DATA_VERSION, VersionHelper.WORLD_VERSION);
        data.putInt(K_STATUS, this.stage.ordinal());
        data.putInt(K_CURRENT_TICK, this.currentTick);
        data.putInt(K_TAKEOUT_COUNT, this.takeoutCount);
        data.putInt(K_FINISHED_MAX, this.finishedMax);
        if (!this.lastCookedIngredients.isEmpty()) {
            data.putString(K_LAST_COOKED, this.lastCookedIngredients.stream()
                    .map(Key::asString).collect(Collectors.joining(",")));
        }
        data.putLong(K_SEED, this.seed);
        data.putString(K_SOUP_BASE_ID, this.soupBaseId.asString());

        data.put(K_INGREDIENTS, BlockEntityNbt.saveItems(ingredients));
        if (!result.isEmpty()) {
            data.put(K_RESULT, ItemStackUtils.saveMinecraftItemStackAsTag(result.minecraftItem()));
        }

        if (!lidItem.isEmpty()) {
            data.put(K_LID_ITEM, ItemStackUtils.saveMinecraftItemStackAsTag(lidItem.minecraftItem()));
        }
        tag.put(DATA_KEY, data);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        CompoundTag data = tag.getCompound(DATA_KEY);
        if (data == null) return;

        int dataVersion = data.getInt(K_DATA_VERSION, Config.itemDataFixerUpperFallbackVersion());

        this.stage = StockpotStage.fromOrdinal(data.getInt(K_STATUS, 0));
        this.currentTick = data.getInt(K_CURRENT_TICK, -1);
        this.takeoutCount = data.getInt(K_TAKEOUT_COUNT, 0);
        this.finishedMax = data.getInt(K_FINISHED_MAX, this.takeoutCount);
        this.lastCookedIngredients.clear();
        String lc = data.getString(K_LAST_COOKED, "");
        if (!lc.isEmpty()) for (String s : lc.split(",")) if (!s.isEmpty()) this.lastCookedIngredients.add(Key.of(s));
        this.seed = data.getLong(K_SEED, System.currentTimeMillis());
        this.soupBaseId = Key.of(data.getString(K_SOUP_BASE_ID, ItemKeys.WATER.asString()));

        BlockEntityNbt.loadItems(data, K_INGREDIENTS, dataVersion, this.ingredients);

        this.result = Item.empty();
        if (data.containsKey(K_RESULT)) {
            Item r = ItemStackUtils.wrap(ItemStackUtils.parseMinecraftItem(data.getCompound(K_RESULT), dataVersion));
            if (r != null) this.result = r;
        }

        // 存档损坏时 parseMinecraftItem 会返回 null 这里必须收口成空物品 后续到处调 isEmpty
        this.lidItem = Item.empty();
        if (data.containsKey(K_LID_ITEM)) {
            Item lid = ItemStackUtils.wrap(ItemStackUtils.parseMinecraftItem(data.getCompound(K_LID_ITEM), dataVersion));
            if (lid != null) this.lidItem = lid;
        }

        this.refreshRendering();
    }

    public StockpotStage stage() {
        return stage;
    }

    public int currentTick() {
        return currentTick;
    }

    public int takeoutCount() {
        return takeoutCount;
    }

    public int finishedMax() {
        return finishedMax;
    }

    public List<Key> lastCookedIngredients() {
        return lastCookedIngredientsView;
    }

    public List<Item> ingredients() {
        return ingredientsView;
    }

    public Key soupBaseId() {
        return soupBaseId;
    }

    public Item lidItem() {
        return lidItem;
    }

    public long seed() {
        return seed;
    }

    public RenderTracker renderTracker() {
        return renderTracker;
    }
}
