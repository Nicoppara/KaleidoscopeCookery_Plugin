package net.kaleidoscope.cookery.recipe;

import net.kaleidoscope.cookery.api.ItemTags;
import net.momirealms.craftengine.core.util.Key;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Operation-level generation coherence and index compatibility, independent of live Bukkit items. */
class RecipeReadViewTest {
    private static final Key ID = Key.of("readview:accurate");
    private static final Key INPUT = Key.of("minecraft:potato");
    private static final Key BEEF = Key.of("minecraft:beef");
    private static final Key PORK = Key.of("minecraft:porkchop");
    private static final Key SUGAR = Key.of("minecraft:sugar");
    private static final Key RESULT = Key.of("minecraft:bread");
    private static final Key MEAT = Key.of("readview:meat");
    private static final Key SEASONING = Key.of("readview:seasoning");
    private final FoodRecipeRegistry registry = FoodRecipeRegistry.instance();
    private final List<Key> tags = new ArrayList<>();
    private List<Key> previousEquivalents;
    private List<Key> previousSeasonings;

    @BeforeEach void before() {
        registry.finishConfigurationLoad();
        previousEquivalents = FoodGroups.instance().equivalentTags();
        previousSeasonings = FoodGroups.instance().seasoningTags();
        FoodGroups.instance().equivalentTags(List.of());
        FoodGroups.instance().seasoningTags(List.of());
        registry.atomicUpdate(() -> {
            registry.clearAccurate(); registry.clearFlex(ApplianceType.POT);
            ApplianceFoodRegistry.instance().clear(ApplianceType.STEAMER);
            ApplianceFoodRegistry.instance().clear(ApplianceType.POT);
        });
    }

    @AfterEach void after() {
        registry.finishConfigurationLoad();
        FoodGroups.instance().equivalentTags(previousEquivalents);
        FoodGroups.instance().seasoningTags(previousSeasonings);
        registry.atomicUpdate(() -> {
            for (Key tag : tags) ItemTags.instance().remove(tag);
            registry.clearAccurate(); registry.clearFlex(ApplianceType.POT);
            ApplianceFoodRegistry.instance().clear(ApplianceType.STEAMER);
            ApplianceFoodRegistry.instance().clear(ApplianceType.POT);
        });
    }

