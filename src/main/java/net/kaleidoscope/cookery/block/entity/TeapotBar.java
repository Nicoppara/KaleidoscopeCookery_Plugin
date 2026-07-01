package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.TeapotLiquid;
import net.momirealms.craftengine.core.util.Key;

public final class TeapotBar {
    private TeapotBar() {}

    public static final String ITEM_DATA_KEY = "kaleidoscopecookery:teapot_data";
    public static final int CELLS = 8;

    public static String build(Key fluid) {
        return build(fluid, CELLS);
    }

    public static String build(Key fluid, int filled) {
        FoodRecipeRegistry reg = FoodRecipeRegistry.instance();
        TeapotLiquid liquid = fluid == null ? null : reg.getTeapotLiquid(fluid);
        if (liquid == null) {
            TeapotLiquid def = reg.defaultTeapotLiquid();
            if (def == null) {
                return "";
            }
            return bar(def.barLeft(), def.barEmpty(), def.barEmpty(), def.barRight(), 0);
        }
        int f = Math.max(0, Math.min(CELLS, filled));
        return bar(liquid.barLeft(), liquid.barFull(), liquid.barEmpty(), liquid.barRight(), f);
    }

    private static String bar(String left, String fullGlyph, String emptyGlyph, String right, int filled) {
        StringBuilder sb = new StringBuilder("<white><image:").append(left).append(">");
        for (int i = 0; i < CELLS; i++) {
            sb.append("<shift:-1><image:").append(i < filled ? fullGlyph : emptyGlyph).append(">");
        }
        sb.append("<shift:-1><image:").append(right).append(">");
        return sb.toString();
    }
}
