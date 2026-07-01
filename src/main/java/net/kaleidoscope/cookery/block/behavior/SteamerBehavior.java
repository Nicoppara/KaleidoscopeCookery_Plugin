package net.kaleidoscope.cookery.block.behavior;
import net.kaleidoscope.cookery.api.event.SteamerBreakFullEvent;
import net.kaleidoscope.cookery.block.entity.SteamerController;

import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitFallableBlock;
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
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelReaderProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.dimension.DimensionTypeProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelWriterProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.BlocksProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.FallingBlockProxy;
import org.bukkit.Location;
import net.kaleidoscope.cookery.util.EventUtils;
import net.kaleidoscope.cookery.util.HeatSourceUtils;
import net.kaleidoscope.cookery.util.BehaviorConfig;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import static net.momirealms.craftengine.core.block.UpdateFlags.UPDATE_ALL;

public final class SteamerBehavior extends BukkitBlockBehavior implements EntityBlock, BukkitFallableBlock {
    public static final BlockBehaviorFactory<SteamerBehavior> FACTORY = new Factory();

    // 掉落暂存 键为 FallingBlock 实体 值为下落前的蒸笼数据快照
    public static final Map<Object, PendingData> pendingData = new ConcurrentHashMap<>();

    private static final Key PLACE_SOUND = Key.of("minecraft:block.wood.place");
    private static final Key LID_SOUND = Key.of("minecraft:block.barrel.close");
    public static final Key STOVE_BLOCK_KEY = Key.of("kaleidoscopecookery:stove");

    public int campfireStackHeight = 8;
    public int stoveStackHeight = 16;
    public int cookingTime = 200;
    public int particleInterval = 20;
    public int particleCount = 3;
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

    // 下落数据快照
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

    // 由 SteamerFallingBlockListener 在领地插件取消落地事件、且落点不受支撑时调用 
    // 此时 CE 不会再触发 onLand / onBrokenAfterFall 需手动掉落以免蒸笼及内容物丢失 
    public static void dropPendingSteamer(Object level, Object blockPos, Object fallingBlockEntity) {
        PendingData data = pendingData.remove(fallingBlockEntity);
        if (data == null) {
            return;
        }
        SteamerBehavior behavior = data.steamerState.behavior().getFirst(SteamerBehavior.class);
        if (behavior != null) {
            behavior.dropSteamer(level, blockPos, data);
        }
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        World level = context.getLevel();
        CEWorld world = level.storageWorld();
        BlockEntity blockEntity = world.getBlockEntityAtIfLoaded(context.getClickedPos());
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }
        SteamerController controller = blockEntity.controller.get(SteamerController.class, this.controllerId);
        if (controller == null) {
            return InteractionResult.PASS;
        }

        BlockPos clickedPos = context.getClickedPos();
        if (!InteractGuard.canInteract(player, level, clickedPos)) {
            return InteractionResult.PASS;
        }

        // 蒸笼操作不涉及工具 只认主手 副手触发直接放行
        if (context.getHand() == InteractionHand.OFF_HAND) {
            return InteractionResult.PASS;
        }
        InteractionHand hand = InteractionHand.MAIN_HAND;
        Item itemInHand = player.getItemInHand(hand);

