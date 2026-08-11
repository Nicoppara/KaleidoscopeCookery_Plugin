package net.kaleidoscope.cookery.api.ui;

import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Replaces whole recipe screens with your own inventory.
 * Every method returns {@code false} by default, meaning "not handled", so the
 * built-in screen opens as usual; override only the screens you want and return
 * {@code true} once you have opened your own.
 * Callbacks run on the thread that triggered the menu, which is the region
 * thread owning the player — open your inventory directly, do not hop threads.
 * The data behind these screens comes from
 * {@link net.kaleidoscope.cookery.api.KaleidoscopeCookeryAPI#foodRecipes()}.
 */
public interface RecipeMenuProvider {

    /**
     * The appliance picker, opened by {@code /kcrecipe}.
     *
     * @param player the viewer
     * @param editable true when opened in edit mode
     * @return true if handled
     */
    default boolean openHome(@NotNull Player player, boolean editable) {
        return false;
    }

    /**
     * The paged recipe list of one appliance.
     *
     * @param player the viewer
     * @param cook the appliance
     * @param editable true when opened in edit mode
     * @return true if handled
     */
    default boolean openList(@NotNull Player player, @NotNull ApplianceType cook, boolean editable) {
        return false;
    }

    /**
     * The read-only detail of one recipe.
     *
     * @param player the viewer
     * @param cook the appliance the recipe belongs to
     * @param recipeId the recipe id
     * @return true if handled
     */
    default boolean openDetail(@NotNull Player player, @NotNull ApplianceType cook, @Nullable Key recipeId) {
        return false;
    }
}
