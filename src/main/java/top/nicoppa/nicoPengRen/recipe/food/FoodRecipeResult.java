package top.nicoppa.nicoPengRen.recipe.food;

import net.momirealms.craftengine.core.item.Item;

/**
 * 配方查询结果 已应用名称/lore 的成品物品 + 数量倍数
 * 调用方需执行 item.count(count) 后再给予玩家
 */
public record FoodRecipeResult(Item item, int count) {}