        // 潜行 + 空手 开关盖子
        if (itemInHand.isEmpty() && player.isSecondaryUseActive()) {
            return handleLid(context, level, world, player, hand);
        }
        // 手持蒸笼方块 叠笼
        if (!itemInHand.isEmpty() && isThisSteamerItem(itemInHand)) {
            return handleStackSteamer(context, state, level, player, hand, itemInHand);
        }
        // 手持其他物品 放料
        if (!itemInHand.isEmpty()) {
            return handlePlaceFood(context, level, world, player, hand, itemInHand);
        }
        // 空手 取料
        return handleTakeFood(controller, player, hand);
    }

    private InteractionResult handleLid(UseOnContext context, World level, CEWorld world, Player player, InteractionHand hand) {
        toggleTopLid(level, world, context.getClickedPos());
        player.swingHand(hand);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    private InteractionResult handleStackSteamer(UseOnContext context, ImmutableBlockState state, World level, Player player, InteractionHand hand, Item itemInHand) {
        int cap = stackHeightCap(level, context.getClickedPos());
        if (stackLayerCount(level, context.getClickedPos()) >= cap) {
            player.sendActionBar(Component.text(msgMaxLayers.replace("{max}", String.valueOf(cap))));
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        // 向上找到第一个可放置的层
        BlockPos placePos = context.getClickedPos();
        ImmutableBlockState walkState = state;
        int guard = 0;
        while (walkState != null
                && walkState.owner().value() == super.blockDefinition
                && !walkState.get(hasLidProperty)
                && walkState.get(typeProperty) == SlabType.DOUBLE
                && guard++ < 256) {
            placePos = placePos.above();
            walkState = level.getBlock(placePos).customBlockState();
        }

        BlockHitResult hitResult = new BlockHitResult(context.getClickedLocation(), Direction.UP, placePos, false);
        BlockPlaceContext placeContext = new BlockPlaceContext(level, player, hand, itemInHand, hitResult);
        // 叠笼实际放置点是向上 walk 出的新位置 与点击位可能跨领地 放动作前再校验目标位置
        if (!InteractGuard.canInteract(player, level, placePos)) {
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

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

    private InteractionResult handlePlaceFood(UseOnContext context, World level, CEWorld world, Player player, InteractionHand hand, Item itemInHand) {
        int placed = placeFoodAcrossStack(level, world, context.getClickedPos(), itemInHand, player);
        if (placed > 0) {
            InventoryUtils.shrinkHeld(player, itemInHand, placed);
            player.swingHand(hand);
            playSound(level, Vec3d.atCenterOf(context.getClickedPos()), PLACE_SOUND);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        // placed == 0 为非食材 放行；-1 为蒸笼已满
        return placed == 0 ? InteractionResult.PASS : InteractionResult.SUCCESS_AND_CANCEL;
    }

    private InteractionResult handleTakeFood(SteamerController controller, Player player, InteractionHand hand) {
        Item extracted = controller.takeFood(player);
        if (!extracted.isEmpty()) {
            InventoryUtils.giveOrHold(player, hand, extracted);
            player.swingHand(hand);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        return InteractionResult.PASS;
    }

    private boolean isThisSteamerItem(Item item) {
        Optional<ItemDefinition> def = item.getDefinition();
        if (def.isEmpty()) {
            return false;
        }
        MutableBoolean sameId = new MutableBoolean(false);
        def.get().behavior().let(BlockItem.class, b -> {
            if (b.block().equals(super.blockDefinition.id())) {
                sameId.set(true);
            }
        });
        return sameId.booleanValue();
    }

    private int placeFoodAcrossStack(World level, CEWorld world, BlockPos clicked, Item food, Player player) {
        List<SteamerController> stack = collectStack(level, world, clicked);
        if (stack.isEmpty()) {
            return 0;
        }
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
            if (remaining == 0) {
                break;
            }
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
            if (s == null || s.owner().value() != super.blockDefinition) {
                break;
            }
            BlockEntity be = world.getBlockEntityAtIfLoaded(p);
            if (be != null) {
                SteamerController c = be.controller.get(SteamerController.class, this.controllerId);
                if (c != null) {
                    stack.add(c);
                }
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
            if (s == null || s.owner().value() != super.blockDefinition) {
                break;
            }
            bottom = below;
        }
        return bottom;
    }

    private int stackLayerCount(World level, BlockPos anyPos) {
        int layers = 0;
        BlockPos p = stackBottom(level, anyPos);
        while (true) {
            ImmutableBlockState s = level.getBlock(p).customBlockState();
            if (s == null || s.owner().value() != super.blockDefinition) {
                break;
            }
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
        if (belowCustom != null && belowCustom.owner().value().id().equals(STOVE_BLOCK_KEY)) {
            return stoveStackHeight;
        }
        if (HeatSourceUtils.isHeatSource(nmsLevel, belowPos)) {
            return campfireStackHeight;
        }
        return Integer.MAX_VALUE;
    }

    private void toggleTopLid(World level, CEWorld world, BlockPos clicked) {
        // 找到栈顶
        BlockPos topPos = clicked;
        while (true) {
            BlockPos above = topPos.above();
            ImmutableBlockState aboveState = level.getBlock(above).customBlockState();
            if (aboveState == null || aboveState.owner().value() != super.blockDefinition) {
                break;
            }
            topPos = above;
        }

        ImmutableBlockState topState = level.getBlock(topPos).customBlockState();
        if (topState == null || topState.owner().value() != super.blockDefinition) {
            return;
        }

        BlockEntity topEntity = world.getBlockEntityAtIfLoaded(topPos);
        SteamerController topController = topEntity != null
                ? topEntity.controller.get(SteamerController.class, this.controllerId) : null;
        if (topController == null) {
            return;
        }

        boolean newLid = !topState.get(hasLidProperty);
        ImmutableBlockState newState = topState.with(hasLidProperty, newLid);
        LevelWriterProxy.INSTANCE.setBlock(level.minecraftWorld(), LocationUtils.toBlockPos(topPos), newState.customBlockState().minecraftState(), 3);
        topController.setHasLid(newLid);
        playSound(level, Vec3d.atCenterOf(topPos), LID_SOUND);
    }

    private InteractionResult placeSteamer(BlockPlaceContext context) {
        ImmutableBlockState newState = updateStateForPlacement(context, super.blockDefinition.defaultState());
        if (newState == null) {
            return InteractionResult.FAIL;
        }

        BlockPos pos = context.getClickedPos();
        LevelWriterProxy.INSTANCE.setBlock(context.getLevel().minecraftWorld(), LocationUtils.toBlockPos(pos), newState.customBlockState().minecraftState(), UPDATE_ALL);

        InventoryUtils.shrinkHeld(context.getPlayer(), context.getItem(), 1);
        context.getPlayer().swingHand(context.getHand());
        playSound(context.getLevel(), Vec3d.atCenterOf(pos), PLACE_SOUND);

        BlockEntity belowEntity = context.getLevel().storageWorld().getBlockEntityAtIfLoaded(pos.below());
        if (belowEntity != null) {
            SteamerController belowController = belowEntity.controller.get(SteamerController.class, this.controllerId);
            if (belowController != null) {
                belowController.refreshElementState();
            }
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
        if (optionalCustomState.isEmpty()) {
            return;
        }
        ImmutableBlockState customState = optionalCustomState.get();

        // 世界底部以下不触发下落
        int minY = DimensionTypeProxy.INSTANCE.getMinY(LevelReaderProxy.INSTANCE.dimensionType(level));
        if (Vec3iProxy.INSTANCE.getY(blockPos) < minY) {
            return;
        }

        Object belowPos = LocationUtils.below(blockPos);
        Object belowState = BlockGetterProxy.INSTANCE.getBlockState(level, belowPos);

        if (!FallingBlockProxy.INSTANCE.isFree(belowState)) {
            return;
        }
        if (HeatSourceUtils.isHeatSource(level, belowPos)) {
            return;
        }
        ImmutableBlockState belowCustom = BlockStateUtils.getOptionalCustomBlockState(belowState).orElse(null);
        if (belowCustom != null && belowCustom.owner().value() == super.blockDefinition) {
            return;
        }

        // 快照 NBT 并切断原方块实体的掉落 转交给下落实体
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
        PendingData pending = new PendingData(tag, customState, this.controllerId);
        if (fallingBlockEntity == null) {
            dropSteamer(level, blockPos, pending);
            return;
        }
        pendingData.put(fallingBlockEntity, pending);
    }

    @Override
    public void onLand(Object thisBlock, Object[] args) {
        Object level = args[0];
        Object blockPos = args[1];
        Object fallingBlock = args[4];
        PendingData data = pendingData.remove(fallingBlock);
        if (data == null) {
            return;
        }

        BlockPos landPos = LocationUtils.fromBlockPos(blockPos);
        CEWorld ceWorld = BukkitWorldManager.instance().getWorld(LevelProxy.INSTANCE.getWorld(level).getUID());

        // 标记落地方块为下落中 避免 onRemove 误掉落
        BlockEntity landingEntity = ceWorld.getBlockEntityAtIfLoaded(landPos);
        if (landingEntity != null) {
            SteamerController landingController = landingEntity.controller.get(SteamerController.class, this.controllerId);
            if (landingController != null) {
                landingController.markFallingAway();
            }
        }

        Object belowPos = LocationUtils.below(blockPos);
        if (!isLandingSupported(level, belowPos)) {
            LevelWriterProxy.INSTANCE.setBlock(level, blockPos, BlocksProxy.AIR$defaultState, UPDATE_ALL);
            dropSteamer(level, blockPos, data);
            return;
        }

        LevelWriterProxy.INSTANCE.setBlock(level, blockPos, data.steamerState.customBlockState().minecraftState(), UPDATE_ALL);
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

    @Override
    public void onBrokenAfterFall(Object thisBlock, Object[] args) {
        Object level = args[0];
        Object blockPos = args[1];
        Object fallingBlock = args[2];
        PendingData data = pendingData.remove(fallingBlock);
        if (data != null) {
            dropSteamer(level, blockPos, data);
        }
    }

    private boolean isLandingSupported(Object level, Object belowPos) {
        if (HeatSourceUtils.isHeatSource(level, belowPos)) {
            return true;
        }
        Object belowState = BlockGetterProxy.INSTANCE.getBlockState(level, belowPos);
        ImmutableBlockState belowCustom = BlockStateUtils.getOptionalCustomBlockState(belowState).orElse(null);
        if (belowCustom == null) {
            return false;
        }
        if (belowCustom.owner().value() == super.blockDefinition) {
            return true;
        }
        return belowCustom.owner().value().id().equals(STOVE_BLOCK_KEY);
    }

    private void dropSteamer(Object level, Object blockPos, PendingData data) {
        try {
            CEWorld ceWorld = BukkitWorldManager.instance().getWorld(LevelProxy.INSTANCE.getWorld(level).getUID());
            BlockPos pos = LocationUtils.fromBlockPos(blockPos);
            Vec3d dropPos = Vec3d.atCenterOf(pos);

            CompoundTag sd = data.tag.getCompound(SteamerController.DATA_KEY);
            if (sd != null) {
                ListTag itemsTag = sd.getList("items");
                if (itemsTag != null) {
                    int dataVersion = sd.getInt("data_version", Config.itemDataFixerUpperFallbackVersion());
                    for (Tag itemTag : itemsTag) {
                        Object nmsItem = ItemStackUtils.parseMinecraftItem(itemTag, dataVersion);
                        if (nmsItem != null) {
                            ceWorld.world().dropItemNaturally(dropPos, ItemStackUtils.wrap(nmsItem));
                        }
                    }
                }
            }

            int amount = (data.steamerState.get(this.typeProperty) == SlabType.DOUBLE) ? 2 : 1;
            Key steamerKey = data.steamerState.owner().value().id();
            Item steamerItem = InventoryUtils.createOrEmpty(steamerKey);
            if (!ItemUtils.isEmpty(steamerItem)) {
                ceWorld.world().dropItemNaturally(dropPos, steamerItem.copyWithCount(amount));
            }
        } catch (Exception e) {
            KaleidoscopeCookeryPlugin.instance().getLogger().log(Level.SEVERE, "无法掉落蒸笼，出错，请报告作者", e);
        }
    }

    @Override
    public ImmutableBlockState updateStateForPlacement(BlockPlaceContext context, ImmutableBlockState state) {
        BlockPos clickedPos = context.getClickedPos();
        ImmutableBlockState existingState = context.getLevel().getBlock(clickedPos).customBlockState();

        // 原位是蒸笼底层时合并为双层
        if (existingState != null && existingState.owner().value() == super.blockDefinition) {
            return existingState.with(this.typeProperty, SlabType.DOUBLE);
        }

        Object level = context.getLevel().minecraftWorld();

        ImmutableBlockState belowCustomState = context.getLevel().getBlock(clickedPos.below()).customBlockState();
        if (belowCustomState != null && belowCustomState.owner().value() == super.blockDefinition) {
            return state.with(this.typeProperty, SlabType.BOTTOM).with(this.facingProperty, context.getHorizontalDirection()).with(this.hasBaseProperty, shouldHasBase(level, clickedPos));
        }

        Object belowPos = LocationUtils.below(LocationUtils.toBlockPos(clickedPos));
        if (!HeatSourceUtils.isHeatSource(level, belowPos)) {
            if (context.getPlayer() != null) {
                context.getPlayer().sendActionBar(Component.text(msgNeedStove));
            }
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
        if (customState == null || customState.isEmpty()) {
            return blockState;
        }

        // 上方接蒸笼时收起盖子
        if (nmsDirection == Direction.UP && this.hasLidProperty != null) {
            ImmutableBlockState neighborCustomState = BlockStateUtils.getOptionalCustomBlockState(args[updateShape$neighborState]).orElse(null);
            if (neighborCustomState != null && neighborCustomState.owner().value() == super.blockDefinition) {
                return customState.with(this.hasLidProperty, false).customBlockState().minecraftState();
            }
        }

        // 下方变化时同步底座
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
        Object nmsPlayer = args.length > 3 ? args[3] : null;
        CEWorld ceWorld = BukkitWorldManager.instance().getWorld(LevelProxy.INSTANCE.getWorld(args[0]).getUID());
        BlockEntity be = ceWorld.getBlockEntityAtIfLoaded(LocationUtils.fromBlockPos(args[1]));
        SteamerController c = be != null ? be.controller.get(SteamerController.class, this.controllerId) : null;
        if (c == null) {
            return args[2];
        }

        // 蒸笼装满成品被破坏时触发事件 取消则跳过成品特殊掉落
        if (c.isFullOfFinishedProducts()) {
            org.bukkit.entity.Player bukkitPlayer = bukkitPlayer(nmsPlayer);
            if (bukkitPlayer != null) {
                List<org.bukkit.inventory.ItemStack> products = c.finishedProductStacks();
                BlockPos pos = LocationUtils.fromBlockPos(args[1]);
                Location location = new Location((org.bukkit.World) ceWorld.world().platformWorld(), pos.x(), pos.y(), pos.z());
                boolean cancelled = EventUtils.fireAndCheckCancel(new SteamerBreakFullEvent(bukkitPlayer, location, products));
                if (cancelled) {
                    c.markSkipFoodDrop();
                }
            }
        }
        return args[2];
    }

    private static volatile Method GET_BUKKIT_ENTITY;

    private static org.bukkit.entity.Player bukkitPlayer(Object nmsPlayer) {
        if (nmsPlayer == null) {
            return null;
        }
        try {
            Method m = GET_BUKKIT_ENTITY;
            if (m == null) {
                m = nmsPlayer.getClass().getMethod("getBukkitEntity");
                GET_BUKKIT_ENTITY = m;
            }
            Object bukkitEntity = m.invoke(nmsPlayer);
            return bukkitEntity instanceof org.bukkit.entity.Player p ? p : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public boolean canBeReplaced(BlockPlaceContext context, ImmutableBlockState state) {
        SlabType type = state.get(this.typeProperty);
        if (type == SlabType.DOUBLE || type == SlabType.TOP) {
            return false;
        }
        if (ItemUtils.isEmpty(context.getItem()) || context.getItem().getDefinition().isEmpty()) {
            return false;
        }
        return isThisSteamerItem(context.getItem());
    }

    @Override
    public boolean canSurvive(Object thisBlock, Object[] args) {
        return true;
    }

    private boolean shouldHasBase(Object level, BlockPos pos) {
        Object belowNmsPos = LocationUtils.below(LocationUtils.toBlockPos(pos));
        Object belowState = BlockGetterProxy.INSTANCE.getBlockState(level, belowNmsPos);
        ImmutableBlockState belowCustomState = BlockStateUtils.getOptionalCustomBlockState(belowState).orElse(null);
        if (belowCustomState != null && belowCustomState.owner().value().id().equals(STOVE_BLOCK_KEY)) {
            return false; // 炉灶不带底座
        }
        return HeatSourceUtils.isHeatSource(level, belowNmsPos);
    }

    private void playSound(World world, Vec3d pos, Key sound) {
        world.playSound(pos, sound, 1.0f, 1.0f, SoundSource.BLOCK);
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new SteamerController(blockEntity, this);
    }

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    public int getControllerId() {
        return this.controllerId;
    }

    public Property<SlabType> getTypeProperty() {
        return typeProperty;
    }

    public Property<Boolean> getHasLidProperty() {
        return hasLidProperty;
    }

    public Property<Boolean> getHasBaseProperty() {
        return hasBaseProperty;
    }

    public Property<Direction> getFacingProperty() {
        return facingProperty;
    }

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
            behavior.particleInterval = BehaviorConfig.getInt(section, behavior.particleInterval, "particle_interval", "particle-interval");
            behavior.particleCount = BehaviorConfig.getInt(section, behavior.particleCount, "particle_count", "particle-count");
            behavior.msgMaxLayers = BehaviorConfig.getString(section, behavior.msgMaxLayers, "msg_max_layers", "msg-max-layers");
            behavior.msgFull = BehaviorConfig.getString(section, behavior.msgFull, "msg_full", "msg-full");
            behavior.msgNeedStove = BehaviorConfig.getString(section, behavior.msgNeedStove, "msg_need_stove", "msg-need-stove");
            return behavior;
        }
    }
}
