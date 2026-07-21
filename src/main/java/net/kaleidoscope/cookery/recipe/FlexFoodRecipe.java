package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

import java.util.List;

// 模糊配方 flex 适用于 POT 与 STOCKPOT 这类不要求精确食材的厨具
// liquids 仅高汤锅使用 当前汤底桶 id 须命中其一才匹配 为空表示不限汤底 炒锅恒为空
public record FlexFoodRecipe(
        Key id,
        Key result,
        ApplianceType cook,
        List<ItemRequirement> require,
        List<RawRequirement> raw,
        List<Key> preferred,
        List<Key> unpreferred,
        List<LoreCondition> loreConditions,
        List<Key> liquids
) {
    // 非零 raw 要求的数量 用于多配方优先级排序
    public int nonZeroRawCount() {
        int count = 0;
        for (RawRequirement r : raw) {
            if (r.min() > 0) {
                count++;
            }
        }
        return count;
    }

    // 所有非零 raw 要求的 min 之和 用于同优先级时的次级排序
    public int totalMinCount() {
        int total = 0;
        for (RawRequirement r : raw) {
            if (r.min() > 0) {
                total += r.min();
            }
        }
        return total;
    }
}
