package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

import java.util.List;

// 砧板配方
// input 原料 stage 切的次数 values 各阶段展示模型 放下 values[0] 每切一刀阶段加一
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
