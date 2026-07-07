package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

// 精确食材要求 某个物品至少需要 count 个
/**
 * Required amount of a specific item.
 *
 * @param item required item id
 * @param count minimum required count
 */
public record ItemRequirement(Key item, int count) {}
