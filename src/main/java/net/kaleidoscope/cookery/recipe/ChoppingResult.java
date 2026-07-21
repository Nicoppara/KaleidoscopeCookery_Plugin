package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

// 砧板成品 物品 数量 相对权重 配置写法形如 物品 数量 权重 可重复同一物品
// weight 语义随 ChoppingMode 变 单产物挑选时是相对权重 独立判定时当作百分比
public record ChoppingResult(Key key, int count, int weight) {}
