package net.kaleidoscope.cookery.util;

import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.core.block.BlockKeys;
import net.momirealms.craftengine.core.block.BlockStateWrapper;
import net.momirealms.craftengine.core.block.UpdateFlags;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.World;
import org.bukkit.Bukkit;

// 往世界里写真的 minecraft:light 方块来照亮
public final class LightBlocks {
    private LightBlocks() {}

    public static final int MAX_LEVEL = 15;

    // 每档一个状态 解析一次永久复用 别每次点灯现 createBlockData
    private static final Object[] AIR_LIGHTS = new Object[MAX_LEVEL + 1];
    private static final Object[] WATER_LIGHTS = new Object[MAX_LEVEL + 1];
    private static final int AIR_ID;
    private static final int WATER_ID;

    static {
        AIR_LIGHTS[0] = BlockStateUtils.blockDataToBlockState(Bukkit.createBlockData("minecraft:air"));
        WATER_LIGHTS[0] = BlockStateUtils.blockDataToBlockState(Bukkit.createBlockData("minecraft:water"));
        for (int level = 1; level <= MAX_LEVEL; level++) {
            AIR_LIGHTS[level] = BlockStateUtils.blockDataToBlockState(
                    Bukkit.createBlockData("minecraft:light[level=" + level + "]"));
            WATER_LIGHTS[level] = BlockStateUtils.blockDataToBlockState(
                    Bukkit.createBlockData("minecraft:light[level=" + level + ",waterlogged=true]"));
        }
        AIR_ID = BlockStateUtils.blockStateToId(AIR_LIGHTS[0]);
        WATER_ID = BlockStateUtils.blockStateToId(WATER_LIGHTS[0]);
    }

    // level 为 0 表示熄灭 返回是否真的动了方块
    public static boolean set(World world, BlockPos pos, int level) {
        int clamped = Math.max(0, Math.min(MAX_LEVEL, level));
        BlockStateWrapper current = world.getBlockState(pos);
        if (current == null) {
            return false;
        }
        int id = current.registryId();
        if (id == AIR_ID) {
            return write(world, pos, AIR_LIGHTS[clamped]);
        }
        if (id == WATER_ID) {
            return write(world, pos, WATER_LIGHTS[clamped]);
        }
        // 只有本插件点亮的那格才允许改档或熄灭
        if (!BlockKeys.LIGHT.equals(current.ownerId())) {
            return false;
        }
        // 区块每次加载都会重新认领已存在的光源 档位没变就别再写一次
        if (clamped == levelOf(current)) {
            return true;
        }
        boolean waterlogged = Boolean.TRUE.equals(current.getProperty("waterlogged"));
        return write(world, pos, (waterlogged ? WATER_LIGHTS : AIR_LIGHTS)[clamped]);
    }

    // 熄灭前确认那格还是光源 玩家在原地盖了东西就别去动它
    public static void clear(World world, BlockPos pos) {
        BlockStateWrapper current = world.getBlockState(pos);
        if (current != null && BlockKeys.LIGHT.equals(current.ownerId())) {
            set(world, pos, 0);
        }
    }

    private static int levelOf(BlockStateWrapper state) {
        Object level = state.getProperty("level");
        if (level instanceof Integer value) {
            return value;
        }
        return level == null ? -1 : Integer.parseInt(level.toString());
    }

    private static boolean write(World world, BlockPos pos, Object blockState) {
        world.setBlockState(pos, BlockStateUtils.toBlockStateWrapper(blockState), UpdateFlags.UPDATE_ALL);
        return true;
    }
}
