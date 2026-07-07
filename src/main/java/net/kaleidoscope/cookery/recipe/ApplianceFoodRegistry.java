package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// 蒸笼/烤架等的可放入食材白名单 只有登记过的食材才允许放入该厨具
// 与 FoodCategoryRegistry 区分 那是 pot stockpot 的分类配方系统 这里只判定能否放入
/**
 * Registry of items that may be inserted into specific appliances.
 *
 * <p>This registry answers the "can this ingredient go into this appliance"
 * question. It is separate from category-based pot recipe matching.</p>
 */
@SuppressWarnings("unused")
public final class ApplianceFoodRegistry {
    private static final ApplianceFoodRegistry INSTANCE = new ApplianceFoodRegistry();
    private final Map<ApplianceType, Set<Key>> allowed = new ConcurrentHashMap<>();

    private ApplianceFoodRegistry() {
    }

    /**
     * Returns the shared registry.
     *
     * @return the singleton registry
     */
    public static ApplianceFoodRegistry instance() {
        return INSTANCE;
    }

    /**
     * Allows an item in an appliance.
     *
     * @param type appliance type
     * @param key CraftEngine or vanilla item id
     */
    public void register(ApplianceType type, Key key) {
        allowed.computeIfAbsent(type, k -> ConcurrentHashMap.newKeySet()).add(key);
    }

    /**
     * Allows an item in an appliance.
     *
     * @param type appliance type
     * @param key item id such as {@code minecraft:carrot}
     */
    public void register(ApplianceType type, String key) {
        register(type, Key.of(key));
    }

    /**
     * Checks whether an item can be inserted into an appliance.
     *
     * @param type appliance type
     * @param key item id
     * @return {@code true} if the item is allowed
     */
    public boolean isAllowed(ApplianceType type, Key key) {
        Set<Key> set = allowed.get(type);
        return set != null && set.contains(key);
    }

    /**
     * Checks whether an item can be inserted into an appliance.
     *
     * @param type appliance type
     * @param key item id such as {@code minecraft:carrot}
     * @return {@code true} if the item is allowed
     */
    public boolean isAllowed(ApplianceType type, String key) {
        return isAllowed(type, Key.of(key));
    }

    /**
     * Clears all allowed items for an appliance type.
     *
     * @param type appliance type
     */
    public void clear(ApplianceType type) {
        Set<Key> set = allowed.get(type);
        if (set != null) {
            set.clear();
        }
    }
}
