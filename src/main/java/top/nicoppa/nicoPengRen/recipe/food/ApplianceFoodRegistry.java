package top.nicoppa.nicoPengRen.recipe.food;

import net.momirealms.craftengine.core.util.Key;
import top.nicoppa.nicoPengRen.recipe.ApplianceType;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 蒸笼/烤架的可放入食材白名单：只有在对应 *_food_raw 段登记过的食材，才允许放入该厨具
 * 与 {@link FoodCategoryRegistry}(pot/stockpot 的分类配方系统)区分：这里只做能否放入的判定
 */
public final class ApplianceFoodRegistry {
    private static final ApplianceFoodRegistry INSTANCE = new ApplianceFoodRegistry();
    private final Map<ApplianceType, Set<Key>> allowed = new ConcurrentHashMap<>();

    private ApplianceFoodRegistry() {}
    public static ApplianceFoodRegistry instance() { return INSTANCE; }

    public void register(ApplianceType type, Key key) {
        allowed.computeIfAbsent(type, k -> ConcurrentHashMap.newKeySet()).add(key);
    }

    public boolean isAllowed(ApplianceType type, Key key) {
        Set<Key> set = allowed.get(type);
        return set != null && set.contains(key);
    }

    public void clear(ApplianceType type) {
        Set<Key> set = allowed.get(type);
        if (set != null) set.clear();
    }
}
