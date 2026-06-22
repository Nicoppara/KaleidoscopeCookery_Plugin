package top.nicoppa.nicoPengRen.content.steamer;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitFallableBlock;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.nms.FastNMS;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.DirectionUtils;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.bukkit.world.BukkitWorldManager;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.EntityBlock;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.block.property.type.SlabType;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.ItemDefinition;
import net.momirealms.craftengine.core.item.behavior.BlockItem;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.MutableBoolean;
import net.momirealms.craftengine.core.world.BlockHitResult;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.BlockPlaceContext;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.ListTag;
import net.momirealms.craftengine.libraries.nbt.Tag;
import net.momirealms.craftengine.proxy.minecraft.core.Vec3iProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.BlockGetterProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelAccessorProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelWriterProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.BlocksProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.FallingBlockProxy;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import top.nicoppa.nicoPengRen.common.block.HeatSourceUtils;
import top.nicoppa.nicoPengRen.common.config.BehaviorConfig;
import top.nicoppa.nicoPengRen.common.item.InventoryUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static net.momirealms.craftengine.core.block.UpdateFlags.UPDATE_ALL;

/**
 * 蒸笼方块行为 右键放料/取料、盖盖子、堆叠蒸笼，失去支撑时掉落。
 * 旧的 custom:steamer_proxy 代理方块已废弃归档至 {@link top.nicoppa.nicoPengRen.content.steamer.deprecated}。
 * 数据与状态机见 {@link SteamerController}，渲染见 {@link SteamerElement}
 *
 * 可在 behaviors 配置里覆盖（默认值即下方所示，键名支持下划线/连字符）：
 * behaviors:
 *   - type: nicopengren:steamer
 *     cooking_time: 200            # 每个食材蒸熟所需 tick
 *     campfire_stack_height: 8     # 在篝火等热源上最多叠几层
 *     stove_stack_height: 16       # 在炉灶上最多叠几层
 *     msg_max_layers: "蒸笼最多只能叠 {max} 层"   # {max} 替换为实际上限
 *     msg_full: "蒸笼已满"
 *     msg_need_stove: "请放在炉灶上方"
 */
public final class SteamerBehavior extends BukkitBlockBehavior implements EntityBlock, BukkitFallableBlock {
    public static final BlockBehaviorFactory<SteamerBehavior> FACTORY = new Factory();

    /** 掉落暂存：键为 FallingBlock 实体，值为下落前的蒸笼数据快照，落地/摔碎时取回。 */
    public static final Map<Object, PendingData> pendingData = new ConcurrentHashMap<>();

    private static final float DEFAULT_VOLUME = 1.0f;
    private static final Key PLACE_SOUND = Key.of("minecraft:block.wood.place");
    private static final Key LID_SOUND = Key.of("minecraft:block.barrel.close");
    private static final String STOVE_BLOCK_ID = "custom:stove";

    public int campfireStackHeight = 8;
    public int stoveStackHeight = 16;
    public int cookingTime = 200;
    public String msgMaxLayers = "蒸笼最多只能叠 {max} 层";
    public String msgFull = "蒸笼已满";
    public String msgNeedStove = "请放在炉灶上方";

    private int controllerId;
    private Property<SlabType> typeProperty;
    private Property<Boolean> hasLidProperty;
    private Property<Boolean> hasBaseProperty;
    private Property<Direction> facingProperty;

    private SteamerBehavior(BlockDefinition blockDefinition) {
        super(blockDefinition);
    }

    // 下落数据快照：下落前暂存的 NBT、原方块状态与 controllerId，落地后回灌还原
    public static class PendingData {
        public final CompoundTag tag;
        public final ImmutableBlockState steamerState;
        public final int controllerId;

