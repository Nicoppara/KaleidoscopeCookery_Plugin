package net.kaleidoscope.cookery.recipe.edit;

import net.momirealms.craftengine.core.util.Key;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** Immutable menu provenance, captured by configuration loading or the asynchronous file editor. */
public final class RecipeProcessingMetadata {
    public enum Origin { RECIPE, TEMPLATE, TEMPLATE_OVERRIDE, FACTORY, FACTORY_PARAMETER, DEFAULT }

    public record Setting(int configuredValue, Origin origin, String reference,
                          int inheritedValue, boolean inheritanceKnown,
                          Origin inheritedOrigin, String inheritedReference) {}

    private static final Pattern PARAMETER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)(?::-.*)?}");

    private RecipeProcessingMetadata() {}

    /** Capture only loaded data; informational previews must never evaluate template arguments again. */
    static RecipeFileStore.SourceTarget capture(RecipeFileStore.SourceTarget target, Key id,
                                                Map<String, Object> expanded) {
        if (target == null || expanded == null || !target.resolved()) return target;
        String field = field(target.generatedNode(), expanded);
        if (field == null) return target;
        String[] aliases = aliases(field, target.generatedNode());
        Map<String, Object> raw = rawRecipe(target);
        if (raw.isEmpty()) return target;
        int configured = positive(expanded, aliases);
        String templates = templateReferences(raw);
        Origin origin = origin(raw, target, aliases, configured, templates);
        String reference = reference(raw, target, aliases, origin, templates);
        int inherited = configured;
        boolean known = true;
        Origin inheritedOrigin = origin;
        String inheritedReference = reference;
        if (containsLocal(raw, aliases)) {
            Map<String, Object> withoutLocal = copy(raw);
            removeLocal(withoutLocal, aliases);
            String inheritedTemplates = templateReferences(withoutLocal);
            inheritedOrigin = inheritedTemplates.isEmpty() ? Origin.DEFAULT : Origin.TEMPLATE;
            inheritedReference = inheritedTemplates;
            if (inheritedTemplates.isEmpty()) {
                inherited = 0;
            } else {
                // CE's public API does not expose the inherited field before local overrides.
                // Re-expanding here could advance a stateful argument hidden inside the template.
                // The safe file editor resolves the actual inherited value when saving.
                known = false;
            }
        }
        return target.withProcessing(Map.of(field, new Setting(configured, origin, reference,
                inherited, known, inheritedOrigin, inheritedReference)));
    }

    private static String field(String node, Map<String, Object> expanded) {
        if (node == null || !node.contains(".")) return null;
        String section = node.substring(0, node.indexOf('.')).split("#", 2)[0].replace('-', '_');
        return switch (section) {
            case "accurate_foods" -> "millstone".equalsIgnoreCase(String.valueOf(expanded.get("cook"))) ? "rotations" : "cooking_time";
            case "pot_flex_foods" -> "stir_fry_count";
            case "stock_flex_foods", "teapot_result" -> "cooking_time";
            case "chopping_board_raws" -> "stage";
            default -> null;
        };
    }

    private static String[] aliases(String field, String node) {
        if (field.equals("cooking_time")) return node.startsWith("teapot_result") || node.startsWith("teapot-result")
                ? new String[]{"cooking_time", "cooking-time", "time"}
                : new String[]{"cooking_time", "cooking-time"};
        return field.equals("stir_fry_count") ? new String[]{"stir_fry_count", "stir-fry-count"}
                : new String[]{field};
    }

    private static Origin origin(Map<String, Object> raw, RecipeFileStore.SourceTarget target,
                                  String[] aliases, int configured, String templates) {
        if (configured <= 0) return Origin.DEFAULT;
        if (!templates.isEmpty()) {
            // CE applies ordinary fields, then overrides, then merges for map templates.
            for (String key : List.of("merges", "overrides")) {
                if (!(raw.get(key) instanceof Map<?, ?> map)) continue;
                for (String alias : aliases) {
                    if (map.containsKey(alias)) return Origin.TEMPLATE_OVERRIDE;
                }
            }
        }
        for (String alias : aliases) {
            if (!raw.containsKey(alias)) continue;
            if (parameter(raw.get(alias), target.instance()) != null) return Origin.FACTORY_PARAMETER;
            return target.factory() ? Origin.FACTORY : Origin.RECIPE;
        }
        if (containsLocal(raw, aliases)) return templates.isEmpty()
                ? (target.factory() ? Origin.FACTORY : Origin.RECIPE) : Origin.TEMPLATE_OVERRIDE;
        return !templates.isEmpty() ? Origin.TEMPLATE : target.factory() ? Origin.FACTORY : Origin.RECIPE;
    }

    private static String reference(Map<String, Object> raw, RecipeFileStore.SourceTarget target,
                                    String[] aliases, Origin origin, String templates) {
        if (origin == Origin.FACTORY_PARAMETER) {
            for (String alias : aliases) {
                String parameter = parameter(raw.get(alias), target.instance());
                if (parameter != null) return parameter;
            }
        }
        return origin == Origin.TEMPLATE || origin == Origin.TEMPLATE_OVERRIDE ? templates : "";
    }

    private static String parameter(Object value, Map<String, Object> instance) {
        if (!(value instanceof String string)) return null;
        var matcher = PARAMETER.matcher(string);
        return matcher.matches() && instance.containsKey(matcher.group(1)) ? matcher.group(1) : null;
    }

    private static boolean containsLocal(Map<String, Object> raw, String[] aliases) {
        if (Arrays.stream(aliases).anyMatch(raw::containsKey)) return true;
        for (String key : List.of("overrides", "merges")) {
            if (raw.get(key) instanceof Map<?, ?> map && Arrays.stream(aliases).anyMatch(map::containsKey)) return true;
        }
        return false;
    }

    private static void removeLocal(Map<String, Object> raw, String[] aliases) {
        for (String alias : aliases) raw.remove(alias);
        for (String key : List.of("overrides", "merges")) {
            if (raw.get(key) instanceof Map<?, ?> map) for (String alias : aliases) map.remove(alias);
        }
    }

    private static Map<String, Object> rawRecipe(RecipeFileStore.SourceTarget target) {
        Object raw = target.originalSource();
        if (target.factory()) {
            raw = target.originalSource().get(target.blueprintKey());
            if (target.recipeKey() == null) return Map.of();
            for (String part : target.recipeKey().split("\\.")) {
                if (!(raw instanceof Map<?, ?> map)) return Map.of();
                raw = map.get(part);
            }
        }
        return raw instanceof Map<?, ?> map ? copy(map) : Map.of();
    }

    private static String templateReferences(Map<String, Object> raw) {
        Object template = raw.getOrDefault("template", raw.get("templates"));
        if (template instanceof Collection<?> list) return String.join(", ", list.stream().map(String::valueOf).toList());
        return template == null ? "" : String.valueOf(template);
    }

    private static int positive(Map<?, ?> node, String[] aliases) {
        for (String alias : aliases) {
            Object value = node.get(alias);
            if (value == null) continue;
            try { return Math.max(0, new BigDecimal(String.valueOf(value)).intValueExact()); }
            catch (NumberFormatException | ArithmeticException ignored) { return 0; }
        }
        return 0;
    }

    private static Map<String, Object> copy(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), copyValue(value)));
        return result;
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) return copy(map);
        if (value instanceof List<?> list) return new ArrayList<>(list.stream().map(RecipeProcessingMetadata::copyValue).toList());
        return value;
    }
}
