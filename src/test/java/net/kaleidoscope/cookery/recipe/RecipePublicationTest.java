package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;
import net.kaleidoscope.cookery.recipe.edit.RecipeSourceIndex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.momirealms.craftengine.core.plugin.config.ResourceException;
import net.momirealms.craftengine.core.plugin.config.UnknownResourceException;
import static org.junit.jupiter.api.Assertions.*;

class RecipePublicationTest {
    private static final Key ID = Key.of("test:publication"), INPUT = Key.of("minecraft:potato"), RESULT = Key.of("minecraft:baked_potato");
    private final FoodRecipeRegistry registry = FoodRecipeRegistry.instance();

    @BeforeEach @AfterEach void clear() {
        registry.finishConfigurationLoad();
        registry.atomicUpdate(() -> { registry.clearAccurate(); ApplianceFoodRegistry.instance().clear(ApplianceType.STEAMER); });
        RecipeSourceIndex.instance().clear();
    }

    private AccurateFoodRecipe recipe(int value) {
        return new AccurateFoodRecipe(ID, INPUT, List.of(new WeightedResult(RESULT, 100)), ApplianceType.STEAMER,
                0, value, List.of(), value);
    }

    private void install(int value) {
        registry.atomicUpdate(() -> {
            registry.removeAccurate(ID);
            registry.registerAccurate(recipe(value));
            ApplianceFoodRegistry.instance().register(ApplianceType.STEAMER, INPUT);
        });
    }

    @Test void loaderKeepsPreviousGenerationUntilFinalPublicationAndReplaysEdits() {
        install(10);
        registry.beginConfigurationLoad();
        registry.configurationUpdate(() -> { registry.clearAccurate(); registry.registerAccurate(recipe(20)); });
        assertEquals(10, registry.findAccurateRecipe(ApplianceType.STEAMER, INPUT).cookingTime());
        install(30);
        assertEquals(30, registry.planAccurate(ApplianceType.STEAMER, INPUT, 200).workRequired());
        registry.finishConfigurationLoad();
        assertEquals(30, registry.findAccurateRecipe(ApplianceType.STEAMER, INPUT).cookingTime());
    }

    @Test void failedLoadDoesNotPublishPartialRecipes() {
        install(10);
        registry.beginConfigurationLoad();
        assertThrows(IllegalArgumentException.class, () -> registry.configurationUpdate(() -> {
            registry.clearAccurate(); throw new IllegalArgumentException("invalid processing field");
        }));
        registry.finishConfigurationLoad();
        assertEquals(10, registry.findAccurateRecipe(ApplianceType.STEAMER, INPUT).cookingTime());
    }

    @Test void planRemainsIndependentAfterItsRecipeIsDeleted() {
        install(23);
        CookingPlan old = registry.planAccurate(ApplianceType.STEAMER, INPUT, 200);
        registry.removeAccurate(ID);
        assertTrue(old.matched()); assertEquals(23, old.workRequired());
        assertEquals(old, CookingPlan.load(old.save()));
        assertFalse(registry.planAccurate(ApplianceType.STEAMER, INPUT, 200).matched());
    }

    @Test void abortedLoadRestoresPublishedRecipesAndSourceObjectsThenReleasesCallbacks() {
        install(10);
        RecipeSourceIndex index = RecipeSourceIndex.instance();
        AccurateFoodRecipe old = registry.findAccurateRecipe(ApplianceType.STEAMER, INPUT);
        Path oldPath = Path.of("test-pack", "old-recipes.yml").toAbsolutePath().normalize();
        index.put(RecipeSourceIndex.Kind.ACCURATE, ID, oldPath, "accurate_foods.test:publication", old, true);
        var oldTarget = index.target(old);
        registry.beginConfigurationLoad();
        long token = registry.configurationLoadToken();
        AccurateFoodRecipe partial = recipe(20);
        registry.configurationUpdate(() -> {
            registry.clearAccurate();
            registry.registerAccurate(partial);
            index.clearKind(RecipeSourceIndex.Kind.ACCURATE);
            index.put(RecipeSourceIndex.Kind.ACCURATE, ID, Path.of("test-pack", "new-recipes.yml"),
                    "accurate_foods.test:publication", partial, false);
        });
        AtomicInteger resumed = new AtomicInteger();
        registry.afterConfigurationLoad(resumed::incrementAndGet);
        assertTrue(registry.hasConfigurationLoad(token));
        assertSame(old, registry.findAccurateRecipe(ApplianceType.STEAMER, INPUT));
        assertEquals(oldPath, index.get(old));
        registry.abortConfigurationLoad(token);
        assertFalse(registry.hasConfigurationLoad(token));
        assertEquals(1, resumed.get());
        assertSame(old, registry.findAccurateRecipe(ApplianceType.STEAMER, INPUT));
        assertEquals(oldPath, index.get(old));
        assertEquals(oldTarget, index.target(old));
        assertTrue(index.isDuplicate(old));
        assertNull(index.get(partial));
        registry.afterConfigurationLoad(resumed::incrementAndGet);
        assertEquals(2, resumed.get(), "new callbacks must run immediately after rollback");
    }

