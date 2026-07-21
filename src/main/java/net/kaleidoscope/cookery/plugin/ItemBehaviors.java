package net.kaleidoscope.cookery.plugin;

import net.momirealms.craftengine.core.item.behavior.ItemBehaviorType;
import net.momirealms.craftengine.core.util.Key;
import net.kaleidoscope.cookery.item.behavior.SickleRangeHarvestBehavior;
import net.kaleidoscope.cookery.item.behavior.TeapotItemBehavior;
import net.kaleidoscope.cookery.item.behavior.TransmutationLunchBagBehavior;

// 物品行为注册
public final class ItemBehaviors {
    public static ItemBehaviorType<SickleRangeHarvestBehavior> SICKLE_RANGE_HARVEST;
    public static ItemBehaviorType<TeapotItemBehavior> TEAPOT;
    public static ItemBehaviorType<TransmutationLunchBagBehavior> TRANSMUTATION_LUNCH_BAG;

    private ItemBehaviors() {}

    public static void register() {
        if (SICKLE_RANGE_HARVEST == null) {
            SICKLE_RANGE_HARVEST = RegistryUtils.registerItemBehavior(
                    Key.of("kaleidoscopecookery:sickle_range_harvest"),
                    SickleRangeHarvestBehavior.FACTORY
            );
        }
        if (SICKLE_RANGE_HARVEST == null) {
            SICKLE_RANGE_HARVEST = RegistryUtils.registerItemBehavior(
                    Key.of("kaleidoscopecookery:sickle_range_harvest"),
                    SickleRangeHarvestBehavior.FACTORY
            );
        }
        if (TEAPOT == null) {
            TEAPOT = RegistryUtils.registerItemBehavior(
                    Key.of("kaleidoscopecookery:teapot_item"),
                    TeapotItemBehavior.FACTORY
            );
        }
        if (TRANSMUTATION_LUNCH_BAG == null) {
            TRANSMUTATION_LUNCH_BAG = RegistryUtils.registerItemBehavior(
                    Key.of("kaleidoscopecookery:transmutation_lunch_bag"),
                    TransmutationLunchBagBehavior.FACTORY
            );
        }
    }
}
