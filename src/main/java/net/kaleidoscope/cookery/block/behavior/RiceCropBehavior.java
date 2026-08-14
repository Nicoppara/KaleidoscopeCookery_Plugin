package net.kaleidoscope.cookery.block.behavior;

import net.kaleidoscope.cookery.block.entity.render.Particles;
import net.kaleidoscope.cookery.util.BehaviorConfig;
import net.kaleidoscope.cookery.util.ChunkEntityCache;
import net.kaleidoscope.cookery.util.Hands;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.kaleidoscope.cookery.util.SoundCluster;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.UpdateFlags;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BonemealableBlock;
import net.momirealms.craftengine.core.block.behavior.BucketPickup;
import net.momirealms.craftengine.core.block.behavior.RandomTickBlock;
import net.momirealms.craftengine.core.block.property.IntegerProperty;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.plugin.config.ConfigConstants;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.context.ContextHolder;
import net.momirealms.craftengine.core.plugin.context.SimpleContext;
import net.momirealms.craftengine.core.plugin.context.number.NumberProvider;
import net.momirealms.craftengine.core.plugin.context.parameter.DirectContextParameters;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.core.util.random.RandomUtils;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import net.momirealms.craftengine.proxy.bukkit.craftbukkit.event.CraftEventFactoryProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.EntityProxy;
import net.momirealms.craftengine.proxy.minecraft.world.item.ItemStackProxy;
import net.momirealms.craftengine.proxy.minecraft.world.item.ItemsProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.material.FluidStateProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.material.FluidsProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.BlockAndLightGetterProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.BlockGetterProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelAccessorProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.ScheduledTickAccessProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelWriterProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.BonemealableBlockProxy;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

