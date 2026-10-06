package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DishCarrierCompatibilityTest {
    @Test void legacyRebuildUsesItsArgumentAndConfigurationStillWins() {
        FoodRecipeRegistry registry = FoodRecipeRegistry.instance();
        Key result = Key.of("carrier_compat:dish");
        Key bowl = Key.of("minecraft:bowl");
        Key bucket = Key.of("minecraft:bucket");
        FlexFoodRecipe supplied = FlexFoodRecipe.of(Key.of("carrier_compat:recipe"), result,
                ApplianceType.POT, Map.of(Key.of("minecraft:potato"), 1), List.of(), bowl);
        try {
            DishCarriers.rebuild(List.of(supplied));
            assertEquals(bowl, DishCarriers.of(result));
            registry.registerConfiguredCarrier(result, bucket);
            assertEquals(bucket, DishCarriers.of(result));
            DishCarriers.rebuild(List.of());
            assertEquals(bucket, DishCarriers.of(result));
            registry.clearConfiguredCarriers();
            assertNull(DishCarriers.of(result));
        } finally {
            registry.clearConfiguredCarriers();
            DishCarriers.rebuild(java.util.stream.Stream.concat(registry.flexRecipes(ApplianceType.POT).stream(),
                    registry.flexRecipes(ApplianceType.STOCKPOT).stream()).toList());
        }
    }
}
