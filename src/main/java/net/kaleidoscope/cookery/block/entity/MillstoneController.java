package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.api.MillstoneAnimals;
import net.kaleidoscope.cookery.block.behavior.MillstoneBehavior;
import net.kaleidoscope.cookery.block.entity.render.Particles;

import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureController;
import net.momirealms.craftengine.core.entity.furniture.element.FurnitureElement;
import net.momirealms.craftengine.core.entity.furniture.hitbox.FurnitureHitBox;
import net.momirealms.craftengine.core.entity.furniture.tick.FurnitureTicker;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.UUIDUtils;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.core.world.context.InteractEntityContext;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.ListTag;
import net.momirealms.craftengine.libraries.nbt.Tag;
import net.kaleidoscope.cookery.api.event.MillstoneGrindCompleteEvent;
import net.kaleidoscope.cookery.util.EventUtils;
import net.kaleidoscope.cookery.util.Hands;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.util.FoliaUtil;
import net.kaleidoscope.cookery.util.Localization;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.ApplianceFoodRegistry;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.FoodRecipeResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Snow;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class MillstoneController extends FurnitureController {

    public static final ConcurrentHashMap<UUID, MillstoneController> ACTIVE_PUSHERS = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<UUID, MillstoneController> ACTIVE_ANIMAL_PULLERS = new ConcurrentHashMap<>();

    // 罢工标记写在动物自己的 PDC 上 关闭 AI 并禁骑乘/道具交互 直到重新拴绳绑定+正常停止才恢复
    private static final NamespacedKey STRUCK_KEY =
            Objects.requireNonNull(NamespacedKey.fromString("kaleidoscopecookery:millstone_struck"));

    public static boolean isStruck(Entity e) {
        return e.getPersistentDataContainer().has(STRUCK_KEY, PersistentDataType.BYTE);
    }

    public static void setStruck(Entity e, boolean struck) {
        if (struck) {
            e.getPersistentDataContainer().set(STRUCK_KEY, PersistentDataType.BYTE, (byte) 1);
        } else {
            e.getPersistentDataContainer().remove(STRUCK_KEY);
        }
    }

    // 拴绳生物搜索半径 原版拴绳超过 10 格就断 20 足够覆盖
    private static final double LEASH_SEARCH_RADIUS = 20;
    // 上面半径换算成区块 用于扫描前的 region 归属校验 切比雪夫距离
    private static final int LEASH_SEARCH_CHUNK_RADIUS = ((int) LEASH_SEARCH_RADIUS >> 4) + 1;

    // 拉磨会关掉玩家的飞行权限 该状态随 playerdata 落盘 只存内存字段则硬崩溃后权限永久丢失
    private static final NamespacedKey RESTORE_ALLOW_FLIGHT_KEY =
            Objects.requireNonNull(NamespacedKey.fromString("kaleidoscopecookery:millstone_restore_allow_flight"));

    private static void markFlightRestore(org.bukkit.entity.Player player) {
        player.getPersistentDataContainer().set(
                RESTORE_ALLOW_FLIGHT_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    private static boolean hasFlightMark(org.bukkit.entity.Player player) {
        return player.getPersistentDataContainer()
                .has(RESTORE_ALLOW_FLIGHT_KEY, PersistentDataType.BYTE);
    }

    private static void clearFlightMark(org.bukkit.entity.Player player) {
        player.getPersistentDataContainer().remove(RESTORE_ALLOW_FLIGHT_KEY);
    }

    // 同一次会话内正常停止 期间权限不会变 直接还原
    public static void restoreFlight(org.bukkit.entity.Player player) {
        if (!hasFlightMark(player)) {
            return;
        }
        clearFlightMark(player);
        player.setAllowFlight(true);
    }

    // 崩溃后重新登录的兜底 只清标记不授予飞行
    public static void clearFlightMarkOnJoin(org.bukkit.entity.Player player) {
        clearFlightMark(player);
    }

    public static void register(UUID uuid, MillstoneController ctrl) {
        ACTIVE_PUSHERS.put(uuid, ctrl);
    }

    public static void unregister(UUID uuid) {
        if (uuid != null) {
            ACTIVE_PUSHERS.remove(uuid);
        }
    }

    // 当前拉磨者的拉一圈秒数 玩家默认 7.5 生物按各自档案 被打时临时用骡子的速度
    private float currentSeconds = (float) MillstoneAnimals.PLAYER_SECONDS;
    // 当前拉磨者的绕磨半径 起始与行走位置离磨心的距离
    private float currentRadius = (float) MillstoneAnimals.DEFAULT_ORBIT_RADIUS;

    // 生物拉磨时每隔这么多 tick 掷一次骰子 命中则罢工
    private static final int STRIKE_CHECK_INTERVAL = 100;
    private static final double STRIKE_CHANCE = 0.005;

    // 视觉旋转每隔几 tick 更新一次
    private static final int VISUAL_UPDATE_INTERVAL = 5;

    // 拉磨者跟位 偏离超过这个距离平方才瞬移归位 平时靠速度推进
    private static final double TELEPORT_SNAP_DIST_SQ = 4.0;
    // 每 tick 朝目标推进的比例与位移上限
    private static final double STEP_FACTOR = 0.8;
    private static final double MAX_STEP = 0.25;
    // 位移小于这个平方值就不更新朝向 免得原地抖动
    private static final double YAW_UPDATE_EPSILON = 1e-6;

    private UUID pendingAnimalUUID = null;
    private boolean savedAnimalWasAI = true;

    private final MillstoneBehavior behavior;
    private final MillstoneElement element;

    private boolean animating = false;
    private int rawTick = 0;
    private float orbitAngle = 0f;
    private float currentAngle = 0f;
    private boolean boosted = false;

    private Player pullingPlayer = null;

    private LivingEntity pullingAnimal = null;
    private boolean animalWasAI = true;
    private org.bukkit.entity.Player leadOwner = null;

    private static final String DATA_KEY = "kaleidoscopecookery:millstone";
    private static final String K_ANIMATING = "animating";
    private static final String K_ORBIT_ANGLE = "orbit_angle";
    private static final String K_CURRENT_ANGLE = "current_angle";
    private static final String K_BOOSTED = "boosted";
    private static final String K_ANIM_SECONDS = "anim_seconds";
    private static final String K_RAW_TICK = "raw_tick";
    private static final String K_ANIMAL_UUID = "animal_uuid";
    private static final String K_ANIMAL_WAS_AI = "animal_was_ai";
    private static final String K_GRIND_ITEMS = "grind_items";
    private static final String K_GRIND_DATA_VERSION = "grind_data_version";
    private static final String K_SLOT = "slot";
    private static final String K_ITEM = "item";
    private static final String K_PROGRESS = "progress";
    private static final String K_ROTATIONS = "rotations";

    public static final int GRIND_SLOTS = 8;
    private final Item[] grindItems = new Item[GRIND_SLOTS];
    // grindProgress 已转圈数 requiredRotations 该料产出所需圈数
    private final int[] grindProgress = new int[GRIND_SLOTS];
    private final int[] requiredRotations = new int[GRIND_SLOTS];

    public MillstoneController(Furniture furniture, MillstoneBehavior behavior) {
        super(furniture);
        this.behavior = behavior;
        Arrays.fill(this.grindItems, Item.empty());
        this.element = new MillstoneElement(this, furniture.position());
    }

    public MillstoneBehavior behavior() {
        return behavior;
    }

    public boolean canGrind(Item item) {
        return ApplianceFoodRegistry.instance().isAllowed(ApplianceType.MILLSTONE, item.id());
    }

    private Item getGrindResult(Item input) {
        return FoodRecipeRegistry.instance()
                .findAccurate(ApplianceType.MILLSTONE, input.id())
                .map(FoodRecipeResult::item)
                .orElse(input.copy());
    }

    private int firstEmptyGrindSlot() {
        for (int i = 0; i < GRIND_SLOTS; i++) {
            if (grindItems[i].isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public boolean tryAddGrind(Item item) {
        int i = firstEmptyGrindSlot();
        if (i < 0) {
            return false;
        }
        grindItems[i] = item.copyWithCount(1);
        grindProgress[i] = 0;
        requiredRotations[i] = FoodRecipeRegistry.instance().findGrindRotations(item.id(), behavior.grindRotations);
        element.spawnGrindSlot(i, grindItems[i]);
        furniture().setUnsaved();
        return true;
    }

    private void clearGrindSlot(int i) {
        grindItems[i] = Item.empty();
        grindProgress[i] = 0;
        requiredRotations[i] = 0;
        element.removeGrindSlot(i);
    }

    public boolean takeGrind(Player player, InteractionHand hand) {
        for (int i = 0; i < GRIND_SLOTS; i++) {
            if (grindItems[i].isEmpty()) {
                continue;
            }
            InventoryUtils.giveOrHold(player, hand, grindItems[i].copy());
            clearGrindSlot(i);
            furniture().setUnsaved();
            tryFeedFromChest();
            return true;
        }
        return false;
    }

    public boolean grindIsEmpty() {
        for (int i = 0; i < GRIND_SLOTS; i++) {
            if (!grindItems[i].isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // 每转满一圈推进研磨 转够该料所需圈数就产出 真实耗时由转速(秒/圈)决定 转得慢产得慢
    private void advanceGrind() {
        List<Item> products = new ArrayList<>();
        for (int i = 0; i < GRIND_SLOTS; i++) {
            if (grindItems[i].isEmpty()) {
                continue;
            }
            grindProgress[i]++;
            if (grindProgress[i] >= requiredRotations[i]) {
                products.add(getGrindResult(grindItems[i]));
                clearGrindSlot(i);
            }
        }

        if (products.isEmpty()) {
            return;
        }
        ejectProducts(products);
        furniture().setUnsaved();
        tryFeedFromChest();
    }

    // 按槽取 不返回数组本身 否则调用方能直接改锅内物品且绕过脏标记
    public Item grindItem(int slot) {
        return grindItems[slot];
    }

    private double[] facingDir() {
        double rad = Math.toRadians(furniture().position().yRot());
        return new double[]{-Math.sin(rad), Math.cos(rad)};
    }

    // 生物罢工
    private void strikeAnimal() {
        LivingEntity animal = this.pullingAnimal;
        if (animal == null) {
            return;
        }
        ACTIVE_ANIMAL_PULLERS.remove(animal.getUniqueId());
        setStruck(animal, true);
        if (animal.isValid()) {
            animal.setGravity(true);
            animal.setVelocity(new Vector(0, 0, 0));
        }
        if (leadOwner != null) {
            ejectLead();
        }
        this.pullingAnimal = null;
        this.leadOwner = null;
        this.animating = false;
        this.boosted = false;
        this.rawTick = 0;
        furniture().setUnsaved();
    }

    // 拴绳从产物口弹出
    private void ejectLead() {
        org.bukkit.World world = getBukkitWorld();
        double[] dir = facingDir();
        double ex = furniture().position().x + dir[0] * 0.6;
        double ey = furniture().position().y + 1.0;
        double ez = furniture().position().z + dir[1] * 0.6;
        org.bukkit.entity.Item dropped = world.dropItem(
                new org.bukkit.Location(world, ex, ey, ez), new ItemStack(Material.LEAD));
        dropped.setVelocity(new Vector(dir[0] * 0.25, 0.12, dir[1] * 0.25));
    }

    // 磨完一批 触发事件 未被取消则逐个朝石磨朝向喷出
    private void ejectProducts(List<Item> products) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Item product : products) {
            if (product.isEmpty()) {
                continue;
            }
            stacks.add(ItemStackUtils.getBukkitStack(product.minecraftItem()));
        }
        if (stacks.isEmpty()) {
            return;
        }

        WorldPosition pos = furniture().position();
        Location location = new Location((World) pos.world().platformWorld(), pos.x, pos.y, pos.z);
        org.bukkit.entity.Player pusher = pullingPlayer == null ? null
                : org.bukkit.Bukkit.getPlayer(pullingPlayer.uuid());
        MillstoneGrindCompleteEvent event = new MillstoneGrindCompleteEvent(pusher, location, stacks);
        if (EventUtils.fireAndCheckCancel(event)) {
            return;
        }

        for (ItemStack stack : stacks) {
            ejectProduct(stack);
        }
    }

    // 成品像发射器/投掷器一样朝石磨朝向喷出
    private void ejectProduct(ItemStack stack) {
        org.bukkit.World world = getBukkitWorld();
        double[] dir = facingDir();
        double ex = furniture().position().x + dir[0] * 0.6;
        double ey = furniture().position().y + 1.0;
        double ez = furniture().position().z + dir[1] * 0.6;
        org.bukkit.entity.Item dropped = world.dropItem(new org.bukkit.Location(world, ex, ey, ez), stack);
        dropped.setVelocity(new Vector(dir[0] * 0.25, 0.12, dir[1] * 0.25));
        dropped.setPickupDelay(10);
    }

    // 研磨粒子
    private void grindParticles() {
        Item current = null;
        for (int i = 0; i < GRIND_SLOTS; i++) {
            if (!grindItems[i].isEmpty()) {
                current = grindItems[i];
                break;
            }
        }
        if (current == null) {
            return;
        }
        org.bukkit.World world = getBukkitWorld();
        float rad = (float) Math.toRadians(currentAngle);
        float baseYaw = (float) Math.toRadians(-furniture().position().yRot() - 90);
        Vector3f p = new Vector3f(0, 1, -0.5f).rotateY(rad).rotateY(baseYaw + (float) Math.PI);
        double px = furniture().position().x + p.x;
        double py = furniture().position().y + p.y;
        double pz = furniture().position().z + p.z;
        // 粒子纯装饰 物品转换失败不该打断磨盘运转 这里是每 tick 路径 不记日志免刷屏
        try {
            ItemStack stack = ItemStackUtils.getBukkitStack(current.minecraftItem());
            Particles.emit(world, Particle.ITEM, px, py, pz, 5, 0.1, 0.1, 0.1, 0.05, stack);
        } catch (Exception ignored) {
        }
    }

    private static final Key[] MILLSTONE_SOUNDS = {
            Key.of("kaleidoscopecookery:millstone_0"),
            Key.of("kaleidoscopecookery:millstone_1"),
            Key.of("kaleidoscopecookery:millstone_2"),
            Key.of("kaleidoscopecookery:millstone_3"),
            Key.of("kaleidoscopecookery:millstone_4"),
            Key.of("kaleidoscopecookery:millstone_5"),
    };

    private void playMillstoneSound(float volume, float pitch) {
        Key sound = MILLSTONE_SOUNDS[ThreadLocalRandom.current().nextInt(MILLSTONE_SOUNDS.length)];
        furniture().position().world().playSound(furniture().position(), sound, volume, pitch, SoundSource.BLOCK);
    }

    private void playGrindSound() {
        playMillstoneSound(0.5f, 0.9f + ThreadLocalRandom.current().nextFloat() * 0.2f);
    }

    // 驴/骡自动化
    private void tryFeedFromChest() {
        if (!(pullingAnimal instanceof ChestedHorse horse) || !horse.isCarryingChest()) {
            return;
        }
        if (firstEmptyGrindSlot() < 0) {
            return;
        }
        Inventory inv = horse.getInventory();
        for (int s = 0; s < inv.getSize(); s++) {
            ItemStack stack = inv.getItem(s);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            Item ce = BukkitItemManager.instance().wrap(stack);
            if (ItemUtils.isEmpty(ce) || !canGrind(ce)) {
                continue;
            }
            while (firstEmptyGrindSlot() >= 0 && stack.getAmount() > 0) {
                if (tryAddGrind(ce)) {
                    stack.setAmount(stack.getAmount() - 1);
                } else {
                    break;
                }
            }
            inv.setItem(s, stack.getAmount() > 0 ? stack : null);
            if (firstEmptyGrindSlot() < 0) {
                break;
            }
        }
    }

    @Override
    public <T extends FurnitureController> FurnitureTicker<T> createFurnitureTicker() {
        return createTickerHelper((f, controller) -> this.tick());
    }

    private org.bukkit.World getBukkitWorld() {
        return (org.bukkit.World) furniture().position().world().platformWorld();
    }

    private boolean isPassable(double targetX, double floorY, double targetZ) {
        org.bukkit.World world = getBukkitWorld();
        int tx = (int) Math.floor(targetX);
        int ty = (int) Math.floor(floorY);
        int tz = (int) Math.floor(targetZ);
        return world.getBlockAt(tx, ty, tz).isPassable()
                && world.getBlockAt(tx, ty + 1, tz).isPassable();
    }

    private boolean hasSolidBlockUnderneath(double x, double y, double z) {
        org.bukkit.World world = getBukkitWorld();
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y) - 1;
        int bz = (int) Math.floor(z);
        Block block = world.getBlockAt(bx, by, bz);
        return !block.isPassable() && block.getType().isSolid();
    }

    private boolean isUnevenStandingPoint(double x, double y, double z) {
        org.bukkit.World world = getBukkitWorld();
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!isFullCubeTop(world.getBlockAt(bx, (int) Math.floor(y) - 1, bz))) {
            return true;
        }
        BlockData feet = world.getBlockAt(bx, (int) Math.floor(y), bz).getBlockData();
        return feet instanceof Snow snow && snow.getLayers() > 1;
    }

    public static boolean isFullCubeTop(Block block) {
        if (block.getBlockData() instanceof Stairs) {
            return false;
        }
        BoundingBox box = block.getBoundingBox();
        double eps = 1e-6;
        return (box.getMinX() - block.getX()) <= eps
                && (box.getMaxX() - block.getX()) >= 1 - eps
                && (box.getMinZ() - block.getZ()) <= eps
                && (box.getMaxZ() - block.getZ()) >= 1 - eps
                && (box.getMaxY() - block.getY()) >= 1 - eps;
    }

    public void stopSpinning() {
        stopSpinning(null);
    }

    // 实体调度器 retired 专用 实体已永久移除 只做纯内存清理 禁止碰世界
    public void releaseAnimalRefs() {
        if (pullingAnimal != null) {
            ACTIVE_ANIMAL_PULLERS.remove(pullingAnimal.getUniqueId());
            pullingAnimal = null;
        }
        if (pullingPlayer != null) {
            unregister(pullingPlayer.uuid());
            pullingPlayer = null;
        }
        leadOwner = null;
        this.animating = false;
        this.rawTick = 0;
        this.boosted = false;
    }

    public void stopSpinning(Player leadRecipient) {
        if (pullingPlayer != null) {
            unregister(pullingPlayer.uuid());
            org.bukkit.entity.Player bukkitPuller = org.bukkit.Bukkit.getPlayer(pullingPlayer.uuid());
            if (bukkitPuller != null) {
                restoreFlight(bukkitPuller);
            }
            pullingPlayer = null;
        }
        if (pullingAnimal != null) {
            ACTIVE_ANIMAL_PULLERS.remove(pullingAnimal.getUniqueId());
            setStruck(pullingAnimal, false);
            if (pullingAnimal.isValid()) {
                pullingAnimal.setAI(animalWasAI);
                pullingAnimal.setGravity(true);
                pullingAnimal.setVelocity(new Vector(0, 0, 0));
            }
            if (leadOwner != null) {
                if (leadRecipient != null) {
                    InventoryUtils.give(leadRecipient, InventoryUtils.createOrEmpty(ItemKeys.LEAD));
                } else {
                    ejectLead();
                }
            }
            pullingAnimal = null;
            leadOwner = null;
        }
        this.animating = false;
        this.rawTick = 0;
        this.boosted = false;
        furniture().setUnsaved();
    }

    public void tick() {
        if (!animating) {
            return;
        }

        if (pullingAnimal != null && rawTick % STRIKE_CHECK_INTERVAL == 0 && ThreadLocalRandom.current().nextDouble() < STRIKE_CHANCE) {
            strikeAnimal();
            return;
        }

        if (pullingAnimal != null && grindIsEmpty() && rawTick % 20 == 0) {
            tryFeedFromChest();
        }

        if (!grindIsEmpty()) {
            if (rawTick % 5 == 2) {
                grindParticles();
            }
            if (rawTick % 25 == 0) {
                playGrindSound();
            }
        }

        float seconds      = boosted ? (float) MillstoneAnimals.BOOST_SECONDS : currentSeconds;
        float anglePerTick = MillstoneAnimals.anglePerTick(seconds);

        // 视觉角度连续累加不归零 配合较短的更新间隔 保证过起点和高速自转时都不会倒转
        if (rawTick % VISUAL_UPDATE_INTERVAL == 0) {
            currentAngle += anglePerTick * VISUAL_UPDATE_INTERVAL;
            element.updateRotation(currentAngle, VISUAL_UPDATE_INTERVAL);
        }

        orbitAngle += anglePerTick;

        if (pullingPlayer != null) {
            if (!movePusher()) {
                return;
            }
        } else if (pullingAnimal != null) {
            if (!moveAnimal()) {
                return;
            }
        }

        rawTick++;

        // 转满一圈 推进研磨 只把轨道角归一 视觉角和 rawTick 继续累加 不归零以免出现倒转
        if (orbitAngle >= 360f) {
            orbitAngle -= 360f;
            advanceGrind();
            furniture().setUnsaved();
        }
    }

    // 拉磨者沿轨道的目标点
    private Vector3f orbitOffset() {
        float furnitureRad = (float) Math.toRadians(-furniture().position().yRot());
        float animRad = (float) Math.toRadians(orbitAngle);
        float radius = currentRadius;
        Vector3f targetOffset = new Vector3f(-radius, 0f, -0.5f);
        targetOffset.rotateY(furnitureRad + animRad);
        return targetOffset;
    }

    // 玩家推磨移动；返回 false 表示已停止
    private boolean movePusher() {
        org.bukkit.entity.Player bukkitPlayer = org.bukkit.Bukkit.getPlayer(pullingPlayer.uuid());
        if (bukkitPlayer == null) {
            stopSpinning();
            return false;
        }

        double heightDiff = Math.abs(bukkitPlayer.getLocation().getY() - furniture().position().y);
        if (heightDiff > 0.5) {
            stopSpinning();
            return false;
        }

        Vector3f targetOffset = orbitOffset();
        org.bukkit.Location currentLoc = bukkitPlayer.getLocation();
        double targetX = furniture().position().x + targetOffset.x;
        double targetZ = furniture().position().z + targetOffset.z;

        if (!isPassable(targetX, currentLoc.getY(), targetZ)) {
            stopSpinning();
            return false;
        }

        if (!hasSolidBlockUnderneath(targetX, currentLoc.getY(), targetZ)) {
            stopSpinning();
            return false;
        }

        double dx = targetX - currentLoc.getX();
        double dz = targetZ - currentLoc.getZ();

        if (dx * dx + dz * dz > TELEPORT_SNAP_DIST_SQ) {
            currentLoc.setX(targetX);
            currentLoc.setZ(targetZ);
            FoliaUtil.teleportThen(bukkitPlayer, currentLoc,
                    () -> bukkitPlayer.setVelocity(new Vector(0, 0, 0)));
            return true;
        }
        bukkitPlayer.setVelocity(clampStep(dx, dz));
        return true;
    }

    // 生物拉磨移动；返回 false 表示已停止
    private boolean moveAnimal() {
        if (!pullingAnimal.isValid() || pullingAnimal.isDead()) {
            stopSpinning();
            return false;
        }

        double heightDiff = Math.abs(pullingAnimal.getLocation().getY() - furniture().position().y);
        if (heightDiff > 1.0) {
            stopSpinning();
            return false;
        }

        Vector3f targetOffset = orbitOffset();
        org.bukkit.Location currentLoc = pullingAnimal.getLocation();
        double targetX = furniture().position().x + targetOffset.x;
        double targetZ = furniture().position().z + targetOffset.z;

        if (!isPassable(targetX, currentLoc.getY(), targetZ)) {
            stopSpinning();
            return false;
        }

        if (!hasSolidBlockUnderneath(targetX, currentLoc.getY(), targetZ)) {
            stopSpinning();
            return false;
        }

        double dx = targetX - currentLoc.getX();
        double dz = targetZ - currentLoc.getZ();
        float yaw = dx * dx + dz * dz > YAW_UPDATE_EPSILON
                ? (float) Math.toDegrees(Math.atan2(-dx, dz))
                : currentLoc.getYaw();

        LivingEntity animal = pullingAnimal;
        // 只在偏离过大时才传送 平时走速度推进
        // 每 tick 对实体调 teleportAsync 在 folia 上是崩溃模式 且跨区域后 NMS 实例会被替换 长期持有的引用失效
        if (dx * dx + dz * dz > TELEPORT_SNAP_DIST_SQ) {
            currentLoc.setX(targetX);
            currentLoc.setZ(targetZ);
            currentLoc.setYaw(yaw);
            FoliaUtil.teleportThen(animal, currentLoc,
                    () -> animal.setRotation(currentLoc.getYaw(), currentLoc.getPitch()));
            return true;
        }
        animal.setVelocity(clampStep(dx, dz));
        animal.setRotation(yaw, currentLoc.getPitch());
        return true;
    }

    // 朝目标推进 单 tick 位移不超过 MAX_STEP 免得生物被甩飞
    private static Vector clampStep(double dx, double dz) {
        double vx = dx * STEP_FACTOR;
        double vz = dz * STEP_FACTOR;
        double speedSq = vx * vx + vz * vz;
        if (speedSq > MAX_STEP * MAX_STEP) {
            double scale = MAX_STEP / Math.sqrt(speedSq);
            vx *= scale;
            vz *= scale;
        }
        return new Vector(vx, 0, vz);
    }

    public boolean spin(Player player) {
        if (animating) return false;

        if (ACTIVE_PUSHERS.containsKey(player.uuid())) {
            player.sendActionBar(Localization.component(behavior.msgAlreadyPushing));
            return false;
        }

        float furnitureRad = (float) Math.toRadians(-furniture().position().yRot());
        float startRad = (float) Math.toRadians(orbitAngle);
        float radius = (float) MillstoneAnimals.DEFAULT_ORBIT_RADIUS;

        Vector3f startOffset = new Vector3f(-radius, 0f, -0.5f);
        startOffset.rotateY(furnitureRad + startRad);

        double startX = furniture().position().x() + startOffset.x;
        double startZ = furniture().position().z() + startOffset.z;

        if (!hasSolidBlockUnderneath(startX, furniture().position().y(), startZ)) {
            player.sendActionBar(Localization.component(behavior.msgNeedGroundBelow));
            return false;
        }

        if (isUnevenStandingPoint(startX, furniture().position().y(), startZ)) {
            player.sendActionBar(Localization.component(behavior.msgUneven));
            return false;
        }

        this.animating = true;
        this.rawTick = 0;
        this.boosted = false;
        this.currentSeconds = (float) MillstoneAnimals.PLAYER_SECONDS;
        this.currentRadius = (float) MillstoneAnimals.DEFAULT_ORBIT_RADIUS;
        this.pullingPlayer = player;
        this.orbitAngle = this.currentAngle;

        register(player.uuid(), this);

        org.bukkit.entity.Player bukkitPlayer = org.bukkit.Bukkit.getPlayer(player.uuid());
        if (bukkitPlayer != null) {
            if (bukkitPlayer.isFlying()) {
                bukkitPlayer.setFlying(false);
                if (bukkitPlayer.getGameMode() != GameMode.CREATIVE
                        && bukkitPlayer.getGameMode() != GameMode.SPECTATOR) {
                    markFlightRestore(bukkitPlayer);
                    bukkitPlayer.setAllowFlight(false);
                }
            }

            org.bukkit.Location loc = bukkitPlayer.getLocation();
            loc.setX(startX);
            loc.setZ(startZ);
            FoliaUtil.teleportThen(bukkitPlayer, loc,
                    () -> bukkitPlayer.setVelocity(new Vector(0, 0, 0)));
        }

        playMillstoneSound(1.0f, 0.8f);

        furniture().setUnsaved();
        return true;
    }

    // owner 为触发绑定的玩家 停止时退还拴绳 怪物蛋触发时传 null
    // 返回是否真的开始拉磨 调用方据此回滚已生成的实体与已扣的物品
    public boolean spinWithAnimal(LivingEntity animal, org.bukkit.entity.Player owner, boolean doInitialTeleport) {
        if (animating) return false;
        // 幼年生物不能拉磨 这里兜底 覆盖拴绳 刷怪蛋和外部 API 全部入口
        if (!MillstoneAnimals.isAdult(animal)) return false;
        MillstoneAnimals.Profile profile = MillstoneAnimals.instance().resolve(animal);
        this.currentSeconds = (float) (profile != null ? profile.secondsPerRevolution() : MillstoneAnimals.PLAYER_SECONDS);
        this.currentRadius = (float) (profile != null ? profile.orbitRadius() : MillstoneAnimals.DEFAULT_ORBIT_RADIUS);
        this.animating = true;
        this.boosted = false;
        this.rawTick = 0;
        this.pullingPlayer = null;
        this.pullingAnimal = animal;
        this.leadOwner = owner;
        this.orbitAngle = this.currentAngle;

        this.animalWasAI = isStruck(animal) || animal.hasAI();
        animal.setAI(false);
        animal.setGravity(false);

        ACTIVE_ANIMAL_PULLERS.put(animal.getUniqueId(), this);

        if (doInitialTeleport) {
            float furnitureRad = (float) Math.toRadians(-furniture().position().yRot());
            float startRad = (float) Math.toRadians(orbitAngle);
            float radius = currentRadius;

            Vector3f startOffset = new Vector3f(-radius, 0f, -0.5f);
            startOffset.rotateY(furnitureRad + startRad);

            org.bukkit.Location loc = animal.getLocation();
            loc.setX(furniture().position().x() + startOffset.x);
            loc.setY(furniture().position().y());
            loc.setZ(furniture().position().z() + startOffset.z);
            FoliaUtil.teleport(animal, loc);
        }

        playMillstoneSound(1.0f, 0.8f);
        tryFeedFromChest();
        furniture().setUnsaved();
        return true;
    }

    // 拉磨者被打 加速到骡子的速度
    public void onPullerDamaged() {
        if (!animating) return;
        this.boosted = true;
    }

    public boolean isAnimating() {
        return animating;
    }

    public float currentAngle() {
        return currentAngle;
    }

    public void refreshRendering() {
        this.element.refreshPackets();
    }

    @Override
    public void gatherElements(Consumer<FurnitureElement> consumer) {
        consumer.accept(this.element);
    }

    @Override
    public InteractionResult useOnFurniture(FurnitureHitBox hitBox, InteractEntityContext context) {
        Player player = context.getPlayer();
        if (!InteractGuard.canInteract(player, furniture().position())) {
            return InteractionResult.PASS;
        }

        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) player.platformPlayer();

        InteractionHand toolHand = Hands.toolHand(player, MillstoneController::isMillstoneTool);
        Item toolItem = player.getItemInHand(toolHand);
        ItemStack toolStack = toolItem.isEmpty() ? null : ItemStackUtils.getBukkitStack(toolItem);
        Material toolMat = toolStack == null ? Material.AIR : toolStack.getType();

        if (player.isSneaking()) {
            return handleSneak(player, toolMat == Material.SHEARS);
        }
        if (toolMat == Material.LEAD) {
            InteractionResult r = handleLeash(player, bukkitPlayer);
            if (r != InteractionResult.PASS) return r;
        }
        if (toolMat.name().endsWith("_SPAWN_EGG")) {
            InteractionResult r = handleSpawnEgg(player, toolMat, toolItem);
            if (r != InteractionResult.PASS) return r;
        }

        Item mainItem = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!mainItem.isEmpty()) {
            if (canGrind(mainItem)) {
                return handleAddGrind(player, mainItem);
            }
            return InteractionResult.PASS;
        }

        if (takeGrind(player, InteractionHand.MAIN_HAND)) {
            player.swingHand(InteractionHand.MAIN_HAND);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        return InteractionResult.PASS;
    }

    // 石磨的工具物品 剪刀停生物 拴绳牵生物 刷怪蛋生成
    private static boolean isMillstoneTool(Item item) {
        Material m = ItemStackUtils.getBukkitStack(item).getType();
        return m == Material.SHEARS || m == Material.LEAD || m.name().endsWith("_SPAWN_EGG");
    }

    private InteractionResult handleSneak(Player player, boolean hasShears) {
        // 剪刀只对生物拉磨生效 没生物在拉就什么都没发生
        if (hasShears) {
            if (!isAnimating() || pullingAnimal == null) {
                return InteractionResult.PASS;
            }
            stopSpinning(player);
            player.swingHand(InteractionHand.MAIN_HAND);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        if (isAnimating()) {
            // 生物在拉时玩家潜行右键无事发生 别挥手也别吞掉原版交互
            if (pullingPlayer == null) {
                return InteractionResult.PASS;
            }
            stopSpinning();
            player.swingHand(InteractionHand.MAIN_HAND);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        double sneakHeightDiff = Math.abs(player.y() - furniture().position().y);
        if (sneakHeightDiff > 0.1) {
            player.sendActionBar(Localization.component(behavior.msgNotSamePlane));
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        if (!spin(player)) {
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        player.sendActionBar(Localization.component(behavior.msgExitHint));
        player.swingHand(InteractionHand.MAIN_HAND);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 找玩家正拴着的可拉磨生物 一次拴多只时取离磨最近的一只
    // getNearbyEntities 要求 AABB 覆盖的每个 section 都归当前 region 否则 folia 抛异常
    // 所以先问归属再扫 isOwnedByCurrentRegion 在 paper 上恒为 true 两端同一套代码
    private LivingEntity findLeashedAnimal(org.bukkit.entity.Player bukkitPlayer) {
        org.bukkit.Location furnitureLoc = new org.bukkit.Location(getBukkitWorld(),
                furniture().position().x, furniture().position().y, furniture().position().z);
        if (!org.bukkit.Bukkit.isOwnedByCurrentRegion(furnitureLoc, LEASH_SEARCH_CHUNK_RADIUS)) {
            return null;
        }

        LivingEntity nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (Entity nearby : furnitureLoc.getWorld().getNearbyEntities(
                furnitureLoc, LEASH_SEARCH_RADIUS, LEASH_SEARCH_RADIUS, LEASH_SEARCH_RADIUS)) {
            if (!(nearby instanceof LivingEntity living)
                    || !isPullCandidate(living)
                    || !living.isLeashed()
                    || !bukkitPlayer.equals(living.getLeashHolder())) {
                continue;
            }
            double distSq = living.getLocation().distanceSquared(furnitureLoc);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = living;
            }
        }
        return nearest;
    }

    // 已在拉别的磨就跳过 防止一只生物被牵去同时拉多个
    private static boolean isPullCandidate(LivingEntity living) {
        return MillstoneAnimals.instance().canPull(living)
                && !ACTIVE_ANIMAL_PULLERS.containsKey(living.getUniqueId());
    }

    // 拴绳右键 把玩家拴着的牛/驴/骡接到石磨上拉磨
    private InteractionResult handleLeash(Player player, org.bukkit.entity.Player bukkitPlayer) {
        if (isAnimating()) {
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        LivingEntity target = findLeashedAnimal(bukkitPlayer);
        if (target == null) {
            return InteractionResult.PASS;
        }

        org.bukkit.Location dropLoc = target.getLocation().clone();
        target.setLeashHolder(null);

        BukkitCraftEngine.instance().scheduler().platform().run(
                () -> {
                    for (Entity e : dropLoc.getWorld().getNearbyEntities(dropLoc, 1, 1, 1)) {
                        if (e instanceof org.bukkit.entity.Item dropped
                                && dropped.getItemStack().getType() == Material.LEAD) {
                            dropped.remove();
                            break;
                        }
                    }
                },
                dropLoc
        );

        spinWithAnimal(target, bukkitPlayer, true);
        player.sendActionBar(Localization.component(behavior.msgStopAnimalHint));
        player.swingHand(InteractionHand.MAIN_HAND);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 刷怪蛋右键 在轨道点生成支持的生物并立即拉磨 非支持生物放行
    private InteractionResult handleSpawnEgg(Player player, Material mat, Item eggItem) {
        if (isAnimating()) {
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        EntityType type = spawnEggType(mat);
        if (type == null) {
            return InteractionResult.PASS;
        }
        MillstoneAnimals.Profile profile = MillstoneAnimals.instance().profileForType(type);
        if (profile == null || !profile.allowed()) {
            return InteractionResult.PASS;
        }

        org.bukkit.World world = getBukkitWorld();
        float furnitureRad = (float) Math.toRadians(-furniture().position().yRot());
        float startRad     = (float) Math.toRadians(currentAngle);
        float radius = (float) profile.orbitRadius();

        Vector3f spawnOffset = new Vector3f(-radius, 0f, -0.5f);
        spawnOffset.rotateY(furnitureRad + startRad);

        org.bukkit.Location spawnLoc = new org.bukkit.Location(world,
                furniture().position().x + spawnOffset.x,
                furniture().position().y,
                furniture().position().z + spawnOffset.z);

        if (!(world.spawnEntity(spawnLoc, type) instanceof LivingEntity animal)) {
            return InteractionResult.PASS;
        }
        // 没能开始拉磨就把刚生成的实体撤掉 别扣蛋也别留个生物在场上
        if (!spinWithAnimal(animal, null, false)) {
            animal.remove();
            return InteractionResult.PASS;
        }
        player.sendActionBar(Localization.component(behavior.msgStopAnimalHint));
        InventoryUtils.shrinkHeld(player, eggItem, 1);
        player.swingHand(InteractionHand.MAIN_HAND);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 刷怪蛋 Material 解析为生物类型 形如 X_SPAWN_EGG 取 X 解析失败返回 null
    private static EntityType spawnEggType(Material mat) {
        String name = mat.name();
        if (!name.endsWith("_SPAWN_EGG")) {
            return null;
        }
        try {
            return EntityType.valueOf(name.substring(0, name.length() - "_SPAWN_EGG".length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // 可研磨的物品右键 尽量塞满研磨槽 一次性加入按实际放入数量扣主手 创造不消耗
    private InteractionResult handleAddGrind(Player player, Item itemInHand) {
        int held = itemInHand.count();
        int placed = 0;
        while (placed < held && tryAddGrind(itemInHand)) {
            placed++;
        }
        if (placed > 0) {
            InventoryUtils.shrinkHeld(player, itemInHand, placed);
            player.swingHand(InteractionHand.MAIN_HAND);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void preRemove(Player player) {
        stopSpinning();
        org.bukkit.World world = getBukkitWorld();
        org.bukkit.Location dropLoc = new org.bukkit.Location(world,
                furniture().position().x, furniture().position().y, furniture().position().z);
        for (int i = 0; i < GRIND_SLOTS; i++) {
            if (!grindItems[i].isEmpty()) {
                world.dropItemNaturally(dropLoc, ItemStackUtils.getBukkitStack(grindItems[i].minecraftItem()));
                clearGrindSlot(i);
            }
        }
    }

    @Override
    public void onLoad() {
        this.refreshRendering();
        this.element.refreshAllGrind();
        if (!this.animating) {
            this.element.updateRotation(this.currentAngle, 0);
        }

        if (this.animating && this.pendingAnimalUUID != null) {
            WorldPosition fp = furniture().position();
            org.bukkit.Location furnitureLoc = new org.bukkit.Location(
                    (World) fp.world().platformWorld(), fp.x, fp.y, fp.z);
            BukkitCraftEngine.instance().scheduler().platform().runLater(this::restoreAnimal, 1L, furnitureLoc);
        }
    }

    private void restoreAnimal() {
        if (pendingAnimalUUID == null) return;

        Entity entity = org.bukkit.Bukkit.getEntity(pendingAnimalUUID);
        this.pendingAnimalUUID = null;

        if (entity instanceof LivingEntity living && living.isValid()) {
            this.pullingAnimal = living;
            this.animalWasAI = this.savedAnimalWasAI;
            MillstoneAnimals.Profile profile = MillstoneAnimals.instance().resolve(living);
            if (profile != null) {
                this.currentSeconds = (float) profile.secondsPerRevolution();
                this.currentRadius = (float) profile.orbitRadius();
            }
            living.setAI(false);
            living.setGravity(false);
            ACTIVE_ANIMAL_PULLERS.put(living.getUniqueId(), this);

            this.currentAngle = this.orbitAngle % 360f;
            this.element.updateRotation(this.currentAngle, 0);

            float furnitureRad = (float) Math.toRadians(-furniture().position().yRot());
            float animRad = (float) Math.toRadians(orbitAngle);
            float radius = currentRadius;
            Vector3f targetOffset = new Vector3f(-radius, 0f, -0.5f);
            targetOffset.rotateY(furnitureRad + animRad);
            org.bukkit.Location loc = living.getLocation();
            loc.setX(furniture().position().x() + targetOffset.x);
            loc.setY(furniture().position().y());
            loc.setZ(furniture().position().z() + targetOffset.z);
            FoliaUtil.teleport(living, loc);
        } else {
            this.animating = false;
            furniture().setUnsaved();
            this.element.updateRotation(this.currentAngle, 0);
        }
    }

    @Override
    public void onUnload(boolean isStopping) {
        if (isStopping) {
            if (pullingPlayer != null) {
                unregister(pullingPlayer.uuid());
            }
            if (pullingAnimal != null) {
                ACTIVE_ANIMAL_PULLERS.remove(pullingAnimal.getUniqueId());
            }
        } else {
            if (pullingPlayer != null) {
                unregister(pullingPlayer.uuid());
                pullingPlayer = null;
            }
            if (pullingAnimal != null) {
                ACTIVE_ANIMAL_PULLERS.remove(pullingAnimal.getUniqueId());
                if (pullingAnimal.isValid()) {
                    pullingAnimal.setAI(animalWasAI);
                    pullingAnimal.setGravity(true);
                    pullingAnimal.setVelocity(new Vector(0, 0, 0));
                }
            }
        }
        furniture().setUnsaved();
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        CompoundTag data = new CompoundTag();
        data.putBoolean(K_ANIMATING, this.animating);
        data.putFloat(K_ORBIT_ANGLE, this.orbitAngle);
        data.putFloat(K_CURRENT_ANGLE, this.currentAngle % 360f);
        data.putBoolean(K_BOOSTED, this.boosted);
        data.putFloat(K_ANIM_SECONDS, this.currentSeconds);
        data.putInt(K_RAW_TICK, this.rawTick);

        if (pullingAnimal != null && pullingAnimal.isValid()) {
            data.putIntArray(K_ANIMAL_UUID, UUIDUtils.uuidToIntArray(pullingAnimal.getUniqueId()));
            data.putBoolean(K_ANIMAL_WAS_AI, this.animalWasAI);
        }

        ListTag grindTag = new ListTag();
        for (int i = 0; i < GRIND_SLOTS; i++) {
            if (grindItems[i].isEmpty()) {
                continue;
            }
            CompoundTag e = new CompoundTag();
            e.putInt(K_SLOT, i);
            e.put(K_ITEM, ItemStackUtils.saveMinecraftItemStackAsTag(grindItems[i].minecraftItem()));
            e.putInt(K_PROGRESS, grindProgress[i]);
            e.putInt(K_ROTATIONS, requiredRotations[i]);
            grindTag.add(e);
        }
        data.put(K_GRIND_ITEMS, grindTag);
        data.putInt(K_GRIND_DATA_VERSION, VersionHelper.WORLD_VERSION);
        tag.put(DATA_KEY, data);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        for (int i = 0; i < GRIND_SLOTS; i++) {
            grindItems[i] = Item.empty();
            grindProgress[i] = 0;
            requiredRotations[i] = 0;
        }

        CompoundTag data = tag.getCompound(DATA_KEY);
        if (data == null) return;
        this.animating = data.getBoolean(K_ANIMATING, false);
        this.orbitAngle = data.getFloat(K_ORBIT_ANGLE, 0f);
        this.currentAngle = data.getFloat(K_CURRENT_ANGLE, 0f);
        this.boosted = data.getBoolean(K_BOOSTED, false);
        this.currentSeconds = data.getFloat(K_ANIM_SECONDS, (float) MillstoneAnimals.PLAYER_SECONDS);
        this.rawTick = data.getInt(K_RAW_TICK, 0);

        int[] animalUuid = data.getIntArray(K_ANIMAL_UUID);
        if (this.animating && animalUuid != null && animalUuid.length == 4) {
            this.pendingAnimalUUID = UUIDUtils.uuidFromIntArray(animalUuid);
            this.savedAnimalWasAI = data.getBoolean(K_ANIMAL_WAS_AI, true);
        }

        int gdv = data.getInt(K_GRIND_DATA_VERSION, Config.itemDataFixerUpperFallbackVersion());
        ListTag grindTag = data.getList(K_GRIND_ITEMS);
        if (grindTag != null) {
            for (Tag t : grindTag) {
                if (!(t instanceof CompoundTag e)) {
                    continue;
                }
                int slot = e.getInt(K_SLOT, -1);
                if (slot < 0 || slot >= GRIND_SLOTS) {
                    continue;
                }
                Object nms = ItemStackUtils.parseMinecraftItem(e.getCompound(K_ITEM), gdv);
                if (nms != null) {
                    grindItems[slot] = ItemStackUtils.wrap(nms);
                    grindProgress[slot] = e.getInt(K_PROGRESS, 0);
                    requiredRotations[slot] = e.getInt(K_ROTATIONS, behavior.grindRotations);
                }
            }
        }
    }

    public WorldPosition position() {
        return furniture().position();
    }
}
