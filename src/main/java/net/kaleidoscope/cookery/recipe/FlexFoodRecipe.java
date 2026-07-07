package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

import java.util.List;

// 模糊配方 flex 适用于 POT 与 STOCKPOT 这类不要求精确食材的厨具
// liquids 仅高汤锅使用 当前汤底桶 id 须命中其一才匹配 为空表示不限汤底 炒锅恒为空
/**
 * Flexible recipe definition used by pots and stockpots.
 *
 * @param id recipe id
 * @param result output item id
 * @param cook appliance type
 * @param require exact item requirements
 * @param raw category requirements
 * @param preferred preferred ingredient ids
 * @param unpreferred unpreferred ingredient ids
 * @param loreConditions conditional lore lines
 * @param liquids allowed soup base ids; empty means any liquid
 */
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
    /**
     * Counts category requirements that affect matching.
     *
     * @return number of raw requirements with a minimum above zero
     */
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
    /**
     * Sums the minimum values of category requirements that affect matching.
     *
     * @return total minimum category count
     */
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
