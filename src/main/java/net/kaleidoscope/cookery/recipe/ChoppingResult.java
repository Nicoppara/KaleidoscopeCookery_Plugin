package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

// 砧板成品 物品 数量 相对权重 配置写法形如 物品 数量 权重 可重复同一物品
public record ChoppingResult(Key key, int count, int weight) {}
