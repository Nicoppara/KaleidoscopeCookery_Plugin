package top.nicoppa.nicoPengRen.common.block;

import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.ReflectionUtils;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import net.momirealms.craftengine.proxy.minecraft.world.level.BlockGetterProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.state.StateHolderProxy;
import net.momirealms.sparrow.reflection.SReflection;

import java.lang.reflect.Field;

/**
 * 热源判定 检查某方块是否为点燃的热源（原版 LIT 属性、岩浆/火/营火等，及自定义方块的 lit 属性）。
 */
public final class HeatSourceUtils {
    private static final Object LIT_PROPERTY;

    // TODO 反射获取原版 LIT 属性，类名/字段名硬编码，正式版需要重写
    static {
        try {
            Class<?> blockStatePropertiesClass = Class.forName("net.minecraft.world.level.block.state.properties.BlockStateProperties");
            Field field = ReflectionUtils.getDeclaredField(blockStatePropertiesClass, "LIT");
            LIT_PROPERTY = field.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get LIT property", e);
        }
    }

    // 交互时点击方块的正下方是否为热源
    public static boolean isHeatSourceBelow(UseOnContext context) {
        Object level = context.getLevel().minecraftWorld();
        Object blockPos = LocationUtils.toBlockPos(context.getClickedPos());
        return isHeatSource(level, LocationUtils.below(blockPos));
    }

    public static boolean isHeatSource(Object level, Object blockPos) {
        Object blockState = BlockGetterProxy.INSTANCE.getBlockState(level, blockPos);
        if (blockState == null) return false;

        // 检查 LIT 属性
        if (StateHolderProxy.INSTANCE.hasProperty(blockState, LIT_PROPERTY)) {
            Object litValue = StateHolderProxy.INSTANCE.getValue(blockState, LIT_PROPERTY);
            if (litValue instanceof Boolean && (Boolean) litValue) {
                return true;
            }
        }

        // 检查原版热源方块
        Key blockId = BlockStateUtils.getBlockOwnerIdFromState(blockState);
        String blockIdString = blockId.toString();

        if (isHeatSourceBlockId(blockIdString)) {
            if (blockIdString.equals("minecraft:campfire") || blockIdString.equals("minecraft:soul_campfire")) {
                if (StateHolderProxy.INSTANCE.hasProperty(blockState, LIT_PROPERTY)) {
                    Object litValue = StateHolderProxy.INSTANCE.getValue(blockState, LIT_PROPERTY);
                    return litValue instanceof Boolean && (Boolean) litValue;
                }
            }
            return true;
        }

        // 检查自定义方块
        return isCustomHeatSource(blockState);
    }

    private static boolean isHeatSourceBlockId(String blockId) {
        return blockId.equals("minecraft:magma_block") ||
                blockId.equals("minecraft:fire") ||
                blockId.equals("minecraft:soul_fire") ||
                blockId.equals("minecraft:lava") ||
                blockId.equals("minecraft:campfire") ||
                blockId.equals("minecraft:soul_campfire");
    }

    private static boolean isCustomHeatSource(Object blockState) {
        try {
            var optionalCustomState = BlockStateUtils.getOptionalCustomBlockState(blockState);
            if (optionalCustomState.isPresent()) {
                ImmutableBlockState state = optionalCustomState.get();
                var litProperty = state.owner().value().getProperty("lit");
                if (litProperty != null) {
                    Object litValue = state.get(litProperty);
                    return litValue instanceof Boolean && (Boolean) litValue;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}