package net.kaleidoscope.cookery.api;

import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.kaleidoscope.cookery.recipe.ApplianceFoodRegistry;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.api.ui.RecipeMenuHooks;
import net.kaleidoscope.cookery.api.ui.RecipeMenuStyle;
import net.kaleidoscope.cookery.recipe.SoupBaseRegistry;
import org.bukkit.plugin.Plugin;

/**
 * Static entry point for plugins integrating with Kaleidoscope Cookery. Use
 * this class instead of depending on internal controller or behavior packages.
 * Runtime registries returned here are shared by the plugin and can be extended
 * during another plugin's enable phase.
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
     * Returns the item tag registry backing every {@code #namespace:tag}
     * reference in the behavior configs.
     *
     * @return the item tag API
     */
    public static ItemTags itemTags() {
        return ItemTags.instance();
    }

    /**
     * Returns the vanilla block tag registry, backing the {@code block_tags}
     * config section. CraftEngine custom blocks use {@code settings.tags} instead.
     *
     * @return the block tag registry
     */
    public static BlockTags blockTags() {
        return BlockTags.instance();
    }

    /**
     * Returns the boolean entity property registry used by loot conditions.
     *
     * @return the entity property registry
     */
    public static EntityProperties entityProperties() {
        return EntityProperties.instance();
    }

    /**
     * Returns the reskin hook for the built-in recipe menus: button icons,
     * screen titles and appliance names, without writing any inventory code.
     *
     * @return the recipe menu style registry
     */
    public static RecipeMenuStyle recipeMenuStyle() {
        return RecipeMenuStyle.instance();
    }

    /**
     * Returns the registration point for replacing whole recipe screens.
     *
     * @return the recipe menu hooks
     */
    public static RecipeMenuHooks recipeMenuHooks() {
        return RecipeMenuHooks.instance();
    }

    /**
     * Returns the pot cooking condition registry.
     *
     * @return the pot cook condition API
     */
    public static PotCookConditions potCookConditions() {
        return PotCookConditions.instance();
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
     * Returns the stockpot soup base registry.
     *
     * @return the soup base registry
     */
    public static SoupBaseRegistry soupBases() {
        return SoupBaseRegistry.instance();
    }
}
