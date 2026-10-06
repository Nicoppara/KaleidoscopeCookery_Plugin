package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ProcessingPlanTest {
    private static final Key INPUT = Key.of("minecraft:potato");
    private static final Key RESULT = Key.of("minecraft:baked_potato");

    @Test void outputSelectionAndWorkSurviveRoundTripWithoutRegistry() {
        CookingPlan plan = new CookingPlan(true, true, Key.of("test:stored"), 23, 2, Key.of("minecraft:bowl"),
                List.of(new CookingPlan.Output(RESULT, 3, List.of("<green>Test"), DishQuality.EXCELLENT)), List.of("test:model/0"));
        assertEquals(plan, CookingPlan.load(plan.save()));
        assertEquals(7, plan.withWorkRequired(7).workRequired());
        assertEquals(plan.outputs(), plan.withWorkRequired(7).outputs());
    }

    @Test void missingMalformedAndFutureFormatsBlockRatherThanReroll() {
        assertFalse(CookingPlan.load(new CompoundTag()).valid());
        CompoundTag future = CookingPlan.unmatched(12).save();
        future.putInt("version", 999);
        assertFalse(CookingPlan.load(future).valid());
        CompoundTag zero = CookingPlan.unmatched(12).save();
        zero.putInt("work", 0);
        assertFalse(CookingPlan.load(zero).valid());
    }

    @Test void processingIntegersRejectFractionsOverflowZeroAndConflictingAliases() {
        assertEquals(0, field(Map.of()));
        assertEquals(600, field(Map.of("cooking_time", 600, "cooking-time", "600")));
        for (Object bad : List.of(0, -1, 1.5, 1.0, "0.5", "2147483648")) {
            assertThrows(IllegalArgumentException.class, () -> field(Map.of("cooking_time", bad)));
        }
        assertThrows(IllegalArgumentException.class, () -> field(Map.of("cooking_time", 20, "cooking-time", 21)));
    }

    private int field(Map<String, Object> values) {
        return RecipeProcessingFields.optionalPositive(ConfigSection.of("test:recipe", values), "cooking_time", "cooking-time");
    }

    @Test void legacyConstructorsAndFactoryMethodsKeepInheritance() {
        AccurateFoodRecipe accurate = new AccurateFoodRecipe(Key.of("test:old"), INPUT,
                List.of(new WeightedResult(RESULT, 100)), ApplianceType.STEAMER, 0, 1, List.of());
        assertEquals(0, accurate.cookingTime());
        FlexFoodRecipe old = FlexFoodRecipe.of(Key.of("test:flex"), RESULT, ApplianceType.POT,
                Map.of(INPUT, 1), List.of(), null);
        assertEquals(0, old.cookingTime()); assertEquals(0, old.stirFryCount());
        FlexFoodRecipe configured = FlexFoodRecipe.of(old.id(), RESULT, ApplianceType.POT, old.perfect(), List.of(), null,
                true, true, 0, 8);
        assertEquals(8, configured.withToggles(false, false).stirFryCount());
    }
}
