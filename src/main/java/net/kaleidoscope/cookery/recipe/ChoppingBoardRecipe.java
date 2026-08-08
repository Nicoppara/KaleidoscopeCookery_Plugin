package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

import java.util.List;

// 砧板配方 input 原料 stage 切的刀数 values 各阶段模型 放下用 values[0] 每切一刀加一
// mode 产出模式 results 主产物 extras 附带产物 仅 SINGLE_EXTRA 用到
public record ChoppingBoardRecipe(
        Key id,
        Key input,
        int stage,
        List<String> values,
        ChoppingMode mode,
        List<ChoppingResult> results,
        List<ChoppingResult> extras
) {}
