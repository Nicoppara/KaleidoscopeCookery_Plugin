package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

// 精准配方的带权重成品 weight 为相对权重 求和后归一化计算概率
/**
 * Weighted output item used by accurate recipes.
 *
 * @param key output item id
 * @param weight relative weight
 */
public record WeightedResult(Key key, int weight) {}
