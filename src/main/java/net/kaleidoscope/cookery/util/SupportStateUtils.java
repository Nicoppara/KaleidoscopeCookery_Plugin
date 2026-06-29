package net.kaleidoscope.cookery.util;

import net.momirealms.craftengine.proxy.minecraft.core.DirectionProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.SupportTypeProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.state.BlockBehaviourProxy;

// 方块支撑判定 判断某方块上表面是否为完整坚固面
public final class SupportStateUtils {
    private SupportStateUtils() {}

    public static boolean isSturdyUp(Object level, Object pos, Object blockState) {
        try {
            return BlockBehaviourProxy.BlockStateBaseProxy.INSTANCE.isFaceSturdy(
                    blockState, level, pos, DirectionProxy.UP, SupportTypeProxy.FULL);
        } catch (Exception e) {
            return true;
        }
    }
}
