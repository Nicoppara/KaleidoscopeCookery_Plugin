package net.kaleidoscope.cookery.util;

import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.UpdateFlags;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelWriterProxy;
import org.jetbrains.annotations.Nullable;

// 方块状态访问与同步
public final class BlockStates {
    private BlockStates() {}

    public static <T extends Comparable<T>> T value(
            ImmutableBlockState state,
            @Nullable Property<T> property,
            T fallback
    ) {
        if (property == null) {
            return fallback;
        }
        T value = state.getNullable(property);
        if (value != null) {
            return value;
        }

        // CE 重载会重建 Property，旧控制器需按当前状态重新解析
        Property<T> current = currentProperty(state, property);
        return current == null ? fallback : state.get(current);
    }

    public static <T extends Comparable<T>, V extends T> ImmutableBlockState with(
            ImmutableBlockState state,
            @Nullable Property<T> property,
            V value
    ) {
        Property<T> current = currentProperty(state, property);
        if (current == null || current.indexOf(value) == -1) {
            return state;
        }
        return state.with(current, value);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> Property<T> currentProperty(
            ImmutableBlockState state,
            @Nullable Property<T> property
    ) {
        if (property == null) {
            return null;
        }
        if (state.contains(property)) {
            return property;
        }
        Property<?> current = state.getProperty(property.name());
        if (current == null || current.valueClass() != property.valueClass()) {
            return null;
        }
        return (Property<T>) current;
    }

    public static void sync(BlockEntity blockEntity, ImmutableBlockState newState) {
        sync(blockEntity, newState, UpdateFlags.UPDATE_ALL);
    }

    public static void sync(BlockEntity blockEntity, ImmutableBlockState newState, int flags) {
        if (newState.equals(blockEntity.blockState)) {
            return;
        }
        LevelWriterProxy.INSTANCE.setBlock(
                blockEntity.world.world().minecraftWorld(),
                LocationUtils.toBlockPos(blockEntity.pos),
                newState.customBlockState().minecraftState(),
                flags
        );
        blockEntity.setBlockState(newState);
    }
}
