package net.kaleidoscope.cookery.api.ui;

import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface RecipeMenuProvider {
   default boolean openHome(@NotNull Player player, boolean editable) {
      return false;
   }

   default boolean openList(@NotNull Player player, @NotNull ApplianceType cook, boolean editable) {
      return false;
   }

   default boolean openDetail(@NotNull Player player, @NotNull ApplianceType cook, @Nullable Key recipeId) {
      return false;
   }
}
