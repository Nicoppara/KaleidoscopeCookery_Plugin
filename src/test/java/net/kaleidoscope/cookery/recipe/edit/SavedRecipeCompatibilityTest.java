package net.kaleidoscope.cookery.recipe.edit;

import net.kaleidoscope.cookery.recipe.*;
import net.momirealms.craftengine.core.util.Key;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SavedRecipeCompatibilityTest {
    @Test void timingOnlySaveRetainsHyphenatedMatchingToggles() throws Exception {
        Key id = Key.of("save_compat:soup"), input = Key.of("minecraft:potato"), output = Key.of("minecraft:bread");
        FlexFoodRecipe original = FlexFoodRecipe.of(id, output, ApplianceType.STOCKPOT,
                Map.of(input, 1), List.of(), null, false, false);
        FlexFoodRecipe saved = (FlexFoodRecipe) reconstruct(original, Map.of(
                "result", output.asString(), "perfect", Map.of(input.asString(), 1),
                "use-equivalent-foods", false, "use-seasonings", false, "cooking_time", 23));
        assertFalse(saved.useEquivalentFoods());
        assertFalse(saved.useSeasonings());
        assertEquals(23, saved.cookingTime());
    }

    @Test void unrelatedTimeAttributeIsNotAdoptedAsAnAccurateCookingTime() throws Exception {
        Key id = Key.of("save_compat:accurate"), input = Key.of("minecraft:potato"), output = Key.of("minecraft:bread");
        AccurateFoodRecipe original = new AccurateFoodRecipe(id, input,
                List.of(new WeightedResult(output, 100)), ApplianceType.STEAMER, 0, 1, List.of());
        AccurateFoodRecipe saved = (AccurateFoodRecipe) reconstruct(original, Map.of(
                "require", input.asString(), "result", output.asString(), "time", 50, "result-count", -1));
        assertEquals(0, saved.cookingTime());
        assertEquals(1, saved.resultCount());
    }

    private Object reconstruct(Object recipe, Map<String, Object> node) throws Exception {
        var method = RecipeEditService.class.getDeclaredMethod("savedRecipe", Object.class, Map.class);
        method.setAccessible(true);
        return method.invoke(null, recipe, node);
    }
}
