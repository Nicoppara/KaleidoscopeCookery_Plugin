package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Key;

// 配方查询结果 已套用名称/lore 的成品 + 份数 调用方需 item.count(count) 后再给予玩家
// carrier 为盛装容器 null 表示空手就能取
public record FoodRecipeResult(Item item, int count, Key carrier) {}
