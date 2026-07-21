package net.kaleidoscope.cookery.entity.cat;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import net.kaleidoscope.cookery.util.FoliaUtil;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 猫趴果篮 AI 扫描附近果篮 寻路过去 趴下
// 一个果篮同一时间只允许一只猫认领 驯服猫可挤走野猫 驯服猫占着时其它猫不来
public final class FruitBasketCatGoal implements Goal<Cat> {

    private static final Key FRUIT_BASKET = Key.of("kaleidoscopecookery:fruit_basket");

    // 每隔多少 tick 扫描一次
    private static final int SCAN_INTERVAL = 40;
    // 趴下后多少 tick 自检一次是否还在果篮上
    private static final int SAFETY_INTERVAL = 10;
    // 趴上去给的生命恢复时长 60 秒 每 60 秒刷一次
    private static final int REGEN_DURATION = 1200;
    private static final int REGEN_AMPLIFIER = 0;
    // 水平搜索半径
    private static final int H_RADIUS = 6;
    // 垂直搜索半径 猫与果篮通常同层附近
    private static final int V_RADIUS = 1;
    // 到达判定 水平距离平方
    private static final double ARRIVE_DIST_SQ = 1.8 * 1.8;
    private static final double SPEED = 1.1;
    // 果篮高度 0.5 格 趴在顶面
    private static final double BASKET_TOP = 0.5;

    // 认领表 果篮位置到认领者 Folia 下方块线程与猫线程可能并发 用并发表
    private static final Map<BasketPos, Claim> CLAIMS = new ConcurrentHashMap<>();

    private record Claim(UUID cat, boolean tamed) {}

    // 果篮方块位置作认领表键 用记录而非字符串拼接 避免每次扫描产生 toString 开销
    private record BasketPos(UUID world, int x, int y, int z) {}

    private static BasketPos posOf(UUID worldId, int x, int y, int z) {
        return new BasketPos(worldId, x, y, z);
    }

