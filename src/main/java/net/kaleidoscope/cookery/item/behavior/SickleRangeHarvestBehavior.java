package net.kaleidoscope.cookery.item.behavior;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.kaleidoscope.cookery.api.event.SickleHarvestEvent;
import net.kaleidoscope.cookery.block.behavior.GenericAgeCrop;
import net.kaleidoscope.cookery.block.behavior.HarvestableCrop;
import net.kaleidoscope.cookery.util.BehaviorConfig;
import net.kaleidoscope.cookery.util.EventUtils;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.item.BukkitItem;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.behavior.ItemBehavior;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.inventory.ItemStack;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

// 镰刀范围收割
public final class SickleRangeHarvestBehavior extends ItemBehavior {
    public static final ItemBehaviorFactory<SickleRangeHarvestBehavior> FACTORY = new Factory();

    private static final int DEFAULT_HORIZONTAL_RADIUS = 2;
    private static final int MAX_HORIZONTAL_RADIUS = 8;
    private static final int DEFAULT_HEIGHT = 2;
    private static final int DEFAULT_COOLDOWN_TICKS = 10;
    private static final float SWEEP_VOLUME = 1.0f;
    private static final float SWEEP_PITCH = 1.0f;

    private final int horizontalRadius;
    private final int height;
    private final int cooldownTicks;
    private final boolean damageItem;
    private final int durabilityPerUse;
    private final Set<Key> crops;
    private final Set<Key> bushes;
    private final Set<Key> blacklist;

    private SickleRangeHarvestBehavior(int horizontalRadius, int height, int cooldownTicks, boolean damageItem,
                                       int durabilityPerUse, Set<Key> crops, Set<Key> bushes, Set<Key> blacklist) {
        this.horizontalRadius = horizontalRadius;
        this.height = height;
        this.cooldownTicks = cooldownTicks;
        this.damageItem = damageItem;
        this.durabilityPerUse = durabilityPerUse;
        this.crops = crops;
        this.bushes = bushes;
        this.blacklist = blacklist;
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) player.platformPlayer();
        Item held = context.getItem();
        ItemStack heldStack = held instanceof BukkitItem bukkitItem ? bukkitItem.getBukkitItem() : null;
        if (heldStack != null && bukkitPlayer.hasCooldown(heldStack)) {
            return InteractionResult.PASS;
        }

        Sweep sweep = new Sweep(context.getLevel(), (org.bukkit.World) context.getLevel().platformWorld(),
                player, bukkitPlayer, heldStack, new LongOpenHashSet());
        BlockPos origin = context.getClickedPos();
        int harvested = 0;
        for (int x = -this.horizontalRadius; x <= this.horizontalRadius; x++) {
            for (int z = -this.horizontalRadius; z <= this.horizontalRadius; z++) {
                harvested += harvestColumn(sweep, origin.x() + x, origin.y(), origin.z() + z);
            }
        }

        if (harvested == 0) {
            return InteractionResult.PASS;
        }

        sweep.world.playSound(bukkitPlayer.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                SoundCategory.PLAYERS, SWEEP_VOLUME, SWEEP_PITCH);
        bukkitPlayer.swingMainHand();
        // 按次扣不按格扣 照格扣一挥就是几十点耐久
        if (this.damageItem && held != null && !player.canInstabuild()) {
            held.hurtAndBreak(this.durabilityPerUse, player, null);
        }
        if (this.cooldownTicks > 0 && heldStack != null) {
            bukkitPlayer.setCooldown(heldStack, this.cooldownTicks);
        }
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 一次挥砍的上下文 逐格传六个参数会让每个方法签名都糊成一片
    // stalks 记已收过的根部 多段作物一刀会同时命中同一株的两段
    private record Sweep(World level, org.bukkit.World world, Player player,
                         org.bukkit.entity.Player bukkitPlayer, ItemStack sickle, LongSet stalks) {
    }

