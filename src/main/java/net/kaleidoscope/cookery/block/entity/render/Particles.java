package net.kaleidoscope.cookery.block.entity.render;

import com.destroystokyo.paper.ParticleBuilder;
import net.kaleidoscope.cookery.util.FoliaUtil;
import net.momirealms.craftengine.core.world.CEWorld;
import org.bukkit.Particle;
import org.bukkit.World;

// 粒子发包入口 调用方负责调度到该坐标所属 region 线程
// folia 不能用 receivers 它底下的 AABB 查询跨 region 会抛 退回 force 广播 收件人范围远大于 8 格
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
