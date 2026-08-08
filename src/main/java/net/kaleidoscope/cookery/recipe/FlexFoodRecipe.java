package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

import java.util.List;
import java.util.Map;

// 模糊配方 perfect 是理想配比向量 走并集余弦取最近邻 不是约束满足
// 所以材料集互为子集也不遮蔽 唯一约束是方向两两不同 norm 与 totalWeight 是解析期烤好的派生量
public record FlexFoodRecipe(
        Key id,
        Key result,
        ApplianceType cook,
        Map<Key, Integer> perfect,
        List<Key> liquids,
        // 盛装容器 null 表示空手就能取 出锅提示与盛出判定都看它
        Key carrier,
        double norm,
        int totalWeight
) {
    public static FlexFoodRecipe of(Key id, Key result, ApplianceType cook,
                                    Map<Key, Integer> perfect, List<Key> liquids, Key carrier) {
        double square = 0;
        int total = 0;
        for (int weight : perfect.values()) {
            square += (double) weight * weight;
            total += weight;
        }
        return new FlexFoodRecipe(id, result, cook, Map.copyOf(perfect), List.copyOf(liquids), carrier,
                Math.sqrt(square), total);
    }
}