    @Test void staleRecoveryTokenCannotAbortTheNextLoadOrRunItsCallbacks() {
        install(10);
        registry.beginConfigurationLoad();
        long stale = registry.configurationLoadToken();
        registry.abortConfigurationLoad(stale);
        registry.beginConfigurationLoad();
        long current = registry.configurationLoadToken();
        assertNotEquals(stale, current);
        registry.configurationUpdate(() -> { registry.clearAccurate(); registry.registerAccurate(recipe(30)); });
        AtomicBoolean resumed = new AtomicBoolean();
        registry.afterConfigurationLoad(() -> resumed.set(true));
        long generation = registry.generation();
        registry.abortConfigurationLoad(stale);
        assertTrue(registry.hasConfigurationLoad(current));
        assertFalse(resumed.get());
        assertEquals(generation, registry.generation());
        assertEquals(10, registry.findAccurateRecipe(ApplianceType.STEAMER, INPUT).cookingTime());
        registry.finishConfigurationLoad();
        assertFalse(registry.hasConfigurationLoad(current));
        assertTrue(resumed.get());
        assertEquals(30, registry.findAccurateRecipe(ApplianceType.STEAMER, INPUT).cookingTime());
    }

    @Test void secondBeginAfterSkippedEndStillRestoresTheFirstCompleteSourceBackup() {
        install(10);
        RecipeSourceIndex index = RecipeSourceIndex.instance();
        AccurateFoodRecipe old = registry.findAccurateRecipe(ApplianceType.STEAMER, INPUT);
        Path oldPath = Path.of("test-pack", "complete.yml").toAbsolutePath().normalize();
        index.put(RecipeSourceIndex.Kind.ACCURATE, ID, oldPath, "accurate_foods.test:publication", old, false);
        registry.beginConfigurationLoad();
        long abandoned = registry.configurationLoadToken();
        AccurateFoodRecipe partial = recipe(20);
        registry.configurationUpdate(() -> {
            registry.clearAccurate(); registry.registerAccurate(partial);
            index.clearKind(RecipeSourceIndex.Kind.ACCURATE);
            index.put(RecipeSourceIndex.Kind.ACCURATE, ID, Path.of("test-pack", "partial.yml"),
                    "accurate_foods.test:publication", partial, false);
        });
        AtomicInteger resumed = new AtomicInteger();
        registry.afterConfigurationLoad(resumed::incrementAndGet);
        // A new CE reload can start before the watchdog for a failed load gets its lease.
        registry.beginConfigurationLoad();
        long current = registry.configurationLoadToken();
        assertNotEquals(abandoned, current);
        registry.abortConfigurationLoad(abandoned);
        assertTrue(registry.hasConfigurationLoad(current));
        registry.abortConfigurationLoad(current);
        assertEquals(1, resumed.get());
        assertSame(old, registry.findAccurateRecipe(ApplianceType.STEAMER, INPUT));
        assertEquals(oldPath, index.get(old));
        assertNotNull(index.target(old));
        assertNull(index.get(partial));
    }

