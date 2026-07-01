package net.kaleidoscope.cookery.block.behavior;
import net.kaleidoscope.cookery.block.entity.TeapotController;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;

import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.DirectionUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.bukkit.world.BukkitWorldManager;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.EntityBlock;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.BlockPlaceContext;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import net.momirealms.craftengine.proxy.minecraft.core.DirectionProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.BlockGetterProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.SupportTypeProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.state.BlockBehaviourProxy;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.item.ItemMatch;
import net.kaleidoscope.cookery.util.BehaviorConfig;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.kaleidoscope.cookery.util.SupportStateUtils;
import org.bukkit.GameMode;

import java.lang.reflect.Method;

public final class TeapotBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<TeapotBehavior> FACTORY = new Factory();

    private int controllerId;
    private Property<Direction> facingProperty;
    private Property<Boolean> hasBaseProperty;
    private Property<Boolean> hasChainsProperty;
    public int animChunkRadius = TrackedPlayers.DEFAULT_ANIM_CHUNK_RADIUS;
    public int particleInterval = 20;
    public int particleCount = 3;

    public String msgPut = "准备";
    public String msgProcessing = "熬煮中";
    public String msgFinished = "完成";

    private TeapotBehavior(BlockDefinition blockDefinition) {
        super(blockDefinition);
    }

    // 满液体桶加液体 空桶取液体(无食材) 原料放料 潜行空手取料/释放液体 空手取成品 只认主手
    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        if (player == null || context.getHand() == InteractionHand.OFF_HAND) {
            return InteractionResult.PASS;
        }
        World world = context.getLevel();
        if (!InteractGuard.canInteract(player, world, context.getClickedPos())) {
            return InteractionResult.PASS;
        }
        BlockEntity blockEntity = world.storageWorld().getBlockEntityAtIfLoaded(context.getClickedPos());
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }
        TeapotController controller = blockEntity.controller.get(TeapotController.class, this.controllerId);
        if (controller == null) {
            return InteractionResult.PASS;
        }

        InteractionHand hand = InteractionHand.MAIN_HAND;
        Item held = player.getItemInHand(hand);
        boolean done;
        if (ItemMatch.is(held, ItemKeys.WATER_BUCKET)) {
            done = controller.addFluidBucket(player, hand, held, ItemKeys.WATER);
        } else if (ItemMatch.is(held, ItemKeys.LAVA_BUCKET)) {
            done = controller.addFluidBucket(player, hand, held, ItemKeys.LAVA);
        } else if (ItemMatch.is(held, ItemKeys.BUCKET)) {
            done = controller.drainToBucket(player, hand, held);
        } else if (held.isEmpty()) {
            if (player.isSecondaryUseActive()) {
                done = controller.removeIngredient(player, hand);
            } else {
                done = controller.takeTeapot(player, hand);
            }
        } else {
            done = controller.addIngredient(player, held);
        }

        if (done) {
            player.swingHand(hand);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        return InteractionResult.PASS;
    }

    // 上方有铁链显示链子 否则脚下无支撑显示底座 链子优先 与汤锅一致
    @Override
    public ImmutableBlockState updateStateForPlacement(BlockPlaceContext context, ImmutableBlockState state) {
        Object level = context.getLevel().minecraftWorld();
        Object clickedPos = LocationUtils.toBlockPos(context.getClickedPos());
        Object abovePos = LocationUtils.above(clickedPos);
        Object aboveState = BlockGetterProxy.INSTANCE.getBlockState(level, abovePos);
        boolean shouldHaveChains = canHangChains(level, abovePos, aboveState);
        Object belowPos = LocationUtils.below(clickedPos);
        Object belowState = BlockGetterProxy.INSTANCE.getBlockState(level, belowPos);
        boolean shouldHaveBase = !SupportStateUtils.isSturdyUp(level, belowPos, belowState) && !shouldHaveChains;
        return state.with(this.hasChainsProperty, shouldHaveChains).with(this.hasBaseProperty, shouldHaveBase);
    }

    @Override
    public Object updateShape(Object thisBlock, Object[] args) {
        Object blockState = args[0];
        Object level = args[updateShape$level];
        Object blockPos = args[updateShape$blockPos];
        Object direction = args[updateShape$direction];

        ImmutableBlockState customState = BlockStateUtils.getOptionalCustomBlockState(blockState).orElse(null);
        if (customState == null || customState.isEmpty()) {
            return blockState;
        }

        Direction nmsDirection = DirectionUtils.fromNMSDirection(direction);

        if (nmsDirection == Direction.DOWN) {
            Object belowPos = LocationUtils.below(blockPos);
            Object belowState = BlockGetterProxy.INSTANCE.getBlockState(level, belowPos);
            boolean hasChains = customState.get(this.hasChainsProperty);
            boolean shouldHaveBase = !SupportStateUtils.isSturdyUp(level, belowPos, belowState) && !hasChains;
            if (customState.get(this.hasBaseProperty) != shouldHaveBase) {
                return customState.with(this.hasBaseProperty, shouldHaveBase).customBlockState().minecraftState();
            }
        }

        if (nmsDirection == Direction.UP) {
            Object abovePos = LocationUtils.above(blockPos);
            Object aboveState = BlockGetterProxy.INSTANCE.getBlockState(level, abovePos);
            boolean shouldHaveChains = canHangChains(level, abovePos, aboveState);
            Object belowPos = LocationUtils.below(blockPos);
            Object belowState = BlockGetterProxy.INSTANCE.getBlockState(level, belowPos);
            boolean shouldHaveBase = !SupportStateUtils.isSturdyUp(level, belowPos, belowState) && !shouldHaveChains;
            if (customState.get(this.hasChainsProperty) != shouldHaveChains
                    || customState.get(this.hasBaseProperty) != shouldHaveBase) {
                return customState
                        .with(this.hasChainsProperty, shouldHaveChains)
                        .with(this.hasBaseProperty, shouldHaveBase)
                        .customBlockState().minecraftState();
            }
        }
        return blockState;
    }

    // 创造破坏时标记 让 onRemove 不掉方块本体
    @Override
    public Object playerWillDestroy(Object thisBlock, Object[] args) {
        Object nmsPlayer = args.length > 3 ? args[3] : null;
        if (isCreativePlayer(nmsPlayer)) {
            CEWorld ceWorld = BukkitWorldManager.instance().getWorld(LevelProxy.INSTANCE.getWorld(args[0]).getUID());
            BlockEntity be = ceWorld.getBlockEntityAtIfLoaded(LocationUtils.fromBlockPos(args[1]));
            if (be != null) {
                be.controller.let(TeapotController.class, this.controllerId, TeapotController::markCreativeBreak);
            }
        }
        return args[2];
    }

    private static volatile Method GET_BUKKIT_ENTITY;

    private static boolean isCreativePlayer(Object nmsPlayer) {
        if (nmsPlayer == null) {
            return false;
        }
        try {
            if (GET_BUKKIT_ENTITY == null) {
                GET_BUKKIT_ENTITY = nmsPlayer.getClass().getMethod("getBukkitEntity");
            }
            Object bukkit = GET_BUKKIT_ENTITY.invoke(nmsPlayer);
            return bukkit instanceof org.bukkit.entity.Player p && p.getGameMode() == GameMode.CREATIVE;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean canHangChains(Object level, Object abovePos, Object aboveState) {
        try {
            return BlockBehaviourProxy.BlockStateBaseProxy.INSTANCE.isFaceSturdy(
                    aboveState, level, abovePos, DirectionProxy.DOWN, SupportTypeProxy.CENTER);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new TeapotController(blockEntity, this);
    }

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    public Property<Direction> getFacingProperty() {
        return facingProperty;
    }

    public Property<Boolean> getHasBaseProperty() {
        return hasBaseProperty;
    }

    public Property<Boolean> getHasChainsProperty() {
        return hasChainsProperty;
    }

    private static class Factory implements BlockBehaviorFactory<TeapotBehavior> {
        @Override
        public TeapotBehavior create(BlockDefinition block, ConfigSection section) {
            TeapotBehavior b = new TeapotBehavior(block);
            b.facingProperty = BlockBehaviorFactory.getProperty(section.path(), block, "facing", Direction.class);
            b.hasBaseProperty = BlockBehaviorFactory.getProperty(section.path(), block, "has_base", Boolean.class);
            b.hasChainsProperty = BlockBehaviorFactory.getProperty(section.path(), block, "has_chains", Boolean.class);
            b.animChunkRadius = BehaviorConfig.getInt(section, b.animChunkRadius, "animation_view_distance", "animation-view-distance");
            b.particleInterval = BehaviorConfig.getInt(section, b.particleInterval, "particle_interval", "particle-interval");
            b.particleCount = BehaviorConfig.getInt(section, b.particleCount, "particle_count", "particle-count");
            b.msgPut = BehaviorConfig.getString(section, b.msgPut, "msg_put", "msg-put");
            b.msgProcessing = BehaviorConfig.getString(section, b.msgProcessing, "msg_processing", "msg-processing");
            b.msgFinished = BehaviorConfig.getString(section, b.msgFinished, "msg_finished", "msg-finished");
            return b;
        }
    }
}
