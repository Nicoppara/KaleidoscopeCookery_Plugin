package net.kaleidoscope.cookery.block.entity;

import java.util.Arrays;
import java.util.function.Consumer;
import net.kaleidoscope.cookery.api.event.ShawarmaExtractEvent;
import net.kaleidoscope.cookery.block.behavior.ShawarmaSpitBehavior;
import net.kaleidoscope.cookery.recipe.ApplianceFoodRegistry;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.util.EventUtils;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.block.entity.tick.BlockEntityTicker;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.block.property.type.DoubleBlockHalf;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.ListTag;
import net.momirealms.craftengine.libraries.nbt.Tag;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

public class ShawarmaSpitController extends BlockEntityController {
   public static final int LAYERS = 2;
   public static final int SLOTS = 8;
   private static final String DATA_KEY = "kaleidoscopecookery:shawarma_spit";
   private static final String K_DATA_VERSION = "data_version";
   private static final String K_ITEMS = "items";
   private static final String K_LAYER = "layer";
   private static final String K_SLOT = "slot";
   private static final String K_ITEM = "item";
   private static final String K_PROGRESS = "progress";
   private static final String K_TIME = "time";
   private static final String K_CURRENT_ROTATION = "current_rotation";
   private final ShawarmaSpitBehavior behavior;
   private final boolean lower;
   private final Item[][] items = new Item[2][8];
   private final int[][] cookingProgress = new int[2][8];
   private final int[][] cookingTime = new int[2][8];
   private float currentRotation = 0.0F;
   private int animationTick = 0;
   private boolean wasActive = false;
   private final ShawarmaSpitElement element;

   public ShawarmaSpitController(BlockEntity blockEntity, ShawarmaSpitBehavior behavior) {
      super(blockEntity);
      this.behavior = behavior;
      Property<DoubleBlockHalf> halfProperty = behavior.getHalfProperty();
      this.lower = BlockStates.value(blockEntity.blockState, halfProperty, halfProperty.defaultValue()) != DoubleBlockHalf.UPPER;

      for (Item[] layer : this.items) {
         Arrays.fill(layer, Item.empty());
      }

      this.element = this.lower ? new ShawarmaSpitElement(this, behavior) : null;
   }

   public <C extends BlockEntityController> BlockEntityTicker<C> createBlockEntityTicker(CEWorld world, ImmutableBlockState blockState) {
      Property<DoubleBlockHalf> halfProperty = this.behavior.getHalfProperty();
      return BlockStates.value(blockState, halfProperty, halfProperty.defaultValue()) == DoubleBlockHalf.UPPER
         ? null
         : createTickerHelper((w, pos, state, controller) -> this.tick());
   }

   private boolean isPowered() {
      return BlockStates.value(this.blockEntity.blockState, this.behavior.getPoweredProperty(), false);
   }

   private boolean hasRaw() {
      for (int l = 0; l < 2; l++) {
         for (int s = 0; s < 8; s++) {
            if (this.cookingTime[l][s] > 0) {
               return true;
            }
         }
      }

      return false;
   }

   public boolean isEmpty() {
      for (int l = 0; l < 2; l++) {
         for (int s = 0; s < 8; s++) {
            if (!this.items[l][s].isEmpty()) {
               return false;
            }
         }
      }

      return true;
   }

   public boolean isActive() {
      return this.isPowered() && (this.isEmpty() || this.hasRaw());
   }

   public void tick() {
      if (!this.isActive()) {
         this.wasActive = false;
      } else {
         if (!this.wasActive) {
            this.animationTick = 0;
            this.wasActive = true;
         }

         boolean changed = false;

         for (int l = 0; l < 2; l++) {
            for (int s = 0; s < 8; s++) {
               if (this.cookingTime[l][s] > 0) {
                  this.cookingProgress[l][s]++;
                  if (this.cookingProgress[l][s] >= this.cookingTime[l][s]) {
                     this.items[l][s] = this.getRecipeResult(this.items[l][s]);
                     this.cookingTime[l][s] = -1;
                     this.cookingProgress[l][s] = 0;
                     this.element.updateSlotItem(l, s, this.items[l][s]);
                     changed = true;
                  }
               }
            }
         }

         if (changed) {
            this.blockEntity.world.blockEntityChanged(this.blockEntity.pos);
         }

         if (this.animationTick % 20 == 0) {
            this.currentRotation = (this.currentRotation + 45.0F) % 360.0F;
            this.element.updateRotation();
         }

         this.animationTick++;
      }
   }

   private Item getRecipeResult(Item input) {
      return FoodRecipeRegistry.instance().findAccurate(ApplianceType.SHAWARMA, input.id()).map(fr -> fr.item().count(fr.count())).orElse(input.copy());
   }

   public boolean canCook(Item item) {
      return ApplianceFoodRegistry.instance().isAllowed(ApplianceType.SHAWARMA, item.id());
   }

   private int firstEmptySlot(int layer) {
      for (int s = 0; s < 8; s++) {
         if (this.items[layer][s].isEmpty()) {
            return s;
         }
      }

      return -1;
   }

   public boolean tryAddOne(int layer, Item item) {
      int s = this.firstEmptySlot(layer);
      if (s < 0) {
         return false;
      }

      this.items[layer][s] = item.copyWithCount(1);
      this.cookingProgress[layer][s] = 0;
      this.cookingTime[layer][s] = this.behavior.grillTime;
      this.element.spawnSlot(layer, s, this.items[layer][s]);
      this.blockEntity.world.blockEntityChanged(this.blockEntity.pos);
      return true;
   }

