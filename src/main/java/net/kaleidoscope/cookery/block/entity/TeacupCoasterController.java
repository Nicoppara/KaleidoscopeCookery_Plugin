package net.kaleidoscope.cookery.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import net.kaleidoscope.cookery.block.behavior.TeacupCoasterBehavior;
import net.kaleidoscope.cookery.block.entity.render.Particles;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.TeaCup;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.block.entity.tick.BlockEntityTicker;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.ListTag;
import net.momirealms.craftengine.libraries.nbt.Tag;
import org.bukkit.Particle;

public final class TeacupCoasterController extends BlockEntityController {
   public static final int MAX_CUPS = 4;
   private static final String DATA_KEY = "kaleidoscopecookery:teacup_coaster";
   private static final String K_CUPS = "cups";
   private static final String K_ITEM = "item";
   private static final String K_MODEL = "model";
   private static final Key POUR_SOUND = Key.of("minecraft:block.brewing_stand.brew");
   private final TeacupCoasterBehavior behavior;
   private final TeacupCoasterElement element;
   private final List<Item> cupItems = new ArrayList<>(4);
   private final List<Key> cupModels = new ArrayList<>(4);
   private int shownCount;

   public TeacupCoasterController(BlockEntity blockEntity, TeacupCoasterBehavior behavior) {
      super(blockEntity);
      this.behavior = behavior;
      this.element = new TeacupCoasterElement(this);
   }

   public BlockPos getPos() {
      return this.blockEntity.pos;
   }

   public CEWorld getWorld() {
      return this.blockEntity.world;
   }

   public Direction facing() {
      Property<Direction> facingProperty = this.behavior.getFacingProperty();
      return BlockStates.value(this.blockEntity.blockState, facingProperty, facingProperty.defaultValue());
   }

   public float facingYaw() {
      return switch (this.facing()) {
         case SOUTH -> 180.0F;
         case EAST -> 90.0F;
         case WEST -> 270.0F;
         default -> 0.0F;
      };
   }

   public int cupCount() {
      return this.cupItems.size();
   }

   public Key cupModel(int index) {
      return this.cupModels.get(index);
   }

   public double cupYOffset() {
      return this.behavior.cupYOffset;
   }

   public float cupScale() {
      return this.behavior.cupScale;
   }

   public boolean placeCup(Player player, InteractionHand hand, Item held) {
      if (this.cupItems.size() >= 4) {
         return false;
      }

      Key id = held.id();
      Key model;
      if (id.equals(this.behavior.emptyCupItem)) {
         model = this.behavior.emptyCupModel;
      } else {
         TeaCup tc = FoodRecipeRegistry.instance().getTeaCupByItem(id);
         if (tc == null) {
            return false;
         }

         model = FoodRecipeRegistry.instance().pickTeaModel(tc.tea());
      }

      if (model == null) {
         return false;
      }

      this.cupItems.add(held.copyWithCount(1));
      this.cupModels.add(model);
      InventoryUtils.shrinkHeld(player, held, 1);
      this.markChanged();
      this.refreshCups();
      return true;
   }

   public boolean pourInto(Key teaKey) {
      TeaCup tc = FoodRecipeRegistry.instance().getTeaCup(teaKey);
      if (tc == null) {
         return false;
      }

      Key model = FoodRecipeRegistry.instance().pickTeaModel(teaKey);
      Item teaItem = InventoryUtils.createOrEmpty(tc.item());
      if (model != null && !teaItem.isEmpty()) {
         for (int i = 0; i < this.cupItems.size(); i++) {
            if (this.cupItems.get(i).id().equals(this.behavior.emptyCupItem)) {
               this.cupItems.set(i, teaItem);
               this.cupModels.set(i, model);
               this.markChanged();
               this.element.rebuildPackets();
               int idx = i;
               TrackedPlayers.forEach(this.blockEntity, p -> p.sendPacket(this.element.cupMeta(idx), false));
               this.pourEffects();
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private void pourEffects() {
      float pitch = 0.9F + ThreadLocalRandom.current().nextFloat() * 0.2F;
      this.blockEntity.world.world().playSound(Vec3d.atCenterOf(this.blockEntity.pos), POUR_SOUND, 0.6F, pitch, SoundSource.BLOCK);
      Particles.emit(
         this.blockEntity.world,
         Particle.CRIT,
         this.blockEntity.pos.x() + 0.5,
         this.blockEntity.pos.y() + 0.3,
         this.blockEntity.pos.z() + 0.5,
         4,
         0.1,
         0.05,
         0.1,
         0.0,
         null
      );
   }

   public boolean takeCup(Player player, InteractionHand hand) {
      if (this.cupItems.isEmpty()) {
         return false;
      }

      int last = this.cupItems.size() - 1;
      Item removed = this.cupItems.remove(last);
      this.cupModels.remove(last);
      InventoryUtils.give(player, removed);
      this.markChanged();
      this.refreshCups();
      return true;
   }

   private void markChanged() {
      this.blockEntity.world.blockEntityChanged(this.blockEntity.pos);
   }

   private void refreshCups() {
      int oldCount = this.shownCount;
      int newCount = this.cupItems.size();
      this.element.rebuildPackets();
      TrackedPlayers.forEach(this.blockEntity, p -> this.element.broadcastChange(p, oldCount, newCount));
      this.shownCount = newCount;
   }

   public <C extends BlockEntityController> BlockEntityTicker<C> createBlockEntityTicker(CEWorld world, ImmutableBlockState blockState) {
      return null;
   }

   public boolean hasElement() {
      return true;
   }

   public void gatherElements(Consumer<BlockEntityElement> consumer) {
      consumer.accept(this.element);
   }

   public void onRemove() {
      for (Item item : this.cupItems) {
         if (!item.isEmpty()) {
            DropUtils.dropOnRemove(this.blockEntity, item);
         }
      }

      this.cupItems.clear();
      this.cupModels.clear();
      super.onRemove();
   }

   public void saveCustomData(CompoundTag tag) {
      CompoundTag data = new CompoundTag();
      ListTag list = new ListTag();

      for (int i = 0; i < this.cupItems.size(); i++) {
         Tag itemTag = BlockEntityNbt.itemTag(this.cupItems.get(i));
         if (itemTag != null) {
            CompoundTag c = new CompoundTag();
            c.put("item", itemTag);
            c.putString("model", this.cupModels.get(i).asString());
            list.add(c);
         }
      }

      data.put("cups", list);
      tag.put("kaleidoscopecookery:teacup_coaster", data);
   }

   public void loadCustomData(CompoundTag tag) {
      CompoundTag data = tag.getCompound("kaleidoscopecookery:teacup_coaster");
      if (data != null) {
         this.cupItems.clear();
         this.cupModels.clear();
         if (data.containsKey("cups")) {
            int version = Config.itemDataFixerUpperFallbackVersion();

            for (Tag t : data.getList("cups")) {
               if (t instanceof CompoundTag c) {
                  Object nms = ItemStackUtils.parseMinecraftItem(c.get("item"), version);
                  Item item = (Item)(nms == null ? Item.empty() : ItemStackUtils.wrap(nms));
                  String model = c.getString("model");
                  if (!item.isEmpty() && model != null && !model.isEmpty() && this.cupItems.size() < 4) {
                     this.cupItems.add(item);
                     this.cupModels.add(Key.of(model));
                  }
               }
            }
         }

         this.shownCount = this.cupItems.size();
      }
   }
}
