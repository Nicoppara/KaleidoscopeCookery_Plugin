package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.kaleidoscope.cookery.util.FoliaUtil;
import net.kaleidoscope.cookery.block.entity.render.Particles;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.nms.NmsBridgeProvider;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.bukkit.plugin.network.BukkitNetworkManager;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
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
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.core.world.context.InteractEntityContext;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.ListTag;
import net.momirealms.craftengine.libraries.nbt.Tag;
import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class TrashCanController extends FurnitureController {
    private static final int SLOTS = 3;
    private static final String SOUND = "kaleidoscopecookery:trashcan";
    private static final String DATA_KEY = "kaleidoscopecookery:trashcan";

    private static final NamespacedKey RESTORE_GAMEMODE_KEY =
            new NamespacedKey(KaleidoscopeCookeryPlugin.instance(), "trashcan_restore_gamemode");
    private static final NamespacedKey RESTORE_HELMET_KEY =
            new NamespacedKey(KaleidoscopeCookeryPlugin.instance(), "trashcan_restore_helmet");

    private static final Map<BlockKey, TrashCanController> BY_BLOCK = new ConcurrentHashMap<>();

    private record BlockKey(UUID world, int x, int y, int z) {}
    private static final Map<UUID, TrashCanController> BY_OCCUPANT = new ConcurrentHashMap<>();

    private static final String TELEPORT_BLOCKER = "kaleidoscopecookery_trashcan_spectate";

    private static final double CAMERA_EYE_Y = 0.9;
    private static final float VIEW_PITCH = 0f;

    private static final float[][] BODY_ENTER = {
            {0f, 0f},
            {2f, 5f},
            {4f, -5f},
            {6f, 5f},
            {8f, 0f},
    };

    private static final float[][] LID_ENTER = {
            {0f,  0f,   0f},
            {2f,  10f,  5f},
            {5f,  -5f,  0f},
            {7f,  2.5f, 0f},
            {10f, 0f,   0f},
    };

    private static final float LID_IDLE_ROT = -10f;
    private static final float LID_IDLE_Y = 0.5f;
    private static final float EYE_PEEK_Y = 4f;
    private static final int IDLE_PERIOD = 61;

    private static final float[][] EYE_BOB = {
            {0f, 0f, 0f},
            {2f, 0f, -2f},
            {6f, 0f, 0f},
    };

    private static final float[][] EYE_SWAY = {
            {0f,  0f,  0f},
            {8f,  -1f, 0f},
            {28f, 1f,  0f},
            {40f, 0f,  0f},
    };

    private static final float[][] OPEN_FRAMES = {
            {0f,  0f,     0f},
            {2f,  -12.5f, 2f},
            {4f,  -12.5f, 0f},
            {7f,  7.5f,   0f},
            {8f,  -7.5f,  0f},
            {10f, 2.5f,   0f},
            {11f, -2.5f,  0f},
            {12f, 0f,     0f},
    };

    private static final float[][] WITHDRAW_FRAMES = {
            {0f,  0f,    0f},
            {2f,  -7.5f, 1f},
            {5f,  5f,    0f},
            {8f,  -2.5f, 0f},
            {10f, 0f,    0f},
    };

    private final TrashCanElement element;
    private final Item[] storage = new Item[SLOTS];
    private final int animChunkRadius;
    private final int srcChunkX;
    private final int srcChunkZ;
    private boolean animating;
    // 是否有玩家进入桶内 占用时禁止投放取出 桶内掉落物隐藏
    private boolean occupied;
    private UUID occupantId;
    // 占用代次 每次进入自增 待机调度任务带着进入时的代次 代次变了就失效 防止旧任务在快速换人后乱发
    private int occupancyId;
    // 进入前的状态 退出时还原
    private GameMode previousGameMode;
    private ItemStack previousHelmet;
    // 固定视角用的相机实体 玩家旁观它 视角与位置都被锁住
    private Entity camera;
    private final Key helmetItem;

    public TrashCanController(Furniture furniture, int animChunkRadius, Key helmetItem) {
        super(furniture);
        Arrays.fill(this.storage, Item.empty());
        WorldPosition pos = furniture.position();
        this.animChunkRadius = animChunkRadius;
        this.helmetItem = helmetItem;
        this.srcChunkX = ((int) Math.floor(pos.x)) >> 4;
        this.srcChunkZ = ((int) Math.floor(pos.z)) >> 4;
        this.element = new TrashCanElement(this, pos);
    }

    public Item[] storage() {
        return storage;
    }

    public boolean isAnimating() {
        return animating;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public float facingDegrees() {
        return furniture().position().yRot();
    }

    @Override
    public InteractionResult useOnFurniture(FurnitureHitBox hitBox, InteractEntityContext context) {
        Player player = context.getPlayer();
        if (!InteractGuard.canInteract(player, furniture().position())) {
            return InteractionResult.PASS;
        }
        if (occupied || animating) {
            return InteractionResult.PASS;
        }

        Item itemInHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (itemInHand.isEmpty()) {
            if (!withdrawItem(player, InteractionHand.MAIN_HAND)) {
                return InteractionResult.PASS;
            }
            player.swingHand(InteractionHand.MAIN_HAND);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        putItem(itemInHand, player);
        player.swingHand(InteractionHand.MAIN_HAND);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 满了挤掉最旧的 新物品放末位
    public void putItem(Item held, Player player) {
        int before = held.count();
        Item remaining = held.copy();
        for (int i = 0; i < SLOTS && !remaining.isEmpty(); i++) {
            if (!storage[i].isEmpty() && InventoryUtils.isSameType(storage[i], remaining)) {
                int room = storage[i].maxStackSize() - storage[i].count();
                if (room > 0) {
                    int move = Math.min(room, remaining.count());
                    storage[i] = storage[i].copyWithCount(storage[i].count() + move);
                    remaining = remaining.copyWithCount(remaining.count() - move);
                }
            }
        }
        for (int i = 0; i < SLOTS && !remaining.isEmpty(); i++) {
            if (storage[i].isEmpty()) {
                int move = Math.min(remaining.maxStackSize(), remaining.count());
                storage[i] = remaining.copyWithCount(move);
                remaining = remaining.copyWithCount(remaining.count() - move);
            }
        }
        int stored;
        if (remaining.count() == before) {
            storage[0] = storage[1];
            storage[1] = storage[2];
            int move = Math.min(held.maxStackSize(), held.count());
            storage[2] = held.copyWithCount(move);
            stored = move;
        } else {
            stored = before - remaining.count();
        }

        InventoryUtils.shrinkHeld(player, held, stored);
        element.refreshItemsAndBroadcast();
        playEffects(1.0f);
        playAnimation(OPEN_FRAMES);
        furniture().setUnsaved();
    }

    public boolean withdrawItem(Player player, InteractionHand hand) {
        for (int i = SLOTS - 1; i >= 0; i--) {
            if (!storage[i].isEmpty()) {
                InventoryUtils.giveOrHold(player, hand, storage[i].copy());
                storage[i] = Item.empty();
                element.refreshItemsAndBroadcast();
                playEffects(0.8f);
                playAnimation(WITHDRAW_FRAMES);
                furniture().setUnsaved();
                return true;
            }
        }
        return false;
    }

    private void playAnimation(float[][] frames) {
        animating = true;
        List<Player> recipients = rangePlayers();
        Location loc = topCenter();
        for (int i = 1; i < frames.length; i++) {
            int delay = (int) frames[i - 1][0];
            int duration = Math.max(1, (int) (frames[i][0] - frames[i - 1][0]));
            float rot = frames[i][1];
            float posY = frames[i][2];
            Runnable send = () -> {
                Object meta = element.openFrameMeta(rot, posY, duration);
                recipients.forEach(p -> p.sendPacket(meta, false));
            };
            if (delay <= 0) {
                send.run();
            } else {
                BukkitCraftEngine.instance().scheduler().platform().runLater(send, delay, loc);
            }
        }
        int totalTicks = (int) frames[frames.length - 1][0];
        BukkitCraftEngine.instance().scheduler().platform().runLater(() -> animating = false, totalTicks, loc);
    }

    private void playEffects(float pitch) {
        Location loc = topCenter();
        Particles.emit(loc.getWorld(), Particle.SMOKE, loc.getX(), loc.getY(), loc.getZ(), 3, 0.2, 0.05, 0.2, 0.01, null);
        loc.getWorld().playSound(loc, SOUND, SoundCategory.BLOCKS, 1.0f, pitch);
    }

    private Location topCenter() {
        WorldPosition p = furniture().position();
        return new Location((World) p.world().platformWorld(), p.x, p.y + 1.1, p.z);
    }

    // 进入桶内 旁观相机锁视角 戴南瓜遮罩 隐藏掉落物
    public void enter(org.bukkit.entity.Player player) {
        if (occupied || animating) {
            return;
        }
        WorldPosition p = furniture().position();
        World world = (World) p.world().platformWorld();
        int bx = (int) Math.floor(p.x);
        int by = (int) Math.floor(p.y);
        int bz = (int) Math.floor(p.z);
        Location camLoc = new Location(world,
                bx + 0.5, by + CAMERA_EYE_Y, bz + 0.5, facingDegrees(), VIEW_PITCH);

        this.previousGameMode = player.getGameMode();
        this.previousHelmet = player.getInventory().getHelmet();
        markRestoreState(player, previousGameMode, previousHelmet);
        this.occupied = true;
        int gen = ++this.occupancyId;
        this.occupantId = player.getUniqueId();
        BY_OCCUPANT.put(occupantId, this);

        // 相机用空 ItemDisplay 无物品即无模型 旁观模式下无剪影无阴影(隐形盔甲架会被旁观者看到半透明剪影) 仍是真实体可被旁观锁定
        ItemDisplay display = world.spawn(camLoc, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setGravity(false);
        });
        this.camera = display;

        player.setGameMode(GameMode.SPECTATOR);
        player.getInventory().setHelmet(helmetStack());
        Entity cam = this.camera;
        addTeleportBlocker(player);
        BukkitCraftEngine.instance().scheduler().platform().runLater(() -> {
            if (occupied && player.isOnline() && cam.isValid()) {
                player.setSpectatorTarget(cam);
            }
        }, null, 1L, player);

        element.setItemsHidden(true);
        clearNearbyHostility(player);
        playEnterAnimation();
        // 进入摆动结束后转入占用待机 盖子微抬 眼睛冒出 并开始微动循环
        int enterEnd = (int) LID_ENTER[LID_ENTER.length - 1][0] + 1;
        BukkitCraftEngine.instance().scheduler().platform().runLater(() -> beginOccupiedIdle(gen), enterEnd, topCenter());
    }

    // 进桶头盔 配置的自定义帽(带 camera_overlay 遮罩) 取不到退回南瓜
    private ItemStack helmetStack() {
        Item item = InventoryUtils.createOrEmpty(helmetItem);
        if (ItemUtils.isEmpty(item)) {
            return new ItemStack(Material.CARVED_PUMPKIN);
        }
        return ItemStackUtils.getBukkitStack(item);
    }

    // 旁观传送(teleport_to_entity)不触发 PlayerTeleportEvent 在玩家管线上挂一道 netty 拦截 直接丢弃该包 进桶挂上 退出摘掉
    private void addTeleportBlocker(org.bukkit.entity.Player player) {
        Channel channel = BukkitNetworkManager.instance().getChannel(player);
        if (channel == null) {
            return;
        }
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(TELEPORT_BLOCKER) != null || pipeline.get("packet_handler") == null) {
            return;
        }
        pipeline.addBefore("packet_handler", TELEPORT_BLOCKER, new SpectateTeleportBlocker());
    }

    private void removeTeleportBlocker(org.bukkit.entity.Player player) {
        Channel channel = BukkitNetworkManager.instance().getChannel(player);
        if (channel == null) {
            return;
        }
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(TELEPORT_BLOCKER) != null) {
            pipeline.remove(TELEPORT_BLOCKER);
        }
    }

    private static final class SpectateTeleportBlocker extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (NmsBridgeProvider.bridge().isSpectateTeleportPacket(msg)) {
                return;
            }
            super.channelRead(ctx, msg);
        }
    }

    // 占用待机 盖子定格微抬 眼睛冒出 然后开始微动循环
    private void beginOccupiedIdle(int gen) {
        if (!occupied || gen != occupancyId) {
            return;
        }
        sendToTracked(element.openFrameMeta(LID_IDLE_ROT, LID_IDLE_Y, 5));
        sendToTracked(element.eyeMeta(0f, EYE_PEEK_Y, 5));
        scheduleIdle(gen);
    }

    // 待机微动循环 每 IDLE_PERIOD 随机播眼睛下沉或左右摆 代次变化或占用结束自动停止
    private void scheduleIdle(int gen) {
        if (!occupied || gen != occupancyId) {
            return;
        }
        if (ThreadLocalRandom.current().nextBoolean()) {
            playEyeTrack(EYE_BOB);
        } else {
            playEyeTrack(EYE_SWAY);
        }
        BukkitCraftEngine.instance().scheduler().platform().runLater(() -> scheduleIdle(gen), IDLE_PERIOD, topCenter());
    }

    // 播放一条眼睛轨道 每个关键帧把下一帧目标发给追踪玩家 Y 叠加在冒出基准上
    private void playEyeTrack(float[][] frames) {
        List<Player> recipients = rangePlayers();
        Location loc = topCenter();
        for (int i = 1; i < frames.length; i++) {
            int delay = (int) frames[i - 1][0];
            int duration = Math.max(1, (int) (frames[i][0] - frames[i - 1][0]));
            float x = frames[i][1];
            float y = frames[i][2];
            Runnable send = () -> {
                Object meta = element.eyeMeta(x, EYE_PEEK_Y + y, duration);
                recipients.forEach(p -> p.sendPacket(meta, false));
            };
            scheduleFrame(send, delay, loc);
        }
    }

    // 动画视距内的追踪玩家 远处玩家反正不渲染动画 不发动画包
    private List<Player> rangePlayers() {
        return TrackedPlayers.snapshotInRange(furniture().getTrackedBy(), srcChunkX, srcChunkZ, animChunkRadius);
    }

    private void sendToTracked(Object meta) {
        rangePlayers().forEach(p -> p.sendPacket(meta, false));
    }

    // 退出垃圾桶 解除相机 还原模式与头盔 传送回桶顶 恢复交互与掉落物显示
    public void exit() {
        exit(false);
    }

    // shutdown=true 为关服路径 只还原玩家关键状态与相机 不做传送和渲染回发 避免关闭阶段的多余/不稳定操作
    private void exit(boolean shutdown) {
        if (FoliaUtil.isFolia() && occupantId != null) {
            org.bukkit.entity.Player player = Bukkit.getPlayer(occupantId);
            if (player != null) {
                FoliaUtil.runEntity(player, () -> exitOwned(shutdown));
                return;
            }
        }
        exitOwned(shutdown);
    }

    private void exitOwned(boolean shutdown) {
        if (!occupied) {
            return;
        }
        this.occupied = false;
        this.occupancyId++;
        UUID id = this.occupantId;
        this.occupantId = null;
        // 先从登记表移除再解除相机 解除会触发停止旁观事件 此时反查为空不会重入
        if (id != null) {
            BY_OCCUPANT.remove(id);
            org.bukkit.entity.Player bp = Bukkit.getPlayer(id);
            if (bp != null) {
                removeTeleportBlocker(bp);
                bp.setSpectatorTarget(null);
                bp.getInventory().setHelmet(previousHelmet);
                if (!shutdown) {
                    WorldPosition p = furniture().position();
                    Location out = new Location((World) p.world().platformWorld(),
                            Math.floor(p.x) + 0.5, Math.floor(p.y) + 1.0, Math.floor(p.z) + 0.5,
                            bp.getLocation().getYaw(), bp.getLocation().getPitch());
                    FoliaUtil.teleport(bp, out);
                }
                if (previousGameMode != null) {
                    bp.setGameMode(previousGameMode);
                }
                clearRestoreState(bp);
            }
        }
        if (camera != null) {
            if (camera.isValid()) {
                camera.remove();
            }
            camera = null;
        }
        if (!shutdown) {
            // 还原盖子与眼睛到静止姿势
            sendToTracked(element.openFrameMeta(0f, 0f, 5));
            sendToTracked(element.eyeMeta(0f, 0f, 5));
            element.setItemsHidden(false);
        }
        this.previousHelmet = null;
        this.previousGameMode = null;
    }

    private static void markRestoreState(org.bukkit.entity.Player player, GameMode gameMode, ItemStack helmet) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(RESTORE_GAMEMODE_KEY, PersistentDataType.STRING, gameMode.name());
        if (helmet != null && !helmet.getType().isAir()) {
            pdc.set(RESTORE_HELMET_KEY, PersistentDataType.BYTE_ARRAY, helmet.serializeAsBytes());
        } else {
            pdc.remove(RESTORE_HELMET_KEY);
        }
    }

    private static void clearRestoreState(org.bukkit.entity.Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.remove(RESTORE_GAMEMODE_KEY);
        pdc.remove(RESTORE_HELMET_KEY);
    }

    // 硬崩溃后重新登录时的兜底 据持久化数据把卡在旁观加南瓜的玩家还原 正常退出已即时还原并清标记 不会进这里
    public static void restoreIfCrashed(org.bukkit.entity.Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String gameModeName = pdc.get(RESTORE_GAMEMODE_KEY, PersistentDataType.STRING);
        if (gameModeName == null) {
            return;
        }
        player.setSpectatorTarget(null);
        try {
            player.setGameMode(GameMode.valueOf(gameModeName));
        } catch (IllegalArgumentException ignored) {
        }
        byte[] helmetBytes = pdc.get(RESTORE_HELMET_KEY, PersistentDataType.BYTE_ARRAY);
        player.getInventory().setHelmet(
                helmetBytes == null ? null : ItemStack.deserializeBytes(helmetBytes));
        clearRestoreState(player);
    }

    // 在桶里死亡后点重生 松开桶的相机/拦截/占用 再据 PDC 还原游戏模式+头盔 让原版重生点与生存模式生效
    public static void handleRespawn(org.bukkit.entity.Player player) {
        TrashCanController c = BY_OCCUPANT.remove(player.getUniqueId());
        boolean marked = player.getPersistentDataContainer().has(RESTORE_GAMEMODE_KEY, PersistentDataType.STRING);
        if (c != null) {
            c.detachForRespawn(player);
        }
        if (c == null && !marked) {
            return;
        }
        // 延后一 tick 待重生完成再还原游戏模式 否则可能被重生逻辑盖掉
        BukkitCraftEngine.instance().scheduler().platform().runLater(
                () -> restoreIfCrashed(player), null, 1L, player);
    }

    // 只松开占用/相机/拦截 不传送不改模式(交给上面延后的 restoreIfCrashed) 先移除登记再解旁观避免重入
    private void detachForRespawn(org.bukkit.entity.Player player) {
        this.occupied = false;
        this.occupancyId++;
        this.occupantId = null;
        removeTeleportBlocker(player);
        player.setSpectatorTarget(null);
        if (camera != null) {
            if (camera.isValid()) {
                camera.remove();
            }
            camera = null;
        }
        sendToTracked(element.openFrameMeta(0f, 0f, 5));
        sendToTracked(element.eyeMeta(0f, 0f, 5));
        element.setItemsHidden(false);
        this.previousHelmet = null;
        this.previousGameMode = null;
    }

    // 服务器关闭时统一把所有进入桶里的玩家放出来 避免卡在旁观模式
    public static void releaseAll() {
        for (TrashCanController c : new ArrayList<>(BY_OCCUPANT.values())) {
            if (FoliaUtil.isFolia()) {
                c.releaseOnFolia();
            } else {
                c.exit(true);
            }
        }
    }

    private void releaseOnFolia() {
        exit(true);
    }

    // 清除附近生物对进入玩家的敌意 仿模组进桶即安全
    private void clearNearbyHostility(org.bukkit.entity.Player player) {
        Location center = player.getLocation();
        World world = center.getWorld();
        if (!FoliaUtil.isFolia()) {
            for (Entity e : world.getNearbyEntities(center, 32, 32, 32)) {
                if (e instanceof Mob mob && mob.getTarget() == player) {
                    mob.setTarget(null);
                }
            }
            return;
        }

        double minX = center.getX() - 32;
        double maxX = center.getX() + 32;
        double minY = center.getY() - 32;
        double maxY = center.getY() + 32;
        double minZ = center.getZ() - 32;
        double maxZ = center.getZ() + 32;
        int minChunkX = ((int) Math.floor(minX)) >> 4;
        int maxChunkX = ((int) Math.floor(maxX)) >> 4;
        int minChunkZ = ((int) Math.floor(minZ)) >> 4;
        int maxChunkZ = ((int) Math.floor(maxZ)) >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                int chunkX = cx;
                int chunkZ = cz;
                BukkitCraftEngine.instance().scheduler().platform().run(() -> {
                    if (!world.isChunkLoaded(chunkX, chunkZ)) {
                        return;
                    }
                    for (Entity entity : world.getChunkAt(chunkX, chunkZ).getEntities()) {
                        if (!(entity instanceof Mob mob)) {
                            continue;
                        }
                        FoliaUtil.runEntity(mob, () -> {
                            Location loc = mob.getLocation();
                            if (loc.getX() >= minX && loc.getX() <= maxX
                                    && loc.getY() >= minY && loc.getY() <= maxY
                                    && loc.getZ() >= minZ && loc.getZ() <= maxZ
                                    && mob.getTarget() == player) {
                                mob.setTarget(null);
                            }
                        });
                    }
                }, world, chunkX, chunkZ);
            }
        }
    }

    // 进入动画
    private void playEnterAnimation() {
        animating = true;
        List<Player> recipients = rangePlayers();
        Location loc = topCenter();
        for (int i = 1; i < BODY_ENTER.length; i++) {
            int delay = (int) BODY_ENTER[i - 1][0];
            int duration = Math.max(1, (int) (BODY_ENTER[i][0] - BODY_ENTER[i - 1][0]));
            float rotY = BODY_ENTER[i][1];
            Runnable send = () -> {
                Object meta = element.bodyEnterFrameMeta(rotY, duration);
                recipients.forEach(p -> p.sendPacket(meta, false));
            };
            scheduleFrame(send, delay, loc);
        }
        for (int i = 1; i < LID_ENTER.length; i++) {
            int delay = (int) LID_ENTER[i - 1][0];
            int duration = Math.max(1, (int) (LID_ENTER[i][0] - LID_ENTER[i - 1][0]));
            float rotZ = LID_ENTER[i][1];
            float posY = LID_ENTER[i][2];
            Runnable send = () -> {
                Object meta = element.lidEnterFrameMeta(rotZ, posY, duration);
                recipients.forEach(p -> p.sendPacket(meta, false));
            };
            scheduleFrame(send, delay, loc);
        }
        int total = (int) LID_ENTER[LID_ENTER.length - 1][0];
        BukkitCraftEngine.instance().scheduler().platform().runLater(() -> animating = false, total, loc);
    }

    private void scheduleFrame(Runnable send, int delay, Location loc) {
        if (delay <= 0) {
            send.run();
        } else {
            BukkitCraftEngine.instance().scheduler().platform().runLater(send, delay, loc);
        }
    }

    // 按玩家脚下方块查询唯一垃圾桶 脚下方块或其下一格 检测到多个则返回 null 不进入
    public static TrashCanController findUnder(org.bukkit.entity.Player player) {
        UUID world = player.getWorld().getUID();
        Location loc = player.getLocation();
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        TrashCanController here = BY_BLOCK.get(new BlockKey(world, bx, by, bz));
        TrashCanController below = BY_BLOCK.get(new BlockKey(world, bx, by - 1, bz));
        if (here != null && below != null && here != below) {
            return null;
        }
        return here != null ? here : below;
    }

    public static TrashCanController byOccupant(UUID playerId) {
        return BY_OCCUPANT.get(playerId);
    }

    private BlockKey myBlockKey() {
        WorldPosition p = furniture().position();
        UUID world = ((World) p.world().platformWorld()).getUID();
        return new BlockKey(world, (int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z));
    }

    @Override
    public <T extends FurnitureController> FurnitureTicker<T> createFurnitureTicker() {
        return null;
    }

    @Override
    public void gatherElements(Consumer<FurnitureElement> consumer) {
        consumer.accept(element);
    }

    private void unregisterFromBlock() {
        BY_BLOCK.remove(myBlockKey(), this);
    }

    @Override
    public void onLoad() {
        BY_BLOCK.put(myBlockKey(), this);
        element.rebuildPackets();
    }

    @Override
    public void onUnload(boolean isStopping) {
        unregisterFromBlock();
        if (occupied && !isStopping) {
            exit();
        }
    }

    @Override
    public void preRemove(Player player) {
        unregisterFromBlock();
        if (occupied) {
            exit();
        }
        World world = (World) furniture().position().world().platformWorld();
        WorldPosition p = furniture().position();
        Location dropLoc = new Location(world, p.x, p.y + 0.5, p.z);
        for (Item item : storage) {
            if (!item.isEmpty()) {
                world.dropItemNaturally(dropLoc, ItemStackUtils.getBukkitStack(item.minecraftItem()));
            }
        }
        Arrays.fill(storage, Item.empty());
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        CompoundTag data = new CompoundTag();
        data.putInt("data_version", VersionHelper.WORLD_VERSION);
        ListTag list = new ListTag();
        for (int i = 0; i < SLOTS; i++) {
            if (storage[i].isEmpty()) {
                continue;
            }
            CompoundTag e = new CompoundTag();
            e.putInt("slot", i);
            e.put("item", ItemStackUtils.saveMinecraftItemStackAsTag(storage[i].minecraftItem()));
            list.add(e);
        }
        data.put("items", list);
        tag.put(DATA_KEY, data);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        Arrays.fill(storage, Item.empty());
        CompoundTag data = tag.getCompound(DATA_KEY);
        if (data != null) {
            int dataVersion = data.getInt("data_version", Config.itemDataFixerUpperFallbackVersion());
            ListTag list = data.getList("items");
            if (list != null) {
                for (Tag t : list) {
                    if (!(t instanceof CompoundTag e)) {
                        continue;
                    }
                    int slot = e.getInt("slot", -1);
                    if (slot < 0 || slot >= SLOTS) {
                        continue;
                    }
                    Object nms = ItemStackUtils.parseMinecraftItem(e.getCompound("item"), dataVersion);
                    if (nms != null) {
                        storage[slot] = ItemStackUtils.wrap(nms);
                    }
                }
            }
        }
        element.refreshItems();
    }
}