   private void clearSlot(int layer, int s) {
      this.items[layer][s] = Item.empty();
      this.cookingProgress[layer][s] = 0;
      this.cookingTime[layer][s] = 0;
      this.element.removeSlot(layer, s);
   }

   public boolean takeFromLayer(int layer, Player player, InteractionHand hand) {
      boolean hasCooked = false;

      for (int s = 0; s < 8; s++) {
         if (!this.items[layer][s].isEmpty() && this.cookingTime[layer][s] == -1) {
            hasCooked = true;
            break;
         }
      }

      boolean took;
      if (hasCooked) {
         took = this.takeCookedProducts(layer, player);
      } else {
         took = this.takeOneRaw(layer, player, hand);
      }

      if (took) {
         this.blockEntity.world.blockEntityChanged(this.blockEntity.pos);
      }

      return took;
   }

   private boolean takeCookedProducts(int layer, Player player) {
      boolean took = false;

      for (int s = 0; s < 8; s++) {
         if (!this.items[layer][s].isEmpty() && this.cookingTime[layer][s] == -1) {
            Item it = this.items[layer][s].copy();
            ItemStack product = ItemStackUtils.getBukkitStack(it);
            ShawarmaExtractEvent event = new ShawarmaExtractEvent((org.bukkit.entity.Player)player.platformPlayer(), this.extractLocation(), product);
            if (!EventUtils.fireAndCheckCancel(event)) {
               Item give = BukkitItemManager.instance().wrap(event.product());
               if (!InventoryUtils.hasSpaceFor(player, give)) {
                  break;
               }

               InventoryUtils.give(player, give);
               this.clearSlot(layer, s);
               took = true;
            }
         }
      }

      return took;
   }

   private boolean takeOneRaw(int layer, Player player, InteractionHand hand) {
      for (int s = 0; s < 8; s++) {
         if (!this.items[layer][s].isEmpty()) {
            InventoryUtils.giveOrHold(player, hand, this.items[layer][s].copy());
            this.clearSlot(layer, s);
            return true;
         }
      }

      return false;
   }

   private Location extractLocation() {
      BlockPos pos = this.blockEntity.pos;
      return new Location((World)this.blockEntity.world.world().platformWorld(), pos.x(), pos.y(), pos.z());
   }

   public boolean layerEmpty(int layer) {
      for (int s = 0; s < 8; s++) {
         if (!this.items[layer][s].isEmpty()) {
            return false;
         }
      }

      return true;
   }

   public Item[][] getItems() {
      return this.items;
   }

   public float getCurrentRotation() {
      return this.currentRotation;
   }

   public BlockPos getPos() {
      return this.blockEntity.pos;
   }

   public ImmutableBlockState getBlockState() {
      return this.blockEntity.blockState;
   }

   public CEWorld getWorld() {
      return this.blockEntity.world;
   }

   public boolean hasElement() {
      return this.lower;
   }

   public void gatherElements(Consumer<BlockEntityElement> consumer) {
      if (this.element != null) {
         consumer.accept(this.element);
      }
   }

   public void onRemove() {
      for (int l = 0; l < 2; l++) {
         for (int s = 0; s < 8; s++) {
            if (!this.items[l][s].isEmpty()) {
               DropUtils.dropOnRemove(this.blockEntity, this.items[l][s]);
            }
         }
      }

      super.onRemove();
   }

   public void saveCustomData(CompoundTag tag) {
      if (this.lower) {
         CompoundTag data = new CompoundTag();
         data.putInt("data_version", VersionHelper.WORLD_VERSION);
         ListTag itemsTag = new ListTag();

         for (int l = 0; l < 2; l++) {
            for (int s = 0; s < 8; s++) {
               Tag itemTag = BlockEntityNbt.itemTag(this.items[l][s]);
               if (itemTag != null) {
                  CompoundTag entry = new CompoundTag();
                  entry.putInt("layer", l);
                  entry.putInt("slot", s);
                  entry.put("item", itemTag);
                  entry.putInt("progress", this.cookingProgress[l][s]);
                  entry.putInt("time", this.cookingTime[l][s]);
                  itemsTag.add(entry);
               }
            }
         }

         data.put("items", itemsTag);
         data.putFloat("current_rotation", this.currentRotation);
         tag.put("kaleidoscopecookery:shawarma_spit", data);
      }
   }

   public void loadCustomData(CompoundTag tag) {
      if (this.lower) {
         for (int l = 0; l < 2; l++) {
            Arrays.fill(this.items[l], Item.empty());
            Arrays.fill(this.cookingProgress[l], 0);
            Arrays.fill(this.cookingTime[l], 0);
         }

         CompoundTag data = tag.getCompound("kaleidoscopecookery:shawarma_spit");
         if (data != null) {
            int dataVersion = data.getInt("data_version", Config.itemDataFixerUpperFallbackVersion());
            ListTag itemsTag = data.getList("items");
            if (itemsTag != null) {
               for (Tag t : itemsTag) {
                  if (t instanceof CompoundTag entry) {
                     int l = entry.getInt("layer", 0);
                     int s = entry.getInt("slot", -1);
                     if (l >= 0 && l < 2 && s >= 0 && s < 8) {
                        Object nms = ItemStackUtils.parseMinecraftItem(entry.getCompound("item"), dataVersion);
                        if (nms != null) {
                           this.items[l][s] = ItemStackUtils.wrap(nms);
                           this.cookingProgress[l][s] = entry.getInt("progress", 0);
                           this.cookingTime[l][s] = entry.getInt("time", this.behavior.grillTime);
                        }
                     }
                  }
               }
            }

            this.currentRotation = data.getFloat("current_rotation", 0.0F);
            this.element.refreshAllItems();
         }
      }
   }
}
