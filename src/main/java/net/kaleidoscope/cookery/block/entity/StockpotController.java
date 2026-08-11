package net.kaleidoscope.cookery.block.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.kaleidoscope.cookery.block.behavior.StockpotBehavior;
import net.kaleidoscope.cookery.block.entity.render.Particles;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.FoodRecipeResult;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.util.FoliaUtil;
import net.kaleidoscope.cookery.util.HeatSourceUtils;
import net.kaleidoscope.cookery.util.InventoryUtils;
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
import org.bukkit.Particle;
import org.bukkit.World;

public class StockpotController extends BlockEntityController {
   public static final int MAX_INGREDIENTS = 8;
   private static final int ANIM_INTERVAL = 4;
   private static final Key[] STOCKPOT_SOUNDS = new Key[]{
      Key.of("kaleidoscopecookery:stockpot_0"),
      Key.of("kaleidoscopecookery:stockpot_1"),
      Key.of("kaleidoscopecookery:stockpot_2"),
      Key.of("kaleidoscopecookery:stockpot_3"),
      Key.of("kaleidoscopecookery:stockpot_4"),
      Key.of("kaleidoscopecookery:stockpot_5"),
      Key.of("kaleidoscopecookery:stockpot_6")
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
   private static final String K_CARRIER = "carrier";
   private static final String K_LID_ITEM = "lid_item";
   private StockpotStage stage = StockpotStage.PUT_SOUP_BASE;
   private int currentTick = -1;
   private int takeoutCount = 0;
   private int finishedMax = 0;
   private boolean heatedCache = false;
   private int heatCheckTick = 0;
   private final List<Item> ingredients = new ArrayList<>();
   private Item result = Item.empty();
   private Key resultCarrier = null;
   private final List<Key> lastCookedIngredients = new ArrayList<>();
   private final List<Item> ingredientsView = Collections.unmodifiableList(this.ingredients);
   private final List<Key> lastCookedIngredientsView = Collections.unmodifiableList(this.lastCookedIngredients);
   private final StockpotElement element;
   private final StockpotBehavior behavior;
   private Key soupBaseId = ItemKeys.WATER;
   private Item lidItem = Item.empty();
   private long seed = System.currentTimeMillis();
   private final StockpotController.RenderTracker renderTracker = new StockpotController.RenderTracker();

   public <C extends BlockEntityController> BlockEntityTicker<C> createBlockEntityTicker(CEWorld world, ImmutableBlockState blockState) {
      return createTickerHelper((w, pos, state, controller) -> this.tick());
   }

   public StockpotController(BlockEntity blockEntity, StockpotBehavior behavior) {
      super(blockEntity);
      this.behavior = behavior;
      this.element = new StockpotElement(
         this, new WorldPosition(null, super.blockEntity.pos.x() + 0.5F, super.blockEntity.pos.y() + 0.1F, super.blockEntity.pos.z() + 0.5F)
      );
   }

   public void refreshDynamicElement(BiConsumer<StockpotElement, Player> consumer) {
      TrackedPlayers.forEach(super.blockEntity, player -> consumer.accept(this.element, player));
   }

   public void refreshAnimation(int interpDuration) {
      Object bundle = this.element.buildAnimationBundle(interpDuration);
      if (bundle != null) {
         for (Player player : TrackedPlayers.snapshotInRange(super.blockEntity, this.behavior.animChunkRadius)) {
            player.sendPacket(bundle, false);
         }
      }
   }

   public void tick() {
      if (this.stage != StockpotStage.PUT_SOUP_BASE) {
         if (this.heatCheckTick == 0) {
            this.heatedCache = this.hasHeatSource();
         }

         this.heatCheckTick = (this.heatCheckTick + 1) % 20;
         if (this.heatedCache) {
            boolean hasLid = this.hasLid();
            World bWorld = (World)super.blockEntity.world.world().platformWorld();
            long gameTime = bWorld.getGameTime();
            if (gameTime % 15L == 0L) {
               float volume = hasLid ? 0.3F : 0.6F;
               float pitch = 0.9F + (float)ThreadLocalRandom.current().nextDouble() * 0.2F;
               super.blockEntity
                  .world
                  .world()
                  .playSound(
                     Vec3d.atCenterOf(super.blockEntity.pos),
                     STOCKPOT_SOUNDS[ThreadLocalRandom.current().nextInt(STOCKPOT_SOUNDS.length)],
                     volume,
                     pitch,
                     SoundSource.BLOCK
                  );
            }

            if (!hasLid) {
               if (gameTime % this.behavior.particleInterval == 0L) {
                  double bx = super.blockEntity.pos.x() + 0.3 + ThreadLocalRandom.current().nextDouble() * 0.4;
                  double by = super.blockEntity.pos.y() + 0.4;
                  double bz = super.blockEntity.pos.z() + 0.3 + ThreadLocalRandom.current().nextDouble() * 0.4;
                  int pc = this.behavior.particleCount;
                  if (this.isLavaSoup()) {
                     Particles.emit(super.blockEntity.world, Particle.LAVA, bx, by, bz, pc, 0.05, 0.0, 0.05, 0.0, null);
                  } else {
                     Particles.emit(super.blockEntity.world, Particle.SPLASH, bx, by, bz, pc, 0.05, 0.0, 0.05, 0.1, null);
                     Particles.emit(super.blockEntity.world, Particle.BUBBLE_POP, bx, by, bz, pc, 0.05, 0.0, 0.05, 0.02, null);
                  }
               }

               if (gameTime % 4L == 0L) {
                  this.refreshAnimation(4);
               }
            } else {
               if (gameTime % this.behavior.particleInterval == 0L) {
                  int pc = this.behavior.particleCount;
                  if (this.stage == StockpotStage.FINISHED) {
                     double bx = super.blockEntity.pos.x() + 0.5 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.6;
                     double by = super.blockEntity.pos.y() + 0.85 + ThreadLocalRandom.current().nextDouble() * 0.25;
                     double bz = super.blockEntity.pos.z() + 0.5 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.6;
                     Particles.emit(super.blockEntity.world, Particle.CLOUD, bx, by, bz, pc, 0.05, 0.0, 0.05, 0.01, null);
                  } else {
                     double bx = super.blockEntity.pos.x() + 0.5 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.3;
                     double by = super.blockEntity.pos.y() + 0.85;
                     double bz = super.blockEntity.pos.z() + 0.5 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.3;
                     Particle p = this.isLavaSoup() ? Particle.LARGE_SMOKE : Particle.CAMPFIRE_COSY_SMOKE;
                     Particles.emit(super.blockEntity.world, p, bx, by, bz, pc, 0.05, 0.1, 0.05, 0.02, null);
                  }
               }

               if (this.stage == StockpotStage.PUT_INGREDIENT && !this.ingredients.isEmpty() && gameTime % 5L == 0L) {
                  this.stage = StockpotStage.COOKING;
                  this.currentTick = this.behavior.cookingTime;
                  this.refreshRendering();
               } else {
                  if (this.stage == StockpotStage.COOKING) {
                     if (this.currentTick > 0) {
                        this.currentTick--;
                        return;
                     }

                     this.stage = StockpotStage.FINISHED;
                     this.currentTick = -1;
                     List<Key> ids = this.ingredientIds();
                     Optional<FoodRecipeResult> res = FoodRecipeRegistry.instance().cookFlex(ApplianceType.STOCKPOT, ids, this.soupBaseId);
                     int servings;
                     if (res.isPresent()) {
                        FoodRecipeResult fr = res.get();
                        this.result = fr.item().copyWithCount(1);
                        this.resultCarrier = fr.carrier();
                        servings = Math.max(1, fr.count());
                     } else {
                        this.result = InventoryUtils.createOrEmpty(ItemKeys.SUSPICIOUS_STIR_FRY).copyWithCount(1);
                        this.resultCarrier = null;
                        servings = 1;
                     }

                     servings = Math.min(servings, 8);
                     this.takeoutCount = servings;
                     this.finishedMax = servings;
                     this.lastCookedIngredients.clear();
                     this.lastCookedIngredients.addAll(ids);
                     this.ingredients.clear();
                     this.refreshRendering();
                     this.refreshDynamicElement(StockpotElement::onFinished);
                     super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
                  }
               }
            }
         }
      }
   }

   private List<Key> ingredientIds() {
      List<Key> ids = new ArrayList<>(this.ingredients.size());

      for (Item item : this.ingredients) {
         ids.add(item.id());
      }

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
      StockpotBehavior behavior = (StockpotBehavior)state.behavior().getFirst(StockpotBehavior.class);
      return behavior != null && behavior.getHasLidProperty() != null ? BlockStates.value(state, behavior.getHasLidProperty(), false) : false;
   }

   public boolean addLid(Item lidItem) {
      if (this.hasLid()) {
         return false;
      }

      this.lidItem = lidItem.copyWithCount(1);
      this.refreshRendering();
      super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
      FoliaUtil.runLater(
         () -> this.refreshDynamicElement(StockpotElement::hide),
         2L,
         super.blockEntity.world.world(),
         super.blockEntity.pos.x >> 4,
         super.blockEntity.pos.z >> 4
      );
      return true;
   }

   public Item removeLid() {
      if (!this.hasLid()) {
         return null;
      }

      Item lid = this.lidItem.isEmpty() ? InventoryUtils.createOrEmpty(this.behavior.lidItem) : this.lidItem.copy();
      this.lidItem = Item.empty();
      this.refreshRendering();
      super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
      this.refreshDynamicElement((element, p) -> {
         element.hide(p);
         element.forceShow(p);
      });
      return lid;
   }

   public Item extractSoupBase() {
      if (this.stage != StockpotStage.PUT_INGREDIENT) {
         return null;
      }

      if (this.hasLid()) {
         return null;
      }

      if (!this.ingredients.isEmpty()) {
         return null;
      }

      Key soupBaseId = this.soupBaseId;
      this.soupBaseId = ItemKeys.WATER;
      this.stage = StockpotStage.PUT_SOUP_BASE;
      this.seed = System.currentTimeMillis();
      this.renderTracker.snapshot(this.stage, this.ingredients.size(), this.soupBaseId);
      this.updateBlockState();
      this.refreshRendering();
      super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
      this.refreshDynamicElement((element, p) -> {
         element.hide(p);
         element.show(p);
      });
      Item bucket = InventoryUtils.createOrEmpty(soupBaseId);
      return ItemUtils.isEmpty(bucket) ? InventoryUtils.createOrEmpty(ItemKeys.WATER_BUCKET) : bucket;
   }

   private boolean isLavaSoup() {
      return ItemKeys.LAVA_BUCKET.equals(this.soupBaseId);
   }

   public boolean addSoupBase(Key soupBaseId, boolean hasHeatSource) {
      if (this.stage != StockpotStage.PUT_SOUP_BASE) {
         return false;
      }

      if (this.hasLid()) {
         return false;
      }

      this.soupBaseId = soupBaseId;
      this.stage = StockpotStage.PUT_INGREDIENT;
      this.seed = System.currentTimeMillis();
      this.renderTracker.snapshot(this.stage, this.ingredients.size(), this.soupBaseId);
      this.refreshRendering();
      super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
      this.refreshDynamicElement((element, p) -> {
         element.hide(p);
         element.show(p);
      });
      return true;
   }

   public boolean addIngredient(Item item) {
      if (this.hasLid()) {
         return false;
      }

      if (this.stage != StockpotStage.PUT_INGREDIENT && this.stage != StockpotStage.COOKING) {
         return false;
      }

      if (this.ingredients.size() >= 8) {
         return false;
      }

      this.ingredients.add(item);
      if (this.stage == StockpotStage.COOKING) {
         this.stage = StockpotStage.PUT_INGREDIENT;
         this.currentTick = -1;
      }

      this.refreshRendering();
      super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
      int index = this.ingredients.size() - 1;
      this.refreshDynamicElement((element, p) -> element.showIndex(p, index));
      return true;
   }

   public Item extractIngredient(Player player) {
      if (this.hasLid()) {
         return Item.empty();
      }

      if (this.ingredients.isEmpty()) {
         return Item.empty();
      }

      Item extracted = this.ingredients.remove(this.ingredients.size() - 1);
      if (this.stage == StockpotStage.COOKING) {
         if (player != null) {
            player.damage(2.0, DAMAGE_GENERIC, null);
         }

         this.stage = StockpotStage.PUT_INGREDIENT;
         this.currentTick = -1;
      }

      this.refreshRendering();
      super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
      this.refreshDynamicElement((element, p) -> element.hideIndex(p, this.ingredients.size()));
      return extracted;
   }

   public Key resultCarrier() {
      return this.resultCarrier;
   }

   public Item peekResult() {
      return this.stage == StockpotStage.FINISHED && !this.hasLid() && this.takeoutCount > 0 && !this.result.isEmpty()
         ? this.result.copyWithCount(1)
         : Item.empty();
   }

   public Item takeOutResult() {
      if (this.stage != StockpotStage.FINISHED) {
         return Item.empty();
      }

      if (this.hasLid()) {
         return Item.empty();
      }

      if (this.takeoutCount > 0 && !this.result.isEmpty()) {
         Item toReturn = this.result.copyWithCount(1);
         this.takeoutCount--;
         if (this.takeoutCount <= 0) {
            this.resetStockpot();
         } else {
            this.refreshRendering();
            this.refreshDynamicElement(StockpotElement::refreshLiquidLevel);
            super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
         }

         return toReturn;
      } else {
         return Item.empty();
      }
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
      this.updateBlockState();
      this.refreshRendering();
      this.refreshDynamicElement(StockpotElement::clearAll);
      super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
   }

   private void updateBlockState() {
      StockpotBehavior behavior = (StockpotBehavior)super.blockEntity.blockState.behavior().getFirst(StockpotBehavior.class);
      if (behavior != null) {
         ImmutableBlockState newState = BlockStates.with(super.blockEntity.blockState, behavior.getHasLidProperty(), this.hasLid());
         BlockStates.sync(super.blockEntity, newState);
      }
   }

   private void refreshRendering() {
      this.element.refreshPackets();
      this.renderTracker.snapshot(this.stage, this.ingredients.size(), this.soupBaseId);
   }

   public boolean hasElement() {
      return true;
   }

   public void gatherElements(Consumer<BlockEntityElement> consumer) {
      consumer.accept(this.element);
   }

   public void onRemove() {
      if (!this.ingredients.isEmpty()) {
         for (Item item : this.ingredients) {
            DropUtils.dropOnRemove(super.blockEntity, item);
         }

         this.ingredients.clear();
      }

      if (!this.lidItem.isEmpty()) {
         DropUtils.dropOnRemove(super.blockEntity, this.lidItem);
      }

      super.onRemove();
   }

   public void saveCustomData(CompoundTag tag) {
      CompoundTag data = new CompoundTag();
      data.putInt("data_version", VersionHelper.WORLD_VERSION);
      data.putInt("status", this.stage.ordinal());
      data.putInt("current_tick", this.currentTick);
      data.putInt("takeout_count", this.takeoutCount);
      data.putInt("finished_max", this.finishedMax);
      if (!this.lastCookedIngredients.isEmpty()) {
         data.putString("last_cooked", this.lastCookedIngredients.stream().<CharSequence>map(Key::asString).collect(Collectors.joining(",")));
      }

      data.putLong("seed", this.seed);
      data.putString("soup_base_id", this.soupBaseId.asString());
      data.put("ingredients", BlockEntityNbt.saveItems(this.ingredients));
      BlockEntityNbt.putItem(data, "result", this.result);
      if (this.resultCarrier != null) {
         data.putString("carrier", this.resultCarrier.asString());
      }

      BlockEntityNbt.putItem(data, "lid_item", this.lidItem);
      tag.put("kaleidoscopecookery:stockpot", data);
   }

   public void loadCustomData(CompoundTag tag) {
      CompoundTag data = tag.getCompound("kaleidoscopecookery:stockpot");
      if (data != null) {
         int dataVersion = data.getInt("data_version", Config.itemDataFixerUpperFallbackVersion());
         this.stage = StockpotStage.fromOrdinal(data.getInt("status", 0));
         this.currentTick = data.getInt("current_tick", -1);
         this.takeoutCount = data.getInt("takeout_count", 0);
         this.finishedMax = data.getInt("finished_max", this.takeoutCount);
         this.lastCookedIngredients.clear();
         String lc = data.getString("last_cooked", "");
         if (!lc.isEmpty()) {
            for (String s : lc.split(",")) {
               if (!s.isEmpty()) {
                  this.lastCookedIngredients.add(Key.of(s));
               }
            }
         }

         this.seed = data.getLong("seed", System.currentTimeMillis());
         this.soupBaseId = Key.of(data.getString("soup_base_id", ItemKeys.WATER.asString()));
         BlockEntityNbt.loadItems(data, "ingredients", dataVersion, this.ingredients);
         this.result = BlockEntityNbt.getItem(data, "result", dataVersion);
         String carrier = data.getString("carrier", null);
         this.resultCarrier = carrier != null && !carrier.isEmpty() ? Key.of(carrier) : null;
         this.lidItem = BlockEntityNbt.getItem(data, "lid_item", dataVersion);
         this.refreshRendering();
      }
   }

   public StockpotStage stage() {
      return this.stage;
   }

   public int currentTick() {
      return this.currentTick;
   }

   public int takeoutCount() {
      return this.takeoutCount;
   }

   public int finishedMax() {
      return this.finishedMax;
   }

   public List<Key> lastCookedIngredients() {
      return this.lastCookedIngredientsView;
   }

   public List<Item> ingredients() {
      return this.ingredientsView;
   }

   public Key soupBaseId() {
      return this.soupBaseId;
   }

   public Item lidItem() {
      return this.lidItem;
   }

   public long seed() {
      return this.seed;
   }

   public StockpotController.RenderTracker renderTracker() {
      return this.renderTracker;
   }

   public static final class RenderTracker {
      private StockpotStage stage = StockpotStage.PUT_SOUP_BASE;
      private int ingredientCount = 0;
      private Key soupBaseId = ItemKeys.WATER;

      public StockpotStage stage() {
         return this.stage;
      }

      public int ingredientCount() {
         return this.ingredientCount;
      }

      public Key soupBaseId() {
         return this.soupBaseId;
      }

      void snapshot(StockpotStage stage, int ingredientCount, Key soupBaseId) {
         this.stage = stage;
         this.ingredientCount = ingredientCount;
         this.soupBaseId = soupBaseId;
      }

      void reset() {
         this.snapshot(StockpotStage.PUT_SOUP_BASE, 0, ItemKeys.WATER);
      }
   }
}
