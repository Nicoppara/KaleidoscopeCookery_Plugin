package net.kaleidoscope.cookery.recipe;

import net.kaleidoscope.cookery.recipe.edit.RecipeFileStore;
import net.kaleidoscope.cookery.recipe.edit.RecipeSourceIndex;
import net.momirealms.craftengine.core.util.Key;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecipeSourceOrderTest {
    @Test void fileOrderIsRestoredWithoutMovingOtherFileOrApiRegistrationSlots() {
        FoodRecipeRegistry registry = FoodRecipeRegistry.instance();
        RecipeSourceIndex sources = RecipeSourceIndex.instance();
        registry.atomicUpdate(registry::clearAccurate);
        AccurateFoodRecipe second = recipe("second", "minecraft:potato", 2);
        AccurateFoodRecipe otherFile = recipe("other", "minecraft:carrot", 3);
        AccurateFoodRecipe api = recipe("api", "minecraft:apple", 4);
        AccurateFoodRecipe first = recipe("first", "minecraft:potato", 1);
        try {
            source(sources, second, "one.yml", 2);
            source(sources, otherFile, "two.yml", 0);
            source(sources, first, "one.yml", 1);
            registry.atomicUpdate(() -> {
                for (var recipe : List.of(second, otherFile, api, first)) {
                    registry.registerAccurate(recipe);
                    registry.registerMenuAccurate(recipe);
                }
            });
            List<AccurateFoodRecipe> expected = List.of(first, otherFile, api, second);
            assertEquals(expected, registry.accurateRecipes(ApplianceType.STEAMER));
            assertEquals(expected, registry.menuAccurateRecipes(ApplianceType.STEAMER));
            assertSame(first, registry.findAccurateRecipe(ApplianceType.STEAMER, Key.of("minecraft:potato")));
            assertEquals(1, registry.planAccurate(ApplianceType.STEAMER, Key.of("minecraft:potato"), 999).workRequired());
        } finally {
            registry.atomicUpdate(registry::clearAccurate);
            for (var recipe : List.of(second, otherFile, api, first)) sources.remove(recipe);
        }
    }

    private AccurateFoodRecipe recipe(String id, String input, int work) {
        return new AccurateFoodRecipe(Key.of("source_order:" + id), Key.of(input),
                List.of(new WeightedResult(Key.of("minecraft:bread"), 100)), ApplianceType.STEAMER,
                0, 1, List.of(), work);
    }

    private void source(RecipeSourceIndex sources, AccurateFoodRecipe recipe, String file, int ordinal) {
        sources.put(RecipeSourceIndex.Kind.ACCURATE, recipe.id(), Path.of(file),
                RecipeFileStore.SourceTarget.direct("accurate_foods." + recipe.id()).withOrdinal(ordinal),
                recipe, false);
    }
}
