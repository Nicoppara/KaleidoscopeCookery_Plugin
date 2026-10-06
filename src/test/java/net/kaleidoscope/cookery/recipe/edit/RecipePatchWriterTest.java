package net.kaleidoscope.cookery.recipe.edit;

import net.momirealms.craftengine.core.plugin.config.ConfigValue;
import net.momirealms.craftengine.core.plugin.config.IdValueConfigParser;
import net.momirealms.craftengine.core.plugin.config.template.TemplateManager;
import net.momirealms.craftengine.core.util.Key;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RecipePatchWriterTest {
    @org.junit.jupiter.api.BeforeAll
    static void initializeCraftEngineTemplates() throws Exception { CraftEngineTemplateFixture.initialize(); }
    @org.junit.jupiter.api.BeforeEach
    void resetTemplates() { TemplateManager.INSTANCE.unload(); }
    @org.junit.jupiter.api.AfterEach
    void clearTemplates() { TemplateManager.INSTANCE.unload(); }
    private static final String NODE = "accurate_foods.test:b";

    @Test void directFieldPatchPreservesUnknownSettingsAndSiblingRecipes() throws Exception {
        Map<String, Object> body = map("require", "minecraft:beef", "result", "minecraft:cooked_beef",
                "cook", "steamer", "foreign_setting", map("enabled", true));
        Map<String, Object> root = map("accurate_foods", map("test:b", body, "test:sibling", map("enabled", true)));
        var saved = RecipePatchWriter.apply(root, RecipePatchWriter.direct(NODE, body), NODE,
                Key.of("test:b"), map("cooking_time", 47));
        assertEquals(47, saved.expanded().get("cooking_time"));
        assertEquals(map("enabled", true), saved.expanded().get("foreign_setting"));
        assertEquals(((Map<?, ?>) root.get("accurate_foods")).get("test:sibling"),
                ((Map<?, ?>) saved.root().get("accurate_foods")).get("test:sibling"));
        assertFalse(body.containsKey("cooking_time"));
    }

    @Test void templateOverrideWinsOverMergesAndKeepsArguments() throws Exception {
        template("test:processing_base", map("require", "minecraft:beef", "result", "minecraft:cooked_beef",
                "cook", "steamer", "cooking-time", 200, "foreign_setting", 19));
        Map<String, Object> body = map("template", "test:processing_base", "arguments", map("extra", "keep"),
                "merges", map("cooking-time", 350, "foreign_merge", true), "overrides", map("other", 31));
        var saved = RecipePatchWriter.apply(map("accurate_foods", map("test:b", body)),
                RecipePatchWriter.direct(NODE, body), NODE, Key.of("test:b"), map("cooking_time", 100));
        assertEquals(100, saved.expanded().get("cooking_time"));
        assertEquals(100, saved.expanded().get("cooking-time"));
        assertEquals(31, saved.expanded().get("other"));
        assertEquals(true, saved.expanded().get("foreign_merge"));
        Map<?, ?> raw = (Map<?, ?>) ((Map<?, ?>) saved.root().get("accurate_foods")).get("test:b");
        assertEquals(body.get("arguments"), raw.get("arguments"));
        assertEquals("test:processing_base", raw.get("template"));
    }

    @Test void unrelatedTimeFieldIsPreservedForNonTeapotRecipes() throws Exception {
        Map<String, Object> body = map("require", "minecraft:beef", "result", "minecraft:cooked_beef",
                "cook", "steamer", "time", "foreign setting");
        var saved = RecipePatchWriter.apply(map("accurate_foods", map("test:b", body)),
                RecipePatchWriter.direct(NODE, body), NODE, Key.of("test:b"), map("cooking_time", 47));
        assertEquals("foreign setting", saved.expanded().get("time"));
        assertEquals(47, saved.expanded().get("cooking_time"));
    }

    @Test void teapotPatchUpdatesAndValidatesTheLegacyTimeAlias() throws Exception {
        String node = "teapot_result.test:tea";
        Map<String, Object> body = map("fluid", "minecraft:water", "require", "minecraft:beef",
                "result", "minecraft:cooked_beef", "time", 200);
        var saved = RecipePatchWriter.apply(map("teapot_result", map("test:tea", body)),
                RecipePatchWriter.direct(node, body), node, Key.of("test:tea"), map("cooking_time", 47));
        assertNull(saved.expanded().get("time"));
        assertEquals(47, saved.expanded().get("cooking_time"));
    }

    @Test void unrecognizedFactoryArgumentCannotBeReplayedForValidation() throws Exception {
        Map<String, Object> root = factory(false, false);
        Map<String, Object> factory = (Map<String, Object>) root.get("config_factory#food");
        factory.put("instances", List.of(map("id", "b", "time", map("type", "third_party:random", "value", 20))));
        IOException error = assertThrows(IOException.class, () -> RecipePatchWriter.apply(root, target(root, 0),
                NODE, Key.of("test:b"), map("cooking_time", 47)));
        assertTrue(error.getMessage().contains("third_party:random"));
    }

    @Test void clearingLocalSettingRestoresTheRealTemplateValue() throws Exception {
        template("test:inherited_processing", map("cooking_time", 350, "require", "minecraft:beef"));
        Map<String, Object> body = map("template", "test:inherited_processing", "overrides", map("cooking_time", 100));
        var saved = RecipePatchWriter.apply(map("accurate_foods", map("test:b", body)),
                RecipePatchWriter.direct(NODE, body), NODE, Key.of("test:b"), map("cooking_time", null));
        assertEquals(350, saved.expanded().get("cooking_time"));
    }

    @Test void factoryIsolationRetainsEveryOutputAndInstanceOrder() throws Exception {
        Map<String, Object> root = factory(false, false);
        var target = target(root, 1);
        var saved = RecipePatchWriter.apply(root, target, NODE, Key.of("test:b"), map("cooking_time", 60));
        assertEquals(List.of("config_factory#food", "config_factory#food_recipe_edit", "config_factory#food_recipe_after"),
                new ArrayList<>(saved.root().keySet()));
        assertEquals(60, saved.expanded().get("cooking_time"));
        for (Object value : saved.root().values()) {
            Map<?, ?> blueprint = (Map<?, ?>) ((Map<?, ?>) value).get("blueprint");
            assertTrue(blueprint.containsKey("items"), "The unrelated generated item must stay in every split blueprint");
        }
        assertEquals(List.of(map("id", "b", "time", 20)), ((Map<?, ?>) saved.root().get(saved.target().factoryKey())).get("instances"));
        assertEquals(3, ((List<?>) ((Map<?, ?>) root.get("config_factory#food")).get("instances")).size());
    }

    @Test void privateScalarParameterIsEditedWithoutFactorySplitting() throws Exception {
        Map<String, Object> root = factory(true, false);
        var saved = RecipePatchWriter.apply(root, target(root, 1), NODE, Key.of("test:b"), map("cooking_time", 77));
        assertEquals(List.of("config_factory#food"), new ArrayList<>(saved.root().keySet()));
        assertEquals(77, saved.expanded().get("cooking_time"));
        assertEquals(77, saved.target().instance().get("time"));
    }

    @Test void parameterAlsoUsedByAnotherGeneratedItemIsSafelyIsolated() throws Exception {
        Map<String, Object> root = factory(true, true);
        var saved = RecipePatchWriter.apply(root, target(root, 1), NODE, Key.of("test:b"), map("cooking_time", 77));
        assertEquals(3, saved.root().size());
        assertEquals(20, saved.target().instance().get("time"), "Shared parameter must not be modified");
    }

    @Test void firstAndLastInstancesDoNotLeaveEmptyFactories() throws Exception {
        for (int selected : new int[]{0, 2}) {
            Map<String, Object> root = factory(false, false);
            String node = "accurate_foods.test:" + (selected == 0 ? "a" : "c");
            var source = target(root, selected);
            var saved = RecipePatchWriter.apply(root, source, node, Key.of(selected == 0 ? "test:a" : "test:c"), map("cooking_time", 31));
            assertEquals(2, saved.root().size());
            for (Object value : saved.root().values()) assertFalse(((List<?>) ((Map<?, ?>) value).get("instances")).isEmpty());
        }
    }

    @Test void factoryWithoutHashSuffixStillProducesRecognizedFactorySections() throws Exception {
        Map<String, Object> root = factory(false, false);
        var old = target(root, 1);
        Object body = root.remove("config_factory#food");
        root.put("config_factory", body);
        var source = new RecipeFileStore.SourceTarget(old.generatedNode(), "config_factory", old.instancesKey(),
                old.instanceIndex(), old.instance(), old.blueprintKey(), old.recipeKey(), old.originalSource());
        var saved = RecipePatchWriter.apply(root, source, NODE, Key.of("test:b"), map("cooking_time", 30));
        assertEquals(List.of("config_factory", "config_factory#recipe_edit", "config_factory#recipe_after"),
                new ArrayList<>(saved.root().keySet()));
        assertEquals(30, saved.expanded().get("cooking_time"));
    }

    @Test void ambiguousIdenticalInstancesAreRejectedWithoutMutation() {
        Map<String, Object> root = factory(false, false);
        Map<String, Object> factory = cast(root.get("config_factory#food"));
        factory.put("instances", List.of(map("id", "b", "time", 20), map("id", "b", "time", 20)));
        String original = root.toString();
        assertThrows(IOException.class, () -> RecipePatchWriter.apply(root, target(root, 0), NODE, Key.of("test:b"), map("cooking_time", 50)));
        assertEquals(original, root.toString());
    }

    @Test void staleSourceAndStatefulArgumentsAreRejected() {
        Map<String, Object> root = factory(false, false);
        var source = target(root, 1);
        cast(root.get("config_factory#food")).put("new_setting", true);
        Map<String, Object> staleRoot = root;
        assertThrows(IOException.class, () -> RecipePatchWriter.apply(staleRoot, source, NODE, Key.of("test:b"), map("cooking_time", 50)));
        root = factory(false, false);
        cast(root.get("config_factory#food")).put("argument", map("type", "self_increase_int", "from", 1, "to", 10));
        Map<String, Object> statefulRoot = root;
        assertThrows(IOException.class, () -> RecipePatchWriter.apply(statefulRoot, target(statefulRoot, 1), NODE,
                Key.of("test:b"), map("cooking_time", 50)));
    }

    @Test void atomicFilePatchRelocatesOtherFactoryRecipesForTheNextEdit(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path folder) throws Exception {
        java.nio.file.Path file = folder.resolve("recipes.yml");
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        factory(false, false).forEach(yaml::set);
        yaml.save(file.toFile());
        RecipeSourceIndex index = RecipeSourceIndex.instance();
        Object firstRecipe = new Object(), nextRecipe = new Object();
        Key firstId = Key.of("test:b"), nextId = Key.of("test:c");
        var firstTarget = RecipeFileStore.resolveTargets(RecipeSourceIndex.Kind.ACCURATE, firstId, file, NODE).getFirst();
        var nextTarget = RecipeFileStore.resolveTargets(RecipeSourceIndex.Kind.ACCURATE, nextId, file, "accurate_foods.test:c").getFirst();
        index.put(RecipeSourceIndex.Kind.ACCURATE, firstId, file, firstTarget, firstRecipe, false);
        index.put(RecipeSourceIndex.Kind.ACCURATE, nextId, file, nextTarget, nextRecipe, false);
        try {
            RecipeFileStore.patchTarget(file, firstTarget, NODE, firstId, map("cooking_time", 61), () -> false);
            index.prepareRelocations(file).run();
            var relocated = index.target(nextRecipe);
            assertNotEquals(nextTarget.factoryKey(), relocated.factoryKey());
            var second = RecipeFileStore.patchTarget(file, relocated, "accurate_foods.test:c", nextId, map("cooking_time", 99), () -> false);
            assertEquals(99, second.expanded().get("cooking_time"));
            assertTrue(java.nio.file.Files.readString(file).contains("items:"));
        } finally { index.clear(); }
    }

    @Test void reloadStartingDuringValidationLeavesOriginalFileUntouched(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path folder) throws Exception {
        java.nio.file.Path file = folder.resolve("recipe.yml");
        String original = "accurate_foods:\n  test:b:\n    require: minecraft:beef\n    result: minecraft:cooked_beef\n    cook: steamer\n";
        java.nio.file.Files.writeString(file, original);
        var source = RecipeFileStore.resolveTargets(RecipeSourceIndex.Kind.ACCURATE, Key.of("test:b"), file, NODE).getFirst();
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(IOException.class, () -> RecipeFileStore.patchTarget(file, source, NODE, Key.of("test:b"),
                map("cooking_time", 30), () -> reads.incrementAndGet() >= 2));
        assertEquals(original, java.nio.file.Files.readString(file));
        assertFalse(java.nio.file.Files.exists(folder.resolve("recipe.yml.cookery.tmp")));
    }

    private static Map<String, Object> factory(boolean parameter, boolean shared) {
        return map("config_factory#food", map("instances", List.of(
                        map("id", "a", "time", 20), map("id", "b", "time", 20), map("id", "c", "time", 20)),
                "blueprint", map("accurate_foods", map("test:${id}", parameter
                                ? map("require", "minecraft:beef", "result", "minecraft:cooked_beef", "cook", "steamer", "cooking_time", "${time}")
                                : map("require", "minecraft:beef", "result", "minecraft:cooked_beef", "cook", "steamer")),
                        "items", map("test:item_${id}", map("material", "paper", "foreign_setting", shared ? "${time}" : "preserved")))));
    }

    private static RecipeFileStore.SourceTarget target(Map<String, Object> root, int index) {
        Map<String, Object> factory = cast(root.get("config_factory#food"));
        Map<String, Object> instance = cast(((List<?>) factory.get("instances")).get(index));
        Map<String, Object> metadata = new LinkedHashMap<>(factory);
        metadata.remove("instances");
        return new RecipeFileStore.SourceTarget("accurate_foods.test:" + instance.get("id"), "config_factory#food",
                "instances", index, instance, "blueprint", "accurate_foods.test:${id}", metadata);
    }

    private static void template(String id, Map<String, Object> body) throws Exception {
        Object parser = TemplateManager.INSTANCE.parser();
        var method = parser.getClass().getDeclaredMethod("parseValue", net.momirealms.craftengine.core.pack.Pack.class,
                java.nio.file.Path.class, Key.class, ConfigValue.class);
        method.setAccessible(true);
        method.invoke(parser, null, java.nio.file.Path.of("templates.yml"), Key.of(id), ConfigValue.of("templates." + id, body));
    }

    @SuppressWarnings("unchecked") private static Map<String, Object> cast(Object object) { return (Map<String, Object>) object; }
    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) map.put(pairs[index].toString(), pairs[index + 1]);
        return map;
    }
}
