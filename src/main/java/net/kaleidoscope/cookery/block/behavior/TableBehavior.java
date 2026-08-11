package net.kaleidoscope.cookery.block.behavior;

import net.kaleidoscope.cookery.block.entity.TableController;
import net.kaleidoscope.cookery.item.CarpetColors;
import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.DirectionUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.EntityBlock;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.property.IntegerProperty;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.Direction.Axis;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.BlockPlaceContext;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import net.momirealms.craftengine.proxy.minecraft.core.BlockPosProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.BlockGetterProxy;

public final class TableBehavior extends BukkitBlockBehavior implements EntityBlock {
   public static final BlockBehaviorFactory<TableBehavior> FACTORY = new TableBehavior.Factory();
   public static final int LINE_X = 0;
   public static final int LINE_Z = 1;
   private static final int SINGLE = 0;
   private static final int LEFT = 1;
   private static final int MIDDLE = 2;
   private static final int RIGHT = 3;
   private static final Key TAKE_SOUND = Key.of("minecraft:entity.item_frame.remove_item");
   private static final Key CARPET_SOUND = Key.of("minecraft:block.wool.place");
   private final IntegerProperty lineProperty;
   private final IntegerProperty positionProperty;
   private int controllerId;

   private TableBehavior(BlockDefinition block, IntegerProperty lineProperty, IntegerProperty positionProperty) {
      super(block);
      this.lineProperty = lineProperty;
      this.positionProperty = positionProperty;
   }

   public int line(ImmutableBlockState state) {
      return BlockStates.value(state, this.lineProperty, (Integer)this.lineProperty.defaultValue());
   }

   public int position(ImmutableBlockState state) {
      return BlockStates.value(state, this.positionProperty, (Integer)this.positionProperty.defaultValue());
   }

   public void initControllerId(int id) {
      this.controllerId = id;
   }

   public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
      return new TableController(blockEntity, this);
   }

   public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
      Player player = context.getPlayer();
      if (player != null && context.getHand() == InteractionHand.MAIN_HAND) {
         World world = context.getLevel();
         BlockPos pos = context.getClickedPos();
         if (!InteractGuard.canInteract(player, world, pos)) {
            return InteractionResult.PASS;
         }

         BlockEntity blockEntity = world.storageWorld().getBlockEntityAtIfLoaded(pos);
         if (blockEntity == null) {
            return InteractionResult.PASS;
         }

         Item inHand = player.getItemInHand(InteractionHand.MAIN_HAND);
         return blockEntity.controller.let(TableController.class, this.controllerId, controller -> {
            return this.interact(player, world, pos, controller, inHand);
         });
      } else {
         return InteractionResult.PASS;
      }
   }

   private InteractionResult interact(Player player, World world, BlockPos pos, TableController controller, Item inHand) {
      if (ItemUtils.isEmpty(inHand)) {
         return this.give(player, world, pos, controller.takeCarpet(), TAKE_SOUND);
      } else {
         return !CarpetColors.isCarpet(inHand.id()) ? InteractionResult.PASS : this.putCarpet(player, world, pos, controller, inHand);
      }
   }

   private InteractionResult putCarpet(Player player, World world, BlockPos pos, TableController controller, Item inHand) {
      Key id = inHand.id();
      if (id.equals(controller.carpet())) {
         return InteractionResult.PASS;
      }

      Item previous = controller.putCarpet(id);
      InventoryUtils.shrinkHeld(player, inHand, 1);
      InventoryUtils.giveOrHold(player, InteractionHand.MAIN_HAND, previous);
      return this.success(player, world, pos, CARPET_SOUND);
   }

   private InteractionResult give(Player player, World world, BlockPos pos, Item taken, Key sound) {
      if (ItemUtils.isEmpty(taken)) {
         return InteractionResult.PASS;
      }

      InventoryUtils.giveOrHold(player, InteractionHand.MAIN_HAND, taken);
      return this.success(player, world, pos, sound);
   }

   private InteractionResult success(Player player, World world, BlockPos pos, Key sound) {
      world.playBlockSound(Vec3d.atCenterOf(pos), sound, 1.0F, 1.0F);
      player.swingHand(InteractionHand.MAIN_HAND);
      return InteractionResult.SUCCESS_AND_CANCEL;
   }

   public ImmutableBlockState updateStateForPlacement(BlockPlaceContext context, ImmutableBlockState state) {
      Object level = context.getLevel().minecraftWorld();
      Object pos = LocationUtils.toBlockPos(context.getClickedPos());
      return context.getHorizontalDirection().axis() == Axis.X ? this.checkNorthSouth(level, pos, state) : this.checkEastWest(level, pos, state);
   }

   public Object updateShape(Object thisBlock, Object[] args) {
      ImmutableBlockState state = (ImmutableBlockState)BlockStateUtils.getOptionalCustomBlockState(args[0]).orElse(null);
      if (state != null && state.owner().value() == super.blockDefinition) {
         Object level = args[updateShape$level];
         Object pos = args[updateShape$blockPos];
         Axis axis = DirectionUtils.fromNMSDirection(args[updateShape$direction]).axis();
         if (axis == Axis.X) {
            return this.checkEastWest(level, pos, state).customBlockState().minecraftState();
         } else {
            return axis == Axis.Z ? this.checkNorthSouth(level, pos, state).customBlockState().minecraftState() : args[0];
         }
      } else {
         return args[0];
      }
   }

   private ImmutableBlockState checkEastWest(Object level, Object pos, ImmutableBlockState base) {
      if (this.line(base) == 1 && this.position(base) != 0) {
         return base;
      }

      boolean east = this.canLink(level, BlockPosProxy.INSTANCE.offset(pos, 1, 0, 0), 1);
      boolean west = this.canLink(level, BlockPosProxy.INSTANCE.offset(pos, -1, 0, 0), 1);
      return this.joined(base, 0, east, west);
   }

   private ImmutableBlockState checkNorthSouth(Object level, Object pos, ImmutableBlockState base) {
      if (this.line(base) == 0 && this.position(base) != 0) {
         return base;
      }

      boolean south = this.canLink(level, BlockPosProxy.INSTANCE.offset(pos, 0, 0, 1), 0);
      boolean north = this.canLink(level, BlockPosProxy.INSTANCE.offset(pos, 0, 0, -1), 0);
      return this.joined(base, 1, south, north);
   }

   private ImmutableBlockState joined(ImmutableBlockState base, int line, boolean forward, boolean backward) {
      if (!forward && !backward) {
         return base.with(this.positionProperty, 0);
      }

      int position = forward && backward ? 2 : (forward ? 1 : 3);
      return base.with(this.positionProperty, position).with(this.lineProperty, line);
   }

   private boolean canLink(Object level, Object pos, int line) {
      ImmutableBlockState state = (ImmutableBlockState)BlockStateUtils.getOptionalCustomBlockState(BlockGetterProxy.INSTANCE.getBlockState(level, pos))
         .orElse(null);
      return state != null && state.owner().value() == super.blockDefinition ? this.line(state) != line || this.position(state) == 0 : false;
   }

   private static class Factory implements BlockBehaviorFactory<TableBehavior> {
      public TableBehavior create(BlockDefinition block, ConfigSection section) {
         return new TableBehavior(
            block,
            (IntegerProperty)BlockBehaviorFactory.getProperty(section.path(), block, "line", Integer.class),
            (IntegerProperty)BlockBehaviorFactory.getProperty(section.path(), block, "position", Integer.class)
         );
      }
   }
}
