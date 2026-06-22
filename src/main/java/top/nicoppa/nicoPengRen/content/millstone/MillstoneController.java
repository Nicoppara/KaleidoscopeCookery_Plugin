package top.nicoppa.nicoPengRen.content.millstone;

import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureController;
import net.momirealms.craftengine.core.entity.furniture.element.FurnitureElement;
import net.momirealms.craftengine.core.entity.furniture.hitbox.FurnitureHitBox;
import net.momirealms.craftengine.core.entity.furniture.tick.FurnitureTicker;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.core.world.context.InteractEntityContext;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.joml.Vector3f;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 石磨家具实体 维护拉磨动画状态（轨道角度/视觉角度/加速标志） 驱动玩家或生物绕石磨拉磨并同步位置
 * 负责重启恢复与 NBT 存读 交互入口在 {@link #useOnFurniture} 渲染见 {@link MillstoneElement}
 * 死亡/受伤事件由 {@link MillstoneDamageListener}、{@link MillstoneAnimalListener} 经静态注册表回调
 */
public class MillstoneController extends FurnitureController {

    /** 供 MillstoneDamageListener 查询玩家拉磨 */
    public static final ConcurrentHashMap<UUID, MillstoneController> ACTIVE_PUSHERS = new ConcurrentHashMap<>();
    /** 供 MillstoneAnimalListener 查询生物死亡 */
    public static final ConcurrentHashMap<UUID, MillstoneController> ACTIVE_ANIMAL_PULLERS = new ConcurrentHashMap<>();

    // 罢工标记 写在动物自己的 PDC 上 AI 关闭、禁骑乘/道具交互，直到重新拴绳绑定+正常停止才恢复
    private static final org.bukkit.NamespacedKey STRUCK_KEY = org.bukkit.NamespacedKey.fromString("nicopengren:millstone_struck");

    public static boolean isStruck(org.bukkit.entity.Entity e) {
        return STRUCK_KEY != null && e.getPersistentDataContainer().has(STRUCK_KEY, org.bukkit.persistence.PersistentDataType.BYTE);
    }
    public static void setStruck(org.bukkit.entity.Entity e, boolean struck) {
        if (STRUCK_KEY == null) return;
        if (struck) e.getPersistentDataContainer().set(STRUCK_KEY, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        else e.getPersistentDataContainer().remove(STRUCK_KEY);
    }

    public static void register(UUID uuid, MillstoneController ctrl) {
        ACTIVE_PUSHERS.put(uuid, ctrl);
    }

    public static void unregister(UUID uuid) {
        if (uuid != null) ACTIVE_PUSHERS.remove(uuid);
    }

    private static final float NORMAL_ANGLE_PER_TICK  = 0.9f;
    private static final float BOOSTED_ANGLE_PER_TICK = 1.2f;
    private static final float NORMAL_VISUAL_STEP     = 18.0f;
    private static final float BOOSTED_VISUAL_STEP    = 24.0f;

    private UUID    pendingAnimalUUID = null;
    private boolean savedAnimalWasAI  = true;

    private final MillstoneBehavior behavior;
    private final MillstoneElement element;

    private boolean animating    = false;
    private int     rawTick      = 0;
    private float   orbitAngle   = 0f;
    private float   currentAngle = 0f;
    private boolean boosted      = false;

    private Player pullingPlayer = null;

    private org.bukkit.entity.LivingEntity pullingAnimal = null;
    private boolean animalWasAI = true;
    private org.bukkit.entity.Player leadOwner = null;

    public static final int GRIND_SLOTS = 8;
    private final Item[] grindItems = new Item[GRIND_SLOTS];
    private final int[] grindProgress = new int[GRIND_SLOTS];
    private final int[] grindTime = new int[GRIND_SLOTS];

    public MillstoneController(Furniture furniture, MillstoneBehavior behavior) {
        super(furniture);
        this.behavior = behavior;
        java.util.Arrays.fill(this.grindItems, Item.empty());
        this.element = new MillstoneElement(this, furniture.position());
    }

    public MillstoneBehavior behavior() { return behavior; }

    public boolean canGrind(Item item) {
        return top.nicoppa.nicoPengRen.recipe.food.ApplianceFoodRegistry.instance()
                .isAllowed(top.nicoppa.nicoPengRen.recipe.ApplianceType.MILLSTONE, item.id());
    }

    private Item getGrindResult(Item input) {
        return top.nicoppa.nicoPengRen.recipe.food.FoodRecipeRegistry.instance()
                .findAccurate(top.nicoppa.nicoPengRen.recipe.ApplianceType.MILLSTONE, input.id())
                .map(top.nicoppa.nicoPengRen.recipe.food.FoodRecipeResult::item)
                .orElse(input.copy());
    }

    private int firstEmptyGrindSlot() {
        for (int i = 0; i < GRIND_SLOTS; i++) if (grindItems[i].isEmpty()) return i;
        return -1;
    }

    public boolean tryAddGrind(Item item) {
        int i = firstEmptyGrindSlot();
        if (i < 0) return false;
        grindItems[i] = item.copyWithCount(1);
        grindProgress[i] = 0;
        grindTime[i] = behavior.playerGrindTime;
        element.spawnGrindSlot(i, grindItems[i]);
        furniture().setUnsaved();
        return true;
    }

    private void clearGrindSlot(int i) {
        grindItems[i] = Item.empty();
        grindProgress[i] = 0;
        grindTime[i] = 0;
        element.removeGrindSlot(i);
    }

    public boolean takeGrind(Player player, net.momirealms.craftengine.core.entity.player.InteractionHand hand) {
        for (int i = 0; i < GRIND_SLOTS; i++) {
            if (grindItems[i].isEmpty()) continue;
            top.nicoppa.nicoPengRen.common.item.InventoryUtils.giveOrHold(player, hand, grindItems[i].copy());
            clearGrindSlot(i);
            furniture().setUnsaved();
            tryFeedFromChest();
            return true;
        }
        return false;
    }

    public boolean grindIsEmpty() {
        for (int i = 0; i < GRIND_SLOTS; i++) if (!grindItems[i].isEmpty()) return false;
        return true;
    }

    // 研磨 只在被推时由 tick 调用 磨好的成品立即朝石磨朝向喷出
    private void grindStep() {
        boolean changed = false;
        int activeTime = pullingAnimal != null ? behavior.animalGrindTime
                : (boosted ? behavior.boostedGrindTime : behavior.playerGrindTime);
        int step = Math.max(1, behavior.playerGrindTime / Math.max(1, activeTime));
        for (int i = 0; i < GRIND_SLOTS; i++) {
            if (grindTime[i] <= 0) continue;
            grindProgress[i] += step;
            if (grindProgress[i] >= grindTime[i]) {
                Item result = getGrindResult(grindItems[i]);
                clearGrindSlot(i);
                ejectProduct(result);
                changed = true;
            }
        }
        if (changed) {
            furniture().setUnsaved();
            tryFeedFromChest();
        }
    }

    public Item[] getGrindItems() { return grindItems; }

    private double[] facingDir() {
        double rad = Math.toRadians(furniture().position().yRot());
        return new double[]{-Math.sin(rad), Math.cos(rad)};
    }

    // 生物罢工
    private void strikeAnimal() {
        org.bukkit.entity.LivingEntity animal = this.pullingAnimal;
        if (animal == null) return;
        ACTIVE_ANIMAL_PULLERS.remove(animal.getUniqueId());
        setStruck(animal, true);
        if (animal.isValid()) {
            animal.setGravity(true);
            animal.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        }
        if (leadOwner != null) ejectLead();
        this.pullingAnimal = null;
        this.leadOwner = null;
        this.animating = false;
        this.boosted = false;
        this.rawTick = 0;
        furniture().setUnsaved();
    }

    // 拴绳从产物口弹出（罢工时）
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

    // 成品像发射器/投掷器一样朝石磨朝向喷出
    private void ejectProduct(Item product) {
        if (product == null || product.isEmpty()) return;
        org.bukkit.World world = getBukkitWorld();
        double[] dir = facingDir();
        double ex = furniture().position().x + dir[0] * 0.6;
        double ey = furniture().position().y + 1.0;
        double ez = furniture().position().z + dir[1] * 0.6;
        org.bukkit.inventory.ItemStack stack = ItemStackUtils.getBukkitStack(product.minecraftItem());
        org.bukkit.entity.Item dropped = world.dropItem(new org.bukkit.Location(world, ex, ey, ez), stack);
        dropped.setVelocity(new org.bukkit.util.Vector(dir[0] * 0.25, 0.12, dir[1] * 0.25));
        dropped.setPickupDelay(10);
    }

    // 研磨粒子
    private void grindParticles() {
        Item current = null;
        for (int i = 0; i < GRIND_SLOTS; i++) if (!grindItems[i].isEmpty()) { current = grindItems[i]; break; }
        if (current == null) return;
        org.bukkit.World world = getBukkitWorld();
        float rad = (float) Math.toRadians(currentAngle);
        float baseYaw = (float) Math.toRadians(-furniture().position().yRot() - 90);
        Vector3f p = new Vector3f(0, 1, -0.5f).rotateY(rad).rotateY(baseYaw + (float) Math.PI);
        double px = furniture().position().x + p.x;
        double py = furniture().position().y + p.y;
        double pz = furniture().position().z + p.z;
        try {
            org.bukkit.inventory.ItemStack stack = ItemStackUtils.getBukkitStack(current.minecraftItem());
            world.spawnParticle(org.bukkit.Particle.ITEM, px, py, pz, 5, 0.1, 0.1, 0.1, 0.05, stack);
        } catch (Exception ignored) {}
    }

    private void playMillstoneSound(float volume, float pitch) {
        furniture().position().world().playSound(furniture().position(),
                Key.of("custom:millstone_" + (int) (Math.random() * 6)), volume, pitch, SoundSource.BLOCK);
    }

    private void playGrindSound() {
        playMillstoneSound(0.5f, 0.9f + (float) Math.random() * 0.2f);
    }

    // 驴/骡自动化
    private void tryFeedFromChest() {
        if (!(pullingAnimal instanceof org.bukkit.entity.ChestedHorse horse) || !horse.isCarryingChest()) return;
        if (firstEmptyGrindSlot() < 0) return;
        org.bukkit.inventory.Inventory inv = horse.getInventory();
        for (int s = 0; s < inv.getSize(); s++) {
            org.bukkit.inventory.ItemStack stack = inv.getItem(s);
            if (stack == null || stack.getType().isAir()) continue;
            Item ce = net.momirealms.craftengine.bukkit.item.BukkitItemManager.instance().wrap(stack);
            if (ce == null || ce.isEmpty() || !canGrind(ce)) continue;
            while (firstEmptyGrindSlot() >= 0 && stack.getAmount() > 0) {
                if (tryAddGrind(ce)) stack.setAmount(stack.getAmount() - 1);
                else break;
            }
            inv.setItem(s, stack.getAmount() > 0 ? stack : null);
            if (firstEmptyGrindSlot() < 0) break;
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
        if (!isFullCubeTop(world.getBlockAt(bx, (int) Math.floor(y) - 1, bz))) return true;
        org.bukkit.block.data.BlockData feet = world.getBlockAt(bx, (int) Math.floor(y), bz).getBlockData();
        return feet instanceof org.bukkit.block.data.type.Snow snow && snow.getLayers() > 1;
    }

    public static boolean isFullCubeTop(org.bukkit.block.Block block) {
        if (block.getBlockData() instanceof org.bukkit.block.data.type.Stairs) return false;
        org.bukkit.util.BoundingBox box = block.getBoundingBox();
        double eps = 1e-6;
        return (box.getMinX() - block.getX()) <= eps
                && (box.getMaxX() - block.getX()) >= 1 - eps
                && (box.getMinZ() - block.getZ()) <= eps
                && (box.getMaxZ() - block.getZ()) >= 1 - eps
                && (box.getMaxY() - block.getY()) >= 1 - eps;
    }

    public void stopSpinning() { stopSpinning(null); }

    public void stopSpinning(Player leadRecipient) {
        // 清理玩家
        if (pullingPlayer != null) {
            unregister(pullingPlayer.uuid());
            pullingPlayer = null;
        }
        // 清理生物
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
                    Item lead = net.momirealms.craftengine.bukkit.item.BukkitItemManager.instance()
                            .createWrappedItem(Key.of("minecraft:lead"), null);
                    if (lead != null) top.nicoppa.nicoPengRen.common.item.InventoryUtils.give(leadRecipient, lead);
                } else {
                    ejectLead();
                }
            }
            pullingAnimal = null;
            leadOwner     = null;
        }
        this.animating = false;
        this.rawTick   = 0;
        this.boosted   = false;
        furniture().setUnsaved();
    }

    public void tick() {
        if (!animating) return;

        grindStep();

        if (pullingAnimal != null && rawTick % 100 == 0 && Math.random() < 0.005) {
            strikeAnimal();
            return;
        }

        if (pullingAnimal != null && grindIsEmpty() && rawTick % 20 == 0) tryFeedFromChest();

        if (!grindIsEmpty()) {
            if (rawTick % 5 == 2) grindParticles();
            if (rawTick % 25 == 0) playGrindSound();
        }

        float anglePerTick = boosted ? BOOSTED_ANGLE_PER_TICK : NORMAL_ANGLE_PER_TICK;
        float visualStep   = boosted ? BOOSTED_VISUAL_STEP    : NORMAL_VISUAL_STEP;

        if (rawTick % 20 == 0) {
            currentAngle += visualStep;
            element.updateRotation(currentAngle, 20);
        }

        orbitAngle += anglePerTick;

        if (pullingPlayer != null) {
            org.bukkit.entity.Player bukkitPlayer = org.bukkit.Bukkit.getPlayer(pullingPlayer.uuid());
            if (bukkitPlayer == null) {
                stopSpinning();
                return;
            }

            double heightDiff = Math.abs(bukkitPlayer.getLocation().getY() - furniture().position().y);
            if (heightDiff > 0.5) {
                stopSpinning();
                return;
            }

            float furnitureRad = (float) Math.toRadians(-furniture().position().yRot());
            float animRad      = (float) Math.toRadians(orbitAngle);
            float radius       = 2.5f;

            Vector3f targetOffset = new Vector3f(-radius, 0f, -0.5f);
            targetOffset.rotateY(furnitureRad + animRad);

            org.bukkit.Location currentLoc = bukkitPlayer.getLocation();
            double targetX = furniture().position().x + targetOffset.x;
            double targetZ = furniture().position().z + targetOffset.z;

            if (!isPassable(targetX, currentLoc.getY(), targetZ)) {
                stopSpinning();
                return;
            }

            if (!hasSolidBlockUnderneath(targetX, currentLoc.getY(), targetZ)) {
                stopSpinning();
                return;
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
        }

        else if (pullingAnimal != null) {
            if (!pullingAnimal.isValid() || pullingAnimal.isDead()) {
                stopSpinning();
                return;
            }

            double heightDiff = Math.abs(pullingAnimal.getLocation().getY() - furniture().position().y);
            if (heightDiff > 1.0) {
                stopSpinning();
                return;
            }

            float furnitureRad = (float) Math.toRadians(-furniture().position().yRot());
            float animRad      = (float) Math.toRadians(orbitAngle);
            float radius       = 2.5f;

            Vector3f targetOffset = new Vector3f(-radius, 0f, -0.5f);
            targetOffset.rotateY(furnitureRad + animRad);

            org.bukkit.Location currentLoc = pullingAnimal.getLocation();
            double targetX = furniture().position().x + targetOffset.x;
            double targetZ = furniture().position().z + targetOffset.z;

            if (!isPassable(targetX, currentLoc.getY(), targetZ)) {
                stopSpinning();
                return;
            }

            if (!hasSolidBlockUnderneath(targetX, currentLoc.getY(), targetZ)) {
                stopSpinning();
                return;
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
        }

        rawTick++;

        if (orbitAngle >= 360f) {
            orbitAngle   -= 360f;
            currentAngle  = 0f;
            rawTick       = 0;
            completeSpin();
        }
    }

    private void completeSpin() {
        furniture().setUnsaved();
    }

    public boolean spin(Player player) {
        if (animating) return false;

        if (ACTIVE_PUSHERS.containsKey(player.uuid())) {
            player.sendActionBar(Component.text(behavior.msgAlreadyPushing));
            return false;
        }

        float furnitureRad = (float) Math.toRadians(-furniture().position().yRot());
        float startRad     = (float) Math.toRadians(orbitAngle);
        float radius       = 2.5f;

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

        this.animating     = true;
        this.rawTick       = 0;
        this.boosted       = false;
        this.pullingPlayer = player;
        this.orbitAngle    = this.currentAngle;

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

        furniture().setUnsaved(); // Bug A fix
        return true;
    }

    /**
     * 生物拉磨。
     *
     * @param animal             拉磨的生物
     * @param owner              触发绑定的玩家，停止时退还拴绳；怪物蛋触发时传 null
     * @param doInitialTeleport  是否立即传送到起始位置
     */
    public void spinWithAnimal(org.bukkit.entity.LivingEntity animal, org.bukkit.entity.Player owner, boolean doInitialTeleport) {
        if (animating) return;
        this.animating     = true;
        this.boosted       = true;
        this.rawTick       = 0;
        this.pullingPlayer = null;
        this.pullingAnimal = animal;
        this.leadOwner     = owner;
        this.orbitAngle    = this.currentAngle;

        this.animalWasAI = isStruck(animal) || animal.hasAI();
        animal.setAI(false);
        animal.setGravity(false);

        ACTIVE_ANIMAL_PULLERS.put(animal.getUniqueId(), this);

        if (doInitialTeleport) {
            float furnitureRad = (float) Math.toRadians(-furniture().position().yRot());
            float startRad     = (float) Math.toRadians(orbitAngle);
            float radius       = 2.5f;

            Vector3f startOffset = new Vector3f(-radius, 0f, -0.5f);
            startOffset.rotateY(furnitureRad + startRad);

            org.bukkit.Location loc = animal.getLocation();
            loc.setX(furniture().position().x() + startOffset.x);
            loc.setY(furniture().position().y());
            loc.setZ(furniture().position().z() + startOffset.z);
            animal.teleport(loc);
        }

        playMillstoneSound(1.0f, 0.8f);

        tryFeedFromChest(); // 优化
        furniture().setUnsaved();
    }

    public void onPlayerDamaged() {
        if (!animating) return;
        this.boosted = true;
    }

    public boolean isAnimating()   { return animating; }
    public float getCurrentAngle() { return currentAngle; }

    public void refreshRendering() { this.element.refreshPackets(); }

    @Override
    public void gatherElements(Consumer<FurnitureElement> consumer) {
        consumer.accept(this.element);
    }

    @Override
    public InteractionResult useOnFurniture(FurnitureHitBox hitBox, InteractEntityContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        // 潜行+右键 手持剪刀则停止生物拉磨；空手则开始/停止玩家推磨
        if (player.isSneaking()) {
            Item sneakHand = player.getItemInHand(net.momirealms.craftengine.core.entity.player.InteractionHand.MAIN_HAND);
            boolean hasShears = !sneakHand.isEmpty()
                    && ItemStackUtils.getBukkitStack(sneakHand).getType() == Material.SHEARS;
            if (hasShears) {
                if (isAnimating() && pullingAnimal != null) stopSpinning(player);
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            if (isAnimating()) {
                if (pullingPlayer != null) stopSpinning();
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

        Item itemInHand = context.getItem();
        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) player.platformPlayer();

        if (!itemInHand.isEmpty()) {
            ItemStack stack = ItemStackUtils.getBukkitStack(itemInHand);
            Material mat = stack.getType();

            if (mat == Material.LEAD) {
                if (isAnimating()) return InteractionResult.SUCCESS_AND_CANCEL;

                org.bukkit.World world = getBukkitWorld();
                org.bukkit.Location furnitureLoc = new org.bukkit.Location(world,
                        furniture().position().x, furniture().position().y, furniture().position().z);

                org.bukkit.entity.LivingEntity target = null;
                for (org.bukkit.entity.Entity nearby : world.getNearbyEntities(furnitureLoc, 20, 20, 20)) {
                    if (!(nearby instanceof org.bukkit.entity.Cow)
                            && !(nearby instanceof org.bukkit.entity.Donkey)
                            && !(nearby instanceof org.bukkit.entity.Mule)) continue;
                    org.bukkit.entity.LivingEntity living = (org.bukkit.entity.LivingEntity) nearby;
                    if (!living.isLeashed()) continue;
                    try {
                        if (living.getLeashHolder().equals(bukkitPlayer)) {
                            target = living;
                            break;
                        }
                    } catch (Exception ignored) {}
                }

                if (target == null) return InteractionResult.PASS;

                org.bukkit.Location dropLoc = target.getLocation().clone();
                target.setLeashHolder(null);

                org.bukkit.Bukkit.getScheduler().runTask(
                        top.nicoppa.nicoPengRen.NicoPengRen.getPlugin(top.nicoppa.nicoPengRen.NicoPengRen.class),
                        () -> {
                            for (org.bukkit.entity.Entity e : dropLoc.getWorld().getNearbyEntities(dropLoc, 1, 1, 1)) {
                                if (e instanceof org.bukkit.entity.Item dropped
                                        && dropped.getItemStack().getType() == Material.LEAD) {
                                    dropped.remove();
                                    break;
                                }
                            }
                        }
                );

                spinWithAnimal(target, bukkitPlayer, true);
                player.sendActionBar(Component.text(behavior.msgStopAnimalHint));
                return InteractionResult.SUCCESS_AND_CANCEL;
            }

            if (mat == Material.COW_SPAWN_EGG || mat == Material.DONKEY_SPAWN_EGG) {
                if (isAnimating()) return InteractionResult.SUCCESS_AND_CANCEL;

                org.bukkit.World world = getBukkitWorld();
                float furnitureRad = (float) Math.toRadians(-furniture().position().yRot());
                float startRad     = (float) Math.toRadians(currentAngle);
                float radius = 2.5f;

                Vector3f spawnOffset = new Vector3f(-radius, 0f, -0.5f);
                spawnOffset.rotateY(furnitureRad + startRad);

                org.bukkit.Location spawnLoc = new org.bukkit.Location(world,
                        furniture().position().x + spawnOffset.x,
                        furniture().position().y,
                        furniture().position().z + spawnOffset.z);

                Class<? extends org.bukkit.entity.LivingEntity> animalClass =
                        mat == Material.COW_SPAWN_EGG
                                ? org.bukkit.entity.Cow.class
                                : org.bukkit.entity.Donkey.class;

                org.bukkit.entity.LivingEntity animal = world.spawn(spawnLoc, animalClass);
                spinWithAnimal(animal, null, false);
                player.sendActionBar(Component.text(behavior.msgStopAnimalHint));

                if (bukkitPlayer.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    stack.setAmount(stack.getAmount() - 1);
                }
                return InteractionResult.SUCCESS_AND_CANCEL;
            }

            if (canGrind(itemInHand)) {
                int held = itemInHand.count();
                int placed = 0;
                while (placed < held && tryAddGrind(itemInHand)) placed++;
                if (placed > 0) {
                    if (bukkitPlayer.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                        stack.setAmount(stack.getAmount() - placed);
                    }
                    player.swingHand(net.momirealms.craftengine.core.entity.player.InteractionHand.MAIN_HAND);
                }
                return InteractionResult.SUCCESS_AND_CANCEL;
            }

            return InteractionResult.PASS;
        }

        if (takeGrind(player, net.momirealms.craftengine.core.entity.player.InteractionHand.MAIN_HAND)) {
            player.swingHand(net.momirealms.craftengine.core.entity.player.InteractionHand.MAIN_HAND);
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
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                    top.nicoppa.nicoPengRen.NicoPengRen.getPlugin(top.nicoppa.nicoPengRen.NicoPengRen.class),
                    this::restoreAnimal,
                    1L
            );
        }
    }

    private void restoreAnimal() {
        if (pendingAnimalUUID == null) return;

        org.bukkit.entity.Entity entity = org.bukkit.Bukkit.getEntity(pendingAnimalUUID);
        this.pendingAnimalUUID = null;

        if (entity instanceof org.bukkit.entity.LivingEntity living && living.isValid()) {
            this.pullingAnimal = living;
            this.animalWasAI = this.savedAnimalWasAI;
            living.setAI(false);
            living.setGravity(false);
            ACTIVE_ANIMAL_PULLERS.put(living.getUniqueId(), this);

            this.currentAngle = this.orbitAngle % 360f;
            this.element.updateRotation(this.currentAngle, 0);

            float furnitureRad = (float) Math.toRadians(-furniture().position().yRot());
            float animRad      = (float) Math.toRadians(orbitAngle);
            float radius       = 2.5f;
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
        tag.putBoolean("animating",   this.animating);
        tag.putFloat("orbit_angle",   this.orbitAngle);
        tag.putFloat("current_angle", this.currentAngle);
        tag.putBoolean("boosted",     this.boosted);
        tag.putInt("raw_tick", this.rawTick);

        if (pullingAnimal != null && pullingAnimal.isValid()) {
            tag.putString("animal_uuid",    pullingAnimal.getUniqueId().toString());
            tag.putString("animal_type",    pullingAnimal instanceof org.bukkit.entity.Cow ? "cow"
                    : (pullingAnimal instanceof org.bukkit.entity.Mule ? "mule" : "donkey"));
            tag.putBoolean("animal_was_ai", this.animalWasAI);
        }

        net.momirealms.craftengine.libraries.nbt.ListTag grindTag = new net.momirealms.craftengine.libraries.nbt.ListTag();
        for (int i = 0; i < GRIND_SLOTS; i++) {
            if (grindItems[i].isEmpty()) continue;
            CompoundTag e = new CompoundTag();
            e.putInt("slot", i);
            e.put("item", ItemStackUtils.saveMinecraftItemStackAsTag(grindItems[i].minecraftItem()));
            e.putInt("progress", grindProgress[i]);
            e.putInt("time", grindTime[i]);
            grindTag.add(e);
        }
        tag.put("grind_items", grindTag);
        tag.putInt("grind_data_version", net.momirealms.craftengine.core.util.VersionHelper.WORLD_VERSION);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        this.animating    = tag.getBoolean("animating", false);
        this.orbitAngle   = tag.getFloat("orbit_angle", 0f);
        this.currentAngle = tag.getFloat("current_angle", 0f);
        this.boosted      = tag.getBoolean("boosted", false);
        this.rawTick = tag.getInt("raw_tick", 0);

        if (this.animating && tag.get("animal_uuid") != null) {
            this.pendingAnimalUUID = UUID.fromString(tag.getString("animal_uuid"));
            this.savedAnimalWasAI  = tag.getBoolean("animal_was_ai", true);
        }

        for (int i = 0; i < GRIND_SLOTS; i++) { grindItems[i] = Item.empty(); grindProgress[i] = 0; grindTime[i] = 0; }
        int gdv = tag.getInt("grind_data_version", net.momirealms.craftengine.core.plugin.config.Config.itemDataFixerUpperFallbackVersion());
        net.momirealms.craftengine.libraries.nbt.ListTag grindTag = tag.getList("grind_items");
        if (grindTag != null) {
            for (net.momirealms.craftengine.libraries.nbt.Tag t : grindTag) {
                if (!(t instanceof CompoundTag e)) continue;
                int slot = e.getInt("slot", -1);
                if (slot < 0 || slot >= GRIND_SLOTS) continue;
                Object nms = ItemStackUtils.parseMinecraftItem(e.getCompound("item"), gdv);
                if (nms != null) {
                    grindItems[slot] = ItemStackUtils.wrap(nms);
                    grindProgress[slot] = e.getInt("progress", 0);
                    grindTime[slot] = e.getInt("time", behavior.playerGrindTime);
                }
            }
        }
    }

    public WorldPosition getPosition() { return furniture().position(); }
}
