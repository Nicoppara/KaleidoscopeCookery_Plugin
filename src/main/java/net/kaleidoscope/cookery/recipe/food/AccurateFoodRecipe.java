package net.kaleidoscope.cookery.recipe.food;

import net.momirealms.craftengine.core.util.Key;
import net.kaleidoscope.cookery.recipe.ApplianceType;

import java.util.List;

// 精准配方 1 对 1，成品为带权重列表（扁平写法即单个、权重 100%），按权重随机产出一个
public record AccurateFoodRecipe(
        Key id,
        Key input,
        List<WeightedResult> results,
        ApplianceType cook,
        List<String> lore
) {
    // 用于展示/记录的代表性成品（取列表首个）
    public Key primaryResult() {
        return results.isEmpty() ? null : results.get(0).key();
    }
}
