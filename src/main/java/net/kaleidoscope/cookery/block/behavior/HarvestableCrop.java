package net.kaleidoscope.cookery.block.behavior;

import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.World;

// 可收割作物 收割语义归方块行为自己定义 右键与镰刀都只负责触发
public interface HarvestableCrop {
    // 归一到这一株的根部 多段作物的任意一段都映射到同一个坐标 范围收割据此去重
    default BlockPos rootPos(World world, BlockPos pos, ImmutableBlockState state) {
        return pos;
    }

    // 未成熟或无权限返回 false 调用方据此决定挥不挥手 扣不扣耐久
    boolean harvest(World world, BlockPos pos, ImmutableBlockState state, Player player);
}
