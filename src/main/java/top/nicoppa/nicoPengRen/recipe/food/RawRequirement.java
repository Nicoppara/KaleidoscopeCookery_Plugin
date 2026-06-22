package top.nicoppa.nicoPengRen.recipe.food;

/**
 * 原料类别要求：某类别至少需要 min 个
 * min == 0 表示不参与匹配判断，也不参与倍数计算
 */
public record RawRequirement(String category, int min) {}