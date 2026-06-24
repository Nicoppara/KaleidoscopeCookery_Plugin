package net.kaleidoscope.cookery.recipe.food;

import net.momirealms.craftengine.core.util.Key;

// 精准配方的带权重成品。weight 为相对权重，求和后归一化计算概率
public record WeightedResult(Key key, int weight) {}
