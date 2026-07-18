package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

import java.util.List;

// 精准配方 1 对 1 成品为带权重列表 按权重随机产出一个
// rotations 石磨产出所需圈数 仅石磨用 0 表示用 behavior 默认
/**
 * One-input recipe definition for appliances such as millstone and steamer.
 *
 * @param id recipe id
 * @param input input item id
 * @param results weighted output item ids
 * @param resultCount number of items produced
 * @param cook appliance type
 * @param rotations millstone rotations; {@code 0} uses the appliance default
 * @param lore lore lines applied to generated result items
 */
public record AccurateFoodRecipe(
        Key id,
        Key input,
        List<WeightedResult> results,
        int resultCount,
        ApplianceType cook,
        int rotations,
        List<String> lore
) {
    // 用于展示/记录的代表性成品
    /**
     * Returns the first configured result id.
     *
     * @return first result item id, or {@code null} when there are no results
     */
    public Key primaryResult() {
        return results.isEmpty() ? null : results.get(0).key();
    }
}
