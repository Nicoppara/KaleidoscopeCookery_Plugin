package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

import java.util.HashMap;
import java.util.Map;

// 菜品吃完退还什么容器 唯一数据源是配方的 carrier
// 两条进食路径都查这里 不往物品存 NBT 所以老物品也跟着最新配方走
public final class DishCarriers {
    private DishCarriers() {
    }

    // 成品 id -> 容器 每次 reload 重建 查询在进食热路径上 不能每次扫全表
    private static volatile Map<Key, Key> cache = Map.of();

    // 同一个成品被多条配方产出时取先注册的那条 这种情况本来就该避免
    public static void rebuild(Iterable<FlexFoodRecipe> recipes) {
        Map<Key, Key> map = new HashMap<>();
        for (FlexFoodRecipe recipe : recipes) {
            if (recipe.carrier() != null) {
                map.putIfAbsent(recipe.result(), recipe.carrier());
            }
        }
        cache = Map.copyOf(map);
    }

    // 没有容器的菜返回 null 调用方什么都不用给
    public static Key of(Key result) {
        return result == null ? null : cache.get(result);
    }

    public static boolean isEmpty() {
        return cache.isEmpty();
    }
}
