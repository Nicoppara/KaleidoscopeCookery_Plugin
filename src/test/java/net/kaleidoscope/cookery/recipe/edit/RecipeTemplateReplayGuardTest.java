package net.kaleidoscope.cookery.recipe.edit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import net.momirealms.craftengine.core.pack.CachedConfigSection;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.pack.PackMeta;
import net.momirealms.craftengine.core.plugin.config.ConfigParser;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.ResourceException;
import net.momirealms.craftengine.core.plugin.config.lifecycle.LoadingStage;
import net.momirealms.craftengine.core.plugin.config.lifecycle.LoadingStages;
import net.momirealms.craftengine.core.util.Key;

import static org.junit.jupiter.api.Assertions.*;

class RecipeTemplateReplayGuardTest {
    @Test void uniqueLiteralTemplateClosureAndKnownArgumentsPassWithoutEvaluation() {
        var source = source(Map.of("templates#processing", Map.of(
                "test:base", Map.of("template", "test:leaf", "arguments", Map.of("value",
                        Map.of("type", "plain", "value", "${unused}"))),
                "test:leaf", Map.of("cooking_time", 31))));
        assertDoesNotThrow(() -> RecipeTemplateReplayGuard.verify(
                Map.of("template", "test:base", "overrides", Map.of("cooking_time", 47)), List.of(source)));
        // Ordinary recipes do not inspect unrelated template definitions.
        assertDoesNotThrow(() -> RecipeTemplateReplayGuard.verify(Map.of("cooking_time", 47),
                List.of(source(Map.of("templates", Map.of("${dynamic}", Map.of("type", "expression")))))));
    }

    @Test void hiddenStatefulAndUnknownArgumentsInsideReferencedTemplatesAreRejected() {
        for (String type : List.of("craftengine:self_increase", "expression", "custom:counter")) {
            var source = source(Map.of("templates", Map.of("test:leaf", Map.of("arguments",
                    Map.of("work", Map.of("type", type, "value", 47))))));
            assertThrows(IOException.class, () -> RecipeTemplateReplayGuard.verify(
                    Map.of("template", "test:leaf"), List.of(source)), type);
        }
        var nested = source(Map.of("templates", Map.of("test:leaf", Map.of("arguments", Map.of(
                "work", Map.of("type", "map", "data", Map.of("hidden", Map.of("type", "custom:counter"))))))));
        assertThrows(IOException.class, () -> RecipeTemplateReplayGuard.verify(
                Map.of("template", "test:leaf"), List.of(nested)));
    }

    @Test void duplicateMissingDynamicFactoryAndCyclicReferencesFailExplicitly() {
        var source = source(Map.of("templates", Map.of("test:leaf", Map.of("cooking_time", 31))));
        assertThrows(IOException.class, () -> RecipeTemplateReplayGuard.verify(
                Map.of("template", "test:leaf"), List.of(source, source)));
        assertThrows(IOException.class, () -> RecipeTemplateReplayGuard.verify(
                Map.of("template", "test:missing"), List.of(source)));
        assertThrows(IOException.class, () -> RecipeTemplateReplayGuard.verify(
                Map.of("template", "${chosen}"), List.of(source)));
        var factory = source(Map.of("config-factory#template", Map.of("blueprint", Map.of(
                "templates", Map.of("test:leaf", Map.of("cooking_time", 31))), "instances", List.of(Map.of("id", "x")))));
        assertThrows(IOException.class, () -> RecipeTemplateReplayGuard.verify(
                Map.of("template", "test:leaf"), List.of(source, factory)));
        var cycle = source(Map.of("templates", Map.of("test:leaf", Map.of("template", "test:leaf"))));
        assertThrows(IOException.class, () -> RecipeTemplateReplayGuard.verify(
                Map.of("template", "test:leaf"), List.of(cycle)));
        // A factory's other item emission is part of the caller's evaluated collection too.
        assertThrows(IOException.class, () -> RecipeTemplateReplayGuard.verify(List.of(
                Map.of("cooking_time", 31), Map.of("template", "test:missing")), List.of(source)));
    }

    @Test void currentDiskDefinitionMustMatchTheExactPreviouslyLoadedDefinitionThroughTheWholeClosure() {
        var original = source(Map.of("templates", Map.of("test:base", Map.of("template", "test:leaf"),
                "test:leaf", Map.of("cooking_time", 31))));
        var changed = source(Map.of("templates", Map.of("test:base", Map.of("template", "test:leaf"),
                "test:leaf", Map.of("cooking_time", 47))));
        assertDoesNotThrow(() -> RecipeTemplateReplayGuard.verify(Map.of("template", "test:base"),
                List.of(original), List.of(original)));
        var error = assertThrows(IOException.class, () -> RecipeTemplateReplayGuard.verify(
                Map.of("template", "test:base"), List.of(changed), List.of(original)));
        assertTrue(error.getMessage().contains("已加载定义不一致"));
        assertDoesNotThrow(() -> RecipeTemplateReplayGuard.verify(Map.of("template", "test:base"),
                List.of(changed), List.of(changed)));
    }

