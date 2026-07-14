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
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.ApplianceFoodRegistry;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.FoodRecipeResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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

    public static final ConcurrentHashMap<UUID, MillstoneController> ACTIVE_PUSHERS = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<UUID, MillstoneController> ACTIVE_ANIMAL_PULLERS = new ConcurrentHashMap<>();

    // 罢工标记写在动物自己的 PDC 上 关闭 AI 并禁骑乘/道具交互 直到重新拴绳绑定+正常停止才恢复
    private static final org.bukkit.NamespacedKey STRUCK_KEY = org.bukkit.NamespacedKey.fromString("kaleidoscopecookery:millstone_struck");

    public static boolean isStruck(org.bukkit.entity.Entity e) {
        return STRUCK_KEY != null && e.getPersistentDataContainer().has(STRUCK_KEY, org.bukkit.persistence.PersistentDataType.BYTE);
    }

    public static void setStruck(org.bukkit.entity.Entity e, boolean struck) {
        if (STRUCK_KEY == null) {
            return;
        }
        if (struck) {
            e.getPersistentDataContainer().set(STRUCK_KEY, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        } else {
            e.getPersistentDataContainer().remove(STRUCK_KEY);
        }
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

    // 视觉旋转每隔几 tick 更新一次
    private static final int VISUAL_UPDATE_INTERVAL = 5;

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

    private org.bukkit.entity.LivingEntity pullingAnimal = null;
    private boolean animalWasAI = true;
    private org.bukkit.entity.Player leadOwner = null;

    private static final String DATA_KEY = "kaleidoscopecookery:millstone";

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

    public Item[] getGrindItems() {
        return grindItems;
    }

    private double[] facingDir() {
        double rad = Math.toRadians(furniture().position().yRot());
        return new double[]{-Math.sin(rad), Math.cos(rad)};
    }

    // 生物罢工
    private void strikeAnimal() {
        org.bukkit.entity.LivingEntity animal = this.pullingAnimal;
        if (animal == null) {
            return;
        }
        ACTIVE_ANIMAL_PULLERS.remove(animal.getUniqueId());
        setStruck(animal, true);
        if (animal.isValid()) {
            animal.setGravity(true);
            animal.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
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
                new org.bukkit.Location(world, ex, ey, ez), new org.bukkit.inventory.ItemStack(org.bukkit.Material.LEAD));
        dropped.setVelocity(new org.bukkit.util.Vector(dir[0] * 0.25, 0.12, dir[1] * 0.25));
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

        for (org.bukkit.inventory.ItemStack stack : stacks) {
            ejectProduct(stack);
        }
    }

    // 成品像发射器/投掷器一样朝石磨朝向喷出
    private void ejectProduct(org.bukkit.inventory.ItemStack stack) {
        org.bukkit.World world = getBukkitWorld();
        double[] dir = facingDir();
        double ex = furniture().position().x + dir[0] * 0.6;
        double ey = furniture().position().y + 1.0;
        double ez = furniture().position().z + dir[1] * 0.6;
        org.bukkit.entity.Item dropped = world.dropItem(new org.bukkit.Location(world, ex, ey, ez), stack);
        dropped.setVelocity(new org.bukkit.util.Vector(dir[0] * 0.25, 0.12, dir[1] * 0.25));
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
        try {
            org.bukkit.inventory.ItemStack stack = ItemStackUtils.getBukkitStack(current.minecraftItem());
            Particles.emit(world, org.bukkit.Particle.ITEM, px, py, pz, 5, 0.1, 0.1, 0.1, 0.05, stack);
        } catch (Exception ignored) {}
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
        if (!(pullingAnimal instanceof org.bukkit.entity.ChestedHorse horse) || !horse.isCarryingChest()) {
            return;
        }
        if (firstEmptyGrindSlot() < 0) {
            return;
        }
        org.bukkit.inventory.Inventory inv = horse.getInventory();
        for (int s = 0; s < inv.getSize(); s++) {
            org.bukkit.inventory.ItemStack stack = inv.getItem(s);
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
        org.bukkit.block.data.BlockData feet = world.getBlockAt(bx, (int) Math.floor(y), bz).getBlockData();
        return feet instanceof org.bukkit.block.data.type.Snow snow && snow.getLayers() > 1;
    }

    public static boolean isFullCubeTop(org.bukkit.block.Block block) {
        if (block.getBlockData() instanceof org.bukkit.block.data.type.Stairs) {
            return false;
        }
        org.bukkit.util.BoundingBox box = block.getBoundingBox();
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

    public void stopSpinning(Player leadRecipient) {
        if (pullingPlayer != null) {
            unregister(pullingPlayer.uuid());
            pullingPlayer = null;
        }
        if (pullingAnimal != null) {
            ACTIVE_ANIMAL_PULLERS.remove(pullingAnimal.getUniqueId());
            setStruck(pullingAnimal, false);
            if (pullingAnimal.isValid()) {
                pullingAnimal.setAI(animalWasAI);
                pullingAnimal.setGravity(true);
                pullingAnimal.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
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

        if (pullingAnimal != null && rawTick % 100 == 0 && Math.random() < 0.005) {
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

        if (dx * dx + dz * dz > 4.0) {
            currentLoc.setX(targetX);
            currentLoc.setZ(targetZ);
            bukkitPlayer.teleport(currentLoc);
            bukkitPlayer.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        } else {
            double vx = dx * 0.8;
            double vz = dz * 0.8;
            double speedSq = vx * vx + vz * vz;
            final double MAX = 0.25;
            if (speedSq > MAX * MAX) {
                double scale = MAX / Math.sqrt(speedSq);
                vx *= scale;
                vz *= scale;
            }
            bukkitPlayer.setVelocity(new org.bukkit.util.Vector(vx, 0, vz));
        }
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
        if (dx * dx + dz * dz > 1e-6) {
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            currentLoc.setYaw(yaw);
        }

        currentLoc.setX(targetX);
        currentLoc.setZ(targetZ);
        pullingAnimal.teleport(currentLoc);
        pullingAnimal.setRotation(currentLoc.getYaw(), currentLoc.getPitch());
        return true;
    }

    public boolean spin(Player player) {
        if (animating) return false;

        if (ACTIVE_PUSHERS.containsKey(player.uuid())) {
            player.sendActionBar(Component.text(behavior.msgAlreadyPushing));
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
            player.sendActionBar(Component.text(behavior.msgNeedGroundBelow));
            return false;
        }

        if (isUnevenStandingPoint(startX, furniture().position().y(), startZ)) {
            player.sendActionBar(Component.text(behavior.msgUneven));
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
                if (bukkitPlayer.getGameMode() != org.bukkit.GameMode.CREATIVE
                        && bukkitPlayer.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                    bukkitPlayer.setAllowFlight(false);
                }
            }

            org.bukkit.Location loc = bukkitPlayer.getLocation();
            loc.setX(startX);
            loc.setZ(startZ);
            bukkitPlayer.teleport(loc);
            bukkitPlayer.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        }

        playMillstoneSound(1.0f, 0.8f);

        furniture().setUnsaved();
        return true;
    }

    // owner 为触发绑定的玩家 停止时退还拴绳 怪物蛋触发时传 null
    public void spinWithAnimal(org.bukkit.entity.LivingEntity animal, org.bukkit.entity.Player owner, boolean doInitialTeleport) {
        if (animating) return;
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
            animal.teleport(loc);
        }

        playMillstoneSound(1.0f, 0.8f);

        tryFeedFromChest();
        furniture().setUnsaved();
    }

    // 拉磨者被打 加速到骡子的速度
    public void onPullerDamaged() {
        if (!animating) return;
        this.boosted = true;
    }

    public boolean isAnimating() {
        return animating;
    }

    public float getCurrentAngle() {
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
        if (hasShears) {
            if (isAnimating() && pullingAnimal != null) {
                stopSpinning(player);
            }
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        if (isAnimating()) {
            if (pullingPlayer != null) {
                stopSpinning();
            }
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        double sneakHeightDiff = Math.abs(player.y() - furniture().position().y);
        if (sneakHeightDiff > 0.1) {
            player.sendActionBar(Component.text(behavior.msgNotSamePlane));
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        if (spin(player)) {
            player.sendActionBar(Component.text(behavior.msgExitHint));
        }
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 拴绳右键 把玩家拴着的牛/驴/骡接到石磨上拉磨
    private InteractionResult handleLeash(Player player, org.bukkit.entity.Player bukkitPlayer) {
        if (isAnimating()) {
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        org.bukkit.World world = getBukkitWorld();
        org.bukkit.Location furnitureLoc = new org.bukkit.Location(world,
                furniture().position().x, furniture().position().y, furniture().position().z);

        org.bukkit.entity.LivingEntity target = null;
        for (org.bukkit.entity.Entity nearby : world.getNearbyEntities(furnitureLoc, 20, 20, 20)) {
            if (!(nearby instanceof org.bukkit.entity.LivingEntity living)) {
                continue;
            }
            if (!MillstoneAnimals.instance().canPull(living) || !living.isLeashed()) {
                continue;
            }
            // 已在拉别的磨就跳过 防止一只生物被牵去同时拉多个
            if (ACTIVE_ANIMAL_PULLERS.containsKey(living.getUniqueId())) {
                continue;
            }
            try {
                if (living.getLeashHolder().equals(bukkitPlayer)) {
                    target = living;
                    break;
                }
            } catch (Exception ignored) {
            }
        }

        if (target == null) {
            return InteractionResult.PASS;
        }

        org.bukkit.Location dropLoc = target.getLocation().clone();
        target.setLeashHolder(null);

        BukkitCraftEngine.instance().scheduler().platform().run(
                () -> {
                    for (org.bukkit.entity.Entity e : dropLoc.getWorld().getNearbyEntities(dropLoc, 1, 1, 1)) {
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
        player.sendActionBar(Component.text(behavior.msgStopAnimalHint));
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 刷怪蛋右键 在轨道点生成支持的生物并立即拉磨 非支持生物放行
    private InteractionResult handleSpawnEgg(Player player, Material mat, Item eggItem) {
        if (isAnimating()) {
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        org.bukkit.entity.EntityType type = spawnEggType(mat);
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

        if (!(world.spawnEntity(spawnLoc, type) instanceof org.bukkit.entity.LivingEntity animal)) {
            return InteractionResult.PASS;
        }
        spinWithAnimal(animal, null, false);
        player.sendActionBar(Component.text(behavior.msgStopAnimalHint));
        InventoryUtils.shrinkHeld(player, eggItem, 1);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 刷怪蛋 Material 解析为生物类型 形如 X_SPAWN_EGG 取 X 解析失败返回 null
    private static org.bukkit.entity.EntityType spawnEggType(Material mat) {
        String name = mat.name();
        if (!name.endsWith("_SPAWN_EGG")) {
            return null;
        }
        try {
            return org.bukkit.entity.EntityType.valueOf(name.substring(0, name.length() - "_SPAWN_EGG".length()));
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

        org.bukkit.entity.Entity entity = org.bukkit.Bukkit.getEntity(pendingAnimalUUID);
        this.pendingAnimalUUID = null;

        if (entity instanceof org.bukkit.entity.LivingEntity living && living.isValid()) {
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
            living.teleport(loc);
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
                    pullingAnimal.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                }
            }
        }
        furniture().setUnsaved();
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        CompoundTag data = new CompoundTag();
        data.putBoolean("animating", this.animating);
        data.putFloat("orbit_angle", this.orbitAngle);
        data.putFloat("current_angle", this.currentAngle % 360f);
        data.putBoolean("boosted", this.boosted);
        data.putFloat("anim_seconds", this.currentSeconds);
        data.putInt("raw_tick", this.rawTick);

        if (pullingAnimal != null && pullingAnimal.isValid()) {
            data.putIntArray("animal_uuid", UUIDUtils.uuidToIntArray(pullingAnimal.getUniqueId()));
            data.putString("animal_type", pullingAnimal instanceof org.bukkit.entity.Cow ? "cow"
                    : (pullingAnimal instanceof org.bukkit.entity.Mule ? "mule" : "donkey"));
            data.putBoolean("animal_was_ai", this.animalWasAI);
        }

        ListTag grindTag = new ListTag();
        for (int i = 0; i < GRIND_SLOTS; i++) {
            if (grindItems[i].isEmpty()) {
                continue;
            }
            CompoundTag e = new CompoundTag();
            e.putInt("slot", i);
            e.put("item", ItemStackUtils.saveMinecraftItemStackAsTag(grindItems[i].minecraftItem()));
            e.putInt("progress", grindProgress[i]);
            e.putInt("rotations", requiredRotations[i]);
            grindTag.add(e);
        }
        data.put("grind_items", grindTag);
        data.putInt("grind_data_version", VersionHelper.WORLD_VERSION);
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
        this.animating = data.getBoolean("animating", false);
        this.orbitAngle = data.getFloat("orbit_angle", 0f);
        this.currentAngle = data.getFloat("current_angle", 0f);
        this.boosted = data.getBoolean("boosted", false);
        this.currentSeconds = data.getFloat("anim_seconds", (float) MillstoneAnimals.PLAYER_SECONDS);
        this.rawTick = data.getInt("raw_tick", 0);

        int[] animalUuid = data.getIntArray("animal_uuid");
        if (this.animating && animalUuid != null && animalUuid.length == 4) {
            this.pendingAnimalUUID = UUIDUtils.uuidFromIntArray(animalUuid);
            this.savedAnimalWasAI = data.getBoolean("animal_was_ai", true);
        }

        int gdv = data.getInt("grind_data_version", Config.itemDataFixerUpperFallbackVersion());
        ListTag grindTag = data.getList("grind_items");
        if (grindTag != null) {
            for (Tag t : grindTag) {
                if (!(t instanceof CompoundTag e)) {
                    continue;
                }
                int slot = e.getInt("slot", -1);
                if (slot < 0 || slot >= GRIND_SLOTS) {
                    continue;
                }
                Object nms = ItemStackUtils.parseMinecraftItem(e.getCompound("item"), gdv);
                if (nms != null) {
                    grindItems[slot] = ItemStackUtils.wrap(nms);
                    grindProgress[slot] = e.getInt("progress", 0);
                    requiredRotations[slot] = e.getInt("rotations", behavior.grindRotations);
                }
            }
        }
    }

    public WorldPosition getPosition() {
        return furniture().position();
    }
}