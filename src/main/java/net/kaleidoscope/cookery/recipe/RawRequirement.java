package net.kaleidoscope.cookery.recipe;

// 原料类别要求 某类别至少需要 min 个 min == 0 表示不参与匹配与倍数计算
/**
 * Required amount of a food category.
 *
 * @param category food category name
 * @param min minimum required count; {@code 0} means it does not affect matching
 */
public record RawRequirement(String category, int min) {}
