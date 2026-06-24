package net.kaleidoscope.cookery.recipe.food;

import net.momirealms.craftengine.core.util.Key;

import java.util.List;

// 砧板配方：input 原料、stage 切的次数、values 各阶段展示模型(放下 values[0]，每切一刀阶段+1)、results 产物
public record ChoppingBoardRecipe(
        Key id,
        Key input,
        int stage,
        List<String> values,
        List<ChoppingResult> results
) {}
