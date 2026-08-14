package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// 蒸笼/烤架等的可放入食材白名单 只有登记过的食材才允许放入该厨具
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

    // 白名单从各配方 perfect 反推 调味品与等效替身反推不到 但都必须能下锅
    public boolean isAllowed(ApplianceType type, Key key) {
        Set<Key> set = allowed.get(type);
        if (set != null && set.contains(key)) {
            return true;
        }
        if (!type.usesFlexRecipes()) {
            return false;
        }
        FoodGroups groups = FoodGroups.instance();
        return groups.isSeasoning(key) || hasEquivalentAllowed(set, groups, key);
    }

    // 同组里有一个进了白名单整组都能下锅 只在该食材属于某个等效组时才扫
    private static boolean hasEquivalentAllowed(Set<Key> allowed, FoodGroups groups, Key key) {
        if (allowed == null || allowed.isEmpty()) {
            return false;
        }
        Key canonical = groups.canonical(key);
        if (canonical.equals(key)) {
            return false;
        }
        for (Key candidate : allowed) {
            if (canonical.equals(groups.canonical(candidate))) {
                return true;
            }
        }
        return false;
    }

    public boolean isAllowed(ApplianceType type, String key) {
        return isAllowed(type, Key.of(key));
    }

    // UI 删除精准配方时同步摘掉白名单 否则原料要等到下次配置重载才禁得掉
    public void unregister(ApplianceType type, Key key) {
        Set<Key> set = allowed.get(type);
        if (set != null) {
            set.remove(key);
        }
    }

    public void clear(ApplianceType type) {
        Set<Key> set = allowed.get(type);
        if (set != null) {
            set.clear();
        }
    }
}
