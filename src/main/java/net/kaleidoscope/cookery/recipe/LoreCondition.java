package net.kaleidoscope.cookery.recipe;

import java.util.List;

// Lore 规则 when 中条件全满足时追加 data 各行
// unpreferred 为 null 时 按 when 是否含配方 unpreferred 食材自动判定底味归属
public record LoreCondition(List<ItemRequirement> when, Boolean unpreferred, List<String> data) {}
