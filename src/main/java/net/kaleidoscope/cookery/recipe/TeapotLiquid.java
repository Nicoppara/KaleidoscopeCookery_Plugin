package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

// 茶壶液体 液体类型 + 中文名 + 液体条字形(左 右 空格 满格) 只有注册过的液体能进茶配方
/**
 * Teapot liquid definition.
 *
 * @param fluid fluid id
 * @param displayName display name used by the plugin UI
 * @param barLeft left glyph for the teapot liquid bar
 * @param barRight right glyph for the teapot liquid bar
 * @param barEmpty empty glyph for the teapot liquid bar
 * @param barFull full glyph for the teapot liquid bar
 */
public record TeapotLiquid(
        Key fluid,
        String displayName,
        String barLeft,
        String barRight,
        String barEmpty,
        String barFull
) {
}
