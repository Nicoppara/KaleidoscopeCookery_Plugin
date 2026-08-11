package net.kaleidoscope.cookery.block.entity;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import net.kaleidoscope.cookery.block.behavior.TeapotBehavior;
import net.kaleidoscope.cookery.block.entity.render.PacketBundles;
import net.kaleidoscope.cookery.block.entity.render.Particles;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.TeapotLiquid;
import net.kaleidoscope.cookery.recipe.TeapotRecipe;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.util.HeatSourceUtils;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.util.Localization;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
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
import net.momirealms.craftengine.core.util.AdventureHelper;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.libraries.adventure.text.TextComponent;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.Tag;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class TeapotController extends BlockEntityController {
   public static final int PUT_INGREDIENT = 0;
   public static final int PROCESSING = 1;
   public static final int FINISHED = 2;
   private static final String DATA_KEY = "kaleidoscopecookery:teapot";
   private static final String K_STATUS = "status";
   private static final String K_CURRENT_TICK = "current_tick";
   private static final String K_FLUID = "fluid";
   private static final String K_INPUT = "input";
   private static final String K_RESULT = "result";
   private static final String K_SERVINGS = "servings";
   private static final int INGREDIENT_TIME = 200;
   private static final int CHECK_INTERVAL = 23;
   private static final int HEAT_CHECK_INTERVAL = 20;
   private static final int FINISH_INTERVAL = 11;
   private static final int ANIM_INTERVAL = 3;
   private static final float LID_RISE = 0.5F;
   private static final float BODY_RISE = 0.25F;
   private static final Key CRACKLE = Key.of("minecraft:block.fire.extinguish");
   private static final Key BUCKET_EMPTY = Key.of("minecraft:item.bucket.empty");
   private static final Key BUCKET_EMPTY_LAVA = Key.of("minecraft:item.bucket.empty_lava");
   private static final Key[] TEAPOT_SOUNDS = new Key[]{
      Key.of("kaleidoscopecookery:teapot_0"),
      Key.of("kaleidoscopecookery:teapot_1"),
      Key.of("kaleidoscopecookery:teapot_2"),
      Key.of("kaleidoscopecookery:teapot_3"),
      Key.of("kaleidoscopecookery:teapot_4")
   };
   private final TeapotBehavior behavior;
   private final TeapotElement element;
   private int status = 0;
   private Key fluid;
   private Item input = Item.empty();
   private Item result = Item.empty();
   private int servings;
   private int currentTick = -1;
   private boolean boilFlip;
   private int animTick;
   private boolean heatedCache;
   private int heatCheckTick;
   private boolean textShown;
   private boolean creativeBreak;
   private boolean pickedUp;

   public void markCreativeBreak() {
      this.creativeBreak = true;
   }

   public TeapotController(BlockEntity blockEntity, TeapotBehavior behavior) {
      super(blockEntity);
      this.behavior = behavior;
      this.element = new TeapotElement(this);
   }

   public BlockPos getPos() {
      return this.blockEntity.pos;
   }

   public CEWorld getWorld() {
      return this.blockEntity.world;
   }

   public float facingYaw() {
      Property<Direction> facingProperty = this.behavior.getFacingProperty();
      Direction d = BlockStates.value(this.blockEntity.blockState, facingProperty, facingProperty.defaultValue());

      return switch (d) {
         case SOUTH -> 0.0F;
         case EAST -> 90.0F;
         case WEST -> -90.0F;
         default -> 180.0F;
      };
   }

   public int getStatus() {
      return this.status;
   }

   public boolean addFluidBucket(Player player, InteractionHand hand, Item bucket, Key fluidType) {
      if (this.status == 0 && this.fluid == null) {
         this.fluid = fluidType;
         this.playSound(fluidType.equals(ItemKeys.LAVA) ? BUCKET_EMPTY_LAVA : BUCKET_EMPTY, 1.0F);
         if (!player.canInstabuild()) {
            InventoryUtils.shrinkHeld(player, bucket, 1);
            InventoryUtils.giveOrHold(player, hand, InventoryUtils.createOrEmpty(ItemKeys.BUCKET));
         }

         this.markChanged();
         this.refreshDisplay();
         this.sendBar(player);
         return true;
      } else {
         return false;
      }
   }

   public boolean drainToBucket(Player player, InteractionHand hand, Item emptyBucket) {
      if (this.status == 0 && this.fluid != null && this.input.isEmpty()) {
         Key back;
         if (this.fluid.equals(ItemKeys.WATER)) {
            back = ItemKeys.WATER_BUCKET;
         } else {
            if (!this.fluid.equals(ItemKeys.LAVA)) {
               return false;
            }

            back = ItemKeys.LAVA_BUCKET;
         }

         this.fluid = null;
         this.currentTick = -1;
         if (!player.canInstabuild()) {
            InventoryUtils.shrinkHeld(player, emptyBucket, 1);
            InventoryUtils.giveOrHold(player, hand, InventoryUtils.createOrEmpty(back));
         }

         this.markChanged();
         this.refreshDisplay();
         this.sendBar(player);
         return true;
      } else {
         return false;
      }
   }

   public boolean addIngredient(Player player, Item held) {
      if (this.status == 0 && this.fluid != null && this.input.isEmpty()) {
         TeapotRecipe recipe = FoodRecipeRegistry.instance().findTeapot(this.fluid, held.id());
         if (recipe == null) {
            return false;
         }

         int added = Math.min(held.count(), Math.max(1, recipe.ingredientCount()));
         this.input = held.copyWithCount(added);
         this.currentTick = 200;
         InventoryUtils.shrinkHeld(player, held, added);
         this.markChanged();
         this.refreshDisplay();
         return true;
      } else {
         return false;
      }
   }

   public boolean removeIngredient(Player player, InteractionHand hand) {
      if (this.status == 0 && !this.input.isEmpty()) {
         InventoryUtils.giveOrHold(player, hand, this.input.copy());
         this.input = Item.empty();
         this.currentTick = -1;
         this.markChanged();
         this.refreshDisplay();
         return true;
      } else {
         return false;
      }
   }

   public boolean takeTeapot(Player player, InteractionHand hand) {
      if (this.status == 1) {
         return false;
      }

      Item teapot = this.buildDroppedTeapot();
      if (ItemUtils.isEmpty(teapot)) {
         return false;
      }

      if (!this.input.isEmpty()) {
         DropUtils.dropAtCenter(this.blockEntity, this.input);
         this.input = Item.empty();
      }

      this.pickedUp = true;
      InventoryUtils.giveOrHold(player, hand, teapot);
      this.removeBlock();
      return true;
   }

   private void removeBlock() {
      World world = (World)this.blockEntity.world.world().platformWorld();
      Block block = world.getBlockAt(this.blockEntity.pos.x(), this.blockEntity.pos.y(), this.blockEntity.pos.z());
      CraftEngineBlocks.remove(block);
   }

   public <C extends BlockEntityController> BlockEntityTicker<C> createBlockEntityTicker(CEWorld world, ImmutableBlockState blockState) {
      return createTickerHelper((w, pos, state, controller) -> this.tick());
   }

   private void tick() {
      long gameTime = ((World)this.blockEntity.world.world().platformWorld()).getGameTime();
      if (this.heatCheckTick == 0) {
         this.heatedCache = this.heated();
      }

      this.heatCheckTick = (this.heatCheckTick + 1) % 20;
      boolean heated = this.heatedCache;
      if (heated && this.fluid != null && gameTime % this.behavior.particleInterval == 0L) {
         this.emitSteam(this.behavior.particleCount);
      }

      if (this.status != 0 && this.status != 1) {
         if (this.status == 2) {
            this.driveBoiling(heated);
            if (heated && Math.floorMod(gameTime + this.posStagger(), 11) == 0) {
               this.onBoilingEffects();
            }
         }
      } else if (Math.floorMod(gameTime + this.posStagger(), 23) == 0) {
         if (this.fluid != null && heated) {
            this.onProcessingEffects();
            if (this.status == 0) {
               this.tickPutIngredient();
            } else {
               this.tickProcessing();
            }
         }
      }
   }

   private void tickPutIngredient() {
      if (!this.input.isEmpty()) {
         if (this.currentTick > 0) {
            this.currentTick = Math.max(-1, this.currentTick - 23);
            this.markChanged();
         } else {
            TeapotRecipe recipe = FoodRecipeRegistry.instance().findTeapot(this.fluid, this.input.id());
            if (recipe != null) {
               this.result = this.makeResult(recipe);
               this.servings = servingsFor(this.input.count(), recipe.ingredientCount());
               this.currentTick = recipe.time();
               this.status = 1;
               this.markChanged();
               this.refreshDisplay();
            } else {
               DropUtils.dropAtCenter(this.blockEntity, this.input);
               this.input = Item.empty();
               this.result = Item.empty();
               this.currentTick = -1;
               this.markChanged();
            }
         }
      }
   }

   private void tickProcessing() {
      if (this.currentTick > 0) {
         this.currentTick = Math.max(-1, this.currentTick - 23);
         this.markChanged();
      } else {
         this.status = 2;
         this.currentTick = -1;
         this.input = Item.empty();
         this.markChanged();
         this.refreshDisplay();
      }
   }

   private void driveBoiling(boolean heated) {
      if (!heated) {
         if (this.animTick != 0) {
            this.animTick = 0;
            this.broadcastAll(this.element.lidBounceMeta(0.0F, 3));
            this.broadcastAll(this.element.bodyBounceMeta(0.0F, 3));
         }
      } else if (++this.animTick % 3 == 0) {
         this.boilFlip = !this.boilFlip;
         Object bundle = PacketBundles.of(
            List.of(this.element.lidBounceMeta(this.boilFlip ? 0.5F : 0.0F, 3), this.element.bodyBounceMeta(this.boilFlip ? 0.0F : 0.25F, 3))
         );
         this.broadcast(bundle);
      }
   }

   private void broadcast(Object packet) {
      for (Player p : TrackedPlayers.snapshotInRange(this.blockEntity, this.behavior.animChunkRadius)) {
         p.sendPacket(packet, false);
      }
   }

   private void broadcastAll(Object packet) {
      TrackedPlayers.forEach(this.blockEntity, p -> p.sendPacket(packet, false));
   }

   private Item makeResult(TeapotRecipe recipe) {
      Item item = InventoryUtils.createOrEmpty(recipe.result());
      return item == null ? Item.empty() : item.copyWithCount(Math.max(1, recipe.resultCount()));
   }

   private static int servingsFor(int added, int required) {
      return required <= 0 ? 8 : Math.max(1, 8 * added / required);
   }

   private boolean heated() {
      Object level = this.blockEntity.world.world().minecraftWorld();
      Object belowPos = LocationUtils.below(LocationUtils.toBlockPos(this.blockEntity.pos));
      return HeatSourceUtils.isHeatSource(level, belowPos);
   }

   private int posStagger() {
      return this.blockEntity.pos.x() * 31 + this.blockEntity.pos.z();
   }

   private void markChanged() {
      this.blockEntity.world.blockEntityChanged(this.blockEntity.pos);
   }

   private void onProcessingEffects() {
      this.playSound(TEAPOT_SOUNDS[ThreadLocalRandom.current().nextInt(TEAPOT_SOUNDS.length)], 0.6F);
   }

   private void onBoilingEffects() {
      this.playSound(CRACKLE, 0.4F);
   }

   private void playSound(Key sound, float volume) {
      float pitch = 0.8F + ThreadLocalRandom.current().nextFloat() * 0.2F;
      this.blockEntity.world.world().playSound(Vec3d.atCenterOf(this.blockEntity.pos), sound, volume, pitch, SoundSource.BLOCK);
   }

   private void emitSteam(int count) {
      ThreadLocalRandom r = ThreadLocalRandom.current();
      double x = this.blockEntity.pos.x() + 0.5 + (r.nextDouble() - 0.5) * 0.4;
      double y = this.blockEntity.pos.y() + 0.9 + r.nextDouble() / 3.0;
      double z = this.blockEntity.pos.z() + 0.5 + (r.nextDouble() - 0.5) * 0.4;
      Particles.emit(this.blockEntity.world, Particle.CLOUD, x, y, z, count, 0.05, 0.05, 0.05, 0.02, null);
   }

   private void sendBar(Player player) {
      player.sendActionBar(AdventureHelper.miniMessage().deserialize(this.buildBar()));
   }

   private String buildBar() {
      return this.status == 2 && !this.result.isEmpty() ? TeapotBar.build(this.fluid, this.servings) : TeapotBar.build(this.fluid);
   }

   private String statusMsg() {
      return switch (this.status) {
         case 1 -> "kaleidoscopecookery.message.teapot.processing";
         case 2 -> "kaleidoscopecookery.message.teapot.finished";
         default -> "kaleidoscopecookery.message.teapot.put";
      };
   }

   private Component statusComponent() {
      Component head = Localization.component(this.statusMsg());
      if (this.status == 2) {
         return head.append(Component.newline()).append(this.itemComponent(this.result, this.servings));
      }

      Component line = this.fluidComponent();
      if (!this.input.isEmpty()) {
         line = line.append(Component.text(" ")).append(this.itemComponent(this.input, this.input.count()));
      }

      return head.append(Component.newline()).append(line);
   }

   private Component fluidComponent() {
      if (this.fluid == null) {
         return Localization.component("kaleidoscopecookery.message.teapot.empty");
      }

      TeapotLiquid liquid = FoodRecipeRegistry.instance().getTeapotLiquid(this.fluid);
      return (Component)(liquid != null && liquid.displayName() != null && !liquid.displayName().isEmpty()
         ? Localization.component(liquid.displayName())
         : Component.text(this.fluid.value()));
   }

   private Component itemComponent(Item item, int count) {
      if (item.isEmpty()) {
         return Localization.component("kaleidoscopecookery.message.teapot.none");
      }

      Component name = (Component)item.hoverNameComponent().orElse(Component.text(item.id().value()));
      return ((TextComponent)Component.empty().append(name)).append(Component.text(" x" + count));
   }

   private void initDisplay() {
      boolean visible = this.fluid != null || this.status != 0;
      this.element.setText((Component)(visible ? this.statusComponent() : Component.empty()), visible);
      this.textShown = visible;
   }

   private void refreshDisplay() {
      boolean visible = this.fluid != null || this.status != 0;
      Object data = this.element.setText((Component)(visible ? this.statusComponent() : Component.empty()), visible);
      if (this.blockEntity.world == null) {
         this.textShown = visible;
      } else {
         if (visible) {
            if (!this.textShown) {
               this.broadcastAll(this.element.textSpawnPacket());
               this.textShown = true;
            }

            this.broadcastAll(data);
         } else if (this.textShown) {
            this.broadcastAll(this.element.textRemovePacket());
            this.textShown = false;
         }
      }
   }

   public boolean hasElement() {
      return true;
   }

   public void gatherElements(Consumer<BlockEntityElement> consumer) {
      consumer.accept(this.element);
   }

   public void onRemove() {
      if (!this.pickedUp) {
         if (!this.input.isEmpty()) {
            DropUtils.dropOnRemove(this.blockEntity, this.input);
         }

         if (!this.creativeBreak) {
            Item teapot = this.buildDroppedTeapot();
            if (!ItemUtils.isEmpty(teapot)) {
               DropUtils.dropOnRemove(this.blockEntity, teapot);
            }
         }
      }
   }

   private Item buildDroppedTeapot() {
      Key blockId = ((BlockDefinition)this.blockEntity.blockState.owner().value()).id();
      Item teapot = InventoryUtils.createOrEmpty(blockId);
      if (ItemUtils.isEmpty(teapot)) {
         return teapot;
      }

      CompoundTag data = new CompoundTag();
      if (this.status != 1 && this.fluid != null) {
         data.putString("fluid", this.fluid.asString());
      }

      String barStr;
      if (this.status == 2 && !this.result.isEmpty()) {
         data.putInt("status", 2);
         BlockEntityNbt.putItem(data, "result", this.result);
         data.putInt("servings", this.servings);
         barStr = TeapotBar.build(this.fluid, this.servings);
      } else if (this.status != 1 && this.fluid != null) {
         barStr = TeapotBar.build(this.fluid);
      } else {
         barStr = TeapotBar.build(null);
      }

      teapot.setSparrowTag(data, new Object[]{"kaleidoscopecookery:teapot_data"});
      teapot.loreJson(List.of(AdventureHelper.componentToJson(AdventureHelper.miniMessage().deserialize("<!i>" + barStr))));
      return teapot;
   }

   public void loadCustomDataFromItem(Item item) {
      if (item.getSparrowTag(new Object[]{"kaleidoscopecookery:teapot_data"}) instanceof CompoundTag data) {
         String fluidStr = data.getString("fluid");
         this.fluid = fluidStr != null && !fluidStr.isEmpty() ? Key.of(fluidStr) : null;
         this.status = data.getInt("status", 0);
         this.servings = data.getInt("servings", 0);
         Tag resultTag = data.get("result");
         if (resultTag != null) {
            Object nms = ItemStackUtils.parseMinecraftItem(resultTag, Config.itemDataFixerUpperFallbackVersion());
            this.result = (Item)(nms == null ? Item.empty() : ItemStackUtils.wrap(nms));
         }
      }

      this.initDisplay();
   }

   public void saveCustomData(CompoundTag tag) {
      CompoundTag data = new CompoundTag();
      data.putInt("status", this.status);
      data.putInt("current_tick", this.currentTick);
      if (this.fluid != null) {
         data.putString("fluid", this.fluid.asString());
      }

      BlockEntityNbt.putItem(data, "input", this.input);
      BlockEntityNbt.putItem(data, "result", this.result);
      data.putInt("servings", this.servings);
      tag.put("kaleidoscopecookery:teapot", data);
   }

   public void loadCustomData(CompoundTag tag) {
      CompoundTag data = tag.getCompound("kaleidoscopecookery:teapot");
      if (data != null) {
         this.status = data.getInt("status", 0);
         this.currentTick = data.getInt("current_tick", -1);
         String fluidStr = data.getString("fluid");
         this.fluid = fluidStr != null && !fluidStr.isEmpty() ? Key.of(fluidStr) : null;
         this.servings = data.getInt("servings", 0);
         int version = Config.itemDataFixerUpperFallbackVersion();
         this.input = BlockEntityNbt.getItem(data, "input", version);
         this.result = BlockEntityNbt.getItem(data, "result", version);
         this.initDisplay();
      }
   }
}
