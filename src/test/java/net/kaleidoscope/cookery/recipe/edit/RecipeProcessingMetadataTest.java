package net.kaleidoscope.cookery.recipe.edit;

import net.momirealms.craftengine.core.util.Key;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static net.kaleidoscope.cookery.recipe.edit.RecipeProcessingMetadata.Origin.*;
import static org.junit.jupiter.api.Assertions.*;

class RecipeProcessingMetadataTest {
    private static final String NODE = "accurate_foods.test:x";
    private static final Key ID = Key.of("test:x");

    @Test void directLocalFieldRestoresApplianceDefaultWithoutChangingSourceIdentity() {
        var original = direct(NODE, Map.of("cook", "steamer", "cooking_time", 47));
        var captured = RecipeProcessingMetadata.capture(original, ID,
                Map.of("cook", "steamer", "cooking_time", 47));
        var setting = captured.processing().get("cooking_time");
        assertEquals(RECIPE, setting.origin());
        assertEquals(47, setting.configuredValue());
        assertEquals(0, setting.inheritedValue());
        assertTrue(setting.inheritanceKnown());
        assertEquals(DEFAULT, setting.inheritedOrigin());
        assertEquals(original, captured);
        assertEquals(original.hashCode(), captured.hashCode());
        assertThrows(UnsupportedOperationException.class, () -> captured.processing().clear());
    }

    @Test void templateCurrentValueIsLoadedDataAndLocalRestoreDoesNotEvaluateAnUnknownTemplate() {
        // The reference deliberately does not exist. Metadata must never evaluate it.
        var inherited = RecipeProcessingMetadata.capture(direct(NODE, Map.of("template", "test:unknown")),
                ID, Map.of("cook", "steamer", "cooking_time", 31)).processing().get("cooking_time");
        assertEquals(TEMPLATE, inherited.origin());
        assertEquals("test:unknown", inherited.reference());
        assertTrue(inherited.inheritanceKnown());
        assertEquals(31, inherited.inheritedValue());

        var local = RecipeProcessingMetadata.capture(direct(NODE, Map.of("template", "test:unknown",
                        "overrides", Map.of("cooking_time", 47))), ID,
                Map.of("cook", "steamer", "cooking_time", 47)).processing().get("cooking_time");
        assertEquals(TEMPLATE_OVERRIDE, local.origin());
        assertFalse(local.inheritanceKnown());
        assertEquals(TEMPLATE, local.inheritedOrigin());
        assertEquals("test:unknown", local.inheritedReference());

        var overlapping = RecipeProcessingMetadata.capture(direct(NODE, Map.of("template", "test:unknown",
                        "cooking_time", 19, "overrides", Map.of("cooking_time", 47))), ID,
                Map.of("cook", "steamer", "cooking_time", 47)).processing().get("cooking_time");
        assertEquals(TEMPLATE_OVERRIDE, overlapping.origin());
        assertEquals(47, overlapping.configuredValue());
    }

    @Test void factoryInstanceParameterAndLiteralBlueprintAreDistinguished() {
        var parameter = RecipeProcessingMetadata.capture(factory(Map.of("cook", "steamer",
                        "cooking_time", "${work}")), ID,
                Map.of("cook", "steamer", "cooking_time", 47)).processing().get("cooking_time");
        assertEquals(FACTORY_PARAMETER, parameter.origin());
        assertEquals("work", parameter.reference());
        assertTrue(parameter.inheritanceKnown());
        assertEquals(0, parameter.inheritedValue());

        var literal = RecipeProcessingMetadata.capture(factory(Map.of("cook", "steamer",
                        "cooking-time", 19)), ID,
                Map.of("cook", "steamer", "cooking-time", 19)).processing().get("cooking_time");
        assertEquals(FACTORY, literal.origin());
    }

    @Test void onlyTeapotUsesLegacyTimeAndEveryApplianceUsesItsOwnWorkField() {
        var accurate = RecipeProcessingMetadata.capture(direct(NODE, Map.of("cook", "steamer", "time", 47)),
                ID, Map.of("cook", "steamer", "time", 47)).processing().get("cooking_time");
        assertEquals(0, accurate.configuredValue());
        assertEquals(DEFAULT, accurate.origin());
        var tea = RecipeProcessingMetadata.capture(direct("teapot_result.test:x", Map.of("time", 47)),
                ID, Map.of("time", 47)).processing().get("cooking_time");
        assertEquals(47, tea.configuredValue());
        assertEquals(RECIPE, tea.origin());
        assertTrue(tea.inheritanceKnown());
        assertEquals(0, tea.inheritedValue());
        assertNotNull(RecipeProcessingMetadata.capture(direct(NODE, Map.of("cook", "millstone", "rotations", 3)),
                ID, Map.of("cook", "millstone", "rotations", 3)).processing().get("rotations"));
        assertNotNull(RecipeProcessingMetadata.capture(direct("pot_flex_foods.test:x", Map.of("stir-fry-count", 3)),
                ID, Map.of("stir-fry-count", 3)).processing().get("stir_fry_count"));
        assertNotNull(RecipeProcessingMetadata.capture(direct("chopping_board_raws.test:x", Map.of("stage", 3)),
                ID, Map.of("stage", 3)).processing().get("stage"));
    }

    private static RecipeFileStore.SourceTarget direct(String node, Map<String, Object> raw) {
        return new RecipeFileStore.SourceTarget(node, null, null, -1, Map.of(), null, null, raw);
    }

    private static RecipeFileStore.SourceTarget factory(Map<String, Object> raw) {
        return new RecipeFileStore.SourceTarget(NODE, "config_factory#test", "instances", 0,
                Map.of("work", 47), "blueprint", "accurate_foods.test:x",
                Map.of("blueprint", Map.of("accurate_foods", Map.of("test:x", raw))));
    }
}
