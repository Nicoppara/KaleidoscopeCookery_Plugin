package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

// 茶壶配方 液体类型+原料 共同决定 产出 result 数量 resultCount 耗时 time tick 消耗原料 ingredientCount 个
public record TeapotRecipe(
        Key id,
        Key fluid,
        Key input,
        int ingredientCount,
        Key result,
        int resultCount,
        int time
) {
}
