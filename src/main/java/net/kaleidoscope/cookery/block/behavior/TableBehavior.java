package net.kaleidoscope.cookery.block.behavior;

import net.kaleidoscope.cookery.block.entity.TableController;
import net.kaleidoscope.cookery.item.CarpetColors;
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
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.BlockPlaceContext;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import net.momirealms.craftengine.proxy.minecraft.core.BlockPosProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.BlockGetterProxy;

// 桌子沿一个水平轴自由拼接
public final class TableBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<TableBehavior> FACTORY = new Factory();

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
        return state.get(this.lineProperty);
    }

    public int position(ImmutableBlockState state) {
        return state.get(this.positionProperty);
    }

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new TableController(blockEntity, this);
    }

    // 手持地毯铺或换色 空手取桌布 其余一律放行
    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        if (player == null || context.getHand() != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
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
            return interact(player, world, pos, controller, inHand);
        });
    }

    // 桌面只认地毯 别的东西一律放行 让它按自己的规则摆到桌上
    private InteractionResult interact(Player player, World world, BlockPos pos, TableController controller, Item inHand) {
        if (ItemUtils.isEmpty(inHand)) {
            return give(player, world, pos, controller.takeCarpet(), TAKE_SOUND);
        }
        if (!CarpetColors.isCarpet(inHand.id())) {
            return InteractionResult.PASS;
        }
        return putCarpet(player, world, pos, controller, inHand);
    }

    private InteractionResult putCarpet(Player player, World world, BlockPos pos, TableController controller, Item inHand) {
        Key id = inHand.id();
        if (id.equals(controller.carpet())) {
            return InteractionResult.PASS;
        }
        Item previous = controller.putCarpet(id);
        InventoryUtils.shrinkHeld(player, inHand, 1);
        InventoryUtils.giveOrHold(player, InteractionHand.MAIN_HAND, previous);
        return success(player, world, pos, CARPET_SOUND);
    }

    // 取物类方法返回空物品表示没东西可取 此时不挥手也不拦原版交互
    private InteractionResult give(Player player, World world, BlockPos pos, Item taken, Key sound) {
        if (ItemUtils.isEmpty(taken)) {
            return InteractionResult.PASS;
        }
        InventoryUtils.giveOrHold(player, InteractionHand.MAIN_HAND, taken);
        return success(player, world, pos, sound);
    }

    private InteractionResult success(Player player, World world, BlockPos pos, Key sound) {
        world.playBlockSound(Vec3d.atCenterOf(pos), sound, 1.0f, 1.0f);
        player.swingHand(InteractionHand.MAIN_HAND);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 放置时定轴 玩家朝东西就沿南北接
    @Override
    public ImmutableBlockState updateStateForPlacement(BlockPlaceContext context, ImmutableBlockState state) {
        Object level = context.getLevel().minecraftWorld();
        Object pos = LocationUtils.toBlockPos(context.getClickedPos());
        return context.getHorizontalDirection().axis() == Direction.Axis.X
                ? checkNorthSouth(level, pos, state)
                : checkEastWest(level, pos, state);
    }

    // 邻居变化时重算自己
    @Override
    public Object updateShape(Object thisBlock, Object[] args) {
        ImmutableBlockState state = BlockStateUtils.getOptionalCustomBlockState(args[0]).orElse(null);
        if (state == null || state.owner().value() != super.blockDefinition) {
            return args[0];
        }
        Object level = args[updateShape$level];
        Object pos = args[updateShape$blockPos];
        Direction.Axis axis = DirectionUtils.fromNMSDirection(args[updateShape$direction]).axis();
        if (axis == Direction.Axis.X) {
            return checkEastWest(level, pos, state).customBlockState().minecraftState();
        }
        if (axis == Direction.Axis.Z) {
            return checkNorthSouth(level, pos, state).customBlockState().minecraftState();
        }
        return args[0];
    }

    private ImmutableBlockState checkEastWest(Object level, Object pos, ImmutableBlockState base) {
        if (line(base) == LINE_Z && position(base) != SINGLE) {
            return base;
        }
        boolean east = canLink(level, BlockPosProxy.INSTANCE.offset(pos, 1, 0, 0), LINE_Z);
        boolean west = canLink(level, BlockPosProxy.INSTANCE.offset(pos, -1, 0, 0), LINE_Z);
        return joined(base, LINE_X, east, west);
    }

    private ImmutableBlockState checkNorthSouth(Object level, Object pos, ImmutableBlockState base) {
        if (line(base) == LINE_X && position(base) != SINGLE) {
            return base;
        }
        boolean south = canLink(level, BlockPosProxy.INSTANCE.offset(pos, 0, 0, 1), LINE_X);
        boolean north = canLink(level, BlockPosProxy.INSTANCE.offset(pos, 0, 0, -1), LINE_X);
        return joined(base, LINE_Z, south, north);
    }

    private ImmutableBlockState joined(ImmutableBlockState base, int line, boolean forward, boolean backward) {
        if (!forward && !backward) {
            return base.with(this.positionProperty, SINGLE);
        }
        int position = forward && backward ? MIDDLE : forward ? LEFT : RIGHT;
        return base.with(this.positionProperty, position).with(this.lineProperty, line);
    }

    private boolean canLink(Object level, Object pos, int line) {
        ImmutableBlockState state = BlockStateUtils.getOptionalCustomBlockState(
                BlockGetterProxy.INSTANCE.getBlockState(level, pos)).orElse(null);
        if (state == null || state.owner().value() != super.blockDefinition) {
            return false;
        }
        return line(state) != line || position(state) == SINGLE;
    }

    private static class Factory implements BlockBehaviorFactory<TableBehavior> {
        @Override
        public TableBehavior create(BlockDefinition block, ConfigSection section) {
            return new TableBehavior(block,
                    (IntegerProperty) BlockBehaviorFactory.getProperty(section.path(), block, "line", Integer.class),
                    (IntegerProperty) BlockBehaviorFactory.getProperty(section.path(), block, "position", Integer.class));
        }
    }
}
