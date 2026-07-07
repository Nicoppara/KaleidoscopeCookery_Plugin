package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

import java.util.List;

// 茶杯成品 tea=成品(茶壶产物)id item=手持可直接放到杯垫的物品 displayModels=成形展示模型 多个则随机
/**
 * Tea cup display definition.
 *
 * @param tea result tea id
 * @param item held item id that can place this tea on a coaster
 * @param displayModels possible display model item ids
 */
public record TeaCup(Key tea, Key item, List<Key> displayModels) {
}