    @Test void abortClearsStagingAndRunsEveryQueuedCallbackEvenIfOneThrows() {
        install(10);
        registry.beginConfigurationLoad();
        long token = registry.configurationLoadToken();
        AtomicInteger callbacks = new AtomicInteger();
        registry.afterConfigurationLoad(() -> { callbacks.incrementAndGet(); throw new IllegalStateException("callback failure"); });
        registry.afterConfigurationLoad(callbacks::incrementAndGet);
        assertThrows(IllegalStateException.class, () -> registry.abortConfigurationLoad(token));
        assertFalse(registry.hasConfigurationLoad(token));
        assertEquals(2, callbacks.get());
        assertEquals(10, registry.findAccurateRecipe(ApplianceType.STEAMER, INPUT).cookingTime());
        registry.afterConfigurationLoad(callbacks::incrementAndGet);
        assertEquals(3, callbacks.get());
    }

    @Test void publicationFreezeFailureRestoresSourcesAndStillReleasesQueuedWork() {
        install(10);
        RecipeSourceIndex index = RecipeSourceIndex.instance();
        AccurateFoodRecipe old = registry.findAccurateRecipe(ApplianceType.STEAMER, INPUT);
        Path oldPath = Path.of("test-pack", "complete.yml").toAbsolutePath().normalize();
        index.put(RecipeSourceIndex.Kind.ACCURATE, ID, oldPath, "accurate_foods.test:publication", old, false);
        registry.beginConfigurationLoad();
        long token = registry.configurationLoadToken();
        AccurateFoodRecipe partial = recipe(20);
        registry.configurationUpdate(() -> {
            registry.clearAccurate(); registry.registerAccurate(partial);
            registry.registerMenuAccurate(null); // Builder accepts it; immutable publication must fail.
            index.clearKind(RecipeSourceIndex.Kind.ACCURATE);
            index.put(RecipeSourceIndex.Kind.ACCURATE, ID, Path.of("test-pack", "partial.yml"),
                    "accurate_foods.test:publication", partial, false);
        });
        AtomicInteger callbacks = new AtomicInteger();
        registry.afterConfigurationLoad(callbacks::incrementAndGet);
        assertThrows(NullPointerException.class, registry::finishConfigurationLoad);
        assertFalse(registry.hasConfigurationLoad(token));
        assertEquals(1, callbacks.get());
        assertSame(old, registry.findAccurateRecipe(ApplianceType.STEAMER, INPUT));
        assertEquals(oldPath, index.get(old));
        assertNotNull(index.target(old));
        assertNull(index.get(partial));
        registry.afterConfigurationLoad(callbacks::incrementAndGet);
        assertEquals(2, callbacks.get());
    }

    @Test void ceReportedErrorMarksWholeLoadFailedAndForwardsTheOriginalDiagnosticExactlyOnce() {
        install(10);
        registry.beginConfigurationLoad();
        registry.configurationUpdate(() -> { registry.clearAccurate(); registry.registerAccurate(recipe(20)); });
        ResourceException failure = new UnknownResourceException(Path.of("test-pack", "recipe.yml"),
                "accurate_foods.test:publication", new IllegalArgumentException("template failed before recipe parser"));
        AtomicReference<ResourceException> forwarded = new AtomicReference<>();
        AtomicInteger reports = new AtomicInteger();
        FoodRecipeManager.trackRecipeLoadErrors(error -> { forwarded.set(error); reports.incrementAndGet(); }).accept(failure);
        assertSame(failure, forwarded.get());
        assertEquals(1, reports.get());
        registry.finishConfigurationLoad();
        assertEquals(10, registry.findAccurateRecipe(ApplianceType.STEAMER, INPUT).cookingTime());
    }

    @Test void concurrentReadersNeverObserveRemoveRegisterHalfState() throws Exception {
        install(1);
        AtomicBoolean running = new AtomicBoolean(true);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> reader = pool.submit(() -> {
            while (running.get()) {
                CookingPlan plan = registry.planAccurate(ApplianceType.STEAMER, INPUT, 999);
                assertTrue(plan.matched());
                assertEquals(plan.workRequired(), plan.outputs().getFirst().count());
            }
        });
        try { for (int i = 2; i < 202; i++) install(i); }
        finally { running.set(false); }
        reader.get(10, TimeUnit.SECONDS);
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }
}