    // 收割一根竖列 返回这列收到的数量
    private int harvestColumn(Sweep sweep, int x, int baseY, int z) {
        int harvested = 0;
        for (int y = baseY; y < baseY + this.height; y++) {
            Block block = sweep.world.getBlockAt(x, y, z);
            // 空气格先筛掉 领地查询比读方块贵得多 一刀几十格全查会把它变成热点
            if (block.getType().isAir()) {
                continue;
            }
            // 逐格校验破坏权限 范围收割不能整片穿透领地保护
            if (!InteractGuard.canBreak(sweep.player, sweep.level, x, y, z)) {
                continue;
            }
            if (harvestOne(sweep, block)) {
                harvested++;
            }
        }
        return harvested;
    }

    private boolean harvestOne(Sweep sweep, Block block) {
        // CE 自定义方块的 getType 返回的是视觉方块 绊线甘蔗之类 拿它查白名单既割不到自定义作物
        // 也会被视觉方块误判成原版作物 所以必须先把自定义方块分流出去
        ImmutableBlockState customState = CraftEngineBlocks.getCustomBlockState(block);
        if (customState != null && !customState.isEmpty()) {
            Boolean taken = fireHarvestEvent(sweep, block);
            if (taken != null) {
                return taken;
            }
            return harvestCustomCrop(sweep, block, customState);
        }
        Key id = blockKey(block);
        if (blacklist.contains(id)) {
            return false;
        }
        // 先给监听器接管的机会 取消后是否仍算耐久由监听器自己定
        Boolean taken = fireHarvestEvent(sweep, block);
        if (taken != null) {
            return taken;
        }
        // 只认白名单里的作物 火焰 甘蔗 竹子 仙人掌 海带在 bukkit 里都是 Ageable
        // 用 instanceof 泛判会把火重置成 age 0 烧不完 也会让甘蔗白掉一个产物
        if (crops.contains(id)) {
            if (!(block.getBlockData() instanceof Ageable ageable)
                    || ageable.getAge() < ageable.getMaximumAge()) {
                return false;
            }
            return harvestVanillaCrop(sweep, block, ageable);
        }
        // 草丛灌木直接破坏 走 CE 的破坏以便自定义方块也能正确掉落
        if (bushes.contains(id)) {
            sweep.player.breakBlock(block.getX(), block.getY(), block.getZ());
            return true;
        }
        return false;
    }

    // 监听器接管了就返回它给的耐久决定 没接管返回 null 表示继续走默认收割
    // 没人监听时连事件对象都不建 一刀几十格全建纯属浪费
    private static Boolean fireHarvestEvent(Sweep sweep, Block block) {
        if (!EventUtils.hasListeners(SickleHarvestEvent.getHandlerList())) {
            return null;
        }
        SickleHarvestEvent event = new SickleHarvestEvent(sweep.bukkitPlayer(), sweep.sickle(), block);
        return EventUtils.fireAndCheckCancel(event) ? event.costDurability() : null;
    }

