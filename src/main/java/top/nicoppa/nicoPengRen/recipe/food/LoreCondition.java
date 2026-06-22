package top.nicoppa.nicoPengRen.recipe.food;

import java.util.List;

/**
 * Lore 规则：when 里的全部条件（某物品达到数量）都满足时，追加 data lore 各行
 * unpreferred 为 null 时，按 when 中是否含配方 unpreferred 食材自动判定底味归属
 */
public record LoreCondition(List<ItemRequirement> when, Boolean unpreferred, List<String> data) {}