    @Test void trackerFreezesParserInputAndPostLoadCacheCleanupDoesNotInvalidateSuccessfulManifest() {
        var delegate = new Delegate();
        var tracker = RecipeTemplateReplayGuard.tracking(delegate);
        var pack = new Pack(Path.of("test"), new PackMeta(null, null, null, "test"), true, new String[0]);
        Map<String, Object> body = new HashMap<>(Map.of("cooking_time", 31));
        delegate.clearConfigs(); // CE clears the original loader independently.
        tracker.clearConfigs();
        tracker.addConfig(new CachedConfigSection(pack, Path.of("template.yml"),
                ConfigSection.of("templates", Map.of("test:leaf", body)), Map.of()));
        body.put("cooking_time", 99); // Metadata is a deep snapshot, not a reference into CE's cached tree.
        delegate.preProcess(); delegate.loadAll(); delegate.postProcess();
        tracker.preProcess(); tracker.loadAll(); tracker.postProcess();
        RecipeTemplateReplayGuard.finishConfigurationLoad(true);
        var old = source(Map.of("templates", Map.of("test:leaf", Map.of("cooking_time", 31))));
        assertDoesNotThrow(() -> RecipeTemplateReplayGuard.verifyLoaded(Map.of("template", "test:leaf"), List.of(old)));
        delegate.clearConfigs();
        tracker.clearConfigs(); // CE also clears its cached configurations after a successful generation.
        assertDoesNotThrow(() -> RecipeTemplateReplayGuard.verifyLoaded(Map.of("template", "test:leaf"), List.of(old)));

        tracker.addConfig(new CachedConfigSection(pack, Path.of("template.yml"),
                ConfigSection.of("templates", Map.of("test:leaf", Map.of("cooking_time", 47))), Map.of()));
        assertThrows(IOException.class, () -> RecipeTemplateReplayGuard.verifyLoaded(Map.of("template", "test:leaf"), List.of(old)));
        delegate.preProcess(); delegate.loadAll(); delegate.postProcess();
        tracker.preProcess(); tracker.loadAll(); tracker.postProcess();
        RecipeTemplateReplayGuard.finishConfigurationLoad(true);
        var updated = source(Map.of("templates", Map.of("test:leaf", Map.of("cooking_time", 47))));
        assertDoesNotThrow(() -> RecipeTemplateReplayGuard.verifyLoaded(Map.of("template", "test:leaf"), List.of(updated)));
        assertThrows(IOException.class, () -> RecipeTemplateReplayGuard.verifyLoaded(Map.of("template", "test:leaf"), List.of(old)));
        RecipeTemplateReplayGuard.finishConfigurationLoad(false);
        assertThrows(IOException.class, () -> RecipeTemplateReplayGuard.verifyLoaded(Map.of("template", "test:leaf"), List.of(updated)));
        assertEquals(2, delegate.loads);
        assertEquals(2, delegate.added);
        assertEquals(2, delegate.preprocessed);
        assertEquals(2, delegate.postprocessed);
        assertEquals(2, delegate.cleared);
        assertEquals(0, tracker.count());
    }

    @Test void observerHasItsOwnConstantBoundRegistryKeyAndStageAfterTheOriginalTemplateLoader() {
        var delegate = new Delegate();
        var tracker = RecipeTemplateReplayGuard.tracking(delegate);
        Set<Key> constantKeys = new HashSet<>(Set.of(delegate.type()));
        // Removing section aliases does not remove the original constant-bound registry key.
        assertFalse(constantKeys.add(delegate.type()));
        assertTrue(constantKeys.add(tracker.type()));
        assertEquals(Key.of("kaleidoscopecookery:template_source_tracker"), tracker.type());
        assertNotSame(delegate.loadingStage(), tracker.loadingStage());
        assertSame(RecipeTemplateReplayGuard.TEMPLATE_SOURCES, tracker.loadingStage());
        assertEquals(List.of(LoadingStages.TEMPLATE), tracker.dependencies());
        assertArrayEquals(delegate.sectionId(), tracker.sectionId());
    }

    private static final class Delegate implements ConfigParser {
        int loads;
        int added;
        int preprocessed;
        int postprocessed;
        int cleared;
        int cached;
        int registered;
        private final LoadingStage stage = new LoadingStage("test template");
        @Override public Key type() { return Key.of("craftengine:template"); }
        @Override public String[] sectionId() { return new String[]{"templates"}; }
        @Override public LoadingStage loadingStage() { return stage; }
        @Override public void addConfig(CachedConfigSection config) { added++; cached += config.config.size(); }
        @Override public void preProcess() { preprocessed++; }
        @Override public void postProcess() { postprocessed++; }
        @Override public void loadAll() { loads++; registered = cached; }
        @Override public void clearConfigs() { cleared++; cached = 0; }
        @Override public int count() { return registered; }
        @Override public void setErrorHandler(Consumer<ResourceException> handler) {}
    }

    private static RecipeTemplateReplayGuard.DefinitionSource source(Map<String, Object> root) {
        return new RecipeTemplateReplayGuard.DefinitionSource("test", root);
    }
}
