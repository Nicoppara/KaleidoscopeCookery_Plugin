package net.kaleidoscope.cookery.recipe.food;

import net.momirealms.craftengine.core.util.Key;

// 精确食材要求：某个物品至少需要 count 个
public record ItemRequirement(Key item, int count) {}
