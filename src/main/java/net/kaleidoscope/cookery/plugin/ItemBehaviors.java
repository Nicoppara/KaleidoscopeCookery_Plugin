package net.kaleidoscope.cookery.plugin;

import net.momirealms.craftengine.core.item.behavior.ItemBehaviorType;
import net.momirealms.craftengine.core.util.Key;
import net.kaleidoscope.cookery.item.behavior.DoughPullingBehavior;
import net.kaleidoscope.cookery.item.behavior.TeapotItemBehavior;

// 物品行为注册
public final class ItemBehaviors {
    public static ItemBehaviorType<DoughPullingBehavior> DOUGH_PULLING;
    public static ItemBehaviorType<TeapotItemBehavior> TEAPOT;

    private ItemBehaviors() {}

    public static void register() {
        if (DOUGH_PULLING == null) {
            DOUGH_PULLING = RegistryUtils.registerItemBehavior(
                    Key.of("kaleidoscopecookery:dough_pulling"),
                    DoughPullingBehavior.FACTORY
            );
        }
        if (TEAPOT == null) {
            TEAPOT = RegistryUtils.registerItemBehavior(
                    Key.of("kaleidoscopecookery:teapot_item"),
                    TeapotItemBehavior.FACTORY
            );
        }
    }
}