    @Test void whitelistAndPlanStayPinnedWhileAnotherThreadPublishesAnEdit() throws Exception {
        install(23, true);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch whitelistRead = new CountDownLatch(1);
        CountDownLatch edited = new CountDownLatch(1);
        Future<CookingPlan> reader = pool.submit(() -> registry.readSnapshot(() -> {
            assertTrue(ApplianceFoodRegistry.instance().isAllowed(ApplianceType.STEAMER, INPUT));
            whitelistRead.countDown(); await(edited);
            return registry.planAccurate(ApplianceType.STEAMER, INPUT, 999);
        }));
        try {
            assertTrue(whitelistRead.await(5, TimeUnit.SECONDS));
            install(97, false);
            edited.countDown();
            CookingPlan pinned = reader.get(5, TimeUnit.SECONDS);
            assertEquals(23, pinned.workRequired());
            assertEquals(23, pinned.outputs().getFirst().count());
            assertFalse(ApplianceFoodRegistry.instance().isAllowed(ApplianceType.STEAMER, INPUT));
            assertEquals(97, registry.planAccurate(ApplianceType.STEAMER, INPUT, 999).workRequired());
        } finally {
            edited.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void nestedSnapshotPreservesOuterViewAndExceptionDoesNotLeakItIntoNextOperation() throws Exception {
        install(13, true);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch edited = new CountDownLatch(1);
        Future<?> reader = pool.submit(() -> assertThrows(IllegalStateException.class, () -> registry.readSnapshot(() -> {
            entered.countDown(); await(edited);
            assertEquals(13, registry.readSnapshot(() -> registry.planAccurate(ApplianceType.STEAMER, INPUT, 999)).workRequired());
            throw new IllegalStateException("abort simulated input operation");
        })));
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); install(31, true); edited.countDown();
            reader.get(5, TimeUnit.SECONDS);
            assertEquals(31, pool.submit(() -> registry.readSnapshot(() ->
                    registry.planAccurate(ApplianceType.STEAMER, INPUT, 999).workRequired())).get(5, TimeUnit.SECONDS));
        } finally {
            edited.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void failedLoadDoesNotReplayAlreadyPublishedAppendEditsTwice() {
        install(7, true);
        registry.beginConfigurationLoad();
        assertThrows(IllegalArgumentException.class, () -> registry.configurationUpdate(() -> {
            registry.clearAccurate(); throw new IllegalArgumentException("invalid new configuration");
        }));
        Key addedId = Key.of("readview:external_append");
        registry.registerAccurate(new AccurateFoodRecipe(addedId, Key.of("minecraft:carrot"),
                List.of(new WeightedResult(RESULT, 1)), ApplianceType.STEAMER, 0, 1, List.of(), 29));
        registry.finishConfigurationLoad();
        assertEquals(2, registry.accurateRecipeCount(ApplianceType.STEAMER));
        assertEquals(29, registry.findAccurateById(addedId).cookingTime());
    }

    @Test void liveRecipeEditDuringLoadCannotExposePartiallyLoadedGroupTable() {
        installMeatView();
        registry.beginConfigurationLoad();
        registry.configurationUpdate(() -> FoodGroups.instance().equivalentTags(List.of()));
        registry.registerAccurate(recipe(11));
        assertTrue(ApplianceFoodRegistry.instance().isAllowed(ApplianceType.POT, PORK));
        assertTrue(registry.planFlex(ApplianceType.POT, List.of(PORK), null, 6).matched());
        registry.finishConfigurationLoad();
    }

    @Test void failedLoadPreservesPreviouslyPublishedCompiledGroups() {
        installMeatView();
        registry.beginConfigurationLoad();
        assertThrows(IllegalArgumentException.class, () -> registry.configurationUpdate(() -> {
            FoodGroups.instance().equivalentTags(List.of());
            registry.clearFlex(ApplianceType.POT);
            throw new IllegalArgumentException("invalid replacement after group parser");
        }));
        registry.finishConfigurationLoad();
        assertTrue(ApplianceFoodRegistry.instance().isAllowed(ApplianceType.POT, PORK));
        assertTrue(registry.planFlex(ApplianceType.POT, List.of(PORK), null, 6).matched());
    }

    @Test void oneFailedDeferredEditDoesNotStrandLaterQueuedOperations() {
        AtomicInteger acknowledged = new AtomicInteger();
        registry.beginConfigurationLoad();
        registry.afterConfigurationLoad(() -> { throw new IllegalArgumentException("first deferred edit rejected"); });
        registry.afterConfigurationLoad(acknowledged::incrementAndGet);
        try { registry.finishConfigurationLoad(); }
        catch (RuntimeException reportedFailure) { /* Reporting failure is fine; leaving siblings queued is not. */ }
        assertEquals(1, acknowledged.get());
    }

    @Test void failedRawGroupsCannotLeakThroughTheNextUnrelatedRecipeEdit() {
        tag(MEAT, List.of(BEEF.asString(), PORK.asString()));
        FoodGroups.instance().equivalentTags(List.of(MEAT));
        registry.registerFlex(flex("before_failure", Map.of(BEEF, 1), true, false, 3));
        assertTrue(registry.planFlex(ApplianceType.POT, List.of(PORK), null, 9).matched());
        registry.beginConfigurationLoad();
        registry.configurationUpdate(() -> {
            ItemTags.instance().register(MEAT, List.of(INPUT.asString()));
            FoodGroups.instance().equivalentTags(List.of());
        });
        registry.abortConfigurationLoad(registry.configurationLoadToken());
        registry.registerAccurate(new AccurateFoodRecipe(ID, INPUT, List.of(new WeightedResult(RESULT, 100)),
                ApplianceType.STEAMER, 0, 1, List.of(), 23));
        assertEquals(List.of(MEAT), FoodGroups.instance().equivalentTags());
        assertTrue(ItemTags.instance().matchesId(MEAT, PORK));
        assertTrue(registry.planFlex(ApplianceType.POT, List.of(PORK), null, 9).matched());
        assertFalse(registry.planFlex(ApplianceType.POT, List.of(INPUT), null, 9).matched());
    }

    @Test void anchorFilteringPreservesFullScanOrderQualityAndAllToggleViews() {
        tag(MEAT, List.of(BEEF.asString(), PORK.asString()));
        tag(SEASONING, List.of(SUGAR.asString()));
        FoodGroups.instance().equivalentTags(List.of(MEAT)); FoodGroups.instance().seasoningTags(List.of(SEASONING));
        List<FlexFoodRecipe> recipes = List.of(
                flex("coarse", Map.of(BEEF, 1), true, true, 2),
                flex("equivalent_first", Map.of(BEEF, 1, INPUT, 1), true, true, 3),
                flex("equivalent_tie_later", Map.of(BEEF, 1, INPUT, 1), true, true, 4),
                flex("strict", Map.of(BEEF, 1, INPUT, 1), false, true, 5),
                flex("with_seasoning", Map.of(BEEF, 1, INPUT, 1, SUGAR, 1), true, false, 6));
        registry.atomicUpdate(() -> recipes.forEach(registry::registerFlex));
        for (List<Key> ingredients : List.of(List.of(BEEF, INPUT), List.of(PORK, INPUT), List.of(PORK, INPUT, SUGAR),
                List.of(BEEF, PORK, INPUT, INPUT), List.of(INPUT), List.of(SUGAR, SUGAR))) {
            FlexMatcher.Match expected = FlexMatcher.bestMatch(recipes, 0, ApplianceType.POT, ingredients, null);
            CookingPlan indexed = registry.planFlex(ApplianceType.POT, ingredients, null, 9);
            assertEquals(expected != null, indexed.matched(), "ingredients=" + ingredients);
            if (expected != null) {
                assertEquals(expected.recipe().id(), indexed.recipeId());
                assertEquals(expected.quality(), indexed.outputs().getFirst().quality());
                assertEquals(expected.portions(), indexed.outputs().getFirst().count());
                assertEquals(expected.recipe().stirFryCount(), indexed.workRequired());
            }
        }
        assertEquals(Key.of("readview:equivalent_first"), registry.planFlex(ApplianceType.POT, List.of(PORK, INPUT), null, 9).recipeId());
    }

    @Test void compiledNestedTagsRetainLegacyMaximumDepthAndDoNotOmitAnchors() {
        List<Key> chain = new ArrayList<>();
        for (int i = 0; i <= 8; i++) chain.add(Key.of("readview:depth_" + i));
        for (int i = 0; i < chain.size(); i++) tag(chain.get(i), i + 1 < chain.size()
                ? List.of("#" + chain.get(i + 1).asString()) : List.of(BEEF.asString(), "craftengine:" + PORK.asString()));
        FoodGroups.instance().equivalentTags(List.of(chain.getFirst()));
        registry.registerFlex(flex("deep", Map.of(BEEF, 1), true, true, 3));
        assertTrue(ItemTags.instance().matchesId(chain.getFirst(), PORK), "legacy resolver accepts depth 8");
        assertEquals(chain.getFirst(), FoodGroupView.capture().canonical(PORK));
        assertTrue(registry.planFlex(ApplianceType.POT, List.of(PORK), null, 6).matched(), "compiled anchor accepts the same equivalent member");
    }

    private void installMeatView() {
        tag(MEAT, List.of(BEEF.asString(), PORK.asString()));
        FoodGroups.instance().equivalentTags(List.of(MEAT));
        registry.atomicUpdate(() -> {
            registry.registerFlex(flex("meat", Map.of(BEEF, 1), true, true, 3));
            ApplianceFoodRegistry.instance().register(ApplianceType.POT, BEEF);
        });
    }
    private void tag(Key key, List<String> members) { tags.add(key); ItemTags.instance().register(key, members); }
    private void install(int work, boolean allowed) {
        registry.atomicUpdate(() -> {
            registry.removeAccurate(ID); registry.registerAccurate(recipe(work));
            if (allowed) ApplianceFoodRegistry.instance().register(ApplianceType.STEAMER, INPUT);
            else ApplianceFoodRegistry.instance().unregister(ApplianceType.STEAMER, INPUT);
        });
    }
    private AccurateFoodRecipe recipe(int work) {
        return new AccurateFoodRecipe(ID, INPUT, List.of(new WeightedResult(RESULT, 1)), ApplianceType.STEAMER, 0, work, List.of(), work);
    }
    private static FlexFoodRecipe flex(String name, Map<Key, Integer> ingredients, boolean equivalents, boolean seasonings, int stirs) {
        return FlexFoodRecipe.of(Key.of("readview:" + name), RESULT, ApplianceType.POT, ingredients, List.of(), null,
                equivalents, seasonings, 0, stirs);
    }
    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS), "publication did not complete"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }
}
