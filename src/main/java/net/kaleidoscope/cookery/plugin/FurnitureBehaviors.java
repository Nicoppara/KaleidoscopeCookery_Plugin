package net.kaleidoscope.cookery.plugin;

import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureBehaviorType;
import net.momirealms.craftengine.core.util.Key;
import net.kaleidoscope.cookery.block.behavior.MillstoneBehavior;
import net.kaleidoscope.cookery.block.behavior.RecipeDisplayBehavior;
import net.kaleidoscope.cookery.block.behavior.TrashCanBehavior;

// 家具行为注册
public final class FurnitureBehaviors {
    public static FurnitureBehaviorType<MillstoneBehavior> MILLSTONE;
    public static FurnitureBehaviorType<RecipeDisplayBehavior> RECIPE_FURNITURE;
    public static FurnitureBehaviorType<TrashCanBehavior> TRASH_CAN;

    private FurnitureBehaviors() {}

    public static void register() {
        if (RECIPE_FURNITURE == null) {
            RECIPE_FURNITURE = RegistryUtils.registerFurnitureBehavior(
                    Key.of("kaleidoscopecookery:recipe_furniture"),
                    RecipeDisplayBehavior.FACTORY
            );
        }
        if (MILLSTONE == null) {
            MILLSTONE = RegistryUtils.registerFurnitureBehavior(
                    Key.of("kaleidoscopecookery:millstone"),
                    MillstoneBehavior.FACTORY
            );
        }
        if (TRASH_CAN == null) {
            TRASH_CAN = RegistryUtils.registerFurnitureBehavior(
                    Key.of("kaleidoscopecookery:trashcan"),
                    TrashCanBehavior.FACTORY
            );
        }
    }
}
