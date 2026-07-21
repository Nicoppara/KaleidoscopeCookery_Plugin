
package net.kaleidoscope.cookery.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import org.bukkit.OfflinePlayer;

import java.util.Locale;
import java.util.Map;

public final class KaleidoscopeCookeryExpansion extends PlaceholderExpansion {
    private static final Map<String, ApplianceType> APPLIANCE_ALIASES = Map.ofEntries(
            Map.entry("pot", ApplianceType.POT),
            Map.entry("cooking_pot", ApplianceType.POT),
            Map.entry("stockpot", ApplianceType.STOCKPOT),
            Map.entry("stock_pot", ApplianceType.STOCKPOT),
            Map.entry("steamer", ApplianceType.STEAMER),
            Map.entry("shawarma", ApplianceType.SHAWARMA),
            Map.entry("shawarma_spit", ApplianceType.SHAWARMA),
            Map.entry("millstone", ApplianceType.MILLSTONE),
            Map.entry("chopping", ApplianceType.CHOPPING_BOARD),
            Map.entry("choppingboard", ApplianceType.CHOPPING_BOARD),
            Map.entry("chopping_board", ApplianceType.CHOPPING_BOARD),
            Map.entry("teapot", ApplianceType.TEAPOT)
    );

    private final KaleidoscopeCookeryPlugin plugin;

    public KaleidoscopeCookeryExpansion(KaleidoscopeCookeryPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "kaleidoscopecookery";
    }

    @Override
    public String getAuthor() {
        String authors = String.join(", ", plugin.getPluginMeta().getAuthors());
        return authors.isBlank() ? "KaleidoscopeCookery" : authors;
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) {
            return null;
        }

        String key = params.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (key.isEmpty()) {
            return null;
        }

        FoodRecipeRegistry recipes = FoodRecipeRegistry.instance();
        return switch (key) {
            case "version" -> plugin.getPluginMeta().getVersion();
            case "enabled", "loaded" -> Boolean.toString(plugin.isEnabled());
            case "recipes_total", "recipe_total", "total_recipes", "total_recipe" ->
                    Integer.toString(recipes.totalRecipeCount());
            case "recipes_flex_total", "recipe_flex_total", "flex_recipes_total", "flex_recipe_total" ->
                    Integer.toString(recipes.flexRecipeCount());
            case "recipes_accurate_total", "recipe_accurate_total", "accurate_recipes_total", "accurate_recipe_total" ->
                    Integer.toString(recipes.accurateRecipeCount());
            case "recipes_chopping_total", "recipe_chopping_total", "chopping_recipes_total", "chopping_recipe_total" ->
                    Integer.toString(recipes.choppingRecipeCount());
            case "recipes_teapot_total", "recipe_teapot_total", "teapot_recipes_total", "teapot_recipe_total" ->
                    Integer.toString(recipes.teapotRecipeCount());
            case "teapot_liquids_total", "teapot_liquid_total" ->
                    Integer.toString(recipes.teapotLiquidCount());
            case "tea_cups_total", "tea_cup_total" ->
                    Integer.toString(recipes.teaCupCount());
            default -> applianceRecipeCount(key, recipes);
        };
    }

    private String applianceRecipeCount(String key, FoodRecipeRegistry recipes) {
        String value = key;
        if (value.startsWith("recipes_")) {
            value = value.substring("recipes_".length());
        } else if (value.startsWith("recipe_")) {
            value = value.substring("recipe_".length());
        } else {
            return null;
        }

        if (!value.endsWith("_total")) {
            return null;
        }

        String applianceName = value.substring(0, value.length() - "_total".length());
        ApplianceType appliance = APPLIANCE_ALIASES.get(applianceName);
        return appliance == null ? null : Integer.toString(recipes.recipeCount(appliance));
    }
}
