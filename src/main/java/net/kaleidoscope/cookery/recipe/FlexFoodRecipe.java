package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

import java.util.List;
import java.util.Map;

// perfect 同时声明必需食材和理想配比 norm 与 totalWeight 在解析期预计算
public record FlexFoodRecipe(
        Key id,
        Key result,
        ApplianceType cook,
        Map<Key, Integer> perfect,
        List<Key> liquids,
        // 盛装容器 null 表示空手就能取 出锅提示与盛出判定都看它
        Key carrier,
        // 这道菜认不认等效食物表与调味品表 见 FoodGroups
        boolean useEquivalentFoods,
        boolean useSeasonings,
        double norm,
        int totalWeight
) {
    public static FlexFoodRecipe of(Key id, Key result, ApplianceType cook,
                                    Map<Key, Integer> perfect, List<Key> liquids, Key carrier) {
        return of(id, result, cook, perfect, liquids, carrier, true, true);
    }

    public static FlexFoodRecipe of(Key id, Key result, ApplianceType cook,
                                    Map<Key, Integer> perfect, List<Key> liquids, Key carrier,
                                    boolean useEquivalentFoods, boolean useSeasonings) {
        double square = 0;
        int total = 0;
        for (int weight : perfect.values()) {
            square += (double) weight * weight;
            total += weight;
        }
        return new FlexFoodRecipe(id, result, cook, Map.copyOf(perfect), List.copyOf(liquids), carrier,
                useEquivalentFoods, useSeasonings, Math.sqrt(square), total);
    }

    public FlexFoodRecipe withToggles(boolean useEquivalentFoods, boolean useSeasonings) {
        return of(this.id, this.result, this.cook, this.perfect, this.liquids, this.carrier,
                useEquivalentFoods, useSeasonings);
    }
}
