package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// 蒸笼/烤架等的可放入食材白名单 只有登记过的食材才允许放入该厨具
// 与 FoodCategoryRegistry 区分 那是 pot stockpot 的分类配方系统 这里只判定能否放入
@SuppressWarnings("unused")
public final class ApplianceFoodRegistry {
    private static final ApplianceFoodRegistry INSTANCE = new ApplianceFoodRegistry();
    private final Map<ApplianceType, Set<Key>> allowed = new ConcurrentHashMap<>();

    private ApplianceFoodRegistry() {
    }

    public static ApplianceFoodRegistry instance() {
        return INSTANCE;
    }

    public void register(ApplianceType type, Key key) {
        allowed.computeIfAbsent(type, k -> ConcurrentHashMap.newKeySet()).add(key);
    }

    public void register(ApplianceType type, String key) {
        register(type, Key.of(key));
    }

    public boolean isAllowed(ApplianceType type, Key key) {
        Set<Key> set = allowed.get(type);
        return set != null && set.contains(key);
    }

    public boolean isAllowed(ApplianceType type, String key) {
        return isAllowed(type, Key.of(key));
    }

    public void clear(ApplianceType type) {
        Set<Key> set = allowed.get(type);
        if (set != null) {
            set.clear();
        }
    }
}
