package net.kaleidoscope.cookery.block.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.kaleidoscope.cookery.api.PotCookConditions;
import net.kaleidoscope.cookery.api.event.PotStirFryEvent;
import net.kaleidoscope.cookery.block.behavior.PotBehavior;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.item.ItemNames;
import net.kaleidoscope.cookery.recipe.ApplianceFoodRegistry;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.FoodRecipeResult;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.util.EventUtils;
import net.kaleidoscope.cookery.util.HeatSourceUtils;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.util.Localization;
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
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

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
   private static final String K_CARRIER = "carrier";
   private static final String K_COOKED_ING = "cooked_ing";
   private static final String K_COOKED_DISH = "cooked_dish";
   private static final Key DAMAGE_GENERIC = Key.of("minecraft:generic");
   private static final Key SOUND_FIRE_AMBIENT = Key.of("minecraft:block.fire.ambient");
   private PotStage stage = PotStage.IDLE;
   private int currentTick = 0;
   private Item result = Item.empty();
   private Key resultCarrier = null;
   private final List<Item> ingredients = new ArrayList<>();
   private final List<Item> ingredientsView = Collections.unmodifiableList(this.ingredients);
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
      this.element = new PotElement(this, new WorldPosition(null, entity.pos.x() + 0.5F, entity.pos.y() + 0.1F, entity.pos.z() + 0.5F));
   }

   public <C extends BlockEntityController> BlockEntityTicker<C> createBlockEntityTicker(CEWorld world, ImmutableBlockState blockState) {
      return createTickerHelper((w, pos, state, controller) -> this.tick());
   }

   public int animChunkRadius() {
      return this.behavior.animChunkRadius;
   }

   public void refreshDynamicElement(BiConsumer<PotElement, Player> consumer) {
      TrackedPlayers.forEach(this.blockEntity, player -> consumer.accept(this.element, player));
   }

   public void tick() {
      if (this.stage != PotStage.IDLE) {
         if (this.stage == PotStage.DONE || this.stage == PotStage.BURNT) {
            if (this.heatCheckTick++ % 20 == 0) {
               this.heated = this.hasHeatBelow();
            }

            if (!this.heated) {
               return;
            }
         }

         if (this.currentTick > 0) {
            this.currentTick--;
            if (this.currentTick % 20 == 0) {
               this.playCookingSound();
               if (this.stage == PotStage.BURNT) {
                  int newBrightness = PotElement.burntBrightness(this.currentTick, this.behavior.burntToCharcoalTime);
                  if (newBrightness != this.lastSentBrightness) {
                     this.lastSentBrightness = newBrightness;
                     this.refreshDynamicElement((e, p) -> e.updateBrightness(p, newBrightness));
                  }
               }
            }

            if (this.currentTick == 0) {
               if (this.stage == PotStage.DONE) {
                  this.burnDish();
               } else if (this.stage == PotStage.BURNT) {
                  this.dropCharcoal();
                  this.resetPot();
               }
            }
         }
      }
   }

   private void burnDish() {
      int prevCount = this.result.isEmpty() ? 0 : this.result.count();
      Item dark = InventoryUtils.createOrEmpty(ItemKeys.DARK_CUISINE);
      this.result = ItemUtils.isEmpty(dark) ? Item.empty() : dark.count(Math.max(1, prevCount));
      this.resultCarrier = null;
      this.cookedIngredientCount = this.ingredients.size();
      this.cookedDishCount = Math.max(1, prevCount);
      this.stage = PotStage.BURNT;
      this.currentTick = this.behavior.burntToCharcoalTime;
      this.lastSentBrightness = -1;
      this.updateBlockState();
      this.blockEntity.updateConstantRenderers();
      this.element.refreshPackets();
      this.blockEntity.world.blockEntityChanged(this.blockEntity.pos);
   }

   public void setOnStirFryCallback(BiConsumer<Player, PotController> callback) {
      this.onStirFryCallback = callback;
   }

   public PotCookConditions.Verdict cookVerdict(boolean hasHeatSource, Player player) {
      if (player != null && PotCookConditions.instance().hasConditions()) {
         PotCookConditions.Verdict custom = PotCookConditions.instance()
            .evaluate((org.bukkit.entity.Player)player.platformPlayer(), this.potState(hasHeatSource));
         if (custom != null) {
            return custom;
         }
      }

      if (!hasHeatSource) {
         return PotCookConditions.Verdict.deny("kaleidoscopecookery.message.pot.need_heat");
      } else {
         return !this.hasOil ? PotCookConditions.Verdict.deny("kaleidoscopecookery.message.pot.need_oil_first") : PotCookConditions.Verdict.ALLOW;
      }
   }

   private PotCookConditions.PotState potState(boolean hasHeatSource) {
      List<ItemStack> stacks = new ArrayList<>(this.ingredients.size());

      for (Item item : this.ingredients) {
         stacks.add(ItemStackUtils.getBukkitStack(item.minecraftItem()));
      }

      Location loc = new Location(
         (World)this.blockEntity.world.world().platformWorld(), this.blockEntity.pos.x(), this.blockEntity.pos.y(), this.blockEntity.pos.z()
      );
      return new PotCookConditions.PotState(loc, this.hasOil, hasHeatSource, List.copyOf(stacks), this.stirFryCount);
   }

   public PotController.StirResult stirFry(boolean hasHeatSource, Player player) {
      if (this.stage != PotStage.DONE && this.stage != PotStage.BURNT && !this.animating && !this.ingredients.isEmpty()) {
         PotCookConditions.Verdict verdict = this.cookVerdict(hasHeatSource, player);
         if (!verdict.allowed()) {
            if (player != null && verdict.message() != null) {
               player.sendActionBar(Localization.component(verdict.message()));
            }

            return PotController.StirResult.DENIED;
         } else {
            if (player != null) {
               Location stirLoc = new Location(
                  (World)this.blockEntity.world.world().platformWorld(), this.blockEntity.pos.x(), this.blockEntity.pos.y(), this.blockEntity.pos.z()
               );
               PotStirFryEvent event = new PotStirFryEvent((org.bukkit.entity.Player)player.platformPlayer(), stirLoc, this.stirFryCount + 1);
               if (EventUtils.fireAndCheckCancel(event)) {
                  return PotController.StirResult.IDLE;
               }
            }

            this.animating = true;
            this.seed = System.currentTimeMillis();
            if (this.onStirFryCallback != null && player != null) {
               this.onStirFryCallback.accept(player, this);
            }

            boolean firstStir = this.stirFryCount == 0;
            this.stirFryCount++;
            if (this.stage == PotStage.IDLE) {
               this.stage = PotStage.COOKING;
            }

            if (firstStir && player != null) {
               player.sendActionBar(Localization.component("kaleidoscopecookery.message.pot.start_cooking"));
            }

            this.element.refreshPackets();
            this.element.playStirFryAnimation(() -> {
               this.animating = false;
               if (this.stirFryCount >= this.behavior.stirFryCount) {
                  this.completeCooking(player);
               }
            });
            return PotController.StirResult.OK;
         }
      } else {
         return PotController.StirResult.IDLE;
      }
   }

   private void completeCooking(Player triggerPlayer) {
      FoodRecipeResult fr = FoodRecipeRegistry.instance().cookFlex(ApplianceType.POT, this.ingredients.stream().<Key>map(Item::id).toList()).orElse(null);
      this.stirFryCount = 0;
      this.hasOil = false;
      if (fr != null) {
         this.result = fr.item().count(fr.count());
         this.resultCarrier = fr.carrier();
         this.stage = PotStage.DONE;
         this.currentTick = this.behavior.cookDoneTime;
         if (triggerPlayer != null) {
            triggerPlayer.sendActionBar(
               this.resultCarrier == null
                  ? Localization.component("kaleidoscopecookery.message.pot.dish_ready_hand")
                  : Localization.componentWithReplacement("kaleidoscopecookery.message.pot.dish_ready", "%s", ItemNames.displayName(this.resultCarrier))
            );
         }
      } else {
         Item suspense = InventoryUtils.createOrEmpty(ItemKeys.SUSPICIOUS_STIR_FRY);
         this.result = ItemUtils.isEmpty(suspense) ? Item.empty() : suspense.count(1);
         this.resultCarrier = null;
         this.stage = PotStage.BURNT;
         this.currentTick = this.behavior.burntToCharcoalTime;
         this.lastSentBrightness = -1;
         if (triggerPlayer != null) {
            triggerPlayer.sendActionBar(Localization.component("kaleidoscopecookery.message.pot.all_burnt"));
         }
      }

      this.cookedIngredientCount = this.ingredients.size();
      this.cookedDishCount = this.result.isEmpty() ? 0 : this.result.count();
      this.heated = this.hasHeatBelow();
      this.updateBlockState();
      this.blockEntity.updateConstantRenderers();
      this.element.refreshPackets();
      this.blockEntity.world.blockEntityChanged(this.blockEntity.pos);
   }

   public boolean addIngredient(Item item, boolean hasHeatSource, Player player) {
      if (!ApplianceFoodRegistry.instance().isAllowed(ApplianceType.POT, item.id())) {
         if (player != null) {
            player.sendActionBar(Localization.component("kaleidoscopecookery.message.pot.not_ingredient"));
         }

         return false;
      } else if (this.stage != PotStage.DONE && this.stage != PotStage.BURNT && !this.animating && this.ingredients.size() < 8) {
         this.stirFryCount = 0;
         int index = this.ingredients.size();
         this.ingredients.add(item);
         this.element.refreshSlotPacket(index);
         this.refreshDynamicElement((el, p) -> el.showIndex(p, index));
         this.blockEntity.world.blockEntityChanged(this.blockEntity.pos);
         return true;
      } else {
         return false;
      }
   }

   public Item extractItem(Player player) {
      if (this.stage != PotStage.DONE && this.stage != PotStage.BURNT && !this.animating && !this.ingredients.isEmpty()) {
         if (this.hasOil && player != null) {
            player.damage(2.0, DAMAGE_GENERIC, null);
         }

         this.stirFryCount = 0;
         int index = this.ingredients.size() - 1;
         Item extracted = this.ingredients.remove(index);
         this.element.refreshSlotPacket(index);
         this.refreshDynamicElement((el, p) -> el.hideIndex(p, index));
         this.blockEntity.world.blockEntityChanged(this.blockEntity.pos);
         return extracted;
      } else {
         return null;
      }
   }

   public void resetPot() {
      this.ingredients.clear();
      this.hasOil = false;
      this.stirFryCount = 0;
      this.result = Item.empty();
      this.resultCarrier = null;
      this.stage = PotStage.IDLE;
      this.currentTick = 0;
      this.lastSentBrightness = -1;
      this.heated = false;
      this.cookedIngredientCount = 0;
      this.cookedDishCount = 0;
      this.updateBlockState();
      this.blockEntity.updateConstantRenderers();
      this.element.refreshPackets();
      this.refreshDynamicElement(PotElement::hideAll);
      this.blockEntity.world.blockEntityChanged(this.blockEntity.pos);
   }

   private void syncIngredientsToResult() {
      if (this.result.isEmpty()) {
         this.resetPot();
      } else {
         int remainingDishes = this.result.count();
         int target = this.cookedDishCount <= 0
            ? this.ingredients.size()
            : Math.round((float)this.cookedIngredientCount * remainingDishes / this.cookedDishCount);

         boolean changed;
         for (changed = false; this.ingredients.size() > target && !this.ingredients.isEmpty(); changed = true) {
            int idx = this.ingredients.size() - 1;
            this.ingredients.remove(idx);
            this.refreshDynamicElement((el, p) -> el.hideIndex(p, idx));
         }

         if (changed) {
            this.element.refreshPackets();
         }

         this.blockEntity.world.blockEntityChanged(this.blockEntity.pos);
      }
   }

   public void setHasOil(boolean hasOil) {
      if (this.hasOil != hasOil) {
         this.hasOil = hasOil;
         this.updateBlockState();
      }
   }

   private boolean hasHeatBelow() {
      Object level = this.blockEntity.world.world().minecraftWorld();
      Object belowPos = LocationUtils.below(LocationUtils.toBlockPos(this.blockEntity.pos));
      return HeatSourceUtils.isHeatSource(level, belowPos);
   }

   private void updateBlockState() {
      Property<Boolean> hasOilProperty = this.behavior.getHasOilProperty();
      if (hasOilProperty != null) {
         ImmutableBlockState state = this.blockEntity.blockState;
         ImmutableBlockState newState = BlockStates.with(state, hasOilProperty, this.hasOil);
         Property<Boolean> hasBaseProperty = this.behavior.getHasBaseProperty();
         if (hasBaseProperty != null) {
            newState = BlockStates.with(newState, hasBaseProperty, (Boolean)BlockStates.value(state, hasBaseProperty, (Boolean)hasBaseProperty.defaultValue()));
         }

         Property<Direction> facingProperty = this.behavior.getFacingProperty();
         if (facingProperty != null) {
            newState = BlockStates.with(
               newState, facingProperty, BlockStates.value(state, facingProperty, facingProperty.defaultValue())
            );
         }

         BlockStates.sync(this.blockEntity, newState);
      }
   }

   private void dropCharcoal() {
      DropUtils.dropAtCenter(this.blockEntity, InventoryUtils.createOrEmpty(ItemKeys.CHARCOAL));
   }

   private void playCookingSound() {
      float volume = 0.5F + ThreadLocalRandom.current().nextFloat() * 0.5F;
      float pitch = 0.8F + ThreadLocalRandom.current().nextFloat() * 0.5F;
      this.blockEntity.world.world().playSound(Vec3d.atCenterOf(this.blockEntity.pos), SOUND_FIRE_AMBIENT, volume, pitch, SoundSource.BLOCK);
   }

   public PotStage stage() {
      return this.stage;
   }

   public boolean hasOil() {
      return this.hasOil;
   }

   public Key resultCarrier() {
      return this.resultCarrier;
   }

   public int resultCount() {
      return this.result.isEmpty() ? 0 : this.result.count();
   }

   public Item peekResult() {
      return this.result.isEmpty() ? Item.empty() : this.result.copyWithCount(1);
   }

   public void consumeResult(int amount) {
      if (amount > 0 && !this.result.isEmpty()) {
         this.result.shrink(Math.min(amount, this.result.count()));
         this.syncIngredientsToResult();
      }
   }

   public List<Item> ingredients() {
      return this.ingredientsView;
   }

   public long seed() {
      return this.seed;
   }

   public int currentTick() {
      return this.currentTick;
   }

   public int burntToCharcoalTime() {
      return this.behavior.burntToCharcoalTime;
   }

   public boolean hasElement() {
      return true;
   }

   public void gatherElements(Consumer<BlockEntityElement> consumer) {
      consumer.accept(this.element);
   }

   public void onRemove() {
      if (!this.ingredients.isEmpty()) {
         this.ingredients.forEach(item -> DropUtils.dropOnRemove(this.blockEntity, item));
         this.ingredients.clear();
      }

      super.onRemove();
   }

   public void saveCustomData(CompoundTag tag) {
      CompoundTag data = new CompoundTag();
      data.putInt("data_version", VersionHelper.WORLD_VERSION);
      data.putLong("seed", this.seed);
      data.putBoolean("has_oil", this.hasOil);
      data.putInt("stir_fry_count", this.stirFryCount);
      data.putInt("cooking_status", this.stage.ordinal());
      data.putInt("current_tick", this.currentTick);
      data.put("ingredients", BlockEntityNbt.saveItems(this.ingredients));
      data.put("results", BlockEntityNbt.saveItems(this.result.isEmpty() ? List.of() : List.of(this.result)));
      if (this.resultCarrier != null) {
         data.putString("carrier", this.resultCarrier.asString());
      }

      data.putInt("cooked_ing", this.cookedIngredientCount);
      data.putInt("cooked_dish", this.cookedDishCount);
      tag.put("kaleidoscopecookery:cooking_pot", data);
   }

   public void loadCustomData(CompoundTag tag) {
      CompoundTag data = tag.getCompound("kaleidoscopecookery:cooking_pot");
      if (data != null) {
         int dataVersion = data.getInt("data_version", Config.itemDataFixerUpperFallbackVersion());
         BlockEntityNbt.loadItems(data, "ingredients", dataVersion, this.ingredients);
         List<Item> loadedResults = new ArrayList<>(1);
         BlockEntityNbt.loadItems(data, "results", dataVersion, loadedResults);
         this.result = loadedResults.isEmpty() ? Item.empty() : loadedResults.get(0);
         String carrier = data.getString("carrier", null);
         this.resultCarrier = carrier != null && !carrier.isEmpty() ? Key.of(carrier) : null;
         this.seed = data.getLong("seed", System.currentTimeMillis());
         this.hasOil = data.getBoolean("has_oil", false);
         this.stirFryCount = data.getInt("stir_fry_count", 0);
         this.stage = PotStage.fromOrdinal(data.getInt("cooking_status", 0));
         this.currentTick = data.getInt("current_tick", 0);
         this.cookedIngredientCount = data.getInt("cooked_ing", this.ingredients.size());
         this.cookedDishCount = data.getInt("cooked_dish", 0);

         try {
            this.heated = (this.stage == PotStage.DONE || this.stage == PotStage.BURNT) && this.hasHeatBelow();
         } catch (Exception ignored) {
            this.heated = false;
         }

         this.element.refreshPackets();
      }
   }

   public enum StirResult {
      OK,
      IDLE,
      DENIED;
   }
}
