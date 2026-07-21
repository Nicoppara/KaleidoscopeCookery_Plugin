package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

import java.util.List;

// 茶杯成品 tea=成品(茶壶产物)id item=手持可直接放到杯垫的物品 displayModels=成形展示模型 多个则随机
public record TeaCup(Key tea, Key item, List<Key> displayModels) {
}
