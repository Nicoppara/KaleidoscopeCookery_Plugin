package net.kaleidoscope.cookery.api;

import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.kaleidoscope.cookery.recipe.ApplianceFoodRegistry;
import net.kaleidoscope.cookery.recipe.FoodCategoryRegistry;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.SoupBaseRegistry;
import org.bukkit.plugin.Plugin;

/**
 * Static entry point for plugins integrating with Kaleidoscope Cookery.
 *
 * <p>Use this class from another plugin instead of depending on internal
 * controller or behavior packages. Runtime registries returned here are shared
 * by the plugin and can be extended during another plugin's enable phase.</p>
 */
@SuppressWarnings("unused")
public final class KaleidoscopeCookeryAPI {
    private KaleidoscopeCookeryAPI() {
    }

    /**
     * Returns the running Bukkit plugin instance.
     *
     * @return the Kaleidoscope Cookery plugin instance
     */
    public static Plugin plugin() {
        return KaleidoscopeCookeryPlugin.instance();
    }

    /**
     * Returns the runtime chopping board knife registry.
     *
     * @return the chopping board knife API
     */
    public static ChoppingBoardKnives choppingBoardKnives() {
        return ChoppingBoardKnives.instance();
    }

    /**
     * Returns the millstone animal profile registry.
     *
     * @return the millstone animal API
     */
    public static MillstoneAnimals millstoneAnimals() {
        return MillstoneAnimals.instance();
    }

    /**
     * Returns the loaded food recipe registry.
     *
     * @return the food recipe registry
     */
    public static FoodRecipeRegistry foodRecipes() {
        return FoodRecipeRegistry.instance();
    }

    /**
     * Returns the appliance ingredient allow-list registry.
     *
     * @return the appliance food registry
     */
    public static ApplianceFoodRegistry applianceFoods() {
        return ApplianceFoodRegistry.instance();
    }

    /**
     * Returns the food category registry used by flexible pot recipes.
     *
     * @return the food category registry
     */
    public static FoodCategoryRegistry foodCategories() {
        return FoodCategoryRegistry.instance();
    }

    /**
     * Returns the stockpot soup base registry.
     *
     * @return the soup base registry
     */
    public static SoupBaseRegistry soupBases() {
        return SoupBaseRegistry.instance();
    }
}
