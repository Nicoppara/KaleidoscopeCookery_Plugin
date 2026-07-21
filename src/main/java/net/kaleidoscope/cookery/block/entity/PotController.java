package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.PotBehavior;

import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.block.entity.tick.BlockEntityTicker;
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
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.util.HeatSourceUtils;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.FoodCategoryRegistry;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.FoodRecipeResult;
import net.kaleidoscope.cookery.util.Localization;
import net.kaleidoscope.cookery.util.EventUtils;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.api.event.PotStirFryEvent;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class PotController extends BlockEntityController {
    private static final int MAX_INGREDIENTS = 8;
    private static final String DATA_KEY = "kaleidoscopecookery:cooking_pot";
    private static final String K_DATA_VERSION = "data_version";
    private static final String K_SEED = "seed";
    private static final String K_HAS_OIL = "has_oil";
    private static final String K_STIR_FRY_COUNT = "stir_fry_count";
    private static final String K_COOKING_STATUS = "cooking_status";
    private static final String K_CURRENT_TICK = "current_tick";
    private static final String K_INGREDIENTS = "ingredients";
    private static final String K_RESULTS = "results";
    private static final String K_COOKED_ING = "cooked_ing";
    private static final String K_COOKED_DISH = "cooked_dish";
    private static final Key DAMAGE_GENERIC = Key.of("minecraft:generic");
    private static final Key SOUND_FIRE_AMBIENT = Key.of("minecraft:block.fire.ambient");

    private PotStage stage = PotStage.IDLE;
    private int currentTick = 0;
    // 一锅只出一种成品 count 为可盛出的份数
    private Item result = Item.empty();
    private final List<Item> ingredients = new ArrayList<>();
    private final List<Item> ingredientsView = Collections.unmodifiableList(ingredients);
    private final PotElement element;
    private boolean animating = false;
    private boolean hasOil = false;
    private int stirFryCount = 0;
    private long seed = System.currentTimeMillis();
    private int lastSentBrightness = -1;
    private boolean heated = false;
    private int heatCheckTick = 0;
    private int cookedIngredientCount = 0;
    private int cookedDishCount = 0;
    private BiConsumer<Player, PotController> onStirFryCallback;

    private final PotBehavior behavior;

    public PotController(BlockEntity entity, PotBehavior behavior) {
        super(entity);
        this.behavior = behavior;
        this.element = new PotElement(this, new WorldPosition(
                null, (float) entity.pos.x() + 0.5f, (float) entity.pos.y() + 0.1f, (float) entity.pos.z() + 0.5f
        ));
    }

    @Override
    public <C extends BlockEntityController> BlockEntityTicker<C> createBlockEntityTicker(CEWorld world, ImmutableBlockState blockState) {
        return createTickerHelper((w, pos, state, controller) -> this.tick());
    }

    public int animChunkRadius() {
        return behavior.animChunkRadius;
    }

    public void refreshDynamicElement(BiConsumer<PotElement, Player> consumer) {
        TrackedPlayers.forEach(blockEntity, player -> consumer.accept(element, player));
    }

    public void tick() {
        if (stage == PotStage.IDLE) return;

        if (stage == PotStage.DONE || stage == PotStage.BURNT) {
            if (heatCheckTick++ % 20 == 0) heated = hasHeatBelow();
            if (!heated) return;
        }
        if (currentTick <= 0) return;
        currentTick--;

        if (currentTick % 20 == 0) {
            playCookingSound();
            if (stage == PotStage.BURNT) {
                int newBrightness = PotElement.burntBrightness(currentTick, behavior.burntToCharcoalTime);
                if (newBrightness != lastSentBrightness) {
                    lastSentBrightness = newBrightness;
                    refreshDynamicElement((e, p) -> e.updateBrightness(p, newBrightness));
                }
            }
        }

        if (currentTick == 0) {
            if (stage == PotStage.DONE) {
                burnDish();
            } else if (stage == PotStage.BURNT) {
                dropCharcoal();
                resetPot();
            }
        }
    }

    // 盛出窗口过点烧焦成黑暗料理
    private void burnDish() {
        int prevCount = result.isEmpty() ? 0 : result.count();
        Item dark = InventoryUtils.createOrEmpty(ItemKeys.DARK_CUISINE);
        result = ItemUtils.isEmpty(dark) ? Item.empty() : dark.count(Math.max(1, prevCount));
        cookedIngredientCount = ingredients.size();
        cookedDishCount = Math.max(1, prevCount);

        stage = PotStage.BURNT;
        currentTick = behavior.burntToCharcoalTime;
        lastSentBrightness = -1;
        updateBlockState();
        blockEntity.updateConstantRenderers();
        element.refreshPackets();
        blockEntity.world.blockEntityChanged(blockEntity.pos);
    }

    public void setOnStirFryCallback(BiConsumer<Player, PotController> callback) {
        this.onStirFryCallback = callback;
    }

    public boolean stirFry(boolean hasHeatSource, Player player) {
        if (stage == PotStage.DONE || stage == PotStage.BURNT || animating || ingredients.isEmpty()) return false;

        // 翻炒前触发事件 本次结果次数取决于是否真正受热
        if (player != null) {
            int resultCount = (hasHeatSource && hasOil) ? stirFryCount + 1 : stirFryCount;
            Location stirLoc = new Location((World) blockEntity.world.world().platformWorld(), blockEntity.pos.x(), blockEntity.pos.y(), blockEntity.pos.z());
            PotStirFryEvent event = new PotStirFryEvent((org.bukkit.entity.Player) player.platformPlayer(), stirLoc, resultCount);
            if (EventUtils.fireAndCheckCancel(event)) return false;
        }

        this.animating = true;
        this.seed = System.currentTimeMillis();

        if (onStirFryCallback != null && player != null) {
            onStirFryCallback.accept(player, this);
        }
        if (hasHeatSource && hasOil) {
            boolean firstStir = stirFryCount == 0;
            stirFryCount++;
            if (stage == PotStage.IDLE) stage = PotStage.COOKING;
            if (firstStir && player != null) player.sendActionBar(Localization.component(behavior.msgStartCooking));
        }

        element.refreshPackets();
        element.playStirFryAnimation(() -> {
            animating = false;
            if (stirFryCount >= behavior.stirFryCount) completeCooking(player);
        });
        return true;
    }

    private void completeCooking(Player triggerPlayer) {
        FoodRecipeResult fr = FoodRecipeRegistry.instance()
                .cookFlex(ApplianceType.POT, ingredients.stream().map(Item::id).toList())
                .orElse(null);

        this.stirFryCount = 0;
        this.hasOil = false;

        if (fr != null) {
            result = fr.item().count(fr.count());
            stage = PotStage.DONE;
            currentTick = behavior.cookDoneTime;
            if (triggerPlayer != null) triggerPlayer.sendActionBar(Localization.component(behavior.msgDishReady));
        } else {
            Item suspense = InventoryUtils.createOrEmpty(ItemKeys.SUSPICIOUS_STIR_FRY);
            result = ItemUtils.isEmpty(suspense) ? Item.empty() : suspense.count(1);
            stage = PotStage.BURNT;
            currentTick = behavior.burntToCharcoalTime;
            lastSentBrightness = -1;
            if (triggerPlayer != null) triggerPlayer.sendActionBar(Localization.component(behavior.msgAllBurnt));
        }
        cookedIngredientCount = ingredients.size();
        cookedDishCount = result.isEmpty() ? 0 : result.count();
        heated = hasHeatBelow();

        updateBlockState();
        blockEntity.updateConstantRenderers();
        element.refreshPackets();
        blockEntity.world.blockEntityChanged(blockEntity.pos);
    }

    // 返回是否真的收下 一键投料据此决定扣不扣背包 拒收还扣就是凭空销毁材料
    public boolean addIngredient(Item item, boolean hasHeatSource, Player player) {
        if (!FoodCategoryRegistry.instance().isRegistered(ApplianceType.POT, item.id())) {
            if (player != null) player.sendActionBar(Localization.component(behavior.msgNotIngredient));
            return false;
        }
        if (stage == PotStage.DONE || stage == PotStage.BURNT || animating || ingredients.size() >= MAX_INGREDIENTS) {
            return false;
        }
        stirFryCount = 0;
        int index = ingredients.size();
        ingredients.add(item);
        element.refreshSlotPacket(index);
        refreshDynamicElement((el, p) -> el.showIndex(p, index));
        blockEntity.world.blockEntityChanged(blockEntity.pos);
        return true;
    }

    public Item extractItem(Player player) {
        if (stage == PotStage.DONE || stage == PotStage.BURNT || animating || ingredients.isEmpty()) return null;
        if (this.hasOil && player != null) {
            player.damage(2, DAMAGE_GENERIC, null);
        }
        stirFryCount = 0;
        int index = ingredients.size() - 1;
        Item extracted = ingredients.remove(index);
        element.refreshSlotPacket(index);
        refreshDynamicElement((el, p) -> el.hideIndex(p, index));
        blockEntity.world.blockEntityChanged(blockEntity.pos);
        return extracted;
    }

    public void resetPot() {
        ingredients.clear();
        hasOil = false;
        stirFryCount = 0;
        result = Item.empty();
        stage = PotStage.IDLE;
        currentTick = 0;
        lastSentBrightness = -1;
        heated = false;
        cookedIngredientCount = 0;
        cookedDishCount = 0;
        updateBlockState();
        blockEntity.updateConstantRenderers();
        element.refreshPackets();
        refreshDynamicElement(PotElement::hideAll);
        blockEntity.world.blockEntityChanged(blockEntity.pos);
    }

    // 按剩余成品比例从顶部扣减食材
    // 盛出后按剩余份数等比例回收食材 无论食材有没有跟着减都必须落盘
    // 只在 changed 时标脏会漏掉部分取出的情况 区块卸载后成品数回滚可无限盛
    private void syncIngredientsToResult() {
        if (result.isEmpty()) {
            resetPot();
            return;
        }
        int remainingDishes = result.count();
        int target = cookedDishCount <= 0 ? ingredients.size()
                : Math.round((float) cookedIngredientCount * remainingDishes / cookedDishCount);

        boolean changed = false;
        while (ingredients.size() > target && !ingredients.isEmpty()) {
            final int idx = ingredients.size() - 1;
            ingredients.remove(idx);
            refreshDynamicElement((el, p) -> el.hideIndex(p, idx));
            changed = true;
        }
        if (changed) {
            element.refreshPackets();
        }
        blockEntity.world.blockEntityChanged(blockEntity.pos);
    }

    public void setHasOil(boolean hasOil) {
        if (this.hasOil != hasOil) {
            this.hasOil = hasOil;
            updateBlockState();
        }
    }

    private boolean hasHeatBelow() {
        Object level = blockEntity.world.world().minecraftWorld();
        Object belowPos = LocationUtils.below(LocationUtils.toBlockPos(blockEntity.pos));
        return HeatSourceUtils.isHeatSource(level, belowPos);
    }

    private void updateBlockState() {
        if (behavior.getHasOilProperty() == null) return;
        ImmutableBlockState newState = blockEntity.blockState.with(behavior.getHasOilProperty(), hasOil);
        if (behavior.getHasBaseProperty() != null) {
            newState = newState.with(behavior.getHasBaseProperty(), blockEntity.blockState.get(behavior.getHasBaseProperty()));
        }
        if (behavior.getFacingProperty() != null) {
            newState = newState.with(behavior.getFacingProperty(), blockEntity.blockState.get(behavior.getFacingProperty()));
        }
        BlockStates.sync(blockEntity, newState);
    }

    private void dropCharcoal() {
        DropUtils.dropAtCenter(blockEntity, InventoryUtils.createOrEmpty(ItemKeys.CHARCOAL));
    }

    private void playCookingSound() {
        float volume = 0.5f + ThreadLocalRandom.current().nextFloat() * 0.5f;
        float pitch = 0.8f + ThreadLocalRandom.current().nextFloat() * 0.5f;
        blockEntity.world.world().playSound(Vec3d.atCenterOf(blockEntity.pos), SOUND_FIRE_AMBIENT, volume, pitch, SoundSource.BLOCK);
    }

    public PotStage stage() {
        return stage;
    }

    public boolean hasOil() {
        return hasOil;
    }

    // 可盛出的份数
    public int resultCount() {
        return result.isEmpty() ? 0 : result.count();
    }

    // 只看不扣 供调用方在发可取消事件前预览
    public Item peekResult() {
        return result.isEmpty() ? Item.empty() : result.copyWithCount(1);
    }

    // 盛出后统一扣份数并落盘 别把 result 暴露出去让调用方自己改
    public void consumeResult(int amount) {
        if (amount <= 0 || result.isEmpty()) {
            return;
        }
        result.shrink(Math.min(amount, result.count()));
        syncIngredientsToResult();
    }

    public List<Item> ingredients() {
        return ingredientsView;
    }

    public long seed() {
        return seed;
    }

    public int currentTick() {
        return currentTick;
    }

    public int burntToCharcoalTime() {
        return behavior.burntToCharcoalTime;
    }

    @Override
    public boolean hasElement() {
        return true;
    }

    @Override
    public void gatherElements(Consumer<BlockEntityElement> consumer) {
        consumer.accept(element);
    }

    @Override
    public void onRemove() {
        if (!ingredients.isEmpty()) {
            ingredients.forEach(item -> DropUtils.dropAtCenter(blockEntity, item));
            ingredients.clear();
        }
        super.onRemove();
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        CompoundTag data = new CompoundTag();
        data.putInt(K_DATA_VERSION, VersionHelper.WORLD_VERSION);
        data.putLong(K_SEED, seed);
        data.putBoolean(K_HAS_OIL, hasOil);
        data.putInt(K_STIR_FRY_COUNT, stirFryCount);
        data.putInt(K_COOKING_STATUS, stage.ordinal());
        data.putInt(K_CURRENT_TICK, currentTick);
        data.put(K_INGREDIENTS, BlockEntityNbt.saveItems(ingredients));
        // TODO 一锅多成品的设计已取消 这里仍存成列表只为兼容旧存档 待确认线上不再有旧格式存档后 改成 data.put(K_RESULT, 单个 tag) 并同步简化 loadCustomData
        data.put(K_RESULTS, BlockEntityNbt.saveItems(result.isEmpty() ? List.of() : List.of(result)));
        data.putInt(K_COOKED_ING, cookedIngredientCount);
        data.putInt(K_COOKED_DISH, cookedDishCount);
        tag.put(DATA_KEY, data);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        CompoundTag data = tag.getCompound(DATA_KEY);
        if (data == null) return;

        int dataVersion = data.getInt(K_DATA_VERSION, Config.itemDataFixerUpperFallbackVersion());
        BlockEntityNbt.loadItems(data, K_INGREDIENTS, dataVersion, ingredients);
        List<Item> loadedResults = new ArrayList<>(1);
        BlockEntityNbt.loadItems(data, K_RESULTS, dataVersion, loadedResults);
        result = loadedResults.isEmpty() ? Item.empty() : loadedResults.get(0);

        seed = data.getLong(K_SEED, System.currentTimeMillis());
        hasOil = data.getBoolean(K_HAS_OIL, false);
        stirFryCount = data.getInt(K_STIR_FRY_COUNT, 0);
        stage = PotStage.fromOrdinal(data.getInt(K_COOKING_STATUS, 0));
        currentTick = data.getInt(K_CURRENT_TICK, 0);
        cookedIngredientCount = data.getInt(K_COOKED_ING, ingredients.size());
        cookedDishCount = data.getInt(K_COOKED_DISH, 0);
        // 读档时下方区块可能还没加载 取热源会抛异常 按无热源处理 tick 起来后会自行纠正
        try {
            heated = (stage == PotStage.DONE || stage == PotStage.BURNT) && hasHeatBelow();
        } catch (Exception ignored) {
            heated = false;
        }
        element.refreshPackets();
    }
}
