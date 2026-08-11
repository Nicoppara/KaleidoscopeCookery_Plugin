package net.kaleidoscope.cookery.block.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.kaleidoscope.cookery.block.behavior.FruitBasketBehavior;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.entity.cat.FruitBasketCatGoal;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.util.ChunkIndex;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.item.DataComponentTypes;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.ListTag;
import net.momirealms.craftengine.libraries.nbt.Tag;
import net.momirealms.craftengine.proxy.minecraft.world.item.ItemStackProxy;
import net.momirealms.craftengine.proxy.minecraft.world.item.component.ItemContainerContentsProxy;
import org.bukkit.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class FruitBasketController extends BlockEntityController {
   private static final int SLOTS = 8;
   private static final String DATA_KEY = "kaleidoscopecookery:fruit_basket";
   private static final String K_DATA_VERSION = "data_version";
   private static final String K_ITEMS = "items";
   private static final String K_SLOT = "slot";
   private static final String K_ITEM = "item";
   private final FruitBasketBehavior behavior;
   private final Item[] items = new Item[8];
   private final Item[] lastItems = new Item[8];
   private final FruitBasketElement element;
   private WorldPosition[] positions;
   private boolean positionsInitialized;
   private boolean creativeBreak;
   private static final ChunkIndex<FruitBasketController> INDEX = new ChunkIndex<>();
   public static final int SEARCH_RADIUS = 6;

   public void markCreativeBreak() {
      this.creativeBreak = true;
   }

   public FruitBasketController(BlockEntity blockEntity, FruitBasketBehavior behavior) {
      super(blockEntity);
      this.behavior = behavior;
      Arrays.fill(this.items, Item.empty());
      Arrays.fill(this.lastItems, Item.empty());
      this.element = new FruitBasketElement(this);
   }

   public boolean hasElement() {
      return true;
   }

   public void gatherElements(Consumer<BlockEntityElement> consumer) {
      consumer.accept(this.element);
   }

   Item[] slotItems() {
      return this.items;
   }

   Item[] slotLastItems() {
      return this.lastItems;
   }

   public static void forEachNear(World world, int blockX, int blockZ, Predicate<FruitBasketController> action) {
      INDEX.forEach(world, blockX, blockZ, controller -> controller.blockEntity().isValid() && action.test(controller));
   }

   void unregisterFromIndex() {
      INDEX.unregister(this);
      this.positionsInitialized = false;
   }

   public static void clearIndex() {
      INDEX.clear();
   }

   public void ensurePositionsInitialized() {
      if (!this.positionsInitialized && super.blockEntity.world != null) {
         INDEX.register(this, (World)super.blockEntity.world.world().platformWorld(), super.blockEntity.pos.x, super.blockEntity.pos.z, 6);
         Direction facing = BlockStates.value(super.blockEntity.blockState, this.behavior.getFacingProperty(), Direction.SOUTH);
         int rotation = facing.data2d() * 90;
         Quaternionf facingRot = new Quaternionf().rotateY((float)Math.toRadians(-rotation));
         WorldPosition[] p = new WorldPosition[8];

         for (int i = 0; i < 8; i++) {
            int row = i / 4;
            int col = i % 4;
            float localX = -0.4F + 0.15F * (col + 1);
            float localZ = -0.15F + 0.32F * row + (i % 2 == 0 ? -0.01F : 0.01F);
            Vector3f v = facingRot.transform(new Vector3f(localX, 0.3F, localZ));
            p[i] = new WorldPosition(
               super.blockEntity.world.world,
               (float)(super.blockEntity.pos.x + 0.5 + v.x),
               super.blockEntity.pos.y + v.y,
               (float)(super.blockEntity.pos.z + 0.5 + v.z)
            );
         }

         Quaternionf leftRot = new Quaternionf().rotateY((float)Math.toRadians(-rotation - 90)).rotateX((float)Math.toRadians(30.0));
         this.positions = p;
         this.element.configure(p, leftRot);

         for (int i = 0; i < 8; i++) {
            this.element.refreshItem(i, this.items[i]);
         }

         this.positionsInitialized = true;
      }
   }

   public int putOn(Item held) {
      if (held.isEmpty()) {
         return 0;
      }

      int remaining = held.count();
      int placed = 0;

      for (int i = 0; i < 8 && remaining > 0; i++) {
         if (!this.items[i].isEmpty() && this.items[i].isSimilar(held)) {
            int room = this.items[i].maxStackSize() - this.items[i].count();
            if (room > 0) {
               int move = Math.min(room, remaining);
               this.items[i] = this.items[i].copyWithCount(this.items[i].count() + move);
               remaining -= move;
               placed += move;
            }
         }
      }

      for (int i = 0; i < 8 && remaining > 0; i++) {
         if (this.items[i].isEmpty()) {
            int move = Math.min(held.maxStackSize(), remaining);
            this.items[i] = held.copyWithCount(move);
            remaining -= move;
            placed += move;
         }
      }

      if (placed > 0) {
         this.refresh();
      }

      return placed;
   }

   public Item takeOut() {
      for (int i = 7; i >= 0; i--) {
         if (!this.items[i].isEmpty()) {
            Item taken = this.items[i];
            this.items[i] = Item.empty();
            this.refresh();
            return taken;
         }
      }

      return Item.empty();
   }

   private void refresh() {
      this.ensurePositionsInitialized();

      for (int i = 0; i < 8; i++) {
         if (!this.items[i].isSimilar(this.lastItems[i])) {
            this.element.refreshItem(i, this.items[i]);
            int slot = i;
            if (this.lastItems[i].isEmpty()) {
               TrackedPlayers.forEach(super.blockEntity, p -> this.element.showSlot(p, slot));
            } else if (this.items[i].isEmpty()) {
               TrackedPlayers.forEach(super.blockEntity, p -> this.element.removeSlot(p, slot));
            } else {
               TrackedPlayers.forEach(super.blockEntity, p -> this.element.metaSlot(p, slot));
            }
         }
      }

      if (super.blockEntity.world != null) {
         super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
      }

      System.arraycopy(this.items, 0, this.lastItems, 0, 8);
   }

   public void saveCustomData(CompoundTag tag) {
      CompoundTag data = new CompoundTag();
      data.putInt("data_version", VersionHelper.WORLD_VERSION);
      data.put("items", BlockEntityNbt.saveItems(this.items));
      tag.put("kaleidoscopecookery:fruit_basket", data);
   }

   public void loadCustomData(CompoundTag tag) {
      Arrays.fill(this.items, Item.empty());
      CompoundTag data = tag.getCompound("kaleidoscopecookery:fruit_basket");
      if (data != null) {
         BlockEntityNbt.loadItems(data.getList("items"), BlockEntityNbt.dataVersion(data), this.items);
      }

      for (int i = 0; i < 8; i++) {
         this.element.refreshItem(i, this.items[i]);
      }

      System.arraycopy(this.items, 0, this.lastItems, 0, 8);
   }

   public void loadCustomDataFromItem(Item item) {
      Arrays.fill(this.items, Item.empty());
      if (item.getComponentAsSparrowTag(DataComponentTypes.CONTAINER) instanceof ListTag list) {
         int version = Config.itemDataFixerUpperFallbackVersion();

         for (Tag entry : list) {
            if (entry instanceof CompoundTag c) {
               int slot = c.getInt("slot", -1);
               Tag itemTag = c.get("item");
               if (slot >= 0 && slot < 8 && itemTag != null) {
                  Object nms = ItemStackUtils.parseMinecraftItem(itemTag, version);
                  if (nms != null) {
                     this.items[slot] = ItemStackUtils.wrap(nms);
                  }
               }
            }
         }
      }

      this.ensurePositionsInitialized();

      for (int i = 0; i < 8; i++) {
         this.element.refreshItem(i, this.items[i]);
         if (!this.items[i].isEmpty()) {
            int slot = i;
            TrackedPlayers.forEach(super.blockEntity, p -> this.element.showSlot(p, slot));
         }
      }

      System.arraycopy(this.items, 0, this.lastItems, 0, 8);
   }

   public void onRemove() {
      INDEX.unregister(this);
      if (super.blockEntity.world != null) {
         FruitBasketCatGoal.releaseClaim(super.blockEntity.world.world().uuid(), super.blockEntity.pos.x, super.blockEntity.pos.y, super.blockEntity.pos.z);
      }

      if (this.creativeBreak) {
         for (Item item : this.items) {
            if (!item.isEmpty()) {
               DropUtils.dropOnRemove(super.blockEntity, item);
            }
         }
      } else {
         Key key = ((BlockDefinition)super.blockEntity.blockState.owner().value()).id();
         Item basket = InventoryUtils.createOrEmpty(key);
         if (!ItemUtils.isEmpty(basket)) {
            List<Object> nmsItems = new ArrayList<>(8);

            for (Item item : this.items) {
               nmsItems.add(item.isEmpty() ? ItemStackProxy.EMPTY : item.minecraftItem());
            }

            basket.setExactComponent(DataComponentTypes.CONTAINER, ItemContainerContentsProxy.INSTANCE.fromItems(nmsItems));
            DropUtils.dropOnRemove(super.blockEntity, basket);
         }
      }

      Arrays.fill(this.items, Item.empty());
   }
}
