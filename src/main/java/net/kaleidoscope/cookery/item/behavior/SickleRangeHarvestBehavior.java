package net.kaleidoscope.cookery.item.behavior;

import net.kaleidoscope.cookery.api.event.SickleHarvestEvent;
import net.kaleidoscope.cookery.util.BehaviorConfig;
import net.kaleidoscope.cookery.util.EventUtils;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.momirealms.craftengine.bukkit.item.BukkitItem;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// 镰刀范围收割 右键以点击方块为中心的 5x5x2 范围
// 成熟作物收割后重置为 age 0 让它继续生长 草丛灌木直接破坏
public final class SickleRangeHarvestBehavior extends ItemBehavior {
    public static final ItemBehaviorFactory<SickleRangeHarvestBehavior> FACTORY = new Factory();

    private static final int HORIZONTAL_RADIUS = 2;
    private static final int HEIGHT = 2;
    private static final int DEFAULT_COOLDOWN_TICKS = 10;
    private static final float SWEEP_VOLUME = 1.0f;
    private static final float SWEEP_PITCH = 1.0f;

    private final int cooldownTicks;
    private final boolean damageItem;
    private final Set<Key> crops;
    private final Set<Key> bushes;
    private final Set<Key> blacklist;

    private SickleRangeHarvestBehavior(int cooldownTicks, boolean damageItem,
                                       Set<Key> crops, Set<Key> bushes, Set<Key> blacklist) {
        this.cooldownTicks = cooldownTicks;
        this.damageItem = damageItem;
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

        World level = context.getLevel();
        org.bukkit.World bukkitWorld = (org.bukkit.World) level.platformWorld();
        BlockPos origin = context.getClickedPos();

        int harvested = 0;
        for (int x = -HORIZONTAL_RADIUS; x <= HORIZONTAL_RADIUS; x++) {
            for (int z = -HORIZONTAL_RADIUS; z <= HORIZONTAL_RADIUS; z++) {
                harvested += harvestColumn(origin.x() + x, origin.y(), origin.z() + z,
                        level, bukkitWorld, player, bukkitPlayer, heldStack);
            }
        }

        if (harvested == 0) {
            return InteractionResult.PASS;
        }

        Location loc = bukkitPlayer.getLocation();
        bukkitWorld.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, SWEEP_VOLUME, SWEEP_PITCH);
        bukkitPlayer.swingMainHand();
        if (damageItem && held != null) {
            held.hurtAndBreak(harvested, player, null);
        }
        if (cooldownTicks > 0 && heldStack != null) {
            bukkitPlayer.setCooldown(heldStack, cooldownTicks);
        }
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 收割一根竖列 返回这列收到的数量
    private int harvestColumn(int x, int baseY, int z, World level, org.bukkit.World bukkitWorld,
                              Player player, org.bukkit.entity.Player bukkitPlayer, ItemStack heldStack) {
        int harvested = 0;
        for (int y = baseY; y < baseY + HEIGHT; y++) {
            // 逐格校验破坏权限 范围收割不能整片穿透领地保护
            if (!InteractGuard.canBreak(player, level, x, y, z)) {
                continue;
            }
            if (harvestOne(bukkitWorld.getBlockAt(x, y, z), bukkitPlayer, player, heldStack)) {
                harvested++;
            }
        }
        return harvested;
    }

    private boolean harvestOne(Block block, org.bukkit.entity.Player bukkitPlayer, Player player, ItemStack sickle) {
        if (block.getType().isAir()) {
            return false;
        }
        Key id = blockKey(block);
        if (blacklist.contains(id)) {
            return false;
        }
        // 先给监听器接管的机会 取消后是否仍算耐久由监听器自己定
        SickleHarvestEvent event = new SickleHarvestEvent(bukkitPlayer, sickle, block);
        if (EventUtils.fireAndCheckCancel(event)) {
            return event.costDurability();
        }
        // 只认白名单里的作物 不能用 instanceof Ageable 泛判
        // 火焰 甘蔗 竹子 仙人掌 海带在 bukkit 里都是 Ageable 泛判会把火重置成 age 0 烧不完
        // 也会让甘蔗长满时白拿一个产物而方块还在
        if (crops.contains(id)) {
            if (!(block.getBlockData() instanceof Ageable ageable)
                    || ageable.getAge() < ageable.getMaximumAge()) {
                return false;
            }
            return harvestCrop(block, ageable, bukkitPlayer);
        }
        // 草丛灌木直接破坏 走 CE 的破坏以便自定义方块也能正确掉落
        if (bushes.contains(id)) {
            player.breakBlock(block.getX(), block.getY(), block.getZ());
            return true;
        }
        return false;
    }

    private boolean harvestCrop(Block block, Ageable ageable, org.bukkit.entity.Player bukkitPlayer) {
        ItemStack tool = bukkitPlayer.getInventory().getItemInMainHand();
        Location dropAt = block.getLocation().add(0.5, 0.5, 0.5);
        block.getDrops(tool, bukkitPlayer).forEach(drop -> block.getWorld().dropItemNaturally(dropAt, drop));

        Ageable reset = (Ageable) ageable.clone();
        reset.setAge(0);
        // 水稻这类泡在水里的作物要保住含水状态 否则重置后水会消失
        if (reset instanceof Waterlogged resetWater && block.getBlockData() instanceof Waterlogged current) {
            resetWater.setWaterlogged(current.isWaterlogged());
        }
        block.setBlockData(reset, true);
        block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, block.getType());
        return true;
    }

    // 一次挥砍要查 50 格 每格现 new 一个 Key 纯属浪费 按 Material 缓存
    // folia 上收割跑在各 region 线程 这张表是跨线程共享的 必须用并发容器
    private static final Map<Material, Key> BLOCK_KEYS = new ConcurrentHashMap<>();

    private static Key blockKey(Block block) {
        return BLOCK_KEYS.computeIfAbsent(block.getType(), m -> Key.of(m.getKey().toString()));
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
        private static final String[] COOLDOWN_KEYS = {"cooldown", "cooldown_ticks", "cooldown-ticks"};
        private static final String[] DAMAGE_KEYS = {"damage_item", "damage-item"};
        private static final String[] CROP_KEYS = {"crops", "crop"};
        private static final String[] BUSH_KEYS = {"bushes", "bush"};
        private static final String[] BLACKLIST_KEYS = {"blacklist", "black_list", "black-list"};

        @Override
        public SickleRangeHarvestBehavior create(Pack pack, Path path, Key key, ConfigSection section) {
            return new SickleRangeHarvestBehavior(
                    BehaviorConfig.getInt(section, DEFAULT_COOLDOWN_TICKS, COOLDOWN_KEYS),
                    BehaviorConfig.getBoolean(section, true, DAMAGE_KEYS),
                    keySet(BehaviorConfig.getStringList(section, DEFAULT_CROPS, CROP_KEYS)),
                    keySet(BehaviorConfig.getStringList(section, DEFAULT_BUSHES, BUSH_KEYS)),
                    keySet(BehaviorConfig.getStringList(section, List.of(), BLACKLIST_KEYS)));
        }

        private static Set<Key> keySet(List<String> ids) {
            Set<Key> keys = new HashSet<>(ids.size());
            ids.forEach(id -> keys.add(Key.of(id)));
            return Set.copyOf(keys);
        }
    }
}
