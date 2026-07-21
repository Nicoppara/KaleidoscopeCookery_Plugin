package net.kaleidoscope.cookery.block.entity.render;

import com.destroystokyo.paper.ParticleBuilder;
import net.kaleidoscope.cookery.util.FoliaUtil;
import net.momirealms.craftengine.core.world.CEWorld;
import org.bukkit.Particle;
import org.bukkit.World;

// 粒子发包统一走这里 必须在该坐标所属 region 线程调用 调用方负责调度
// 按端分流 paper 走 receivers 精确过滤省带宽 folia 只能广播
// receivers(radius,true) 底下是 world.getNearbyPlayers 的 AABB 查询 folia 下该范围可能跨越
// 当前 region 未拥有的邻近 region 直接抛 Cannot getEntities asynchronously 所以 folia 不能用
// folia 退回 force(true) 让 spawn 走 world.spawnParticle 广播 该路径不做跨 region 实体扫描
// 代价是收件人范围变成原版 force 的扩展视距 比 8 格大得多 属于 folia 下的已知取舍
public final class Particles {
    private Particles() {}

    // 收件人过滤半径 仅 paper 生效
    public static final int RECEIVER_RADIUS = 8;

    public static void emit(CEWorld ceWorld, Particle particle, double x, double y, double z,
                            int count, double offsetX, double offsetY, double offsetZ, double speed, Object data) {
        emit((World) ceWorld.world().platformWorld(), particle, x, y, z, count, offsetX, offsetY, offsetZ, speed, data);
    }

    public static void emit(World world, Particle particle, double x, double y, double z,
                            int count, double offsetX, double offsetY, double offsetZ, double speed, Object data) {
        ParticleBuilder builder = new ParticleBuilder(particle)
                .location(world, x, y, z)
                .count(count)
                .offset(offsetX, offsetY, offsetZ)
                .extra(speed);
        if (data != null) {
            builder.data(data);
        }
        if (FoliaUtil.isFolia()) {
            builder.force(true);
        } else {
            builder.receivers(RECEIVER_RADIUS, true);
        }
        builder.spawn();
    }
}