        public PendingData(CompoundTag tag, ImmutableBlockState steamerState, int controllerId) {
            this.tag = tag;
            this.steamerState = steamerState;
            this.controllerId = controllerId;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        if (event.getEntity() instanceof FallingBlock) {
            pendingData.remove(event.getEntity());
        }
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        World level = context.getLevel();
        CEWorld world = level.storageWorld();
        BlockEntity blockEntity = world.getBlockEntityAtIfLoaded(context.getClickedPos());
        if (blockEntity == null) return InteractionResult.PASS;
        SteamerController controller = blockEntity.controller.get(SteamerController.class, this.controllerId);
        if (controller == null) return InteractionResult.PASS;

        InteractionHand hand = context.getHand();
        Item itemInHand = player.getItemInHand(hand);

        // 潜行 + 空手
        if (itemInHand.isEmpty() && player.isSecondaryUseActive()) {
            toggleTopLid(level, world, context.getClickedPos());
            player.swingHand(hand);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        // 手持蒸笼方块
        if (!itemInHand.isEmpty() && isThisSteamerItem(itemInHand)) {
            int cap = stackHeightCap(level, context.getClickedPos());
            if (stackLayerCount(level, context.getClickedPos()) >= cap) {
                player.sendActionBar(Component.text(msgMaxLayers.replace("{max}", String.valueOf(cap))));
                return InteractionResult.SUCCESS_AND_CANCEL;
            }

            BlockPos placePos = context.getClickedPos();
            ImmutableBlockState walkState = state;
            while (walkState != null
                    && walkState.owner().value() == super.blockDefinition
                    && !walkState.get(hasLidProperty)
                    && walkState.get(typeProperty) == SlabType.DOUBLE) {
                placePos = placePos.above();
                walkState = level.getBlock(placePos).customBlockState();
            }

            BlockHitResult hitResult = new BlockHitResult(context.getClickedLocation(), Direction.UP, placePos, false);
            BlockPlaceContext placeContext = new BlockPlaceContext(level, player, hand, itemInHand, hitResult);
            ImmutableBlockState targetState = level.getBlock(placePos).customBlockState();

            boolean canStackHere = targetState == null
                    || level.getBlock(placePos).canBeReplaced(placeContext)
                    || (targetState.owner().value() == super.blockDefinition
                        && targetState.get(typeProperty) == SlabType.BOTTOM
                        && !targetState.get(hasLidProperty));
            if (canStackHere) {
                return placeSteamer(placeContext);
            }
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        // 手持其他物品
        if (!itemInHand.isEmpty()) {
            int placed = placeFoodAcrossStack(level, world, context.getClickedPos(), itemInHand, player);
            if (placed > 0) {
                if (!player.canInstabuild()) itemInHand.shrink(placed);
                player.swingHand(hand);
                playSound(level, Vec3d.atCenterOf(context.getClickedPos()), PLACE_SOUND);
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            // 非食材
            return placed == 0 ? InteractionResult.PASS : InteractionResult.SUCCESS_AND_CANCEL;
        }

        // 空手
        Item extracted = controller.takeFood(player);
        if (extracted != null && !extracted.isEmpty()) {
            InventoryUtils.giveOrHold(player, hand, extracted);
            player.swingHand(hand);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        return InteractionResult.PASS;
    }

    private boolean isThisSteamerItem(Item item) {
        Optional<ItemDefinition> def = item.getDefinition();
        if (def.isEmpty()) return false;
        MutableBoolean sameId = new MutableBoolean(false);
        def.get().behavior().let(BlockItem.class, b -> {
            if (b.block().equals(super.blockDefinition.id())) sameId.set(true);
        });
        return sameId.booleanValue();
    }

    private int placeFoodAcrossStack(World level, CEWorld world, BlockPos clicked, Item food, Player player) {
        List<SteamerController> stack = collectStack(level, world, clicked);
        if (stack.isEmpty()) return 0;
        if (!stack.get(0).canSteam(food)) {
            return 0;
        }
        int remaining = food.count();
        int placed = 0;
        for (SteamerController c : stack) {
            while (remaining > 0 && c.tryAddOne(food)) {
                remaining--;
                placed++;
            }
            if (remaining == 0) break;
        }
        if (placed == 0) {
            player.sendActionBar(Component.text(msgFull));
            return -1;
        }
        return placed;
    }

    private List<SteamerController> collectStack(World level, CEWorld world, BlockPos anyPos) {
        BlockPos bottom = stackBottom(level, anyPos);
        List<SteamerController> stack = new ArrayList<>();
        BlockPos p = bottom;
        while (true) {
            ImmutableBlockState s = level.getBlock(p).customBlockState();
            if (s == null || s.owner().value() != super.blockDefinition) break;
            BlockEntity be = world.getBlockEntityAtIfLoaded(p);
            if (be != null) {
                SteamerController c = be.controller.get(SteamerController.class, this.controllerId);
                if (c != null) stack.add(c);
            }
            p = p.above();
        }
        return stack;
    }

    private BlockPos stackBottom(World level, BlockPos anyPos) {
        BlockPos bottom = anyPos;
        while (true) {
            BlockPos below = bottom.below();
            ImmutableBlockState s = level.getBlock(below).customBlockState();
            if (s == null || s.owner().value() != super.blockDefinition) break;
            bottom = below;
        }
        return bottom;
    }

    private int stackLayerCount(World level, BlockPos anyPos) {
        int layers = 0;
        BlockPos p = stackBottom(level, anyPos);
        while (true) {
            ImmutableBlockState s = level.getBlock(p).customBlockState();
            if (s == null || s.owner().value() != super.blockDefinition) break;
            layers += (s.get(typeProperty) == SlabType.DOUBLE) ? 2 : 1;
            p = p.above();
        }
        return layers;
    }

    private int stackHeightCap(World level, BlockPos anyPos) {
        Object nmsLevel = level.minecraftWorld();
        Object belowPos = LocationUtils.below(LocationUtils.toBlockPos(stackBottom(level, anyPos)));
        Object belowState = BlockGetterProxy.INSTANCE.getBlockState(nmsLevel, belowPos);
        ImmutableBlockState belowCustom = BlockStateUtils.getOptionalCustomBlockState(belowState).orElse(null);
        if (belowCustom != null && belowCustom.owner().value().id().toString().equals(STOVE_BLOCK_ID)) return stoveStackHeight;
        if (HeatSourceUtils.isHeatSource(nmsLevel, belowPos)) return campfireStackHeight;
        return Integer.MAX_VALUE;
    }

    private void toggleTopLid(World level, CEWorld world, BlockPos clicked) {
        BlockPos topPos = clicked;
        while (true) {
            BlockPos above = topPos.above();
            ImmutableBlockState aboveState = level.getBlock(above).customBlockState();
            if (aboveState == null || aboveState.owner().value() != super.blockDefinition) break;
            topPos = above;
        }

        ImmutableBlockState topState = level.getBlock(topPos).customBlockState();
        if (topState == null || topState.owner().value() != super.blockDefinition) return;

        BlockEntity topEntity = world.getBlockEntityAtIfLoaded(topPos);
        SteamerController topController = topEntity != null
                ? topEntity.controller.get(SteamerController.class, this.controllerId) : null;
        if (topController == null) return;

        boolean newLid = !topState.get(hasLidProperty);
        ImmutableBlockState newState = topState.with(hasLidProperty, newLid);
        LevelWriterProxy.INSTANCE.setBlock(level.minecraftWorld(), LocationUtils.toBlockPos(topPos), newState.customBlockState().minecraftState(), 3);
        topController.setHasLid(newLid);
        playSound(level, Vec3d.atCenterOf(topPos), LID_SOUND);
    }

    private InteractionResult placeSteamer(BlockPlaceContext context) {
        ImmutableBlockState newState = updateStateForPlacement(context, super.blockDefinition.defaultState());
        if (newState == null) return InteractionResult.FAIL;

        BlockPos pos = context.getClickedPos();
        LevelWriterProxy.INSTANCE.setBlock(context.getLevel().minecraftWorld(), LocationUtils.toBlockPos(pos), newState.customBlockState().minecraftState(), UPDATE_ALL);

        if (!context.getPlayer().canInstabuild()) context.getItem().shrink(1);
        context.getPlayer().swingHand(context.getHand());
        playSound(context.getLevel(), Vec3d.atCenterOf(pos), PLACE_SOUND);

        BlockEntity belowEntity = context.getLevel().storageWorld().getBlockEntityAtIfLoaded(pos.below());
        if (belowEntity != null) {
            SteamerController belowController = belowEntity.controller.get(SteamerController.class, this.controllerId);
            if (belowController != null) belowController.refreshElementState();
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void neighborChanged(Object thisBlock, Object[] args) {
        LevelAccessorProxy.INSTANCE.scheduleTick$0(args[1], args[2], thisBlock, 2);
    }

    @Override
    public void tick(Object thisBlock, Object[] args) {
        Object blockState = args[0], level = args[1], blockPos = args[2];
        Optional<ImmutableBlockState> optionalCustomState = BlockStateUtils.getOptionalCustomBlockState(blockState);
        if (optionalCustomState.isEmpty()) return;
        ImmutableBlockState customState = optionalCustomState.get();

        Object belowPos = LocationUtils.toBlockPos(Vec3iProxy.INSTANCE.getX(blockPos), Vec3iProxy.INSTANCE.getY(blockPos) - 1, Vec3iProxy.INSTANCE.getZ(blockPos));
        Object belowState = BlockGetterProxy.INSTANCE.getBlockState(level, belowPos);

        if (!FallingBlockProxy.INSTANCE.isFree(belowState)) return;
        if (HeatSourceUtils.isHeatSource(level, belowPos)) return;
        ImmutableBlockState belowCustom = BlockStateUtils.getOptionalCustomBlockState(belowState).orElse(null);
        if (belowCustom != null && belowCustom.owner().value() == super.blockDefinition) return;

        BlockPos pos = LocationUtils.fromBlockPos(blockPos);
        CompoundTag tag = new CompoundTag();
        BlockEntity blockEntity = BukkitWorldManager.instance().getWorld(LevelProxy.INSTANCE.getWorld(level).getUID()).getBlockEntityAtIfLoaded(pos);
        if (blockEntity != null) {
            SteamerController controller = blockEntity.controller.get(SteamerController.class, this.controllerId);
            if (controller != null) {
                controller.saveCustomData(tag);
                controller.markFallingAway();
                Arrays.fill(controller.getItems(), Item.empty());
            }
        }

        Object fallingBlockEntity = FastNMS.INSTANCE.createInjectedFallingBlockEntity(level, blockPos, blockState);
        pendingData.put(fallingBlockEntity, new PendingData(tag, customState, this.controllerId));
    }

    @Override
    public void onLand(Object thisBlock, Object[] args) {
        Object level = args[0];
        Object blockPos = args[1];
        Object fallingBlock = args[4];
        PendingData data = pendingData.remove(fallingBlock);
        if (data == null) return;

        BlockPos landPos = LocationUtils.fromBlockPos(blockPos);
        CEWorld ceWorld = BukkitWorldManager.instance().getWorld(LevelProxy.INSTANCE.getWorld(level).getUID());
        suppressAutoPlacedDrop(ceWorld, landPos);

        Object belowPos = LocationUtils.toBlockPos(Vec3iProxy.INSTANCE.getX(blockPos), Vec3iProxy.INSTANCE.getY(blockPos) - 1, Vec3iProxy.INSTANCE.getZ(blockPos));
        if (!isLandingSupported(level, belowPos)) {
            LevelWriterProxy.INSTANCE.setBlock(level, blockPos, BlocksProxy.AIR$defaultState, 2);
            dropSteamer(level, blockPos, data);
            return;
        }

        LevelWriterProxy.INSTANCE.setBlock(level, blockPos, data.steamerState.customBlockState().minecraftState(), 2);
        BlockEntity blockEntity = ceWorld.getBlockEntityAtIfLoaded(landPos);
        if (blockEntity != null) {
            SteamerController controller = blockEntity.controller.get(SteamerController.class, data.controllerId);
            if (controller != null) {
                controller.loadCustomData(data.tag);
                controller.clearFallingAway();
                controller.refreshElementState();
            }
        }
    }

    private void suppressAutoPlacedDrop(CEWorld world, BlockPos pos) {
        BlockEntity be = world.getBlockEntityAtIfLoaded(pos);
        if (be != null) {
            SteamerController c = be.controller.get(SteamerController.class, this.controllerId);
            if (c != null) c.markFallingAway();
        }
    }

    @Override
    public void onBrokenAfterFall(Object thisBlock, Object[] args) {
        Object level = args[0];
        Object blockPos = args[1];
        Object fallingBlock = args[2];
        PendingData data = pendingData.remove(fallingBlock);
        if (data != null) dropSteamer(level, blockPos, data);
    }

    private boolean isLandingSupported(Object level, Object belowPos) {
        if (HeatSourceUtils.isHeatSource(level, belowPos)) return true;
        Object belowState = BlockGetterProxy.INSTANCE.getBlockState(level, belowPos);
        ImmutableBlockState belowCustom = BlockStateUtils.getOptionalCustomBlockState(belowState).orElse(null);
        if (belowCustom == null) return false;
        if (belowCustom.owner().value() == super.blockDefinition) return true;
        return belowCustom.owner().value().id().toString().equals(STOVE_BLOCK_ID);
    }

    private void dropSteamer(Object level, Object blockPos, PendingData data) {
        try {
            CEWorld ceWorld = BukkitWorldManager.instance().getWorld(LevelProxy.INSTANCE.getWorld(level).getUID());
            BlockPos pos = LocationUtils.fromBlockPos(blockPos);
            Vec3d dropPos = Vec3d.atCenterOf(pos);

            CompoundTag sd = data.tag.getCompound("steamer_data");
            if (sd != null) {
                ListTag itemsTag = sd.getList("items");
                if (itemsTag != null) {
                    int dataVersion = sd.getInt("data_version", Config.itemDataFixerUpperFallbackVersion());
                    for (Tag itemTag : itemsTag) {
                        Object nmsItem = ItemStackUtils.parseMinecraftItem(itemTag, dataVersion);
                        if (nmsItem != null) ceWorld.world().dropItemNaturally(dropPos, ItemStackUtils.wrap(nmsItem));
                    }
                }
            }

            int amount = (data.steamerState.get(this.typeProperty) == SlabType.DOUBLE) ? 2 : 1;
            Key steamerKey = data.steamerState.owner().value().id();
            Item steamerItem = BukkitItemManager.instance().createWrappedItem(steamerKey, null);
            if (steamerItem != null) ceWorld.world().dropItemNaturally(dropPos, steamerItem.copyWithCount(amount));
        } catch (Exception e) {
            CraftEngine.instance().logger().error("无法掉落蒸笼，出错，请报告作者", e);
        }
    }

    @Override
    public ImmutableBlockState updateStateForPlacement(BlockPlaceContext context, ImmutableBlockState state) {
        BlockPos clickedPos = context.getClickedPos();
        ImmutableBlockState existingState = context.getLevel().getBlock(clickedPos).customBlockState();

        if (existingState != null && existingState.owner().value() == super.blockDefinition) {
            return existingState.with(this.typeProperty, SlabType.DOUBLE);
        }

        Object level = context.getLevel().minecraftWorld();
        Object belowPos = LocationUtils.below(LocationUtils.toBlockPos(clickedPos));

        ImmutableBlockState belowCustomState = context.getLevel().getBlock(clickedPos.below()).customBlockState();
        if (belowCustomState != null && belowCustomState.owner().value() == super.blockDefinition) {
            return state.with(this.typeProperty, SlabType.BOTTOM).with(this.facingProperty, context.getHorizontalDirection()).with(this.hasBaseProperty, shouldHasBase(level, clickedPos));
        }

        if (!HeatSourceUtils.isHeatSource(level, belowPos)) {
            if (context.getPlayer() != null) context.getPlayer().sendActionBar(Component.text(msgNeedStove));
            return null;
        }
        return state.with(this.typeProperty, SlabType.BOTTOM).with(this.facingProperty, context.getHorizontalDirection()).with(this.hasBaseProperty, shouldHasBase(level, clickedPos));
    }

    @Override
    public Object updateShape(Object thisBlock, Object[] args) {
        Object blockState = args[0];
        Object level = args[updateShape$level];
        Object blockPos = args[updateShape$blockPos];
        Direction nmsDirection = DirectionUtils.fromNMSDirection(args[updateShape$direction]);

        LevelAccessorProxy.INSTANCE.scheduleTick$0(level, blockPos, thisBlock, 2);

        ImmutableBlockState customState = BlockStateUtils.getOptionalCustomBlockState(blockState).orElse(null);
        if (customState == null || customState.isEmpty()) return blockState;

        if (nmsDirection == Direction.UP && this.hasLidProperty != null) {
            ImmutableBlockState neighborCustomState = BlockStateUtils.getOptionalCustomBlockState(args[updateShape$neighborState]).orElse(null);
            if (neighborCustomState != null && neighborCustomState.owner().value() == super.blockDefinition) {
                return customState.with(this.hasLidProperty, false).customBlockState().minecraftState();
            }
        }

        if (nmsDirection == Direction.DOWN && this.hasBaseProperty != null) {
            boolean shouldHaveBase = shouldHasBase(level, LocationUtils.fromBlockPos(blockPos));
            if (customState.get(this.hasBaseProperty) != shouldHaveBase) {
                return customState.with(this.hasBaseProperty, shouldHaveBase).customBlockState().minecraftState();
            }
        }
        return blockState;
    }

    @Override
    public Object playerWillDestroy(Object thisBlock, Object[] args) {
        if (args.length > 3 && isCreativePlayer(args[3])) {
            CEWorld ceWorld = BukkitWorldManager.instance().getWorld(LevelProxy.INSTANCE.getWorld(args[0]).getUID());
            BlockEntity be = ceWorld.getBlockEntityAtIfLoaded(LocationUtils.fromBlockPos(args[1]));
            if (be != null) {
                SteamerController c = be.controller.get(SteamerController.class, this.controllerId);
                if (c != null) c.markCreativeBreak();
            }
        }
        return args[2];
    }

    // 缓存 NMS ServerPlayer#getBukkitEntity，避免每次破坏方块都做反射方法查找
    private static volatile java.lang.reflect.Method GET_BUKKIT_ENTITY;

    private boolean isCreativePlayer(Object nmsPlayer) {
        if (nmsPlayer == null) return false;
        try {
            java.lang.reflect.Method m = GET_BUKKIT_ENTITY;
            if (m == null) {
                m = nmsPlayer.getClass().getMethod("getBukkitEntity");
                GET_BUKKIT_ENTITY = m;
            }
            Object bukkitEntity = m.invoke(nmsPlayer);
            return bukkitEntity instanceof org.bukkit.entity.Player p && p.getGameMode() == org.bukkit.GameMode.CREATIVE;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public boolean canBeReplaced(BlockPlaceContext context, ImmutableBlockState state) {
        SlabType type = state.get(this.typeProperty);
        if (type == SlabType.DOUBLE || type == SlabType.TOP) return false;
        if (ItemUtils.isEmpty(context.getItem()) || context.getItem().getDefinition().isEmpty()) return false;
        return isThisSteamerItem(context.getItem());
    }

    @Override
    public boolean canSurvive(Object thisBlock, Object[] args) { return true; }

    private boolean shouldHasBase(Object level, BlockPos pos) {
        Object belowNmsPos = LocationUtils.below(LocationUtils.toBlockPos(pos));
        Object belowState = BlockGetterProxy.INSTANCE.getBlockState(level, belowNmsPos);
        ImmutableBlockState belowCustomState = BlockStateUtils.getOptionalCustomBlockState(belowState).orElse(null);
        if (belowCustomState != null && belowCustomState.owner().value().id().toString().equals(STOVE_BLOCK_ID)) return false; // 炉灶不带底座
        return HeatSourceUtils.isHeatSource(level, belowNmsPos);
    }

    private void playSound(World world, Vec3d pos, Key sound) {
        world.playSound(pos, sound, DEFAULT_VOLUME, 1.0f, SoundSource.BLOCK);
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) { return new SteamerController(blockEntity, this); }

    @Override
    public void initControllerId(int id) { this.controllerId = id; }

    public int getControllerId() { return this.controllerId; }
    public Property<SlabType> getTypeProperty() { return typeProperty; }
    public Property<Boolean> getHasLidProperty() { return hasLidProperty; }
    public Property<Boolean> getHasBaseProperty() { return hasBaseProperty; }
    public Property<Direction> getFacingProperty() { return facingProperty; }

    private static class Factory implements BlockBehaviorFactory<SteamerBehavior> {
        @Override
        public SteamerBehavior create(BlockDefinition block, ConfigSection section) {
            SteamerBehavior behavior = new SteamerBehavior(block);
            behavior.typeProperty = BlockBehaviorFactory.getProperty(section.path(), block, "type", SlabType.class);
            behavior.hasLidProperty = BlockBehaviorFactory.getProperty(section.path(), block, "has_lid", Boolean.class);
            behavior.hasBaseProperty = BlockBehaviorFactory.getProperty(section.path(), block, "has_base", Boolean.class);
            behavior.facingProperty = BlockBehaviorFactory.getProperty(section.path(), block, "facing", Direction.class);

            behavior.cookingTime = BehaviorConfig.getInt(section, behavior.cookingTime, "cooking_time", "cooking-time");
            behavior.campfireStackHeight = BehaviorConfig.getInt(section, behavior.campfireStackHeight, "campfire_stack_height", "campfire-stack-height");
            behavior.stoveStackHeight = BehaviorConfig.getInt(section, behavior.stoveStackHeight, "stove_stack_height", "stove-stack-height");
            behavior.msgMaxLayers = BehaviorConfig.getString(section, behavior.msgMaxLayers, "msg_max_layers", "msg-max-layers");
            behavior.msgFull = BehaviorConfig.getString(section, behavior.msgFull, "msg_full", "msg-full");
            behavior.msgNeedStove = BehaviorConfig.getString(section, behavior.msgNeedStove, "msg_need_stove", "msg-need-stove");
            return behavior;
        }
    }
}
