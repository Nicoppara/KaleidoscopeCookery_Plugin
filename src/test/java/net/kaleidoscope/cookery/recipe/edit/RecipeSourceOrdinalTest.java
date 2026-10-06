package net.kaleidoscope.cookery.recipe.edit;

import net.momirealms.craftengine.core.util.Key;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecipeSourceOrdinalTest {
    @BeforeAll static void initialize() throws Exception { CraftEngineTemplateFixture.initialize(); }

    @Test void sourcePositionsFollowDirectRootsFactoryInstancesAndBlueprintSections(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("order.yml");
        Files.writeString(file, """
                accurate_foods#before:
                  test:before: {cook: steamer, require: 'minecraft:beef', result: 'minecraft:cooked_beef'}
                config_factory#food:
                  blueprint:
                    accurate_foods:
                      test:${id}: {cook: steamer, require: 'minecraft:beef', result: 'minecraft:cooked_beef'}
                    chopping_board_raws:
                      test:chop_${id}: {require: 'minecraft:beef', stage: 1}
                  instances:
                    - {id: first}
                    - {id: last}
                accurate-foods#after:
                  test:after: {cook: steamer, require: 'minecraft:beef', result: 'minecraft:cooked_beef'}
                """);
        assertEquals(0, target(file, RecipeSourceIndex.Kind.ACCURATE, "before", "accurate_foods#before").sourceOrdinal());
        assertEquals(1, target(file, RecipeSourceIndex.Kind.ACCURATE, "first", "accurate_foods").sourceOrdinal());
        assertEquals(2, target(file, RecipeSourceIndex.Kind.CHOPPING, "chop_first", "chopping_board_raws").sourceOrdinal());
        assertEquals(3, target(file, RecipeSourceIndex.Kind.ACCURATE, "last", "accurate_foods").sourceOrdinal());
        assertEquals(4, target(file, RecipeSourceIndex.Kind.CHOPPING, "chop_last", "chopping_board_raws").sourceOrdinal());
        assertEquals(5, target(file, RecipeSourceIndex.Kind.ACCURATE, "after", "accurate-foods#after").sourceOrdinal());
        var yaml = YamlConfiguration.loadConfiguration(file.toFile());
        var last = target(file, RecipeSourceIndex.Kind.ACCURATE, "last", "accurate_foods");
        assertEquals(3, RecipeFileStore.sourceOrdinal(yaml.getValues(false), last));
    }

    @Test void isolatedFactorySaveAndRelocationsPreserveOriginalRecipePositions(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("factory.yml");
        Files.writeString(file, """
                config_factory#food:
                  blueprint:
                    accurate_foods:
                      test:${id}: {cook: steamer, require: 'minecraft:beef', result: 'minecraft:cooked_beef'}
                    items:
                      test:display_${id}: {material: paper}
                  instances:
                    - {id: first}
                    - {id: middle}
                    - {id: last}
                """);
        var first = target(file, RecipeSourceIndex.Kind.ACCURATE, "first", "accurate_foods");
        var middle = target(file, RecipeSourceIndex.Kind.ACCURATE, "middle", "accurate_foods");
        var last = target(file, RecipeSourceIndex.Kind.ACCURATE, "last", "accurate_foods");
        var index = RecipeSourceIndex.instance();
        Object firstRecipe = new Object(), lastRecipe = new Object();
        index.put(RecipeSourceIndex.Kind.ACCURATE, Key.of("test:first"), file, first, firstRecipe, false);
        index.put(RecipeSourceIndex.Kind.ACCURATE, Key.of("test:last"), file, last, lastRecipe, false);
        try {
            var saved = RecipeFileStore.patchTarget(file, middle, "accurate_foods.test:middle", Key.of("test:middle"),
                    Map.of("cooking_time", 47), () -> false);
            assertEquals(1, saved.target().sourceOrdinal());
            assertFalse(saved.target().processing().isEmpty());
            index.prepareRelocations(file).run();
            assertEquals(0, index.sourceOrdinal(firstRecipe));
            assertEquals(2, index.sourceOrdinal(lastRecipe));
            assertNotEquals(last.factoryKey(), index.target(lastRecipe).factoryKey());
            assertEquals(1, target(file, RecipeSourceIndex.Kind.ACCURATE, "middle", "accurate_foods").sourceOrdinal());
            assertEquals(2, target(file, RecipeSourceIndex.Kind.ACCURATE, "last", "accurate_foods").sourceOrdinal());
            var yaml = YamlConfiguration.loadConfiguration(file.toFile());
            assertEquals(1, RecipeFileStore.sourceOrdinal(yaml.getValues(false), saved.target()));
        } finally { index.clear(); }
    }

    @Test void oldConstructorsIdentityAndSourceRollbackKeepTheirOwnGenerationPosition() {
        String node = "accurate_foods.test:old";
        var five = new RecipeFileStore.SourceTarget(node, null, null, -1, Map.of());
        var eight = new RecipeFileStore.SourceTarget(node, null, null, -1, Map.of(), null, null, Map.of());
        var nine = new RecipeFileStore.SourceTarget(node, null, null, -1, Map.of(), null, null, Map.of(), Map.of());
        assertEquals(-1, five.sourceOrdinal());
        assertEquals(five, eight);
        assertEquals(five, nine);
        var ordered = nine.withOrdinal(7).withProcessing(Map.of());
        assertEquals(7, ordered.sourceOrdinal());
        assertEquals(nine, ordered);
        assertEquals(nine.hashCode(), ordered.hashCode());
        var index = RecipeSourceIndex.instance();
        Object old = new Object(), replacement = new Object();
        var id = Key.of("test:old");
        var file = Path.of("recipe-order.yml");
        index.put(RecipeSourceIndex.Kind.ACCURATE, id, file, ordered, old, false);
        try {
            index.beginConfigurationLoad();
            index.clearKind(RecipeSourceIndex.Kind.ACCURATE);
            index.put(RecipeSourceIndex.Kind.ACCURATE, id, file, nine.withOrdinal(3), replacement, false);
            assertEquals(7, index.sourceOrdinal(old));
            index.finishConfigurationLoad(false);
            assertEquals(7, index.sourceOrdinal(old));
            assertEquals(-1, index.sourceOrdinal(replacement));
        } finally { index.finishConfigurationLoad(false); index.clear(); }
    }

    private static RecipeFileStore.SourceTarget target(Path file, RecipeSourceIndex.Kind kind, String id, String section) {
        return RecipeFileStore.resolveTargets(kind, Key.of("test:" + id), file, section + ".test:" + id).getFirst();
    }
}
