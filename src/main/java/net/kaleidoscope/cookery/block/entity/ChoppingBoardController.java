package net.kaleidoscope.cookery.block.entity;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.kaleidoscope.cookery.block.behavior.ChoppingBoardBehavior;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.recipe.ApplianceFoodRegistry;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.ChoppingBoardRecipe;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.util.DropUtils;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.block.entity.tick.BlockEntityTicker;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;

public class ChoppingBoardController extends BlockEntityController {
   private static final String DATA_KEY = "kaleidoscopecookery:chopping_board";
   private static final String K_BLOCK_ENTITY_TAG = "BlockEntityTag";
   private static final String K_DATA_VERSION = "data_version";
   private static final String K_STAGE = "stage";
   private static final String K_ITEM = "item";
   private final ChoppingBoardBehavior behavior;
   private final ChoppingBoardElement element;
   private Item placedItem = Item.empty();
   private int currentStage = 0;

   public ChoppingBoardController(BlockEntity blockEntity, ChoppingBoardBehavior behavior) {
      super(blockEntity);
      this.behavior = behavior;
      this.element = new ChoppingBoardElement(
         this, new WorldPosition(null, super.blockEntity.pos.x() + 0.5F, super.blockEntity.pos.y() + 0.625F, super.blockEntity.pos.z() + 0.5F)
      );
   }

   public float facingYawRadians() {
      Property<Direction> facingProperty = this.behavior.getFacingProperty();
      if (facingProperty == null) {
         return 0.0F;
      }

      Direction f = BlockStates.value(super.blockEntity.blockState, facingProperty, facingProperty.defaultValue());

      int data2D = switch (f) {
         case WEST -> 1;
         case NORTH -> 2;
         case EAST -> 3;
         default -> 0;
      };
      float yaw = (float)Math.toRadians(data2D * 90);
      if (f == Direction.NORTH || f == Direction.SOUTH) {
         yaw += (float) Math.PI;
      }

      return yaw;
   }

   public <C extends BlockEntityController> BlockEntityTicker<C> createBlockEntityTicker(CEWorld world, ImmutableBlockState blockState) {
      return null;
   }

   public void refreshDynamicElement(BiConsumer<ChoppingBoardElement, Player> consumer) {
      TrackedPlayers.forEach(super.blockEntity, trackedPlayer -> consumer.accept(this.element, trackedPlayer));
   }

   public void refreshElementState() {
      this.element.prepareUpdate();
      this.refreshDynamicElement(ChoppingBoardElement::update);
   }

   public boolean isEmpty() {
      return this.currentStage == 0 || this.placedItem.isEmpty();
   }

   public boolean canChop(Item food) {
      return ApplianceFoodRegistry.instance().isAllowed(ApplianceType.CHOPPING_BOARD, food.id());
   }

   public Item placedItem() {
      return this.placedItem;
   }

   public int currentStage() {
      return this.currentStage;
   }

   public String currentStageModel() {
      if (this.isEmpty()) {
         return null;
      }

      ChoppingBoardRecipe recipe = this.recipe();
      if (recipe == null) {
         return null;
      }

      int idx = Math.min(this.currentStage, recipe.stage()) - 1;
      return idx >= 0 && idx < recipe.values().size() ? recipe.values().get(idx) : null;
   }

   private ChoppingBoardRecipe recipe() {
      return this.placedItem.isEmpty() ? null : FoodRecipeRegistry.instance().findChoppingByInput(this.placedItem.id());
   }

   public boolean place(Item food) {
      if (this.isEmpty() && this.canChop(food)) {
         this.placedItem = food.copyWithCount(1);
         this.currentStage = 1;
         this.refreshElementState();
         super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
         return true;
      } else {
         return false;
      }
   }

   public ChoppingBoardController.CutResult cut() {
      if (this.isEmpty()) {
         return ChoppingBoardController.CutResult.NOTHING;
      }

      ChoppingBoardRecipe recipe = this.recipe();
      if (recipe == null) {
         return ChoppingBoardController.CutResult.NOTHING;
      }

      if (this.currentStage < recipe.stage()) {
         this.currentStage++;
         this.refreshElementState();
         super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
         return ChoppingBoardController.CutResult.ADVANCED;
      }

      for (Item result : FoodRecipeRegistry.instance().rollChoppingResults(recipe)) {
         if (!result.isEmpty()) {
            DropUtils.dropAtCenter(super.blockEntity, result);
         }
      }

      this.clearBoard();
      return ChoppingBoardController.CutResult.FINISHED;
   }

   public Item takeBack() {
      if (this.isEmpty()) {
         return Item.empty();
      }

      Item ret = this.placedItem;
      this.clearBoard();
      return ret;
   }

   private void clearBoard() {
      this.placedItem = Item.empty();
      this.currentStage = 0;
      this.refreshElementState();
      super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
   }

   public boolean hasElement() {
      return true;
   }

   public void gatherElements(Consumer<BlockEntityElement> consumer) {
      consumer.accept(this.element);
   }

   public void onRemove() {
      if (!this.placedItem.isEmpty()) {
         DropUtils.dropOnRemove(super.blockEntity, this.placedItem);
      }

      super.onRemove();
   }

   public void loadCustomDataFromItem(Item item) {
      Object nmsItem = item.minecraftItem();
      if (ItemStackUtils.saveMinecraftItemStackAsTag(nmsItem) instanceof CompoundTag compoundTag && compoundTag.containsKey("BlockEntityTag")) {
         this.loadCustomData(compoundTag.getCompound("BlockEntityTag"));
      }
   }

   public void saveCustomData(CompoundTag tag) {
      if (!this.isEmpty()) {
         CompoundTag data = new CompoundTag();
         data.putInt("data_version", VersionHelper.WORLD_VERSION);
         data.putInt("stage", this.currentStage);
         BlockEntityNbt.putItem(data, "item", this.placedItem);
         tag.put("kaleidoscopecookery:chopping_board", data);
      }
   }

   public void loadCustomData(CompoundTag tag) {
      CompoundTag data = tag.getCompound("kaleidoscopecookery:chopping_board");
      if (data != null) {
         this.currentStage = data.getInt("stage", 0);
         this.placedItem = BlockEntityNbt.getItem(data, "item", BlockEntityNbt.dataVersion(data));
         if (this.placedItem.isEmpty()) {
            this.currentStage = 0;
         }

         this.element.refreshPackets();
      }
   }

   public enum CutResult {
      NOTHING,
      ADVANCED,
      FINISHED;
   }
}
