package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// 模糊配方的分类食材表 按厨具 POT 与 STOCKPOT 各自独立
// 对应配置 pot_food_raw 与 stock_food_raw 中除 liquid 外的分类
/**
 * Category registry used by flexible pot and stockpot recipes.
 *
 * <p>Categories are free-form strings such as {@code meat}, {@code vegetable},
 * or any value used by a CraftEngine config pack.</p>
 */
@SuppressWarnings("unused")
public final class FoodCategoryRegistry {
    private static final FoodCategoryRegistry INSTANCE = new FoodCategoryRegistry();
    private final Map<ApplianceType, Map<Key, Set<String>>> itemToCategories = new ConcurrentHashMap<>();
    private final Map<ApplianceType, Set<Key>> allItems = new ConcurrentHashMap<>();

    private FoodCategoryRegistry() {
    }

    /**
     * Returns the shared registry.
     *
     * @return the singleton registry
     */
    public static FoodCategoryRegistry instance() {
        return INSTANCE;
    }

    /**
     * Registers an item in a category for an appliance.
     *
     * @param appliance appliance type
     * @param category category name
     * @param itemKey item id
     */
    public void register(ApplianceType appliance, String category, Key itemKey) {
        itemToCategories.computeIfAbsent(appliance, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(itemKey, k -> ConcurrentHashMap.newKeySet()).add(category);
        allItems.computeIfAbsent(appliance, k -> ConcurrentHashMap.newKeySet()).add(itemKey);
    }

    /**
     * Registers an item in a category for an appliance.
     *
     * @param appliance appliance type
     * @param category category name
     * @param itemKey item id such as {@code minecraft:carrot}
     */
    public void register(ApplianceType appliance, String category, String itemKey) {
        register(appliance, category, Key.of(itemKey));
    }

    /**
     * Checks whether an item has any category for an appliance.
     *
     * @param appliance appliance type
     * @param itemKey item id
     * @return {@code true} if the item is registered for the appliance
     */
    public boolean isRegistered(ApplianceType appliance, Key itemKey) {
        Set<Key> set = allItems.get(appliance);
        return set != null && set.contains(itemKey);
    }

    /**
     * Checks whether an item has any category for an appliance.
     *
     * @param appliance appliance type
     * @param itemKey item id such as {@code minecraft:carrot}
     * @return {@code true} if the item is registered for the appliance
     */
    public boolean isRegistered(ApplianceType appliance, String itemKey) {
        return isRegistered(appliance, Key.of(itemKey));
    }

    /**
     * Checks whether an item belongs to a category for an appliance.
     *
     * @param appliance appliance type
     * @param itemKey item id
     * @param category category name
     * @return {@code true} if the item is in the category
     */
    public boolean isInCategory(ApplianceType appliance, Key itemKey, String category) {
        Map<Key, Set<String>> map = itemToCategories.get(appliance);
        if (map == null) {
            return false;
        }
        Set<String> cats = map.get(itemKey);
        return cats != null && cats.contains(category);
    }

    /**
     * Checks whether an item belongs to a category for an appliance.
     *
     * @param appliance appliance type
     * @param itemKey item id such as {@code minecraft:carrot}
     * @param category category name
     * @return {@code true} if the item is in the category
     */
    public boolean isInCategory(ApplianceType appliance, String itemKey, String category) {
        return isInCategory(appliance, Key.of(itemKey), category);
    }

    // 统计 ingredientIds 中属于该厨具 category 的数量
    /**
     * Counts how many ingredient ids belong to a category.
     *
     * @param appliance appliance type
     * @param ingredientIds item ids to count
     * @param category category name
     * @return number of matching ingredients
     */
    public int countInCategory(ApplianceType appliance, List<Key> ingredientIds, String category) {
        int count = 0;
        for (Key key : ingredientIds) {
            if (isInCategory(appliance, key, category)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Clears all category mappings for an appliance type.
     *
     * @param appliance appliance type
     */
    public void clear(ApplianceType appliance) {
        Map<Key, Set<String>> map = itemToCategories.get(appliance);
        if (map != null) {
            map.clear();
        }
        Set<Key> set = allItems.get(appliance);
        if (set != null) {
            set.clear();
        }
    }
}
