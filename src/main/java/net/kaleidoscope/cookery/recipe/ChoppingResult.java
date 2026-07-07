package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

// 砧板成品 物品 数量 相对权重 配置写法形如 物品 数量 权重 可重复同一物品
/**
 * Chopping board output entry.
 *
 * @param key output item id
 * @param count output item count
 * @param weight relative weight or percentage depending on the chopping mode
 */
public record ChoppingResult(Key key, int count, int weight) {}