// 水稻 三格高 只有底部一段参与生长与掉落
public final class RiceCropBehavior extends BukkitBlockBehavior
        implements RandomTickBlock, BonemealableBlock, BucketPickup, HarvestableCrop {
    public static final BlockBehaviorFactory<RiceCropBehavior> FACTORY = new Factory();

    private static final int SHAPE_TICK_TARGET = VersionHelper.isOrAbove1_21_2 ? 2 : 3;
    private static final int SHAPE_POS = VersionHelper.isOrAbove1_21_2 ? 3 : 4;
    // 原版 WaterFluid#getTickDelay 水是 5
    private static final int WATER_TICK_DELAY = 5;

    private static final int PICKUP_PLAYER = 0;
    private static final int PICKUP_LEVEL = 1;
    private static final int PICKUP_POS = 2;
    private static final int PICKUP_STATE = 3;

    private static final int BOTTOM = 0;
    private static final int SEGMENTS = 3;
    // 原版 Level#isNight 的判定区间
    private static final long NIGHT_START = 13000L;
    private static final long NIGHT_END = 23000L;
    private static final float SOUND_VOLUME_BASE = 0.2f;
    private static final float SOUND_VOLUME_RANGE = 0.2f;
    private static final float SOUND_PITCH_BASE = 0.9f;
    private static final float SOUND_PITCH_RANGE = 0.1f;
    // 原版 BoneMealItem 的粒子参数
    private static final int BONE_MEAL_PARTICLE_COUNT = 15;
    private static final double BONE_MEAL_PARTICLE_SPREAD = 0.25;

    private final IntegerProperty ageProperty;
    private final IntegerProperty locationProperty;
    private final Property<Boolean> waterloggedProperty;
    private final int baseGrowth;
    private final float extraGrowChance;
    private final int minGrowLight;
    private final int maxGrowLight;
    private final NumberProvider boneMealBonus;
    private final Key nightSound;
    private final SoundCluster soundCluster;
    private final int boosterRadius;
    private final ChunkEntityCache boosterCache;
    private final CropHarvestRules harvestRules;

    private RiceCropBehavior(BlockDefinition block, IntegerProperty ageProperty, IntegerProperty locationProperty,
                             Property<Boolean> waterloggedProperty, float growSpeed, int minGrowLight, int maxGrowLight,
                             NumberProvider boneMealBonus, Key nightSound, SoundCluster soundCluster, int boosterRadius,
                             ChunkEntityCache boosterCache, CropHarvestRules harvestRules) {
        super(block);
        this.ageProperty = ageProperty;
        this.locationProperty = locationProperty;
        this.waterloggedProperty = waterloggedProperty;
        this.baseGrowth = (int) growSpeed;
        this.extraGrowChance = growSpeed - this.baseGrowth;
        this.minGrowLight = minGrowLight;
        this.maxGrowLight = maxGrowLight;
        this.boneMealBonus = boneMealBonus;
        this.nightSound = nightSound;
        this.soundCluster = soundCluster;
        this.boosterRadius = boosterRadius;
        this.boosterCache = boosterCache;
        this.harvestRules = harvestRules;
    }

    @Override
    public boolean canRandomlyTick(ImmutableBlockState state) {
        return state.get(this.locationProperty) == BOTTOM && state.get(this.ageProperty) < this.ageProperty.max;
    }

    // 底段泡在水里才活得下去
    @Override
    public boolean canSurvive(Object thisBlock, Object[] args) {
        ImmutableBlockState state = BlockStateUtils.getOptionalCustomBlockState(args[0]).orElse(null);
        if (state == null || state.get(this.locationProperty) != BOTTOM) {
            return true;
        }
        return state.get(this.waterloggedProperty) && isWater(args[1], args[2]);
    }

    // 分流与源水是两个不同的 Fluid 实例 只比 WATER 会把流动的水判成没水 稻子就种不下去
    private static boolean isWater(Object level, Object pos) {
        Object type = FluidStateProxy.INSTANCE.getType(BlockGetterProxy.INSTANCE.getFluidState(level, pos));
        return type == FluidsProxy.WATER || type == FluidsProxy.FLOWING_WATER;
    }

    // 含水方块必须自己排水流 tick
    @Override
    public Object updateShape(Object thisBlock, Object[] args) {
        ImmutableBlockState state = BlockStateUtils.getOptionalCustomBlockState(args[0]).orElse(null);
        if (state != null && state.get(this.waterloggedProperty)) {
            if (VersionHelper.isOrAbove1_21_2) {
                ScheduledTickAccessProxy.INSTANCE.scheduleTick$1(args[SHAPE_TICK_TARGET], args[SHAPE_POS], FluidsProxy.WATER, WATER_TICK_DELAY);
            } else {
                LevelAccessorProxy.INSTANCE.scheduleTick$1(args[SHAPE_TICK_TARGET], args[SHAPE_POS], FluidsProxy.WATER, WATER_TICK_DELAY);
            }
        }
        // 组合行为靠 args[0] 往下串 原样返回才不会截断 multi_high 与 bush 的处理
        return args[0];
    }

    // multi_high 放置时会把底段的含水值原样抄给上两段 会在半空中渲染出水 这里钉回去
    @Override
    public void placeMultiState(Object thisBlock, Object[] args) {
        Object level = args[0];
        Object bottomPos = args[1];
        ImmutableBlockState bottom = BlockStateUtils.getOptionalCustomBlockState(args[2]).orElse(null);
        if (bottom == null) {
            return;
        }
        if (!bottom.get(this.waterloggedProperty)) {
            return;
        }
        writeUpperSegments(level, bottomPos, bottom);
    }

    @Override
    public void randomTick(Object thisBlock, Object[] args) {
        Object blockState = args[0];
        Object level = args[1];
        Object blockPos = args[2];
        ImmutableBlockState state = BlockStateUtils.getOptionalCustomBlockState(blockState).orElse(null);
        if (state == null || state.get(this.locationProperty) != BOTTOM) {
            return;
        }
        org.bukkit.World bukkitWorld = LevelProxy.INSTANCE.getWorld(level);
        BlockPos pos = LocationUtils.fromBlockPos(blockPos);
        playNightAmbience(bukkitWorld, pos);

        int age = state.get(this.ageProperty);
        if (age >= this.ageProperty.max) {
            return;
        }
        int brightness = BlockAndLightGetterProxy.INSTANCE.getRawBrightness(level, blockPos, 0);
        if (brightness < this.minGrowLight || brightness > this.maxGrowLight) {
            return;
        }
        int after = age + this.baseGrowth;
        if (after < this.ageProperty.max && RandomUtils.generateRandomFloat(0, 1) < growChance(bukkitWorld, pos)) {
            after++;
        }
        if (after > age) {
            grow(level, blockPos, state, Math.min(after, this.ageProperty.max));
        }
    }

    // 鱼越多长得越快 与模组一致 speed + speed * log2(count)
    private float growChance(org.bukkit.World world, BlockPos pos) {
        if (this.extraGrowChance <= 0) {
            return this.extraGrowChance;
        }
        int boosters = this.boosterCache.countAround(world, pos.x(), pos.y(), pos.z(), this.boosterRadius);
        if (boosters <= 0) {
            return this.extraGrowChance;
        }
        return this.extraGrowChance * (1f + (float) (Math.log(boosters) / Math.log(2)));
    }

    // 每次随机 tick 必响会让一大片田叠成噪音 概率下采样又会断断续续
    // 走 SoundCluster 按网格聚合 密的田更饱满但不会更吵
    private void playNightAmbience(org.bukkit.World world, BlockPos pos) {
        if (this.nightSound == null || world == null) {
            return;
        }
        long time = world.getTime();
        if (time < NIGHT_START || time > NIGHT_END) {
            return;
        }
        float bonus = this.soundCluster.accumulate(world, pos.x(), pos.y(), pos.z());
        if (bonus < 0) {
            return;
        }
        World ceWorld = BukkitAdaptor.adapt(world);
        if (ceWorld == null) {
            return;
        }
        ceWorld.playBlockSound(
                new WorldPosition(ceWorld, Vec3d.atCenterOf(new BlockPos(pos.x(), pos.y() + 1, pos.z()))),
                this.nightSound,
                SOUND_VOLUME_BASE + RandomUtils.generateRandomFloat(0, SOUND_VOLUME_RANGE) + bonus,
                SOUND_PITCH_BASE + RandomUtils.generateRandomFloat(0, SOUND_PITCH_RANGE)
        );
    }

    // 三段共用一个 age 上两段只是同一株的渲染 必须跟着底部一起改 否则模型会断层
    private boolean grow(Object level, Object bottomPos, ImmutableBlockState bottomState, int age) {
        ImmutableBlockState grown = bottomState.with(this.ageProperty, age);
        boolean success = VersionHelper.isOrAbove1_21_5
                ? CraftEventFactoryProxy.INSTANCE.handleBlockGrowEvent(level, bottomPos, grown.customBlockState().minecraftState(), UpdateFlags.UPDATE_CLIENTS)
                : CraftEventFactoryProxy.INSTANCE.handleBlockGrowEvent(level, bottomPos, grown.customBlockState().minecraftState());
        if (success) {
            writeUpperSegments(level, bottomPos, grown);
        }
        return success;
    }

    private void writeUpperSegments(Object level, Object bottomPos, ImmutableBlockState bottom) {
        ImmutableBlockState dry = bottom.with(this.waterloggedProperty, false);
        for (int segment = BOTTOM + 1; segment < SEGMENTS; segment++) {
            Object segmentPos = LocationUtils.above(bottomPos, segment);
            if (!isSameStalk(level, segmentPos, segment)) {
                continue;
            }
            LevelWriterProxy.INSTANCE.setBlock(level, segmentPos,
                    dry.with(this.locationProperty, segment).customBlockState().minecraftState(),
                    UpdateFlags.UPDATE_CLIENTS);
        }
    }

    private boolean isSameStalk(Object level, Object pos, int segment) {
        ImmutableBlockState state = BlockStateUtils.getOptionalCustomBlockState(BlockGetterProxy.INSTANCE.getBlockState(level, pos)).orElse(null);
        return state != null && state.owner().value() == super.blockDefinition && state.get(this.locationProperty) == segment;
    }

    // 空桶抽走底段的水 整株一起没
    @Override
    public Object pickupBlock(Object thisBlock, Object[] args) {
        Object level = args[PICKUP_LEVEL];
        Object pos = args[PICKUP_POS];
        ImmutableBlockState state = BlockStateUtils.getOptionalCustomBlockState(args[PICKUP_STATE]).orElse(null);
        if (state == null || state.get(this.locationProperty) != BOTTOM || !state.get(this.waterloggedProperty)) {
            return ItemStackProxy.EMPTY;
        }
        LevelWriterProxy.INSTANCE.setBlock(level, pos,
                state.with(this.waterloggedProperty, false).customBlockState().minecraftState(), UpdateFlags.UPDATE_ALL);
        LevelWriterProxy.INSTANCE.destroyBlock(level, pos, true);
        swingBucketHand(args);
        return ItemStackProxy.INSTANCE.newInstance(ItemsProxy.WATER_BUCKET, 1);
    }

    // 服务端补一个动画包
    private static void swingBucketHand(Object[] args) {
        if (args[PICKUP_PLAYER] == null) {
            return;
        }
        if (!(EntityProxy.INSTANCE.getBukkitEntity(args[PICKUP_PLAYER]) instanceof org.bukkit.entity.Player player)) {
            return;
        }
        Hands.swing(player, player.getInventory().getItemInMainHand().getType() == Material.BUCKET
                ? EquipmentSlot.HAND
                : EquipmentSlot.OFF_HAND);
    }

    // 交给 CE 的含水行为出原版舀水音效
    @Override
    public Object getPickupSound(Object thisBlock, Object[] args) {
        return Optional.empty();
    }

    @Override
    public boolean isValidBonemealTarget(Object thisBlock, Object[] args) {
        return bottomOf(args[0], args[1], args[2])
                .filter(state -> state.get(this.ageProperty) < this.ageProperty.max)
                .isPresent();
    }

    @Override
    public boolean isBonemealSuccess(Object thisBlock, Object[] args) {
        return true;
    }

    @Override
    public void performBonemeal(Object thisBlock, Object[] args) {
        Object level = args[0];
        Object pos = args[2];
        ImmutableBlockState clicked = BlockStateUtils.getOptionalCustomBlockState(args[3]).orElse(null);
        if (clicked == null) {
            return;
        }
        int segment = clicked.get(this.locationProperty);
        Object bottomPos = segment == BOTTOM ? pos : LocationUtils.below(pos, segment);
        ImmutableBlockState bottom = BlockStateUtils.getOptionalCustomBlockState(BlockGetterProxy.INSTANCE.getBlockState(level, bottomPos)).orElse(null);
        if (bottom == null || bottom.owner().value() != super.blockDefinition) {
            return;
        }
        int age = bottom.get(this.ageProperty);
        if (age >= this.ageProperty.max) {
            return;
        }
        org.bukkit.World bukkitWorld = LevelProxy.INSTANCE.getWorld(level);
        World world = BukkitAdaptor.adapt(bukkitWorld);
        int bonus = this.boneMealBonus.getInt(SimpleContext.of(ContextHolder.builder()
                .withParameter(DirectContextParameters.CUSTOM_BLOCK_STATE, bottom)
                .withParameter(DirectContextParameters.POSITION,
                        new WorldPosition(world, Vec3d.atCenterOf(LocationUtils.fromBlockPos(bottomPos))))
                .build()));
        int after = Math.min(age + bonus, this.ageProperty.max);
        if (after > age && grow(level, bottomPos, bottom, after)) {
            emitBoneMealParticles(bukkitWorld, LocationUtils.fromBlockPos(pos), clicked);
        }
    }

    // 视觉方块不是原版可施肥方块时 客户端收到 levelEvent 也不会自己出粒子 必须服务端补发
    private void emitBoneMealParticles(org.bukkit.World world, BlockPos pos, ImmutableBlockState clicked) {
        Object visualState = clicked.visualBlockState().minecraftState();
        if (BonemealableBlockProxy.CLASS.isInstance(BlockStateUtils.getBlockOwner(visualState))) {
            return;
        }
        Particles.emit(world, Particle.HAPPY_VILLAGER, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
                BONE_MEAL_PARTICLE_COUNT, BONE_MEAL_PARTICLE_SPREAD, BONE_MEAL_PARTICLE_SPREAD, BONE_MEAL_PARTICLE_SPREAD,
                0, null);
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        if (player == null || player.isAdventureMode()) {
            return InteractionResult.PASS;
        }
        if (context.getHand() != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (this.harvestRules.isBlocked(context.getItem())) {
            return InteractionResult.PASS;
        }
        if (!harvest(context.getLevel(), context.getClickedPos(), state, player)) {
            return InteractionResult.PASS;
        }
        player.swingHand(InteractionHand.MAIN_HAND);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    @Override
    public BlockPos rootPos(World world, BlockPos pos, ImmutableBlockState state) {
        int segment = state.get(this.locationProperty);
        return segment == BOTTOM ? pos : new BlockPos(pos.x(), pos.y() - segment, pos.z());
    }

    // 三段共用一个 age 收割一律归到底段 整株一起重置
    @Override
    public boolean harvest(World world, BlockPos pos, ImmutableBlockState state, Player player) {
        BlockPos bottomPos = rootPos(world, pos, state);
        if (!InteractGuard.canBreak(player, world, bottomPos.x(), bottomPos.y(), bottomPos.z())) {
            return false;
        }
        Object level = world.minecraftWorld();
        Object nmsBottomPos = LocationUtils.toBlockPos(bottomPos);
        ImmutableBlockState bottom = BlockStateUtils.getOptionalCustomBlockState(
                BlockGetterProxy.INSTANCE.getBlockState(level, nmsBottomPos)).orElse(null);
        if (bottom == null || bottom.owner().value() != super.blockDefinition || !this.harvestRules.isMature(bottom)) {
            return false;
        }
        this.harvestRules.logBreak(world, bottomPos, player);
        WorldPosition position = new WorldPosition(world, Vec3d.atCenterOf(bottomPos));
        this.harvestRules.dropLoot(world, position, bottom, player);
        ImmutableBlockState reset = bottom.with(this.ageProperty, this.harvestRules.resetAge());
        LevelWriterProxy.INSTANCE.setBlock(level, nmsBottomPos,
                reset.customBlockState().minecraftState(), UpdateFlags.UPDATE_CLIENTS);
        writeUpperSegments(level, nmsBottomPos, reset);
        this.harvestRules.playSound(world, position);
        return true;
    }

    // 骨粉可能点在任意一段上 统一回到底部判断
    private Optional<ImmutableBlockState> bottomOf(Object level, Object pos, Object blockState) {
        ImmutableBlockState state = BlockStateUtils.getOptionalCustomBlockState(blockState).orElse(null);
        if (state == null) {
            return Optional.empty();
        }
        int segment = state.get(this.locationProperty);
        if (segment == BOTTOM) {
            return Optional.of(state);
        }
        return BlockStateUtils.getOptionalCustomBlockState(BlockGetterProxy.INSTANCE.getBlockState(level, LocationUtils.below(pos, segment)))
                .filter(bottom -> bottom.owner().value() == super.blockDefinition);
    }

    private static class Factory implements BlockBehaviorFactory<RiceCropBehavior> {
        private static final String[] GROW_SPEED = {"grow_speed", "grow-speed"};
        private static final String[] LIGHT_REQUIREMENT = {"light_requirement", "light-requirement"};
        private static final String[] MAX_LIGHT_REQUIREMENT = {"max_light_requirement", "max-light-requirement"};
        private static final String[] AGE_BONUS = {"bone_meal_age_bonus", "bone-meal-age-bonus"};
        private static final String[] BOOSTER_ENTITIES = {"booster_entities", "booster-entities"};
        private static final String[] BOOSTER_RADIUS = {"booster_radius", "booster-radius"};
        private static final String[] BOOSTER_CACHE_TICKS = {"booster_cache_ticks", "booster-cache-ticks"};
        private static final String[] BOOSTER_CACHE_MAX_CHUNKS = {"booster_cache_max_chunks", "booster-cache-max-chunks"};
        private static final String[] SOUND_MAX_CELLS = {"night_sound_max_cells", "night-sound-max-cells"};
        // 每世界的缓存上限 超了按时间戳砍掉较旧的一半 取值只影响内存占用不影响行为
        private static final int DEFAULT_CACHE_MAX_ENTRIES = 4096;
        private static final String[] NIGHT_SOUND = {"night_sound", "night-sound"};
        private static final String[] SOUND_CELL_SIZE = {"night_sound_cell_size", "night-sound-cell-size"};
        private static final String[] SOUND_COOLDOWN = {"night_sound_cooldown", "night-sound-cooldown"};
        private static final String[] SOUND_VOLUME_STEP = {"night_sound_volume_step", "night-sound-volume-step"};
        private static final String[] SOUND_MAX_BONUS = {"night_sound_max_bonus", "night-sound-max-bonus"};
        // 一个代表声源覆盖八格见方 与原版音效 16 格衰减半径匹配
        private static final int DEFAULT_CELL_SIZE = 8;
        // 同一格最短间隔两秒 一片田有多个格 错开之后整体是连续的
        private static final int DEFAULT_SOUND_COOLDOWN = 40;
        // 数量每翻十倍加这么多音量 取自声压的对数叠加
        private static final float DEFAULT_VOLUME_STEP = 0.15f;
        private static final float DEFAULT_MAX_BONUS = 0.4f;
        private static final float DEFAULT_GROW_SPEED = 0.0625f;
        private static final int DEFAULT_BOOSTER_RADIUS = 1;
        private static final int DEFAULT_BOOSTER_CACHE_TICKS = 2000;
        private static final int DEFAULT_HARVEST_RESET_AGE = 1;

        @Override
        public RiceCropBehavior create(BlockDefinition block, ConfigSection section) {
            String sound = BehaviorConfig.getString(section, null, NIGHT_SOUND);
            return new RiceCropBehavior(
                    block,
                    (IntegerProperty) BlockBehaviorFactory.getProperty(section.path(), block, "age", Integer.class),
                    (IntegerProperty) BlockBehaviorFactory.getProperty(section.path(), block, "location", Integer.class),
                    BlockBehaviorFactory.getProperty(section.path(), block, "waterlogged", Boolean.class),
                    section.getFloat(GROW_SPEED, DEFAULT_GROW_SPEED),
                    section.getInt(LIGHT_REQUIREMENT),
                    section.getInt(MAX_LIGHT_REQUIREMENT, 15),
                    section.getNumber(AGE_BONUS, ConfigConstants.CONSTANT_ONE),
                    sound == null ? null : Key.of(sound),
                    new SoundCluster(
                            BehaviorConfig.getInt(section, DEFAULT_CELL_SIZE, SOUND_CELL_SIZE),
                            BehaviorConfig.getInt(section, DEFAULT_SOUND_COOLDOWN, SOUND_COOLDOWN),
                            BehaviorConfig.getFloat(section, DEFAULT_VOLUME_STEP, SOUND_VOLUME_STEP),
                            BehaviorConfig.getFloat(section, DEFAULT_MAX_BONUS, SOUND_MAX_BONUS),
                            BehaviorConfig.getInt(section, DEFAULT_CACHE_MAX_ENTRIES, SOUND_MAX_CELLS)),
                    BehaviorConfig.getInt(section, DEFAULT_BOOSTER_RADIUS, BOOSTER_RADIUS),
                    new ChunkEntityCache(
                            boosterTypes(BehaviorConfig.getStringList(section, List.of(), BOOSTER_ENTITIES)),
                            BehaviorConfig.getInt(section, DEFAULT_BOOSTER_CACHE_TICKS, BOOSTER_CACHE_TICKS),
                            BehaviorConfig.getInt(section, DEFAULT_CACHE_MAX_ENTRIES, BOOSTER_CACHE_MAX_CHUNKS)),
                    CropHarvestRules.fromConfig(block, section, DEFAULT_HARVEST_RESET_AGE)
            );
        }

        private static Set<EntityType> boosterTypes(List<String> ids) {
            Set<EntityType> types = EnumSet.noneOf(EntityType.class);
            for (String id : ids) {
                String name = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
                try {
                    types.add(EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("rice_crop: 未知的加速生物 " + id);
                }
            }
            return types;
        }
    }
}
