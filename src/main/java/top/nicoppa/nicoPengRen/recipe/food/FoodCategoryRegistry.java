package top.nicoppa.nicoPengRen.recipe.food;

import net.momirealms.craftengine.core.util.Key;
import top.nicoppa.nicoPengRen.recipe.ApplianceType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模糊配方的分类食材表，按厨具(POT / STOCKPOT)各自独立
 * 对应配置 {@code pot_food_raw} 与 {@code stock_food_raw} 中除 liquid 外的分类
 */
public final class FoodCategoryRegistry {
    private static final FoodCategoryRegistry INSTANCE = new FoodCategoryRegistry();
    private final Map<ApplianceType, Map<Key, Set<String>>> itemToCategories = new ConcurrentHashMap<>();
    private final Map<ApplianceType, Set<Key>> allItems = new ConcurrentHashMap<>();

    private FoodCategoryRegistry() {}
    public static FoodCategoryRegistry instance() { return INSTANCE; }

    public void register(ApplianceType appliance, String category, Key itemKey) {
        itemToCategories.computeIfAbsent(appliance, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(itemKey, k -> ConcurrentHashMap.newKeySet()).add(category);
        allItems.computeIfAbsent(appliance, k -> ConcurrentHashMap.newKeySet()).add(itemKey);
    }

    public boolean isRegistered(ApplianceType appliance, Key itemKey) {
        Set<Key> set = allItems.get(appliance);
        return set != null && set.contains(itemKey);
    }

    public boolean isInCategory(ApplianceType appliance, Key itemKey, String category) {
        Map<Key, Set<String>> map = itemToCategories.get(appliance);
        if (map == null) return false;
        Set<String> cats = map.get(itemKey);
        return cats != null && cats.contains(category);
    }

    /** 统计 ingredientIds 列表中属于该厨具 category 的数量（支持重复计数） */
    public int countInCategory(ApplianceType appliance, List<Key> ingredientIds, String category) {
        int count = 0;
        for (Key key : ingredientIds) {
            if (isInCategory(appliance, key, category)) count++;
        }
        return count;
    }

    public void clear(ApplianceType appliance) {
        Map<Key, Set<String>> map = itemToCategories.get(appliance);
        if (map != null) map.clear();
        Set<Key> set = allItems.get(appliance);
        if (set != null) set.clear();
    }
}
