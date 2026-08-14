package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.ScarecrowBehavior;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.ChunkIndex;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.util.FoliaUtil;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.util.LightBlocks;
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
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.core.world.context.InteractEntityContext;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Parrot;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.Consumer;

// 稻草人
public final class ScarecrowController extends FurnitureController {
    private static final String DATA_KEY = "kaleidoscopecookery:scarecrow";
    private static final String K_ITEMS = "items";

    private static final ChunkIndex<Protection> INDEX = new ChunkIndex<>();

    private final Item[] items = new Item[ScarecrowElement.SLOTS];
    private final ScarecrowElement element;
    private final ScarecrowBehavior behavior;
    // 每个槽一个交互箱 命中哪个箱就是操作哪个槽 不用拿命中高度反推
    private final FurnitureHitBox[] slotHitboxes = new FurnitureHitBox[ScarecrowElement.SLOTS];

    private int tickCounter;
    private UUID perchUuid;
    private volatile long lastInteractNanos;
    private Protection indexedProtection;
    // 点亮的那一格 家具被旋转过后按现位置反算会清错格 所以记下点灯时那个坐标
    private BlockPos litPos;

    public ScarecrowController(Furniture furniture, ScarecrowBehavior behavior) {
        super(furniture);
        this.behavior = behavior;
        this.element = new ScarecrowElement(this, behavior);
        Arrays.fill(this.items, Item.empty());
    }

    public Item item(int slot) {
        return this.items[slot];
    }


    public static boolean protects(World world, int x, int y, int z) {
        return INDEX.anyMatch(world, x, z, protection -> protection.covers(x, y, z));
    }

    // 关服只做纯内存清理 region 与实体调度器不会自己清
    public static void clearIndex() {
        INDEX.clear();
    }

    // 副手挂着灯笼就在灯笼那一格放一个真的 light 方块 取下或移除时熄掉
    private void refreshLight() {
        boolean lit = ItemKeys.isLantern(this.items[ScarecrowElement.SLOT_OFF_HAND].vanillaId());
        BlockPos target = lit ? lightPos() : null;
        if (this.litPos != null && !this.litPos.equals(target)) {
            LightBlocks.clear(furniture().world(), this.litPos);
            this.litPos = null;
        }
        if (target != null && LightBlocks.set(furniture().world(), target, this.behavior.lanternLightLevel)) {
            this.litPos = target;
        }
    }

    private BlockPos lightPos() {
        return BlockPos.fromVec3d(Furniture.getRelativePosition(
                furniture().position(), this.behavior.slots[ScarecrowElement.SLOT_OFF_HAND].position()));
    }

    private void clearLight() {
        if (this.litPos != null) {
            LightBlocks.clear(furniture().world(), this.litPos);
            this.litPos = null;
        }
    }

    private void addToIndex() {
        removeFromIndex();
        WorldPosition position = furniture().position();
        World world = (World) position.world().platformWorld();
        this.indexedProtection = new Protection(
                position.x, position.y, position.z, this.behavior.protectionRadius);
        INDEX.register(this.indexedProtection, world,
                (int) Math.floor(position.x), (int) Math.floor(position.z), this.behavior.protectionRadius);
    }

    private void removeFromIndex() {
        if (this.indexedProtection != null) {
            INDEX.unregister(this.indexedProtection);
            this.indexedProtection = null;
        }
    }


    @Override
    public InteractionResult useOnFurniture(FurnitureHitBox hitBox, InteractEntityContext context) {
        Player player = context.getPlayer();
        if (context.getHand() != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!InteractGuard.canInteract(player, furniture().position())) {
            return InteractionResult.PASS;
        }
        int slot = slotOf(hitBox);
        if (slot < 0) {
            return InteractionResult.PASS;
        }
        long now = System.nanoTime();
        if (now - this.lastInteractNanos < this.behavior.interactCooldownNanos) {
            return InteractionResult.PASS;
        }
        Item inHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        InteractionResult result = slot == ScarecrowElement.SLOT_HEAD
                ? useOnHead(player, inHand)
                : useOnHands(player, inHand, slot);
        if (result != InteractionResult.PASS) {
            this.lastInteractNanos = now;
        }
        return result;
    }

    private int slotOf(FurnitureHitBox hitBox) {
        for (int slot = 0; slot < this.slotHitboxes.length; slot++) {
            if (this.slotHitboxes[slot] == hitBox) {
                return slot;
            }
        }
        return -1;
    }

    // 身体箱走配置里的 variants.hitboxes 只用来挨打 三个槽箱由行为自己造
    @Override
    public void gatherHitboxes(Consumer<FurnitureHitBox> consumer) {
        for (int slot = 0; slot < ScarecrowElement.SLOTS; slot++) {
            FurnitureHitBox hitBox = this.behavior.slots[slot].hitbox().create(furniture());
            this.slotHitboxes[slot] = hitBox;
            consumer.accept(hitBox);
        }
    }