    private boolean harvestVanillaCrop(Sweep sweep, Block block, Ageable ageable) {
        if (!sweep.player().canInstabuild()) {
            ItemStack tool = sweep.bukkitPlayer().getInventory().getItemInMainHand();
            Location dropAt = block.getLocation().add(0.5, 0.5, 0.5);
            block.getDrops(tool, sweep.bukkitPlayer()).forEach(drop -> block.getWorld().dropItemNaturally(dropAt, drop));
        }
        Ageable reset = (Ageable) ageable.clone();
        reset.setAge(0);
        // 水稻这类泡在水里的作物要保住含水状态 否则重置后水会消失
        if (reset instanceof Waterlogged resetWater && block.getBlockData() instanceof Waterlogged current) {
            resetWater.setWaterlogged(current.isWaterlogged());
        }
        EventUtils.logBlockBreak(block, sweep.bukkitPlayer());
        block.setBlockData(reset, true);
        block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, block.getType());
        return true;
    }

    // CE 作物一律委托给方块行为
    private boolean harvestCustomCrop(Sweep sweep, Block block, ImmutableBlockState state) {
        HarvestableCrop crop = state.behavior().getFirst(HarvestableCrop.class);
        if (crop == null) {
            crop = GenericAgeCrop.of(state);
        }
        if (crop == null) {
            return false;
        }
        World world = BukkitAdaptor.adapt(block.getWorld());
        BlockPos pos = new BlockPos(block.getX(), block.getY(), block.getZ());
        // 一株只收一次 一刀的扫描范围会同时命中同一株的两段
        BlockPos root = crop.rootPos(world, pos, state);
        if (!sweep.stalks().add(packed(root.x(), root.y(), root.z()))) {
            return false;
        }
        if (!crop.harvest(world, pos, state, sweep.player())) {
            return false;
        }
        block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, block.getType());
        return true;
    }

    private static long packed(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    private static final Key[] BLOCK_KEYS = new Key[Material.values().length];

    private static Key blockKey(Block block) {
        Material material = block.getType();
        Key cached = BLOCK_KEYS[material.ordinal()];
        if (cached == null) {
            cached = Key.of(material.getKey().toString());
            BLOCK_KEYS[material.ordinal()] = cached;
        }
        return cached;
    }

    private static final List<String> DEFAULT_CROPS = List.of(
            "minecraft:wheat",
            "minecraft:carrots",
            "minecraft:potatoes",
            "minecraft:beetroots",
            "minecraft:nether_wart",
            "minecraft:cocoa",
            "minecraft:sweet_berry_bush",
            "minecraft:torchflower_crop",
            "minecraft:pitcher_crop"
    );

    private static final List<String> DEFAULT_BUSHES = List.of(
            "minecraft:grass",
            "minecraft:short_grass",
            "minecraft:tall_grass",
            "minecraft:fern",
            "minecraft:large_fern",
            "minecraft:short_dry_grass",
            "minecraft:tall_dry_grass",
            "minecraft:seagrass",
            "minecraft:tall_seagrass",
            "minecraft:dead_bush",
            "minecraft:bush"
    );

    private static final class Factory implements ItemBehaviorFactory<SickleRangeHarvestBehavior> {
        private static final String[] RADIUS_KEYS = {"radius", "horizontal_radius", "horizontal-radius"};
        private static final String[] HEIGHT_KEYS = {"height"};
        private static final String[] COOLDOWN_KEYS = {"cooldown", "cooldown_ticks", "cooldown-ticks"};
        private static final String[] DAMAGE_KEYS = {"damage_item", "damage-item"};
        private static final String[] DURABILITY_KEYS = {"durability_per_use", "durability-per-use"};
        private static final int DEFAULT_DURABILITY_PER_USE = 1;
        private static final String[] CROP_KEYS = {"crops", "crop"};
        private static final String[] BUSH_KEYS = {"bushes", "bush"};
        private static final String[] BLACKLIST_KEYS = {"blacklist", "black_list", "black-list"};

        @Override
        public SickleRangeHarvestBehavior create(Pack pack, Path path, Key key, ConfigSection section) {
            return new SickleRangeHarvestBehavior(
                    Math.max(0, Math.min(MAX_HORIZONTAL_RADIUS, BehaviorConfig.getInt(section, DEFAULT_HORIZONTAL_RADIUS, RADIUS_KEYS))),
                    Math.max(1, BehaviorConfig.getInt(section, DEFAULT_HEIGHT, HEIGHT_KEYS)),
                    BehaviorConfig.getInt(section, DEFAULT_COOLDOWN_TICKS, COOLDOWN_KEYS),
                    BehaviorConfig.getBoolean(section, true, DAMAGE_KEYS),
                    BehaviorConfig.getInt(section, DEFAULT_DURABILITY_PER_USE, DURABILITY_KEYS),
                    BehaviorConfig.getKeySet(section, DEFAULT_CROPS, CROP_KEYS),
                    BehaviorConfig.getKeySet(section, DEFAULT_BUSHES, BUSH_KEYS),
                    BehaviorConfig.getKeySet(section, List.of(), BLACKLIST_KEYS));
        }
    }
}
