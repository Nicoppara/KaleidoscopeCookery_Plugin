package net.kaleidoscope.cookery.block.entity.render;

import com.destroystokyo.paper.ParticleBuilder;
import net.momirealms.craftengine.core.world.CEWorld;
import org.bukkit.Particle;
import org.bukkit.World;

// 粒子发包统一走这里 走 World#spawnParticle 广播给正在追踪该区块的玩家 由客户端按渲染距离裁剪
// 必须在该坐标所属 region 线程调用（调用方负责调度）
// 注意 不能用 ParticleBuilder#receivers(radius, force) folia 下它会触发 getNearbyEntities 的 AABB 查询
//      该查询范围可能跨越当前 region 未拥有的邻近 region 从而抛 "Cannot getEntities asynchronously"
//      改用 force(true) 让 spawn() 走 world.spawnParticle 广播 该路径不做跨 region 实体扫描 是 region 安全的
public final class Particles {
    private Particles() {}

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
                .extra(speed)
                .force(true);
        if (data != null) {
            builder.data(data);
        }
        builder.spawn();
    }
}
