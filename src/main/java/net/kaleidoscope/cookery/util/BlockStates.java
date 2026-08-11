package net.kaleidoscope.cookery.util;

import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelWriterProxy;
import org.jetbrains.annotations.Nullable;

public final class BlockStates {
   private BlockStates() {
   }

   public static <T extends Comparable<T>> T value(ImmutableBlockState state, @Nullable Property<T> property, T fallback) {
      if (property == null) {
         return fallback;
      }

      T value = (T)state.getNullable(property);
      if (value != null) {
         return value;
      }

      Property<T> current = currentProperty(state, property);
      return (T)(current == null ? fallback : state.get(current));
   }

   public static <T extends Comparable<T>, V extends T> ImmutableBlockState with(ImmutableBlockState state, @Nullable Property<T> property, V value) {
      Property<T> current = currentProperty(state, property);
      return current != null && current.indexOf(value) != -1 ? state.with(current, value) : state;
   }

   @Nullable
   private static <T extends Comparable<T>> Property<T> currentProperty(ImmutableBlockState state, @Nullable Property<T> property) {
      if (property == null) {
         return null;
      }

      if (state.contains(property)) {
         return property;
      }

      Property<?> current = state.getProperty(property.name());
      return (Property<T>)(current != null && current.valueClass() == property.valueClass() ? current : null);
   }

   public static void sync(BlockEntity blockEntity, ImmutableBlockState newState) {
      sync(blockEntity, newState, 3);
   }

   public static void sync(BlockEntity blockEntity, ImmutableBlockState newState, int flags) {
      if (!newState.equals(blockEntity.blockState)) {
         LevelWriterProxy.INSTANCE
            .setBlock(
               blockEntity.world.world().minecraftWorld(), LocationUtils.toBlockPos(blockEntity.pos), newState.customBlockState().minecraftState(), flags
            );
         blockEntity.setBlockState(newState);
      }
   }
}
