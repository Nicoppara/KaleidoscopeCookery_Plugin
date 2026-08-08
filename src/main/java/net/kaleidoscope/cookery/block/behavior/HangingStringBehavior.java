package net.kaleidoscope.cookery.block.behavior;

import net.kaleidoscope.cookery.util.InteractGuard;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.util.SupportStateUtils;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.UpdateFlags;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import net.momirealms.craftengine.bukkit.util.DirectionUtils;
import net.momirealms.craftengine.proxy.minecraft.world.level.BlockGetterProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelAccessorProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelWriterProxy;
import net.kaleidoscope.cookery.util.BehaviorConfig;

// 悬挂串 辣椒串蘑菇串这类从天花板往下接的方块
// 空手或手持产物右键采摘 未剪过先转成剪过态 已剪过再摘就整段消失
public final class HangingStringBehavior extends BukkitBlockBehavior {
    public static final BlockBehaviorFactory<HangingStringBehavior> FACTORY = new Factory();

    // 1.21.2 起 updateShape 的参数从 6 个变成 8 个 布局完全不同 见 CE BlockBehavior 的 Javadoc
    private static final int SHAPE_LEVEL = VersionHelper.isOrAbove1_21_2 ? 1 : 3;
    private static final int SHAPE_POS = VersionHelper.isOrAbove1_21_2 ? 3 : 4;
    private static final int SHAPE_DIRECTION = VersionHelper.isOrAbove1_21_2 ? 4 : 1;
    private static final int SHAPE_NEIGHBOR_STATE = VersionHelper.isOrAbove1_21_2 ? 6 : 2;
    // 失去支撑后延迟这么多 tick 再塌 与原版悬挂方块一致
    private static final int COLLAPSE_DELAY = 1;

    private final Property<Boolean> isHeadProperty;
    private final Property<Boolean> shearedProperty;
    private final Key harvestItem;
    private final int harvestAmount;
    private final Key harvestSound;

