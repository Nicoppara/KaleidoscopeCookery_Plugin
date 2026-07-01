package net.kaleidoscope.cookery.block.entity.render;

import com.destroystokyo.paper.ParticleBuilder;
import net.momirealms.craftengine.core.world.CEWorld;
import org.bukkit.Particle;
import org.bukkit.World;

// 粒子发包统一走这里 Paper ParticleBuilder 只发给粒子点球形半径内玩家 远处不渲染就不发
// force 默认 false 客户端按自身粒子设置可丢弃 folia 下需在该坐标所属 region 线程调用
public final class Particles {
    private Particles() {}

    // 粒子接收半径 格 球形 远于此不发包
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
                .extra(speed)
                .receivers(RECEIVER_RADIUS, true);
        if (data != null) {
            builder.data(data);
        }
        builder.spawn();
    }
}
