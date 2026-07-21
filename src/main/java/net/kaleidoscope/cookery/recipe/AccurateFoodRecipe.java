package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

import java.util.List;

// 精准配方 1 对 1 成品为带权重列表 按权重随机产出一个
// rotations 石磨产出所需圈数 仅石磨用 0 表示用 behavior 默认
public record AccurateFoodRecipe(
        Key id,
        Key input,
        List<WeightedResult> results,
        ApplianceType cook,
        int rotations,
        List<String> lore
) {
    // 用于展示/记录的代表性成品
    public Key primaryResult() {
        return results.isEmpty() ? null : results.get(0).key();
    }
}