    private HangingStringBehavior(BlockDefinition block, Property<Boolean> isHeadProperty,
                                  Property<Boolean> shearedProperty, Key harvestItem,
                                  int harvestAmount, Key harvestSound) {
        super(block);
        this.isHeadProperty = isHeadProperty;
        this.shearedProperty = shearedProperty;
        this.harvestItem = harvestItem;
        this.harvestAmount = harvestAmount;
        this.harvestSound = harvestSound;
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        if (player == null || context.getHand() != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        // 空手或手上已经拿着同种产物才算采摘 拿着别的东西是想放置方块 别抢掉
        Item held = context.getItem();
        if (!ItemUtils.isEmpty(held) && !held.id().equals(harvestItem)) {
            return InteractionResult.PASS;
        }
        BlockPos pos = context.getClickedPos();
        World world = context.getLevel();
        if (!InteractGuard.canBreak(player, world, pos.x(), pos.y(), pos.z())) {
            return InteractionResult.PASS;
        }

        Item drop = InventoryUtils.createOrEmpty(harvestItem);
        if (!ItemUtils.isEmpty(drop)) {
            InventoryUtils.giveOrHold(player, InteractionHand.MAIN_HAND, drop.copyWithCount(harvestAmount));
        }
        // 未剪过先掉一层外观 已剪过的再摘整段拿走
        if (state.get(shearedProperty)) {
            removeAt(world, pos);
        } else {
            LevelWriterProxy.INSTANCE.setBlock(world.minecraftWorld(), LocationUtils.toBlockPos(pos),
                    state.with(shearedProperty, true).customBlockState().minecraftState(),
                    UpdateFlags.UPDATE_ALL);
        }
        world.playSound(Vec3d.atCenterOf(pos), harvestSound, 1.0f, 0.8f + (float) Math.random() * 0.4f, SoundSource.BLOCK);
        player.swingHand(InteractionHand.MAIN_HAND);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // applyPhysics 必须开 否则挂在下面那几节收不到 updateShape 会留在半空
    private static void removeAt(World world, BlockPos pos) {
        org.bukkit.World bukkitWorld = (org.bukkit.World) world.platformWorld();
        bukkitWorld.getBlockAt(pos.x(), pos.y(), pos.z()).setType(org.bukkit.Material.AIR, true);
    }

    // 挂在上方 顶端接天花板的坚固下表面 其余接同种方块
    @Override
    public boolean canSurvive(Object thisBlock, Object[] args) {
        return isSupported(args[1], args[2]);
    }

    private boolean isSupported(Object level, Object pos) {
        Object abovePos = LocationUtils.above(pos);
        Object aboveState = BlockGetterProxy.INSTANCE.getBlockState(level, abovePos);
        ImmutableBlockState aboveCustom = BlockStateUtils.getOptionalCustomBlockState(aboveState).orElse(null);
        if (aboveCustom != null && aboveCustom.owner().value() == super.blockDefinition) {
            return true;
        }
        return SupportStateUtils.isSturdyDown(level, abovePos, aboveState);
    }

    @Override
    public Object updateShape(Object thisBlock, Object[] args) {
        Object blockState = args[0];
        ImmutableBlockState state = BlockStateUtils.getOptionalCustomBlockState(blockState).orElse(null);
        if (state == null || state.isEmpty()) {
            return blockState;
        }
        Object level = args[SHAPE_LEVEL];
        Object pos = args[SHAPE_POS];
        Direction direction = DirectionUtils.fromNMSDirection(args[SHAPE_DIRECTION]);

        // 顶上的支撑没了就排一个 tick 让它塌 直接返回空气会跳过掉落
        if (direction == Direction.UP && !isSupported(level, pos)) {
            LevelAccessorProxy.INSTANCE.scheduleTick$0(level, pos, thisBlock, COLLAPSE_DELAY);
            return blockState;
        }
        // 末端那一节用不同的模型 下面接没接同种方块决定
        if (direction == Direction.DOWN) {
            ImmutableBlockState below = BlockStateUtils.getOptionalCustomBlockState(args[SHAPE_NEIGHBOR_STATE]).orElse(null);
            boolean isHead = below == null || below.owner().value() != super.blockDefinition;
            if (state.get(isHeadProperty) != isHead) {
                return state.with(isHeadProperty, isHead).customBlockState().minecraftState();
            }
        }
        return blockState;
    }

    @Override
    public void tick(Object thisBlock, Object[] args) {
        if (isSupported(args[1], args[2])) {
            return;
        }
        org.bukkit.World world = LevelProxy.INSTANCE.getWorld(args[1]);
        if (world == null) {
            return;
        }
        BlockPos pos = LocationUtils.fromBlockPos(args[2]);
        world.getBlockAt(pos.x(), pos.y(), pos.z()).breakNaturally();
    }

    private static class Factory implements BlockBehaviorFactory<HangingStringBehavior> {
        private static final int DEFAULT_AMOUNT = 3;
        private static final String DEFAULT_SOUND = "minecraft:block.sweet_berry_bush.pick_berries";

        @Override
        public HangingStringBehavior create(BlockDefinition block, ConfigSection section) {
            String path = section.path();
            Property<Boolean> isHead = BlockBehaviorFactory.getProperty(path, block, "is_head", Boolean.class);
            Property<Boolean> sheared = BlockBehaviorFactory.getProperty(path, block, "sheared", Boolean.class);
            String item = BehaviorConfig.getString(section, null, "harvest_item", "harvest-item");
            if (item == null) {
                throw new IllegalArgumentException("hanging_string 缺少 harvest_item");
            }
            int amount = BehaviorConfig.getInt(section, DEFAULT_AMOUNT, "harvest_amount", "harvest-amount");
            String sound = BehaviorConfig.getString(section, DEFAULT_SOUND, "harvest_sound", "harvest-sound");
            return new HangingStringBehavior(block, isHead, sheared, Key.of(item), amount, Key.of(sound));
        }
    }
}
