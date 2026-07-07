package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.item.Item;

// 配方查询结果 已套用名称/lore 的成品 + 份数 调用方需 item.count(count) 后再给予玩家
/**
 * Rolled recipe result.
 *
 * @param item result item with display data already applied
 * @param count number of items to create
 */
public record FoodRecipeResult(Item item, int count) {}
