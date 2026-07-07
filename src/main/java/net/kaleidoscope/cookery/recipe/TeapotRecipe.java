package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

// 茶壶配方 液体类型+原料 共同决定 产出 result 数量 resultCount 耗时 time tick 消耗原料 ingredientCount 个
/**
 * Teapot recipe matched by liquid and input item.
 *
 * @param id recipe id
 * @param fluid required liquid id
 * @param input input item id
 * @param ingredientCount number of input items consumed
 * @param result result tea id
 * @param resultCount number of result items produced
 * @param time brewing time in ticks
 */
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
