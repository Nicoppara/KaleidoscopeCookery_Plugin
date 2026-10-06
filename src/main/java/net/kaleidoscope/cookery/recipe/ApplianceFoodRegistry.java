package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;


// 蒸笼/烤架等的可放入食材白名单 只有登记过的食材才允许放入该厨具
@SuppressWarnings("unused")
public final class ApplianceFoodRegistry {
    private static final ApplianceFoodRegistry INSTANCE = new ApplianceFoodRegistry();
    private ApplianceFoodRegistry() {
    }

    public static ApplianceFoodRegistry instance() {
        return INSTANCE;
    }

    public void register(ApplianceType type, Key key) {
        FoodRecipeRegistry.instance().registerAllowed(type, key);
    }

    public void register(ApplianceType type, String key) {
        register(type, Key.of(key));
    }

    // 白名单从各配方 perfect 反推 调味品与等效替身反推不到 但都必须能下锅
    public boolean isAllowed(ApplianceType type, Key key) {
        return FoodRecipeRegistry.instance().isAllowed(type, key);
    }

    public boolean isAllowed(ApplianceType type, String key) {
        return isAllowed(type, Key.of(key));
    }

    // UI 删除精准配方时同步摘掉白名单 否则原料要等到下次配置重载才禁得掉
    public void unregister(ApplianceType type, Key key) {
        FoodRecipeRegistry.instance().unregisterAllowed(type, key);
    }

    public void clear(ApplianceType type) {
        FoodRecipeRegistry.instance().clearAllowed(type);
    }
}
