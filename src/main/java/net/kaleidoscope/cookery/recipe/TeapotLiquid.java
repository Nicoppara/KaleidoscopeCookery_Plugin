package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

// 茶壶液体 液体类型 + 中文名 + 液体条字形(左 右 空格 满格) 只有注册过的液体能进茶配方
public record TeapotLiquid(
        Key fluid,
        String displayName,
        String barLeft,
        String barRight,
        String barEmpty,
        String barFull
) {
}