    private InteractionResult useOnHead(Player player, Item inHand) {
        if (ItemUtils.isEmpty(inHand)) {
            return takeSlot(player, ScarecrowElement.SLOT_HEAD);
        }
        if (!isHeadItem(inHand)) {
            return InteractionResult.PASS;
        }
        return putSlot(player, ScarecrowElement.SLOT_HEAD, inHand);
    }

    // 点哪只手取哪只手 但放置按分类走 灯笼只进副手 工具只进主手
    private InteractionResult useOnHands(Player player, Item inHand, int clickedSlot) {
        if (ItemUtils.isEmpty(inHand)) {
            return takeSlot(player, clickedSlot);
        }
        if (ItemKeys.isLantern(inHand.vanillaId())) {
            return putSlot(player, ScarecrowElement.SLOT_OFF_HAND, inHand);
        }
        if (isTool(inHand)) {
            return putSlot(player, ScarecrowElement.SLOT_MAIN_HAND, inHand);
        }
        return InteractionResult.PASS;
    }

    private boolean isHeadItem(Item item) {
        Material material = material(item);
        if (material == null) {
            return false;
        }
        String name = material.name();
        return material.isItem() && (name.endsWith("_HEAD") || name.endsWith("_SKULL"));
    }

    // 创造模式只在空槽放且不扣物品 手里超过一个时只能放进空槽 正好一个时才交换
    private InteractionResult putSlot(Player player, int slot, Item inHand) {
        Item previous = this.items[slot];
        if (player.canInstabuild()) {
            if (!ItemUtils.isEmpty(previous)) {
                return InteractionResult.PASS;
            }
            this.items[slot] = inHand.copyWithCount(1);
        } else if (inHand.count() > 1) {
            if (!ItemUtils.isEmpty(previous)) {
                return InteractionResult.PASS;
            }
            this.items[slot] = inHand.copyWithCount(1);
            InventoryUtils.shrinkHeld(player, inHand, 1);
        } else {
            this.items[slot] = inHand.copyWithCount(1);
            InventoryUtils.shrinkHeld(player, inHand, 1);
            if (!ItemUtils.isEmpty(previous)) {
                InventoryUtils.giveOrHold(player, InteractionHand.MAIN_HAND, previous);
            }
        }
        refresh();
        player.swingHand(InteractionHand.MAIN_HAND);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    private InteractionResult takeSlot(Player player, int slot) {
        if (ItemUtils.isEmpty(this.items[slot])) {
            return InteractionResult.PASS;
        }
        InventoryUtils.giveOrHold(player, InteractionHand.MAIN_HAND, this.items[slot]);
        this.items[slot] = Item.empty();
        refresh();
        player.swingHand(InteractionHand.MAIN_HAND);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 要判物品自己的最大耐久
    // Material#getMaxDurability 是原版基材的耐久 自定义工具挂在无耐久基材上时会漏判
    private boolean isTool(Item item) {
        return !isHeadItem(item) && item.maxDamage() > 0;
    }

    private Material material(Item item) {
        ItemStack stack = ItemStackUtils.getBukkitStack(item.minecraftItem());
        return stack == null ? null : stack.getType();
    }

    // trackedBy 是列表快照 getTrackedBy 返回的是活的集合 别的 region 线程正在改它
    private void refresh() {
        this.element.rebuild();
        furniture().trackedBy().forEach(this.element::update);
        refreshLight();
        // 少了这句物品永远不落盘 重开服手上的东西就没了 也取不出来
        furniture().setUnsaved();
    }


    @Override
    @SuppressWarnings("unchecked")
    public <T extends FurnitureController> FurnitureTicker<T> createFurnitureTicker() {
        if (!this.behavior.parrotPerch) {
            return null;
        }
        return (FurnitureTicker<T>) (FurnitureTicker<ScarecrowController>) (furniture, controller) -> controller.tickPerch();
    }

    private void tickPerch() {
        if (++this.tickCounter < this.behavior.parrotScanInterval) {
            return;
        }
        this.tickCounter = 0;
        WorldPosition position = furniture().position();
        World world = (World) position.world().platformWorld();
        // 锚点是无敌不落盘的隐形载具 鹦鹉死了它自己不会消失
        // 只判锚点还在就 return 会让这个稻草人从此再也停不了第二只鹦鹉 必须连乘客一起判
        if (this.perchUuid != null) {
            Entity anchor = world.getEntity(this.perchUuid);
            if (anchor != null && !anchor.isDead() && !anchor.getPassengers().isEmpty()) {
                return;
            }
            releasePerch();
        }
        Parrot parrot = findParrot(world, position);
        if (parrot == null) {
            return;
        }
        // 家具 ticker 跑在本家具的实体调度器上 鹦鹉又限定在同一区块 两者必然同 region
        // 再往实体调度器投递会推迟一 tick 期间鹦鹉可能飞进别的 region perchUuid 就成了跨线程写
        perch(world, position, parrot);
    }

    // 只扫稻草人自己所在的区块 区块必然完整属于一个 region 不会撞 getEntities 的跨 region 检查
    private Parrot findParrot(World world, WorldPosition position) {
        int chunkX = (int) Math.floor(position.x) >> 4;
        int chunkZ = (int) Math.floor(position.z) >> 4;
        if (!Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ)) {
            return null;
        }
        double range = this.behavior.parrotPickupRange;
        for (Entity entity : world.getChunkAt(chunkX, chunkZ).getEntities()) {
            if (entity.getType() != EntityType.PARROT || !(entity instanceof Parrot parrot)) {
                continue;
            }
            if (parrot.isDead() || parrot.isSitting() || parrot.isInsideVehicle()) {
                continue;
            }
            Location location = parrot.getLocation();
            double dx = location.getX() - position.x;
            double dy = location.getY() - position.y;
            double dz = location.getZ() - position.z;
            if (dx * dx + dy * dy + dz * dz <= range * range) {
                return parrot;
            }
        }
        return null;
    }

    // 不落盘的隐形载具 崩服重启后自然消失 鹦鹉掉下来即可 不需要崩溃恢复快照
    private void perch(World world, WorldPosition position, Parrot parrot) {
        float yaw = (float) Math.toRadians(-position.yRot());
        Location seat = new Location(world,
                position.x + Math.cos(yaw) * this.behavior.shoulderOffset.x,
                position.y + this.behavior.shoulderOffset.y,
                position.z - Math.sin(yaw) * this.behavior.shoulderOffset.x);
        // 肩膀位可能落进邻区块 不归本 region 时生成会抛 下次扫描再试
        if (!Bukkit.isOwnedByCurrentRegion(seat)) {
            return;
        }
        ArmorStand anchor = world.spawn(seat, ArmorStand.class, stand -> {
            stand.setMarker(true);
            stand.setInvisible(true);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setGravity(false);
            stand.setPersistent(false);
        });
        if (!anchor.addPassenger(parrot)) {
            anchor.remove();
            return;
        }
        this.perchUuid = anchor.getUniqueId();
    }

    private void releasePerch() {
        if (this.perchUuid == null) {
            return;
        }
        World world = (World) furniture().position().world().platformWorld();
        Entity anchor = world.getEntity(this.perchUuid);
        this.perchUuid = null;
        if (anchor == null) {
            return;
        }
        FoliaUtil.runEntity(anchor, () -> {
            anchor.eject();
            anchor.remove();
        });
    }


    @Override
    public void gatherElements(Consumer<FurnitureElement> consumer) {
        consumer.accept(this.element);
    }

    @Override
    public void onLoad() {
        addToIndex();
        this.element.rebuild();
        refreshLight();
    }

    @Override
    public void onPlace(Player player) {
        this.element.rebuild();
        refreshLight();
    }

    @Override
    public void onUnload(boolean isStopping) {
        removeFromIndex();
        // 这里跑在区块系统的实体状态变更回调里 移除实体会被 Paper 拒绝并刷一整页栈
        // 锚点是 setPersistent(false) 的 跟着区块一起消失 忘掉 uuid 就行
        this.perchUuid = null;
        // 区块卸载不动世界方块 光源跟着区块一起存盘 下次 onLoad 再认回来
        this.litPos = null;
    }

    @Override
    public InteractionResult onPlayerHit(Player player, FurnitureHitBox hitBox) {
        releasePerch();
        return InteractionResult.PASS;
    }

    @Override
    public void preRemove(Player player) {
        removeFromIndex();
        releasePerch();
        clearLight();
        WorldPosition position = furniture().position();
        // 从身上而不是脚下掉 免得物品卡进底座模型里
        WorldPosition dropAt = new WorldPosition(position.world(), position.x, position.y + 1, position.z);
        DropUtils.dropAll(position.world(), dropAt, Arrays.asList(this.items));
        Arrays.fill(this.items, Item.empty());
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        CompoundTag data = BlockEntityNbt.newData();
        data.put(K_ITEMS, BlockEntityNbt.saveSlots(this.items));
        tag.put(DATA_KEY, data);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        Arrays.fill(this.items, Item.empty());
        CompoundTag data = tag.getCompound(DATA_KEY);
        if (data == null) {
            return;
        }
        BlockEntityNbt.loadSlots(data.getList(K_ITEMS), BlockEntityNbt.dataVersion(data), this.items);
        this.element.rebuild();
    }

    private static final class Protection {
        private final double x;
        private final double y;
        private final double z;
        private final double radius;

        private Protection(double x, double y, double z, double radius) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
        }

        private boolean covers(int blockX, int blockY, int blockZ) {
            double dx = this.x - (blockX + 0.5);
            double dy = this.y - (blockY + 0.5);
            double dz = this.z - (blockZ + 0.5);
            return dx * dx + dy * dy + dz * dz <= this.radius * this.radius;
        }
    }
}
