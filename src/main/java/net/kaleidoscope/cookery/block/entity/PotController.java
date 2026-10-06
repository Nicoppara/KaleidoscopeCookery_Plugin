package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.api.KaleidoscopeAdvancements;

import net.kaleidoscope.cookery.util.MessageKeys;
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
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.util.HeatSourceUtils;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.ApplianceFoodRegistry;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.FoodRecipeResult;
import net.kaleidoscope.cookery.recipe.CookingPlan;
import net.kaleidoscope.cookery.util.Localization;
import net.kaleidoscope.cookery.util.EventUtils;
import net.kaleidoscope.cookery.util.FoliaUtil;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.item.ItemNames;
import net.kaleidoscope.cookery.api.PotCookConditions;
import net.kaleidoscope.cookery.api.event.PotStirFryEvent;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class PotController extends BlockEntityController {
    static final int MAX_INGREDIENTS = 9;
    private static final String DATA_KEY = "kaleidoscopecookery:cooking_pot";
    private static final String K_DATA_VERSION = "data_version";
    private static final String K_SEED = "seed";
    private static final String K_HAS_OIL = "has_oil";
    private static final String K_STIR_FRY_COUNT = "stir_fry_count";
    private static final String K_COOKING_STATUS = "cooking_status";
    private static final String K_CURRENT_TICK = "current_tick";
    private static final String K_INGREDIENTS = "ingredients";
    private static final String K_RESULTS = "results";
    private static final String K_CARRIER = "carrier";
    private static final String K_COOKED_ING = "cooked_ing";
    private static final String K_COOKED_DISH = "cooked_dish";
    private static final String K_PROCESSING_PLAN = "processing_plan";
    private static final String K_STIR_ANIMATION_TICKS = "stir_animation_ticks";
    private static final String K_PROCESSING_BLOCKED = "processing_blocked";
    private static final int STIR_ANIMATION_TICKS = 24;
    private static final Key DAMAGE_GENERIC = Key.of("minecraft:generic");
    private static final Key SOUND_FIRE_AMBIENT = Key.of("minecraft:block.fire.ambient");

    private PotStage stage = PotStage.IDLE;
    private int currentTick = 0;
    // 一锅只出一种成品 count 为可盛出的份数
    private Item result = Item.empty();
    // 这锅成品的盛装容器 null 表示空手就能取 出锅提示与盛出判定都看它
    private Key resultCarrier = null;
    private final List<Item> ingredients = new ArrayList<>();
    private final List<Item> ingredientsView = Collections.unmodifiableList(ingredients);
    private final PotElement element;
    private boolean animating = false;
    private int stirAnimationTicks;
    private Player stirringPlayer;
    private CookingPlan cookingPlan;
    private boolean processingBlocked;
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

        // 翻炒逻辑随已加载厨具 tick，展示任务/玩家追踪不能取消出锅判定。
        if (stage == PotStage.COOKING) {
            if (processingBlocked) return;
            if (stirAnimationTicks > 0 && --stirAnimationTicks > 0) return;
            if (animating) {
                animating = false;
                blockEntity.world.blockEntityChanged(blockEntity.pos);
            }
            if (stirFryCount > 0) {
                ensureCookingPlan();
                if (!cookingPlan.valid()) {
                    blockProcessing();
                } else if (stirFryCount >= cookingPlan.workRequired()) {
                    completeCooking(stirringPlayer);
                }
            }
            stirringPlayer = null;
            return;
        }

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
        Item dark = InventoryUtils.createOrEmpty(behavior.burntResultItem);
        result = ItemUtils.isEmpty(dark) ? Item.empty() : dark.count(Math.max(1, prevCount));
        resultCarrier = result.isEmpty() ? null : behavior.burntResultCarrier;
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

    // 起炒条件 内置规则是在火上且已倒油 可用 PotCookConditions 整条覆写
    // 翻炒与一键投料共用起炒判定；无自定义条件时不构造快照
    public PotCookConditions.Verdict cookVerdict(boolean hasHeatSource, Player player) {
        if (player != null && PotCookConditions.instance().hasConditions()) {
            PotCookConditions.Verdict custom = PotCookConditions.instance().evaluate(
                    (org.bukkit.entity.Player) player.platformPlayer(), potState(hasHeatSource));
            if (custom != null) {
                return custom;
            }
        }
        if (!hasHeatSource) {
            return PotCookConditions.Verdict.deny(MessageKeys.POT_NEED_HEAT);
        }
        if (!hasOil) {
            return PotCookConditions.Verdict.deny(MessageKeys.POT_NEED_OIL_FIRST);
        }
        return PotCookConditions.Verdict.ALLOW;
    }

    private PotCookConditions.PotState potState(boolean hasHeatSource) {
        List<ItemStack> stacks = new ArrayList<>(ingredients.size());
        for (Item item : ingredients) {
            stacks.add(ItemStackUtils.getBukkitStack(item.minecraftItem()));
        }
        Location loc = new Location((World) blockEntity.world.world().platformWorld(),
                blockEntity.pos.x(), blockEntity.pos.y(), blockEntity.pos.z());
        return new PotCookConditions.PotState(loc, hasOil, hasHeatSource, List.copyOf(stacks), stirFryCount);
    }

    // 翻炒结果 IDLE 表示锅里没得炒 交回主手逻辑 DENIED 表示起炒条件没满足 已经提示过玩家
    public enum StirResult { OK, IDLE, DENIED }

    public StirResult stirFry(boolean hasHeatSource, Player player) {
        if (stage == PotStage.DONE || stage == PotStage.BURNT || animating || ingredients.isEmpty()) {
            return StirResult.IDLE;
        }
        if (processingBlocked) return StirResult.DENIED;

        // 起炒条件不满足就彻底拦下 不播动画不挥手 只提示 想改规则走 PotCookConditions
        PotCookConditions.Verdict verdict = cookVerdict(hasHeatSource, player);
        if (!verdict.allowed()) {
            if (player != null && verdict.message() != null) {
                player.sendActionBar(Localization.component(verdict.message()));
            }
            return StirResult.DENIED;
        }

        if (player != null) {
            Location stirLoc = new Location((World) blockEntity.world.world().platformWorld(), blockEntity.pos.x(), blockEntity.pos.y(), blockEntity.pos.z());
            PotStirFryEvent event = new PotStirFryEvent((org.bukkit.entity.Player) player.platformPlayer(), stirLoc, stirFryCount + 1);
            if (EventUtils.fireAndCheckCancel(event)) return StirResult.IDLE;
        }

        ensureCookingPlan();
        if (!cookingPlan.valid()) {
            blockProcessing();
            return StirResult.DENIED;
        }

        this.animating = true;
        this.stirAnimationTicks = STIR_ANIMATION_TICKS;
        this.stirringPlayer = player;
        this.seed = System.currentTimeMillis();

        if (onStirFryCallback != null && player != null) {
            onStirFryCallback.accept(player, this);
        }
        boolean firstStir = stirFryCount == 0;
        stirFryCount++;
        if (stage == PotStage.IDLE) stage = PotStage.COOKING;
        if (firstStir && player != null) player.sendActionBar(Localization.component(MessageKeys.POT_START_COOKING));

        element.refreshPackets();
        element.playStirFryAnimation();
        blockEntity.world.blockEntityChanged(blockEntity.pos);
        return StirResult.OK;
    }

    private void ensureCookingPlan() {
        if (cookingPlan == null) {
            cookingPlan = FoodRecipeRegistry.instance().planFlex(ApplianceType.POT,
                    ingredients.stream().map(Item::id).toList(), null, Math.max(1, behavior.stirFryCount));
            // 旧档已翻炒的批次仍沿用原厨具默认阈值。
            if (stirFryCount > 0) cookingPlan = cookingPlan.withWorkRequired(Math.max(1, behavior.stirFryCount));
        }
    }

    private void blockProcessing() {
        if (processingBlocked) return;
        processingBlocked = true;
        animating = false;
        stirAnimationTicks = 0;
        stirringPlayer = null;
        java.util.logging.Logger.getLogger(PotController.class.getName()).warning(
                "炒锅配方快照无效或产物不可用，保留原料并暂停：" + blockEntity.pos);
        blockEntity.world.blockEntityChanged(blockEntity.pos);
    }

    private void completeCooking(Player triggerPlayer) {
        ensureCookingPlan();
        if (!cookingPlan.valid()) {
            blockProcessing();
            return;
        }
        FoodRecipeResult fr = cookingPlan.matched() ? cookingPlan.buildResult().orElse(null) : null;
        if (cookingPlan.matched() && fr == null) {
            blockProcessing();
            return;
        }

        Item failed;
        try {
            failed = fr == null ? InventoryUtils.createOrEmpty(behavior.failedResultItem) : Item.empty();
        } catch (RuntimeException unavailableOutput) {
            blockProcessing();
            return;
        }
        if (fr == null && ItemUtils.isEmpty(failed)) {
            blockProcessing();
            return;
        }

        this.stirFryCount = 0;
        this.hasOil = false;

        if (fr != null) {
            result = fr.item().count(fr.count());
            resultCarrier = fr.carrier();
            stage = PotStage.DONE;
            currentTick = behavior.cookDoneTime;
            // 出锅时才提示要拿什么盛 开炒前不提示 那会逼着每次都去查一遍配方表
            if (triggerPlayer != null) {
                notifyCooker(triggerPlayer, resultCarrier == null
                        ? Localization.component(MessageKeys.POT_DISH_READY_HAND)
                        : Localization.componentWithReplacement(MessageKeys.POT_DISH_READY, "%s",
                                ItemNames.displayName(resultCarrier)));
            }
        } else {
            result = failed.count(1);
            resultCarrier = result.isEmpty() ? null : behavior.failedResultCarrier;
            stage = PotStage.BURNT;
            currentTick = behavior.burntToCharcoalTime;
            lastSentBrightness = -1;
            if (triggerPlayer != null) notifyCooker(triggerPlayer, Localization.component(MessageKeys.POT_ALL_BURNT));
        }
        cookingPlan = null;
        cookedIngredientCount = ingredients.size();
        cookedDishCount = result.isEmpty() ? 0 : result.count();
        heated = hasHeatBelow();

        updateBlockState();
        blockEntity.updateConstantRenderers();
        element.refreshPackets();
        blockEntity.world.blockEntityChanged(blockEntity.pos);
    }

    private void notifyCooker(Player player, net.momirealms.craftengine.libraries.adventure.text.Component message) {
        if (player.platformPlayer() instanceof org.bukkit.entity.Player bukkitPlayer) {
            FoliaUtil.runEntity(bukkitPlayer, () -> player.sendActionBar(message));
        } else {
            player.sendActionBar(message);
        }
    }

    // 返回是否真的收下 一键投料据此决定扣不扣背包 拒收还扣就是凭空销毁材料
    public boolean addIngredient(Item item, boolean hasHeatSource, Player player) {
        if (!ApplianceFoodRegistry.instance().isAllowed(ApplianceType.POT, item.id())) {
            if (player != null) player.sendActionBar(Localization.component(MessageKeys.POT_NOT_INGREDIENT));
            return false;
        }
        if (stage == PotStage.DONE || stage == PotStage.BURNT || animating || ingredients.size() >= MAX_INGREDIENTS) {
            return false;
        }
        stirFryCount = 0;
        cookingPlan = null;
        processingBlocked = false;
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
            org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) player.platformPlayer();
            double health = bukkitPlayer.getHealth() + bukkitPlayer.getAbsorptionAmount();
            player.damage(2, DAMAGE_GENERIC, null);
            if (bukkitPlayer.getHealth() + bukkitPlayer.getAbsorptionAmount() < health) {
                KaleidoscopeAdvancements.recordEvent(player, "hurt_when_takeout_from_pot");
            }
        }
        stirFryCount = 0;
        cookingPlan = null;
        processingBlocked = false;
        int index = ingredients.size() - 1;
        Item extracted = ingredients.remove(index);
        element.refreshSlotPacket(index);
        refreshDynamicElement((el, p) -> el.hideIndex(p, index));
        blockEntity.world.blockEntityChanged(blockEntity.pos);
        return extracted;
    }

    public void resetPot() {
        element.deactivate();
        animating = false;
        stirAnimationTicks = 0;
        stirringPlayer = null;
        cookingPlan = null;
        processingBlocked = false;
        ingredients.clear();
        hasOil = false;
        stirFryCount = 0;
        result = Item.empty();
        resultCarrier = null;
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

    // 盛出后按剩余份数等比例回收食材 无论食材有没有跟着减都必须落盘
    // 只在 changed 时标脏会漏掉部分取出 区块卸载后成品数回滚可无限盛
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
        var hasOilProperty = behavior.getHasOilProperty();
        if (hasOilProperty == null) return;
        ImmutableBlockState state = blockEntity.blockState;
        ImmutableBlockState newState = BlockStates.with(state, hasOilProperty, hasOil);
        var hasBaseProperty = behavior.getHasBaseProperty();
        if (hasBaseProperty != null) {
            newState = BlockStates.with(
                    newState,
                    hasBaseProperty,
                    BlockStates.value(state, hasBaseProperty, hasBaseProperty.defaultValue())
            );
        }
        var facingProperty = behavior.getFacingProperty();
        if (facingProperty != null) {
            newState = BlockStates.with(
                    newState,
                    facingProperty,
                    BlockStates.value(state, facingProperty, facingProperty.defaultValue())
            );
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
    // null 表示空手就能取
    public Key resultCarrier() {
        return resultCarrier;
    }

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
        element.deactivate();
        animating = false;
        stirAnimationTicks = 0;
        stirringPlayer = null;
        if (!ingredients.isEmpty()) {
            ingredients.forEach(item -> DropUtils.dropOnRemove(blockEntity, item));
            ingredients.clear();
        }
        super.onRemove();
    }

    @Override
    public void onUnload() {
        element.deactivate();
        stirringPlayer = null;
        super.onUnload();
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
        data.putInt(K_STIR_ANIMATION_TICKS, stirAnimationTicks);
        data.putBoolean(K_PROCESSING_BLOCKED, processingBlocked);
        if (cookingPlan != null) data.put(K_PROCESSING_PLAN, cookingPlan.save());
        data.put(K_INGREDIENTS, BlockEntityNbt.saveItems(ingredients));
        // 继续使用列表格式以兼容旧存档中的多成品数据
        data.put(K_RESULTS, BlockEntityNbt.saveItems(result.isEmpty() ? List.of() : List.of(result)));
        if (resultCarrier != null) {
            data.putString(K_CARRIER, resultCarrier.asString());
        }
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
        String carrier = data.getString(K_CARRIER, null);
        resultCarrier = carrier == null || carrier.isEmpty() ? null : Key.of(carrier);

        seed = data.getLong(K_SEED, System.currentTimeMillis());
        hasOil = data.getBoolean(K_HAS_OIL, false);
        stirFryCount = data.getInt(K_STIR_FRY_COUNT, 0);
        stage = PotStage.fromOrdinal(data.getInt(K_COOKING_STATUS, 0));
        currentTick = data.getInt(K_CURRENT_TICK, 0);
        stirAnimationTicks = stage == PotStage.COOKING
                ? Math.max(0, Math.min(STIR_ANIMATION_TICKS, data.getInt(K_STIR_ANIMATION_TICKS, 0))) : 0;
        animating = stirAnimationTicks > 0;
        stirringPlayer = null;
        processingBlocked = data.getBoolean(K_PROCESSING_BLOCKED, false);
        cookingPlan = data.containsKey(K_PROCESSING_PLAN) ? CookingPlan.load(data.getCompound(K_PROCESSING_PLAN)) : null;
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