    private static BasketPos posOf(Location l) {
        return new BasketPos(l.getWorld().getUID(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    // 果篮被破坏时由控制器调用 释放认领 趴着的猫下一 tick 发现失去认领并起身
    public static void releaseClaim(UUID worldId, int x, int y, int z) {
        CLAIMS.remove(posOf(worldId, x, y, z));
    }

    private final Cat cat;
    private final GoalKey<Cat> key;
    private int cooldown;
    // 认领的果篮方块位置
    private Location target;
    // 缓存的果篮中心 start 后不变
    private Location targetCenter;
    private boolean pendingDisplace;
    private boolean lying;
    private int safetyCooldown;
    private double lastHealth;
    private int regenCooldown;

    public FruitBasketCatGoal(Cat cat, Plugin plugin) {
        this.cat = cat;
        this.key = GoalKey.of(Cat.class, new NamespacedKey(plugin, "fruit_basket_lie"));
    }

    private static boolean isFruitBasket(Block block) {
        ImmutableBlockState s = CraftEngineBlocks.getCustomBlockState(block);
        return s != null && s.owner().value().id().equals(FRUIT_BASKET);
    }

    // 返回有效认领 认领者已消失则清掉返回 null
    private Claim resolveClaim(BasketPos k) {
        Claim c = CLAIMS.get(k);
        if (c == null) {
            return null;
        }
        Entity e = Bukkit.getEntity(c.cat());
        if (!(e instanceof Cat) || e.isDead() || !e.isValid()) {
            CLAIMS.remove(k, c);
            return null;
        }
        return c;
    }

    private static Location center(Location basket) {
        return new Location(basket.getWorld(), basket.getBlockX() + 0.5, basket.getBlockY(), basket.getBlockZ() + 0.5);
    }

    @Override
    public boolean shouldActivate() {
        if (cat.isDead() || !cat.isValid() || cat.isSitting()) {
            return false;
        }
        if (cooldown-- > 0) {
            return false;
        }
        cooldown = SCAN_INTERVAL;

        Location base = cat.getLocation();
        World w = base.getWorld();
        if (w == null) {
            return false;
        }
        int cx = base.getBlockX();
        int cy = base.getBlockY();
        int cz = base.getBlockZ();

        Location best = null;
        double bestSq = Double.MAX_VALUE;
        boolean bestDisplace = false;
        boolean tamed = cat.isTamed();
        UUID self = cat.getUniqueId();

        for (int dx = -H_RADIUS; dx <= H_RADIUS; dx++) {
            for (int dy = -V_RADIUS; dy <= V_RADIUS; dy++) {
                for (int dz = -H_RADIUS; dz <= H_RADIUS; dz++) {
                    Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (!isFruitBasket(b)) {
                        continue;
                    }
                    Location bl = b.getLocation();
                    Claim c = resolveClaim(posOf(bl));
                    boolean displace = false;
                    if (c != null) {
                        if (c.cat().equals(self)) {
                            continue;
                        }
                        // 已被占 只有是驯服猫且占者是野猫才能挤走
                        if (c.tamed() || !tamed) {
                            continue;
                        }
                        displace = true;
                    }
                    double sq = bl.distanceSquared(base);
                    if (sq < bestSq) {
                        bestSq = sq;
                        best = bl;
                        bestDisplace = displace;
                    }
                }
            }
        }

        if (best == null) {
            return false;
        }
        this.target = best;
        this.pendingDisplace = bestDisplace;
        return true;
    }

    @Override
    public void start() {
        if (target == null) {
            return;
        }
        BasketPos k = posOf(target);
        if (pendingDisplace) {
            Claim prev = CLAIMS.get(k);
            if (prev != null) {
                Entity e = Bukkit.getEntity(prev.cat());
                if (e instanceof Cat oldCat) {
                    // 把野猫挤起来 它的 goal 会在 shouldStayActive 里发现失去认领并停止
                    oldCat.setLyingDown(false);
                    oldCat.setCollidable(true);
                }
            }
        }
        CLAIMS.put(k, new Claim(cat.getUniqueId(), cat.isTamed()));
        this.targetCenter = center(target);
        cat.getPathfinder().moveTo(targetCenter, SPEED);
        lying = false;
    }

    @Override
    public void tick() {
        if (target == null) {
            return;
        }
        if (lying) {
            // 受到任何伤害 比如 reload 重建瞬间的窒息或被攻击 直接起身脱困恢复 AI
            if (cat.getHealth() < lastHealth) {
                getUpSafely();
                return;
            }
            lastHealth = cat.getHealth();
            if (--regenCooldown <= 0) {
                applyRegen();
                regenCooldown = REGEN_DURATION;
            }
            // 果篮被破坏或 reload 重建期间不是果篮了就起身
            if (--safetyCooldown <= 0) {
                safetyCooldown = SAFETY_INTERVAL;
                if (!isFruitBasket(target.getBlock())) {
                    getUpSafely();
                }
            }
            return;
        }
        cat.lookAt(targetCenter);
        Location catLoc = cat.getLocation();
        double dx = catLoc.getX() - targetCenter.getX();
        double dz = catLoc.getZ() - targetCenter.getZ();
        if (dx * dx + dz * dz <= ARRIVE_DIST_SQ) {
            cat.getPathfinder().stopPathfinding();
            Location on = new Location(target.getWorld(),
                    target.getBlockX() + 0.5, target.getBlockY() + BASKET_TOP, target.getBlockZ() + 0.5,
                    catLoc.getYaw(), 0f);
            // 趴下姿态必须等传送落地再设 否则 folia 下猫会在旧位置就进入趴下状态
            FoliaUtil.teleportThen(cat, on, () -> {
                cat.setSitting(false);
                cat.setLyingDown(true);
                // 趴在果篮上静音 屏蔽睡觉呼噜声
                cat.setSilent(true);
                // 不可被推挤 避免漂离果篮后一直占着认领
                cat.setCollidable(false);
            });
            lying = true;
            applyRegen();
            lastHealth = cat.getHealth();
            safetyCooldown = SAFETY_INTERVAL;
            regenCooldown = REGEN_DURATION;
        } else {
            cat.getPathfinder().moveTo(targetCenter, SPEED);
        }
    }

    // 起身并抬到果篮上方的空气里 脱离重建中的方块模型 再释放认领
    private void getUpSafely() {
        cat.setLyingDown(false);
        cat.setSitting(false);
        cat.setSilent(false);
        cat.setCollidable(true);
        cat.getPathfinder().stopPathfinding();
        Location safe = cat.getLocation();
        safe.setY(target.getBlockY() + 1.0);
        FoliaUtil.teleport(cat, safe);
        cat.removePotionEffect(PotionEffectType.REGENERATION);
        releaseOwnClaim();
        target = null;
        targetCenter = null;
        lying = false;
    }

    // 趴上果篮给一分钟生命恢复 不显示粒子
    private void applyRegen() {
        cat.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, REGEN_DURATION, REGEN_AMPLIFIER, true, false, false));
    }

    private void releaseOwnClaim() {
        if (target == null) {
            return;
        }
        BasketPos k = posOf(target);
        Claim c = CLAIMS.get(k);
        if (c != null && c.cat().equals(cat.getUniqueId())) {
            CLAIMS.remove(k, c);
        }
    }

    @Override
    public boolean shouldStayActive() {
        if (target == null || cat.isDead() || !cat.isValid()) {
            return false;
        }
        Claim c = CLAIMS.get(posOf(target));
        return c != null && c.cat().equals(cat.getUniqueId());
    }

    @Override
    public void stop() {
        releaseOwnClaim();
        try {
            cat.removePotionEffect(PotionEffectType.REGENERATION);
            cat.setLyingDown(false);
            cat.setSilent(false);
            cat.setCollidable(true);
            cat.getPathfinder().stopPathfinding();
        } catch (Exception ignored) {
        }
        target = null;
        targetCenter = null;
        lying = false;
    }

    @Override
    public GoalKey<Cat> getKey() {
        return key;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.MOVE, GoalType.JUMP, GoalType.LOOK);
    }
}
