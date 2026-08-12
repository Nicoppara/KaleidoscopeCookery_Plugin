package net.kaleidoscope.cookery.recipe.edit;

import net.momirealms.craftengine.core.util.Key;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeSourceIndexTest {
    private final RecipeSourceIndex index = RecipeSourceIndex.instance();

    @AfterEach
    void clearSources() {
        index.clear();
    }

    @Test
    void distinguishesDuplicateNodesInsideTheSameFile() {
        Key id = Key.of("test:same_id");
        Path file = Path.of("pack", "recipe.yml");
        Object first = new Object();
        Object second = new Object();

        index.put(RecipeSourceIndex.Kind.ACCURATE, id, file,
                "accurate_foods.test:same_id", first, true);
        assertFalse(index.hasOtherSource(RecipeSourceIndex.Kind.ACCURATE, id, file,
                "accurate_foods.test:same_id"));

        index.put(RecipeSourceIndex.Kind.ACCURATE, id, file,
                "accurate_foods#1.test:same_id", second, true);
        assertTrue(index.hasOtherSource(RecipeSourceIndex.Kind.ACCURATE, id, file,
                "accurate_foods.test:same_id"));
        assertEquals(file.toAbsolutePath().normalize(), index.get(first));
        assertEquals(file.toAbsolutePath().normalize(), index.get(second));
    }

    @Test
    void deletionTombstoneTargetsOnlyOneNode() {
        Key id = Key.of("test:targeted_delete");
        Path file = Path.of("pack", "recipe.yml");
        Object selected = new Object();
        index.put(RecipeSourceIndex.Kind.ACCURATE, id, file,
                "accurate_foods.test:targeted_delete", selected, true);

        index.beginLoad(RecipeSourceIndex.Kind.ACCURATE);
        index.markDeleted(selected);

        assertTrue(index.isDeleted(RecipeSourceIndex.Kind.ACCURATE, id, file,
                "accurate_foods.test:targeted_delete"));
        assertFalse(index.isDeleted(RecipeSourceIndex.Kind.ACCURATE, id, file,
                "accurate_foods#1.test:targeted_delete"));

        index.restore(RecipeSourceIndex.Kind.ACCURATE, id, file,
                "accurate_foods.test:targeted_delete");
        assertTrue(index.isDeleted(RecipeSourceIndex.Kind.ACCURATE, id, file,
                "accurate_foods.test:targeted_delete"));

        index.endLoad(RecipeSourceIndex.Kind.ACCURATE);
        assertFalse(index.isDeleted(RecipeSourceIndex.Kind.ACCURATE, id, file,
                "accurate_foods.test:targeted_delete"));
    }

    @Test
    void hotDeletionWaitsForCurrentReloadAndRemovesOnlySelectedNode() {
        Key id = Key.of("test:reload_delete");
        Path file = Path.of("pack", "recipe.yml");
        Object first = new Object();
        Object second = new Object();
        index.put(RecipeSourceIndex.Kind.ACCURATE, id, file,
                "accurate_foods.test:reload_delete", first, true);
        index.put(RecipeSourceIndex.Kind.ACCURATE, id, file,
                "accurate_foods#1.test:reload_delete", second, true);

        index.beginLoad(RecipeSourceIndex.Kind.ACCURATE);
        boolean[] completed = {false};
        index.afterCurrentLoad(RecipeSourceIndex.Kind.ACCURATE, () -> {
            Object removed = index.removeSource(RecipeSourceIndex.Kind.ACCURATE, id, file,
                    "accurate_foods.test:reload_delete");
            assertSame(first, removed);
            completed[0] = true;
        });

        assertFalse(completed[0]);
        index.endLoad(RecipeSourceIndex.Kind.ACCURATE);
        assertTrue(completed[0]);
        assertEquals(List.of(second), index.recipes(RecipeSourceIndex.Kind.ACCURATE, id));
    }

    @Test
    void resolvesAndDeletesFactoryInstance(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("generated_millstone.yml");
        Files.writeString(file, """
                config_factory#millstone_recipes:
                  instances:
                    - from: allium
                      to: magenta_dye
                      count: 2
                    - from: basalt
                      to: polished_basalt
                  blueprint:
                    accurate_foods:
                      kaleidoscopecookery:millstone_${to}_from_${from}:
                        require: "${from_ns:-minecraft}:${from}"
                        result: "${to_ns:-minecraft}:${to}"
                        result_count: ${count:-1}
                        cook: millstone
                        rotations: 1
                """);

        Key id = Key.of("kaleidoscopecookery:millstone_magenta_dye_from_allium");
        String generatedNode = "accurate_foods." + id.asString();
        List<RecipeFileStore.SourceTarget> targets = RecipeFileStore.resolveTargets(
                RecipeSourceIndex.Kind.ACCURATE, id, file, generatedNode);

        assertEquals(1, targets.size());
        assertTrue(targets.getFirst().factory());
        RecipeFileStore.deleteTarget(file, targets.getFirst());

        String saved = Files.readString(file);
        assertFalse(saved.contains("from: allium"));
        assertTrue(saved.contains("from: basalt"));
        assertFalse(saved.contains("accurate_foods:\n  kaleidoscopecookery:millstone_magenta_dye_from_allium"));
    }

    @Test
    void editingFactoryRecipeDetachesItAsDirectNode(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("generated_steamer.yml");
        Files.writeString(file, """
                config_factory#steamer_recipes:
                  instances:
                    - id: baozi
                      require: kaleidoscopecookery:stuffed_dough_food
                      result: kaleidoscopecookery:baozi
                  blueprint:
                    accurate_foods:
                      kaleidoscopecookery:steamer_${id}:
                        require: "${require}"
                        result: "${result}"
                        cook: steamer
                """);

        Key id = Key.of("kaleidoscopecookery:steamer_baozi");
        String generatedNode = "accurate_foods." + id.asString();
        RecipeFileStore.SourceTarget target = RecipeFileStore.resolveTargets(
                RecipeSourceIndex.Kind.ACCURATE, id, file, generatedNode).getFirst();
        Map<String, Object> replacement = new LinkedHashMap<>();
        replacement.put("require", "kaleidoscopecookery:stuffed_dough_food");
        replacement.put("result", "kaleidoscopecookery:baozi");
        replacement.put("cook", "steamer");
        replacement.put("result_count", 2);

        RecipeFileStore.replaceTarget(file, target, generatedNode, replacement);

        String saved = Files.readString(file);
        assertFalse(saved.contains("- id: baozi"));
        assertTrue(saved.contains("kaleidoscopecookery:steamer_baozi:"));
        assertTrue(saved.contains("result_count: 2"));
    }

    @Test
    void hotUpdateRunsOnlyAfterRecipeIsSaved(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("accurate.yml");
        Files.writeString(file, """
                accurate_foods:
                  test:apple:
                    require: minecraft:apple
                    result: minecraft:apple
                    cook: steamer
                """);
        String nodePath = "accurate_foods.test:apple";
        Map<String, Object> replacement = new LinkedHashMap<>();
        replacement.put("require", "minecraft:apple");
        replacement.put("result", "minecraft:golden_apple");
        replacement.put("cook", "steamer");
        AtomicBoolean applied = new AtomicBoolean();

        RecipeFileStore.replaceTarget(file, RecipeFileStore.SourceTarget.direct(nodePath),
                nodePath, replacement, () -> applied.set(true));

        assertTrue(applied.get());
        assertTrue(Files.readString(file).contains("result: minecraft:golden_apple"));
    }

    @Test
    void missingOriginalRecipeDoesNotRunHotUpdate(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("accurate.yml");
        String original = """
                accurate_foods:
                  test:apple:
                    require: minecraft:apple
                    result: minecraft:apple
                    cook: steamer
                """;
        Files.writeString(file, original);
        String missingNode = "accurate_foods.test:missing";
        Map<String, Object> replacement = Map.of(
                "require", "minecraft:carrot",
                "result", "minecraft:golden_carrot",
                "cook", "steamer"
        );
        AtomicBoolean applied = new AtomicBoolean();

        assertThrows(IOException.class, () -> RecipeFileStore.replaceTarget(
                file, RecipeFileStore.SourceTarget.direct(missingNode), missingNode,
                replacement, () -> applied.set(true)));

        assertFalse(applied.get());
        assertEquals(original, Files.readString(file));
    }

    @Test
    void resolvesPotStockpotAndTeapotFactories(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("generated_recipes.yml");
        Files.writeString(file, """
                config_factory#pot_recipes:
                  instances:
                    - id: fried_egg
                      result: kaleidoscopecookery:fried_egg
                  blueprint:
                    pot_flex_foods:
                      kaleidoscopecookery:pot_${id}:
                        result: "${result}"
                        perfect:
                          minecraft:egg: 1
                config_factory#stock_recipes:
                  instances:
                    - id: egg_soup
                      result: kaleidoscopecookery:egg_soup
                  blueprint:
                    stock_flex_foods:
                      kaleidoscopecookery:stock_${id}:
                        result: "${result}"
                        liquid:
                          - minecraft:water_bucket
                        perfect:
                          minecraft:egg: 1
                config_factory#tea_recipes:
                  instances:
                    - flower: dandelion
                  blueprint:
                    teapot_result:
                      flower_tea_from_${flower}:
                        fluid: minecraft:water
                        require: "minecraft:${flower} 12"
                        result: kaleidoscopecookery:flower_tea
                """);

        assertFactoryTarget(file, RecipeSourceIndex.Kind.POT_FLEX,
                "kaleidoscopecookery:pot_fried_egg", "pot_flex_foods");
        assertFactoryTarget(file, RecipeSourceIndex.Kind.STOCK_FLEX,
                "kaleidoscopecookery:stock_egg_soup", "stock_flex_foods");
        Key teaId = Key.of("kaleidoscopecookery:flower_tea_from_dandelion");
        List<RecipeFileStore.SourceTarget> teaTargets = RecipeFileStore.resolveTargets(
                RecipeSourceIndex.Kind.TEAPOT, teaId, file,
                "teapot_result.flower_tea_from_dandelion");
        assertEquals(1, teaTargets.size());
        assertTrue(teaTargets.getFirst().factory());
    }

    @Test
    void templateBackedDirectRecipeStillTargetsItsRealNode(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("chopping_board.yml");
        Files.writeString(file, """
                templates:
                  kaleidoscopecookery:recipe/chopping_board:
                    stage: 5
                    require: "${require}"
                chopping_board_raws:
                  kaleidoscopecookery:cod:
                    template: kaleidoscopecookery:recipe/chopping_board
                    arguments:
                      require: minecraft:cod
                """);
        Key id = Key.of("kaleidoscopecookery:cod");
        String node = "chopping_board_raws." + id.asString();

        RecipeFileStore.SourceTarget target = RecipeFileStore.resolveTargets(
                RecipeSourceIndex.Kind.CHOPPING, id, file, node).getFirst();

        assertFalse(target.factory());
        assertTrue(target.resolved());
        assertTrue(RecipeFileStore.deleteTarget(file, target));
        assertFalse(Files.readString(file).contains("kaleidoscopecookery:cod:"));
    }

    @Test
    void duplicateFactoryIdsAreMatchedByExpandedContent(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("duplicate_factory.yml");
        Files.writeString(file, """
                config_factory#duplicate_recipes:
                  instances:
                    - id: same
                      result: kaleidoscopecookery:first
                    - id: same
                      result: kaleidoscopecookery:second
                  blueprint:
                    accurate_foods:
                      kaleidoscopecookery:steamer_${id}:
                        require: minecraft:wheat
                        result: "${result}"
                        cook: steamer
                """);
        Key id = Key.of("kaleidoscopecookery:steamer_same");
        String node = "accurate_foods." + id.asString();
        Map<String, Object> expanded = new LinkedHashMap<>();
        expanded.put("require", "minecraft:wheat");
        expanded.put("result", "kaleidoscopecookery:second");
        expanded.put("cook", "steamer");

        List<RecipeFileStore.SourceTarget> targets = RecipeFileStore.resolveTargets(
                RecipeSourceIndex.Kind.ACCURATE, id, file, node, expanded);

        assertEquals(1, targets.size());
        assertEquals("kaleidoscopecookery:second", targets.getFirst().instance().get("result"));
    }

    @Test
    void directAndFactoryDefinitionsWithSameIdRemainSeparateTargets(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("mixed_duplicate.yml");
        Files.writeString(file, """
                accurate_foods:
                  kaleidoscopecookery:steamer_same:
                    require: minecraft:carrot
                    result: minecraft:baked_potato
                    cook: steamer
                config_factory#same_id:
                  instances:
                    - id: same
                      result: minecraft:bread
                  blueprint:
                    accurate_foods:
                      kaleidoscopecookery:steamer_${id}:
                        require: minecraft:wheat
                        result: "${result}"
                        cook: steamer
                """);
        Key id = Key.of("kaleidoscopecookery:steamer_same");
        String node = "accurate_foods." + id.asString();
        Map<String, Object> expanded = new LinkedHashMap<>();
        expanded.put("require", "minecraft:wheat");
        expanded.put("result", "minecraft:bread");
        expanded.put("cook", "steamer");

        List<RecipeFileStore.SourceTarget> targets = RecipeFileStore.resolveTargets(
                RecipeSourceIndex.Kind.ACCURATE, id, file, node, expanded);

        assertEquals(2, targets.size());
        assertFalse(targets.get(0).factory());
        assertTrue(targets.get(1).factory());
    }

    @Test
    void resolvesShawarmaFactoryNamespaces(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("generated_shawarma.yml");
        Files.writeString(file, """
                config_factory#shawarma_recipes:
                  instances:
                    - from: beef
                      to: cooked_beef
                    - from: raw_lamb_chops
                      from_ns: kaleidoscopecookery
                      to: cooked_lamb_chops
                      to_ns: kaleidoscopecookery
                  blueprint:
                    accurate_foods:
                      kaleidoscopecookery:shawarma_${to}_from_${from}:
                        require: "${from_ns:-minecraft}:${from}"
                        result: "${to_ns:-minecraft}:${to}"
                        result_count: 1
                        cook: shawarma
                """);

        assertShawarmaTarget(file, "cooked_beef", "beef",
                "minecraft:beef", "minecraft:cooked_beef");
        assertShawarmaTarget(file, "cooked_lamb_chops", "raw_lamb_chops",
                "kaleidoscopecookery:raw_lamb_chops", "kaleidoscopecookery:cooked_lamb_chops");
    }

    private static void assertShawarmaTarget(Path file, String resultName, String inputName,
                                             String input, String result) {
        Key id = Key.of("kaleidoscopecookery:shawarma_" + resultName + "_from_" + inputName);
        String node = "accurate_foods." + id.asString();
        Map<String, Object> expanded = new LinkedHashMap<>();
        expanded.put("require", input);
        expanded.put("result", result);
        expanded.put("result_count", 1);
        expanded.put("cook", "shawarma");

        List<RecipeFileStore.SourceTarget> targets = RecipeFileStore.resolveTargets(
                RecipeSourceIndex.Kind.ACCURATE, id, file, node, expanded);

        assertEquals(1, targets.size());
        assertTrue(targets.getFirst().factory());
    }

    private static void assertFactoryTarget(Path file, RecipeSourceIndex.Kind kind,
                                            String rawId, String section) {
        Key id = Key.of(rawId);
        String node = section + "." + rawId;
        List<RecipeFileStore.SourceTarget> targets = RecipeFileStore.resolveTargets(kind, id, file, node);
        assertEquals(1, targets.size());
        assertTrue(targets.getFirst().factory());
    }
}
