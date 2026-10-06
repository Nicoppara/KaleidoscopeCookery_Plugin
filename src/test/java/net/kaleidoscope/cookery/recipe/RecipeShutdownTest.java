package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class RecipeShutdownTest {
    @Test void shutdownRejectsLatePublicationAndLoaderCallbacks() throws Exception {
        var constructor = FoodRecipeRegistry.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        FoodRecipeRegistry registry = constructor.newInstance();
        Key input = Key.of("minecraft:potato");
        AccurateFoodRecipe recipe = new AccurateFoodRecipe(Key.of("shutdown:test"), input,
                List.of(new WeightedResult(Key.of("minecraft:bread"), 100)), ApplianceType.STEAMER,
                0, 1, List.of(), 23);
        registry.registerAccurate(recipe);
        long before = registry.generation();
        registry.close();
        AtomicBoolean callback = new AtomicBoolean();
        registry.beginConfigurationLoad();
        registry.configurationUpdate(() -> callback.set(true));
        registry.afterConfigurationLoad(() -> callback.set(true));
        registry.finishConfigurationLoad();
        assertThrows(IllegalStateException.class, () -> registry.removeAccurate(recipe.id()));
        assertFalse(callback.get());
        assertEquals(before, registry.generation());
        assertSame(recipe, registry.findAccurateRecipe(ApplianceType.STEAMER, input));
    }
}
