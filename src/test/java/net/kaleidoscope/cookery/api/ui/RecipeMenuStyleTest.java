package net.kaleidoscope.cookery.api.ui;

import net.momirealms.craftengine.core.util.Key;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecipeMenuStyleTest {
    private final RecipeMenuStyle style = RecipeMenuStyle.instance();

    @AfterEach
    void resetStyle() {
        style.reset();
    }

    @Test
    void resolvesFluidAndBucketIdsForCustomMenuProviders() {
        assertEquals(Key.of("minecraft:water_bucket"), style.liquidIcon(Key.of("minecraft:water")));
        assertEquals(Key.of("minecraft:lava_bucket"), style.liquidIcon(Key.of("minecraft:lava")));
        assertEquals(Key.of("minecraft:lava_bucket"), style.liquidIcon(Key.of("minecraft:lava_bucket")));
    }

    @Test
    void unsetLiquidUsesConfiguredGenericIcon() {
        Key custom = Key.of("example:empty_liquid_slot");
        style.icon(MenuButton.LIQUID, custom);

        assertEquals(custom, style.liquidIcon(null));
    }
}
