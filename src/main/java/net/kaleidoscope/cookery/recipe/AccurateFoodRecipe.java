package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

import java.util.List;

// 精准配方 1 对 1 成品是带权重列表 按权重随机取一个
// rotations 仅石磨用 0 表示用 behavior 默认 resultCount 不配则为 1
public record AccurateFoodRecipe(
        Key id,
        Key input,
        List<WeightedResult> results,
        ApplianceType cook,
        int rotations,
        int resultCount,
        List<String> lore,
        int cookingTime
) {
    public AccurateFoodRecipe {
        results = List.copyOf(results);
        lore = List.copyOf(lore);
        if (cookingTime < 0) throw new IllegalArgumentException("cookingTime must be non-negative");
    }

    /** Binary-compatible constructor; zero means inherit the appliance setting. */
    public AccurateFoodRecipe(Key id, Key input, List<WeightedResult> results, ApplianceType cook,
                              int rotations, int resultCount, List<String> lore) {
        this(id, input, results, cook, rotations, resultCount, lore, 0);
    }
    // 用于展示/记录的代表性成品
    public Key primaryResult() {
        return results.isEmpty() ? null : results.get(0).key();
    }
}
