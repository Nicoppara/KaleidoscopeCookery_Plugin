package net.kaleidoscope.cookery.block.entity;

import java.util.function.Consumer;
import net.kaleidoscope.cookery.block.behavior.KitchenwareRacksBehavior;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.util.DropUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

public final class KitchenwareRacksController extends BlockEntityController {
   private static final String DATA_KEY = "kaleidoscopecookery:kitchenware_racks";
   private static final String K_ITEM_LEFT = "item_left";
   private static final String K_ITEM_RIGHT = "item_right";
   private static final float ITEM_HEIGHT = 0.4375F;
   private static final float SIDE_OFFSET = 0.2F;
   private static final float DEPTH_OFFSET = 0.3F;
   private final KitchenwareRacksBehavior behavior;
   private final KitchenwareRacksElement element = new KitchenwareRacksElement(this);
   @NotNull
   private Item itemLeft = Item.empty();
   @NotNull
   private Item itemRight = Item.empty();
   private WorldPosition leftPosition;
   private WorldPosition rightPosition;
   private boolean positionsInitialized;

   public KitchenwareRacksController(BlockEntity blockEntity, KitchenwareRacksBehavior behavior) {
      super(blockEntity);
      this.behavior = behavior;
   }

   public boolean hasElement() {
      return true;
   }

   public void gatherElements(Consumer<BlockEntityElement> consumer) {
      consumer.accept(this.element);
   }

   @NotNull
   public Item getItemLeft() {
      return this.itemLeft;
   }

   @NotNull
   public Item getItemRight() {
      return this.itemRight;
   }

   public void putLeft(Item item) {
      this.itemLeft = item;
      this.refresh();
   }

   public void putRight(Item item) {
      this.itemRight = item;
      this.refresh();
   }

   public Item takeLeft() {
      Item taken = this.itemLeft;
      this.itemLeft = Item.empty();
      this.refresh();
      return taken;
   }

   public Item takeRight() {
      Item taken = this.itemRight;
      this.itemRight = Item.empty();
      this.refresh();
      return taken;
   }

   void rebuildElement() {
      this.rebuildElement(super.blockEntity.blockState);
   }

   private void rebuildElement(ImmutableBlockState state) {
      if (!this.positionsInitialized && super.blockEntity.world != null) {
         this.leftPosition = this.itemPosition(state, true);
         this.rightPosition = this.itemPosition(state, false);
         this.positionsInitialized = true;
      }

      this.element.rebuild(this.leftPosition, this.rightPosition, this.facingOf(state));
   }

   private void redraw(ImmutableBlockState state) {
      this.rebuildElement(state);
      TrackedPlayers.forEach(super.blockEntity, this.element::update);
   }

   private void refresh() {
      this.redraw(super.blockEntity.blockState);
      super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
   }

   public void preBlockStateChange(ImmutableBlockState newState) {
      if (super.blockEntity.world != null) {
         this.leftPosition = this.itemPosition(newState, true);
         this.rightPosition = this.itemPosition(newState, false);
         this.positionsInitialized = true;
         this.redraw(newState);
      }
   }

   private WorldPosition itemPosition(ImmutableBlockState state, boolean isLeft) {
      float x = (float)(super.blockEntity.pos.x + 0.5);
      float y = super.blockEntity.pos.y + 0.4375F;
      float z = (float)(super.blockEntity.pos.z + 0.5);
      float side = isLeft ? -0.2F : 0.2F;

      return switch (this.facingOf(state)) {
         case SOUTH -> new WorldPosition(super.blockEntity.world.world, x - side, y, z - 0.3F);
         case EAST -> new WorldPosition(super.blockEntity.world.world, x - 0.3F, y, z + side);
         case WEST -> new WorldPosition(super.blockEntity.world.world, x + 0.3F, y, z - side);
         default -> new WorldPosition(super.blockEntity.world.world, x + side, y, z + 0.3F);
      };
   }

   private Direction facingOf(ImmutableBlockState state) {
      return BlockStates.value(state, this.behavior.getFacingProperty(), Direction.NORTH);
   }

   public void saveCustomData(CompoundTag tag) {
      CompoundTag data = BlockEntityNbt.newData();
      BlockEntityNbt.putItem(data, "item_left", this.itemLeft);
      BlockEntityNbt.putItem(data, "item_right", this.itemRight);
      tag.put("kaleidoscopecookery:kitchenware_racks", data);
   }

   public void loadCustomData(CompoundTag tag) {
      this.itemLeft = Item.empty();
      this.itemRight = Item.empty();
      CompoundTag data = tag.getCompound("kaleidoscopecookery:kitchenware_racks");
      if (data != null) {
         int dataVersion = BlockEntityNbt.dataVersion(data);
         this.itemLeft = BlockEntityNbt.getItem(data, "item_left", dataVersion);
         this.itemRight = BlockEntityNbt.getItem(data, "item_right", dataVersion);
      }
   }

   public void onRemove() {
      DropUtils.dropOnRemove(super.blockEntity, this.itemLeft);
      DropUtils.dropOnRemove(super.blockEntity, this.itemRight);
   }
}
