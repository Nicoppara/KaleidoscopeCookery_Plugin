package net.kaleidoscope.cookery.block.entity;
import net.kaleidoscope.cookery.block.behavior.PotBehavior;
import net.kaleidoscope.cookery.block.entity.render.PotElement;

import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.block.entity.tick.BlockEntityTicker;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.sound.SoundSource;
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
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.food.FoodCategoryRegistry;
import net.kaleidoscope.cookery.recipe.food.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.food.FoodRecipeResult;
import net.kaleidoscope.cookery.util.EventUtils;
import net.kaleidoscope.cookery.api.event.PotStirFryEvent;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class PotController extends BlockEntityController {
    private static final int MAX_INGREDIENTS = 8;
    private static final Key OVERCOOKED_RESULT = Key.of("cook:dark_cuisine");
    private static final Key NO_RECIPE_RESULT = Key.of("cook:suspicious_stir_fry");
    private static final Key CHARCOAL = Key.of("minecraft:charcoal");

    private CookingStage stage = CookingStage.IDLE;
    private int currentTick = 0;
    private final List<Item> results = new ArrayList<>();
    private final List<Item> ingredients = new ArrayList<>();
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

    public void refreshDynamicElement(BiConsumer<PotElement, Player> consumer) {
        TrackedPlayers.forEach(blockEntity, player -> consumer.accept(element, player));
    }

    public void tick() {
        if (stage == CookingStage.IDLE) return;

        if (stage == CookingStage.DONE || stage == CookingStage.BURNT) {
            if (heatCheckTick++ % 20 == 0) heated = hasHeatBelow();
            if (!heated) return;
        }
        if (currentTick <= 0) return;
        currentTick--;

        if (currentTick % 20 == 0) {
            playCookingSound();
            if (stage == CookingStage.BURNT) {
                int newBrightness = PotElement.burntBrightness(currentTick, behavior.burntToCharcoalTime);
                if (newBrightness != lastSentBrightness) {
                    lastSentBrightness = newBrightness;
                    refreshDynamicElement((e, p) -> e.updateBrightness(p, newBrightness));
                }
            }
        }

        if (currentTick == 0) {
            if (stage == CookingStage.DONE) {
                burnDish();
            } else if (stage == CookingStage.BURNT) {
                dropCharcoal();
                resetPot();
            }
        }
    }

    // 盛出窗口过点烧焦成黑暗料理
    private void burnDish() {
        int prevCount = 0;
        for (Item it : results) prevCount += it.count();
        results.clear();
        Item dark = BukkitItemManager.instance().createWrappedItem(OVERCOOKED_RESULT, null);
        if (dark != null) results.add(dark.count(Math.max(1, prevCount)));
        cookedIngredientCount = ingredients.size();
        cookedDishCount = Math.max(1, prevCount);

        stage = CookingStage.BURNT;
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
        if (stage == CookingStage.DONE || stage == CookingStage.BURNT || animating || ingredients.isEmpty()) return false;

        // 翻炒前触发事件，本次结果次数取决于是否真正受热
        if (player != null) {
            int resultCount = (hasHeatSource && hasOil) ? stirFryCount + 1 : stirFryCount;
            Location stirLoc = new Location((org.bukkit.World) blockEntity.world.world().platformWorld(), blockEntity.pos.x(), blockEntity.pos.y(), blockEntity.pos.z());
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
            if (stage == CookingStage.IDLE) stage = CookingStage.COOKING;
            if (firstStir && player != null) player.sendActionBar(Component.text(behavior.msgStartCooking));
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
        this.results.clear();

        if (fr != null) {
            results.add(fr.item().count(fr.count()));
            stage = CookingStage.DONE;
            currentTick = behavior.cookDoneTime;
            if (triggerPlayer != null) triggerPlayer.sendActionBar(Component.text(behavior.msgDishReady));
        } else {
            Item suspense = BukkitItemManager.instance().createWrappedItem(NO_RECIPE_RESULT, null);
            if (suspense != null) results.add(suspense.count(1));
            stage = CookingStage.BURNT;
            currentTick = behavior.burntToCharcoalTime;
            lastSentBrightness = -1;
            if (triggerPlayer != null) triggerPlayer.sendActionBar(Component.text(behavior.msgAllBurnt));
        }
        cookedIngredientCount = ingredients.size();
        cookedDishCount = 0;
        for (Item r : results) cookedDishCount += r.count();
        heated = hasHeatBelow();

        updateBlockState();
        blockEntity.updateConstantRenderers();
        element.refreshPackets();
        blockEntity.world.blockEntityChanged(blockEntity.pos);
    }

    public void addIngredient(Item item, boolean hasHeatSource, Player player) {
        if (!FoodCategoryRegistry.instance().isRegistered(ApplianceType.POT, item.id())) {
            if (player != null) player.sendActionBar(Component.text(behavior.msgNotIngredient));
            return;
        }
        if (stage == CookingStage.DONE || stage == CookingStage.BURNT || animating || ingredients.size() >= MAX_INGREDIENTS) return;
        stirFryCount = 0;
        int index = ingredients.size();
        ingredients.add(item);
        element.refreshSlotPacket(index);
        refreshDynamicElement((el, p) -> el.showIndex(p, index));
        blockEntity.world.blockEntityChanged(blockEntity.pos);
    }

    public Item extractItem(Player player) {
        if (stage == CookingStage.DONE || stage == CookingStage.BURNT || animating || ingredients.isEmpty()) return null;
        if (this.hasOil && player != null) {
            player.damage(2, Key.of("minecraft:generic"), null);
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
        results.clear();
        stage = CookingStage.IDLE;
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
    public void syncIngredientsToResults() {
        if (results.isEmpty()) {
            resetPot();
            return;
        }
        int remainingDishes = 0;
        for (Item r : results) remainingDishes += r.count();
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
            blockEntity.world.blockEntityChanged(blockEntity.pos);
        }
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
        DropUtils.dropAtCenter(blockEntity, BukkitItemManager.instance().createWrappedItem(CHARCOAL, null));
    }

    private void playCookingSound() {
        float volume = 0.5f + (float) Math.random() * 0.5f;
        float pitch = 0.8f + (float) Math.random() * 0.5f;
        blockEntity.world.world().playSound(Vec3d.atCenterOf(blockEntity.pos), Key.of("minecraft:block.fire.ambient"), volume, pitch, SoundSource.BLOCK);
    }

    public CookingStage stage() {
        return stage;
    }

    public boolean hasOil() {
        return hasOil;
    }

    public List<Item> getResults() {
        return results;
    }

    public List<Item> getIngredients() {
        return ingredients;
    }

    public long getSeed() {
        return seed;
    }

    public int getCurrentTick() {
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
        data.putInt("data_version", VersionHelper.WORLD_VERSION);
        data.putLong("seed", seed);
        data.putBoolean("has_oil", hasOil);
        data.putInt("stir_fry_count", stirFryCount);
        data.putInt("cooking_status", stage.ordinal());
        data.putInt("current_tick", currentTick);
        data.put("ingredients", BlockEntityNbt.saveItems(ingredients));
        data.put("results", BlockEntityNbt.saveItems(results));
        data.putInt("cooked_ing", cookedIngredientCount);
        data.putInt("cooked_dish", cookedDishCount);
        tag.put(behavior.customDataKey, data);
    }

    // TODO: 含旧存档迁移逻辑，正式版删除
    @Override
    public void loadCustomData(CompoundTag tag) {
        CompoundTag data = tag.getCompound(behavior.customDataKey);
        if (data == null) data = tag;

        int dataVersion = data.getInt("data_version", VersionHelper.WORLD_VERSION);
        BlockEntityNbt.loadItems(data, "ingredients", dataVersion, ingredients);
        BlockEntityNbt.loadItems(data, "results", dataVersion, results);

        if (data.containsKey("result")) {
            Object nmsItem = ItemStackUtils.parseMinecraftItem(data.getCompound("result"), dataVersion);
            if (nmsItem != null) {
                Item oldRes = ItemStackUtils.wrap(nmsItem);
                if (!oldRes.isEmpty()) results.add(oldRes);
            }
        }

        seed = data.getLong("seed", System.currentTimeMillis());
        hasOil = data.getBoolean("has_oil", false);
        stirFryCount = data.getInt("stir_fry_count", 0);
        stage = CookingStage.fromOrdinal(data.getInt("cooking_status", 0));
        currentTick = data.getInt("current_tick", 0);
        cookedIngredientCount = data.getInt("cooked_ing", ingredients.size());
        cookedDishCount = data.getInt("cooked_dish", 0);
        try {
            heated = (stage == CookingStage.DONE || stage == CookingStage.BURNT) && hasHeatBelow();
        } catch (Exception ignored) {
            heated = false;
        }
        element.refreshPackets();
    }
}
