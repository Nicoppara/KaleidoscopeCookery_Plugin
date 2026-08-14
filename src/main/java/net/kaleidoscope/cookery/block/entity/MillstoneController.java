package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.EventUtils;
import net.kaleidoscope.cookery.util.FoliaUtil;
import net.kaleidoscope.cookery.util.Hands;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.util.Localization;
import net.kaleidoscope.cookery.util.MessageKeys;
import net.kaleidoscope.cookery.api.MillstoneAnimals;
import net.kaleidoscope.cookery.block.behavior.MillstoneBehavior;
import net.kaleidoscope.cookery.block.entity.render.Particles;

import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
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
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.ListTag;
import net.momirealms.craftengine.libraries.nbt.Tag;
import net.kaleidoscope.cookery.api.event.MillstoneGrindCompleteEvent;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.ApplianceFoodRegistry;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class MillstoneController extends FurnitureController {

    public static final ConcurrentHashMap<UUID, MillstoneController> ACTIVE_ANIMAL_PULLERS = new ConcurrentHashMap<>();

    // 拴绳生物搜索半径 原版拴绳超过 10 格就断 20 足够覆盖
    private static final double LEASH_SEARCH_RADIUS = 20;
    // 上面半径换算成区块 用于扫描前的 region 归属校验 切比雪夫距离
    private static final int LEASH_SEARCH_CHUNK_RADIUS = ((int) LEASH_SEARCH_RADIUS >> 4) + 1;

    // 当前拉磨生物的拉一圈秒数与绕磨半径 按各自档案 被打时临时用骡子的速度
    private float currentSeconds = (float) MillstoneAnimals.PLAYER_SECONDS;
    private float currentRadius = (float) MillstoneAnimals.DEFAULT_ORBIT_RADIUS;

    // 磨盘空转时驴骡自动投料的检查间隔
    private static final int IDLE_FEED_INTERVAL = 20;

    // 生物匀速拉磨 视觉旋转每隔几 tick 更新一次
    private static final int VISUAL_UPDATE_INTERVAL = 5;
    // 玩家推磨速度不可预测 视觉间隔越长手感越滞后 这里换成两 tick 一发
    private static final int PUSH_VISUAL_INTERVAL = 2;

    // 每 tick 朝目标推进的比例与位移上限
    private static final double STEP_FACTOR = 0.8;
    private static final double MAX_STEP = 0.25;
    // 位移小于这个平方值就不更新朝向 免得原地抖动
    private static final double YAW_UPDATE_EPSILON = 1e-6;

    // 轨道点相对磨心的侧向偏移 orbitOffset 与其反解共用
    private static final float ORBIT_LATERAL_OFFSET = -0.5f;
    // 原版玩家碰撞箱半宽 换算成接触判定的角宽度
    private static final double PLAYER_HALF_WIDTH = 0.3;
    // 站上磨盘本身不算推磨
    private static final double PUSH_MIN_RADIUS = 0.8;
    // 接触环带在杆长之外再放宽的距离 站到杆梢外侧一点也还算摸着
    private static final float PUSH_RADIUS_MARGIN = 0.6f;
    // 磨杆挂在 y+0.9 只要玩家脚下与磨底盘同高就必然落在身体高度内
    private static final double PUSH_MAX_HEIGHT_DIFF = 1.0;
    // 磨杆扫开旁观者的施力间隔
    private static final int SHOVE_INTERVAL = 4;
    // 研磨粒子与音效的发包间隔 粒子错开 2 tick 免得与音效撞在同一 tick
    private static final int GRIND_PARTICLE_INTERVAL = 5;
    private static final int GRIND_PARTICLE_PHASE = 2;
    private static final int GRIND_SOUND_INTERVAL = 25;
    private static final int GRIND_PARTICLE_COUNT = 5;
    // 成品出料口相对磨心的水平偏移与高度 抛出速度按发射器的手感定
    private static final double EJECT_OFFSET = 0.6;
    private static final double EJECT_HEIGHT = 1.0;
    private static final double EJECT_SPEED = 0.25;
    private static final double EJECT_LIFT = 0.12;
    // 喷出的成品给玩家留出的拾取延迟
    private static final int EJECT_PICKUP_DELAY = 10;
    // 摘绳掉出的拴绳的搜索范围 与刚落地的判定阈值 收绳任务最迟下一 tick 执行
    private static final double LEAD_DROP_SEARCH_RADIUS = 1;
    private static final int LEAD_DROP_SEARCH_CHUNK_RADIUS = 1;
    private static final int FRESH_DROP_MAX_TICKS = 2;

    private int pushVisualTick;
    private boolean pushVisualPending;
    // 接触判定每 tick 每个追踪玩家读一次坐标 getLocation 每次都新建 复用同一个游标
    private final Location scratchLoc = new Location(null, 0, 0, 0);
    // 最近一次推磨的玩家 只用于产出事件的归属 不落盘
    private UUID lastPusher;

    private UUID pendingAnimalUUID = null;
    private boolean savedAnimalWasAI = true;

    private final MillstoneBehavior behavior;
    private final MillstoneElement element;

    private boolean animating = false;
    private int rawTick = 0;
    private float orbitAngle = 0f;
    private float currentAngle = 0f;
    private boolean boosted = false;

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
    // 旧键 progress 存的是整圈数 语义已换成角度 换键让旧档从 0 重来 不做兼容
    private static final String K_PROGRESS = "progress_degrees";
    private static final String K_ROTATIONS = "rotations";

    // 接触判定的基准角 构造时定死
    private final float orbitBaseDeg;

    public static final int GRIND_SLOTS = 8;
    private final Item[] grindItems = new Item[GRIND_SLOTS];
    // 非空槽数 grindIsEmpty 每 tick 都要问 别每次扫一遍八个槽
    private int filledSlots;
    // grindDegrees 该槽已研磨的角度 requiredRotations 该料产出所需圈数
    // 按角度累计而不是数 orbitAngle 跨 360 的次数 后者会让新放入的料白蹭上一轮的剩余角度
    private final float[] grindDegrees = new float[GRIND_SLOTS];
    private final int[] requiredRotations = new int[GRIND_SLOTS];

    public MillstoneController(Furniture furniture, MillstoneBehavior behavior) {
        super(furniture);
        this.behavior = behavior;
        Arrays.fill(this.grindItems, Item.empty());
        this.element = new MillstoneElement(this, furniture.position());

        double furnitureRad = Math.toRadians(-furniture.position().yRot());
        double base = Math.atan2(-behavior.pushBarLength, ORBIT_LATERAL_OFFSET);
        this.orbitBaseDeg = (float) Math.toDegrees(furnitureRad + base) - behavior.pushAngleOffset;
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
                // 份数由配方的 result_count 决定 只取 item 会把它吞掉
                .map(fr -> fr.item().count(fr.count()))
                .orElse(input.copy());
    }

    private int firstEmptyGrindSlot() {
        if (filledSlots >= GRIND_SLOTS) {
            return -1;
        }
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
        grindDegrees[i] = 0f;
        filledSlots++;
        // 圈数为 0 会让第一圈就出货 收口成至少一圈
        requiredRotations[i] = Math.max(1, FoodRecipeRegistry.instance().findGrindRotations(item.id(), behavior.grindRotations));
        element.spawnGrindSlot(i, grindItems[i]);
        furniture().setUnsaved();
        return true;
    }

    private void clearGrindSlot(int i) {
        if (!grindItems[i].isEmpty()) {
            filledSlots--;
        }
        grindItems[i] = Item.empty();
        grindDegrees[i] = 0f;
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
        return filledSlots == 0;
    }

    // 磨盘每转过一点角度就按同样的角度推进研磨 攒够该料所需圈数就产出
    // 真实耗时由转速(秒/圈)决定 转得慢产得慢
    private void advanceGrind(float degrees) {
        List<Item> products = null;
        for (int i = 0; i < GRIND_SLOTS; i++) {
            if (grindItems[i].isEmpty()) {
                continue;
            }
            grindDegrees[i] += degrees;
            if (grindDegrees[i] < requiredRotations[i] * 360f) {
                continue;
            }
            if (products == null) {
                products = new ArrayList<>();
            }
            products.add(getGrindResult(grindItems[i]));
            clearGrindSlot(i);
        }

        if (products == null) {
            return;
        }
        ejectProducts(products);
        furniture().setUnsaved();
        tryFeedFromChest();
    }

    // 按槽取 不返回数组本身 否则调用方能直接改研磨槽且绕过脏标记
    public Item grindItem(int slot) {
        return grindItems[slot];
    }

    private double[] facingDir() {
        double rad = Math.toRadians(furniture().position().yRot());
        return new double[]{-Math.sin(rad), Math.cos(rad)};
    }

    // 出料口在磨心朝家具正面偏移一点的位置 像发射器一样朝外抛
    private org.bukkit.entity.Item ejectFromOutlet(ItemStack stack) {
        World world = getBukkitWorld();
        WorldPosition pos = furniture().position();
        double[] dir = facingDir();
        Location loc = new Location(world,
                pos.x + dir[0] * EJECT_OFFSET, pos.y + EJECT_HEIGHT, pos.z + dir[1] * EJECT_OFFSET);
        org.bukkit.entity.Item dropped = world.dropItem(loc, stack);
        dropped.setVelocity(new Vector(dir[0] * EJECT_SPEED, EJECT_LIFT, dir[1] * EJECT_SPEED));
        return dropped;
    }

    private void ejectLead() {
        ejectFromOutlet(new ItemStack(Material.LEAD));
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
        org.bukkit.entity.Player pusher = lastPusher == null ? null : Bukkit.getPlayer(lastPusher);
        MillstoneGrindCompleteEvent event = new MillstoneGrindCompleteEvent(pusher, location, stacks);
        if (EventUtils.fireAndCheckCancel(event)) {
            return;
        }

        for (ItemStack stack : stacks) {
            ejectFromOutlet(stack).setPickupDelay(EJECT_PICKUP_DELAY);
        }
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
        World world = getBukkitWorld();
        float rad = (float) Math.toRadians(currentAngle);
        float baseYaw = (float) Math.toRadians(-furniture().position().yRot() - 90);
        Vector3f p = new Vector3f(0, 1, -0.5f).rotateY(rad).rotateY(baseYaw + (float) Math.PI);
        double px = furniture().position().x + p.x;
        double py = furniture().position().y + p.y;
        double pz = furniture().position().z + p.z;
        // 粒子纯装饰 物品转换失败不该打断磨盘运转 这里是每 tick 路径 不记日志免刷屏
        try {
            ItemStack stack = ItemStackUtils.getBukkitStack(current.minecraftItem());
            Particles.emit(world, Particle.ITEM, px, py, pz, GRIND_PARTICLE_COUNT, 0.1, 0.1, 0.1, 0.05, stack);
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

    private World getBukkitWorld() {
        return (World) furniture().position().world().platformWorld();
    }

    // 身位两格空 脚下一格实心 三次查询合成一趟 免得两个方法各算一遍 floor 各取一次 world
    private boolean canStandAt(double x, double y, double z) {
        World world = getBukkitWorld();
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        if (!world.getBlockAt(bx, by, bz).isPassable() || !world.getBlockAt(bx, by + 1, bz).isPassable()) {
            return false;
        }
        Block ground = world.getBlockAt(bx, by - 1, bz);
        return !ground.isPassable() && ground.getType().isSolid();
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
        leadOwner = null;
        this.animating = false;
        this.rawTick = 0;
        this.boosted = false;
    }

    public void stopSpinning(Player leadRecipient) {
        if (pullingAnimal != null) {
            settleAnimalRotation();
            ACTIVE_ANIMAL_PULLERS.remove(pullingAnimal.getUniqueId());
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
        // 磨上没料就停转 生物留在原地待命 再投料自动继续
        if (grindIsEmpty()) {
            if (animating && pullingAnimal != null) {
                if (rawTick % IDLE_FEED_INTERVAL == 0) {
                    tryFeedFromChest();
                }
                rawTick++;
            }
            return;
        }
        if (animating && pullingAnimal != null) {
            tickAnimalDriven();
            return;
        }
        tickPlayerDriven();
    }

    // 生物匀速拉磨
    private void tickAnimalDriven() {
        float seconds = boosted ? (float) MillstoneAnimals.BOOST_SECONDS : currentSeconds;
        float anglePerTick = MillstoneAnimals.anglePerTick(seconds);

        // 视觉角度连续累加不归零 配合较短的更新间隔 保证过起点和高速自转时都不会倒转
        if (rawTick % VISUAL_UPDATE_INTERVAL == 0) {
            currentAngle += anglePerTick * VISUAL_UPDATE_INTERVAL;
            element.updateRotation(currentAngle, VISUAL_UPDATE_INTERVAL);
        }
        float previousOrbitAngle = orbitAngle;
        orbitAngle += anglePerTick;

        if (!moveAnimal()) {
            orbitAngle = previousOrbitAngle;
            stopSpinning();
            return;
        }
        if (rawTick % SHOVE_INTERVAL == 0) {
            shoveBystanders();
        }
        grindEffects();
        rawTick++;
        advanceGrind(anglePerTick);
        wrapOrbitAngle();
        if (grindIsEmpty()) {
            settleAnimalRotation();
        }
    }

    private void settleAnimalRotation() {
        if (Float.compare(currentAngle, orbitAngle) != 0) {
            currentAngle = orbitAngle;
            furniture().setUnsaved();
        }
        element.updateFinalRotation(currentAngle);
    }

    // 玩家推磨 磨杆跟手 玩家绕磨心走多少度磨就转多少度 松手即停
    private void tickPlayerDriven() {
        float advance = pushAdvance();
        if (advance <= 0f) {
            // 停手时把最后不足一个间隔的角度补发出去 免得磨杆停在旧位置
            if (pushVisualPending) {
                pushVisualPending = false;
                pushVisualTick = 0;
                element.updateFinalRotation(currentAngle);
            }
            return;
        }

        currentAngle += advance;
        orbitAngle += advance;
        pushVisualPending = true;
        if (++pushVisualTick >= PUSH_VISUAL_INTERVAL) {
            pushVisualTick = 0;
            element.updateRotation(currentAngle, PUSH_VISUAL_INTERVAL);
        }

        grindEffects();
        rawTick++;
        advanceGrind(advance);
        wrapOrbitAngle();
        if (grindIsEmpty() && pushVisualPending) {
            pushVisualPending = false;
            pushVisualTick = 0;
            element.updateFinalRotation(currentAngle);
        }
    }

    private void grindEffects() {
        if (rawTick % GRIND_PARTICLE_INTERVAL == GRIND_PARTICLE_PHASE) {
            grindParticles();
        }
        if (rawTick % GRIND_SOUND_INTERVAL == 0) {
            playGrindSound();
        }
    }

    // 角度归一 减掉的是整 360 倍数 建出来的四元数完全相同 客户端不会看到跳变
    // 不归一的话视觉角会一直累加 转上几小时后 float 精度掉到度级 磨盘开始抖
    private void wrapOrbitAngle() {
        if (currentAngle >= 360f) {
            currentAngle %= 360f;
        }
        if (orbitAngle >= 360f) {
            orbitAngle -= 360f;
            furniture().setUnsaved();
        }
    }

    // 轨道点相对磨心的偏移 拉磨者的移动目标 生成点 存档恢复点都走这里
    private Vector3f orbitOffset(float radius, float angleDeg) {
        float furnitureRad = (float) Math.toRadians(-furniture().position().yRot());
        Vector3f offset = new Vector3f(-radius, 0f, ORBIT_LATERAL_OFFSET);
        return offset.rotateY(furnitureRad + (float) Math.toRadians(angleDeg));
    }

    private Vector3f orbitOffset() {
        return orbitOffset(currentRadius, orbitAngle);
    }

    // 把 loc 挪到轨道点 保留原有朝向
    private void moveToOrbit(Location loc, float radius, float angleDeg) {
        WorldPosition pos = furniture().position();
        Vector3f offset = orbitOffset(radius, angleDeg);
        loc.setX(pos.x + offset.x);
        loc.setY(pos.y);
        loc.setZ(pos.z + offset.z);
    }

    // orbitOffset 的反解 JOML rotateY 对 atan2(x,z) 就是加法 逐项相减即可拿回轨道角
    private float orbitAngleOf(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(dx, dz)) - orbitBaseDeg;
    }

    // 玩家半身宽在该距离上张开的角度
    private static float bodyHalfAngle(double distance) {
        return (float) Math.toDegrees(Math.asin(Math.min(1.0, PLAYER_HALF_WIDTH / distance)));
    }

    private static float wrapDegrees(float deg) {
        float d = deg % 360f;
        if (d >= 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    // 玩家相对磨杆的角差 正值为玩家在磨杆前方 负值为磨杆已经扫过他 不在接触区返回 NaN
    // 接触弧刻意不对称 玩家很容易跑到磨杆前面去 前方那侧放得宽得多才拽得住
    private float contactDelta(double dx, double dz, double dy) {
        double distSq = dx * dx + dz * dz;
        float maxRadius = behavior.pushBarLength + PUSH_RADIUS_MARGIN;
        if (distSq < PUSH_MIN_RADIUS * PUSH_MIN_RADIUS || distSq > maxRadius * maxRadius) {
            return Float.NaN;
        }
        if (Math.abs(dy) > PUSH_MAX_HEIGHT_DIFF) {
            return Float.NaN;
        }
        float bodyHalf = bodyHalfAngle(Math.sqrt(distSq));
        float delta = wrapDegrees(orbitAngleOf(dx, dz) - orbitAngle);
        if (delta > bodyHalf + behavior.pushLeadTolerance
                || delta < -bodyHalf - behavior.pushContactTolerance) {
            return Float.NaN;
        }
        return delta;
    }

    // 只取追踪该家具的玩家 getNearbyEntities 在 folia 上跨 region 会直接抛异常
    // 玩家可能归别的 region 所有 跨线程读写位置速度不安全 这里直接跳过
    private static org.bukkit.entity.Player pushCandidate(Player p) {
        if (!(p.platformPlayer() instanceof org.bukkit.entity.Player bukkit)) {
            return null;
        }
        if (bukkit.getGameMode() == GameMode.SPECTATOR || !Bukkit.isOwnedByCurrentRegion(bukkit)) {
            return null;
        }
        return bukkit;
    }

    // 玩家推磨 取推得最多的那个人的角位移 没人推返回 0
    // 顺带在同一趟里施加磨杆斥力 接触判定已经算完 分两个循环纯属浪费
    private float pushAdvance() {
        WorldPosition pos = furniture().position();
        float maxStep = MillstoneAnimals.anglePerTick(behavior.pushMaxSeconds);
        boolean resistTick = rawTick % SHOVE_INTERVAL == 0;
        float best = 0f;
        for (Player p : furniture().getTrackedBy()) {
            org.bukkit.entity.Player bukkit = pushCandidate(p);
            if (bukkit == null) {
                continue;
            }
            Location loc = bukkit.getLocation(scratchLoc);
            double dx = loc.getX() - pos.x;
            double dz = loc.getZ() - pos.z;
            float delta = contactDelta(dx, dz, loc.getY() - pos.y);
            if (Float.isNaN(delta) || delta <= 0f) {
                continue;
            }
            if (resistTick) {
                resistOverrun(bukkit, dx, dz, delta);
            }
            float step = Math.min(delta, maxStep);
            if (step > best) {
                best = step;
                this.lastPusher = bukkit.getUniqueId();
            }
        }
        return best;
    }

    // 磨杆是实心的 玩家越到它前面就沿切线往回顶 顶回去磨杆才追得上 转速自然被卡在上限
    // 斥力随越界角度渐强 贴着杆推的人几乎感觉不到 只有想跑赢磨杆的才会被拽住
    private void resistOverrun(org.bukkit.entity.Player player, double dx, double dz, float delta) {
        double ratio = Math.min(1.0, delta / behavior.pushLeadTolerance);
        if (ratio <= 0) {
            return;
        }
        shoveAlongTangent(player, dx, dz, -behavior.pushResistStrength * ratio, 0);
    }

    // 沿切线施力 切向量在方位角 atan2(x,z) 处是 (cos,-sin) 负强度即逆着磨盘转向往回顶
    // 竖直分量只叠加不覆盖 直接写死 Y 会把玩家的跳跃和下落速度一并抹掉
    private static void shoveAlongTangent(org.bukkit.entity.Player player,
                                          double dx, double dz, double strength, double lift) {
        double theta = Math.atan2(dx, dz);
        double y = Math.max(player.getVelocity().getY(), 0) + lift;
        player.setVelocity(new Vector(Math.cos(theta) * strength, y, -Math.sin(theta) * strength));
    }

    // 生物在拉时 转过来的磨杆把挡道的玩家沿切线扫开
    private void shoveBystanders() {
        WorldPosition pos = furniture().position();
        for (Player p : furniture().getTrackedBy()) {
            org.bukkit.entity.Player bukkit = pushCandidate(p);
            if (bukkit == null) {
                continue;
            }
            Location loc = bukkit.getLocation(scratchLoc);
            double dx = loc.getX() - pos.x;
            double dz = loc.getZ() - pos.z;
            float delta = contactDelta(dx, dz, loc.getY() - pos.y);
            if (Float.isNaN(delta) || delta < 0f) {
                continue;
            }
            // 扫人是真撞上才算 不吃推磨那侧为了跟手放宽的前方容差
            if (delta > bodyHalfAngle(Math.sqrt(dx * dx + dz * dz)) + behavior.pushContactTolerance) {
                continue;
            }
            shoveAlongTangent(bukkit, dx, dz, behavior.pushShoveStrength, 0.1);
        }
    }

    // 生物拉磨移动；返回 false 表示已停止
    private boolean moveAnimal() {
        if (!pullingAnimal.isValid() || pullingAnimal.isDead()) return false;

        // getLocation 每次都新建一个 Location 这是每 tick 路径 只取一次
        Location currentLoc = pullingAnimal.getLocation();
        WorldPosition pos = furniture().position();
        if (Math.abs(currentLoc.getY() - pos.y) > 1.0) return false;

        Vector3f targetOffset = orbitOffset();
        double targetX = pos.x + targetOffset.x;
        double targetZ = pos.z + targetOffset.z;

        if (!canStandAt(targetX, currentLoc.getY(), targetZ)) return false;

        double dx = targetX - currentLoc.getX();
        double dz = targetZ - currentLoc.getZ();
        float yaw = dx * dx + dz * dz > YAW_UPDATE_EPSILON
                ? (float) Math.toDegrees(Math.atan2(-dx, dz))
                : currentLoc.getYaw();

        // setAI(false) 的生物不吃 setVelocity 只能每 tick 重定位 位移仍走 clampStep 限幅免得瞬移
        Vector step = clampStep(dx, dz);
        LivingEntity animal = pullingAnimal;
        // currentLoc 是 getLocation 刚建的新对象 原地改即可 不必再拷一份
        currentLoc.setX(currentLoc.getX() + step.getX());
        currentLoc.setZ(currentLoc.getZ() + step.getZ());
        currentLoc.setYaw(yaw);
        FoliaUtil.teleportThen(animal, currentLoc,
                () -> animal.setRotation(currentLoc.getYaw(), currentLoc.getPitch()));
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
        // 交给生物拉之后产出不再算在推磨玩家头上 不清会让产出事件归属到很久以前推过的人
        this.lastPusher = null;
        this.pullingAnimal = animal;
        this.leadOwner = owner;
        this.orbitAngle = this.currentAngle;

        this.animalWasAI = animal.hasAI();
        animal.setAI(false);
        animal.setGravity(false);

        ACTIVE_ANIMAL_PULLERS.put(animal.getUniqueId(), this);

        if (doInitialTeleport) {
            Location loc = animal.getLocation();
            moveToOrbit(loc, currentRadius, orbitAngle);
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

    // 潜行右键只剩剪刀停生物这一条 玩家推磨已改成走到磨杆上直接推 不再需要开关
    private InteractionResult handleSneak(Player player, boolean hasShears) {
        if (!hasShears || !isAnimating() || pullingAnimal == null) {
            return InteractionResult.PASS;
        }
        stopSpinning(player);
        player.swingHand(InteractionHand.MAIN_HAND);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 找玩家正拴着的可拉磨生物 拴多只时取离磨最近的
    // getNearbyEntities 的 AABB 跨 region 会抛 所以先判归属 该判定在 paper 上恒为 true
    private LivingEntity findLeashedAnimal(org.bukkit.entity.Player bukkitPlayer) {
        Location furnitureLoc = new Location(getBukkitWorld(),
                furniture().position().x, furniture().position().y, furniture().position().z);
        if (!Bukkit.isOwnedByCurrentRegion(furnitureLoc, LEASH_SEARCH_CHUNK_RADIUS)) {
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
        // 成年判定必须在摘绳之前 spinWithAnimal 对幼崽返回 false
        // 先摘绳再发现拉不动 玩家就白亏一根拴绳
        if (!MillstoneAnimals.isAdult(target)) {
            return InteractionResult.PASS;
        }

        Location dropLoc = target.getLocation();
        target.setLeashHolder(null);

        if (!spinWithAnimal(target, bukkitPlayer, true)) {
            return InteractionResult.PASS;
        }

        // 摘绳会掉出一根拴绳物品 拉磨成功才去收掉它
        FoliaUtil.run(() -> removeFreshLeadDrop(dropLoc), dropLoc);

        player.sendActionBar(Localization.component(MessageKeys.MILLSTONE_STOP_ANIMAL_HINT));
        player.swingHand(InteractionHand.MAIN_HAND);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 摘绳掉出的那根拴绳 只认刚落地的单个 否则会把玩家自己扔在旁边的拴绳一并吃掉
    // 调度只保证 dropLoc 本格归本 region AABB 还会外扩一格 跨出去 getNearbyEntities 就抛
    private static void removeFreshLeadDrop(Location dropLoc) {
        if (!Bukkit.isOwnedByCurrentRegion(dropLoc, LEAD_DROP_SEARCH_CHUNK_RADIUS)) {
            return;
        }
        for (Entity e : dropLoc.getWorld().getNearbyEntities(
                dropLoc, LEAD_DROP_SEARCH_RADIUS, LEAD_DROP_SEARCH_RADIUS, LEAD_DROP_SEARCH_RADIUS)) {
            if (!(e instanceof org.bukkit.entity.Item dropped) || dropped.getTicksLived() > FRESH_DROP_MAX_TICKS) {
                continue;
            }
            ItemStack stack = dropped.getItemStack();
            if (stack.getType() == Material.LEAD && stack.getAmount() == 1) {
                dropped.remove();
                return;
            }
        }
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

        World world = getBukkitWorld();
        Location spawnLoc = new Location(world, 0, 0, 0);
        moveToOrbit(spawnLoc, (float) profile.orbitRadius(), currentAngle);

        if (!(world.spawnEntity(spawnLoc, type) instanceof LivingEntity animal)) {
            return InteractionResult.PASS;
        }
        // 没能开始拉磨就把刚生成的实体撤掉 别扣蛋也别留个生物在场上
        if (!spinWithAnimal(animal, null, false)) {
            animal.remove();
            return InteractionResult.PASS;
        }
        player.sendActionBar(Localization.component(MessageKeys.MILLSTONE_STOP_ANIMAL_HINT));
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
        World world = getBukkitWorld();
        Location dropLoc = new Location(world,
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
            Location furnitureLoc = new Location(
                    (World) fp.world().platformWorld(), fp.x, fp.y, fp.z);
            FoliaUtil.runLater(this::restoreAnimal, 1L, furnitureLoc);
        }
    }

    private void restoreAnimal() {
        if (pendingAnimalUUID == null) return;

        Entity entity = Bukkit.getEntity(pendingAnimalUUID);
        this.pendingAnimalUUID = null;

        if (!(entity instanceof LivingEntity living)) {
            abortRestore();
            return;
        }
        // 这里跑在家具所属 region 动物可能归别的 region isValid setAI 都走 getHandle 跨 region 直接抛
        FoliaUtil.runEntity(living, () -> attachRestoredAnimal(living), this::abortRestore);
    }

    private void attachRestoredAnimal(LivingEntity living) {
        this.animalWasAI = this.savedAnimalWasAI;
        // 找到了但已死 没什么可还原的 磨盘退回静止即可
        if (!living.isValid()) {
            abortRestore();
            return;
        }
        this.pullingAnimal = living;
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

        Location loc = living.getLocation();
        moveToOrbit(loc, currentRadius, orbitAngle);
        FoliaUtil.teleport(living, loc);
    }

    // 动物已不在或已永久移除 磨盘退回静止
    private void abortRestore() {
        this.animating = false;
        furniture().setUnsaved();
        this.element.updateFinalRotation(this.currentAngle);
    }

    @Override
    public void onUnload(boolean isStopping) {
        if (isStopping) {
            if (pullingAnimal != null) {
                ACTIVE_ANIMAL_PULLERS.remove(pullingAnimal.getUniqueId());
            }
        } else {
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
            Tag itemTag = BlockEntityNbt.itemTag(grindItems[i]);
            if (itemTag == null) {
                continue;
            }
            CompoundTag e = new CompoundTag();
            e.putInt(K_SLOT, i);
            e.put(K_ITEM, itemTag);
            e.putFloat(K_PROGRESS, grindDegrees[i]);
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
            grindDegrees[i] = 0f;
            requiredRotations[i] = 0;
        }
        filledSlots = 0;

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
                Item loaded = nms == null ? Item.empty() : ItemStackUtils.wrap(nms);
                if (ItemUtils.isEmpty(loaded)) {
                    continue;
                }
                // 同一 slot 在存档里出现两次时只计一次 多计会让 grindIsEmpty 永假 磨盘空转不停
                if (grindItems[slot].isEmpty()) {
                    filledSlots++;
                }
                grindItems[slot] = loaded;
                grindDegrees[slot] = e.getFloat(K_PROGRESS, 0f);
                requiredRotations[slot] = Math.max(1, e.getInt(K_ROTATIONS, behavior.grindRotations));
            }
        }
    }

    public WorldPosition position() {
        return furniture().position();
    }

    // 关服只做纯内存清理 静态表不清会连着旧 ClassLoader 一起泄漏
    public static void clearAll() {
        ACTIVE_ANIMAL_PULLERS.clear();
    }
}
