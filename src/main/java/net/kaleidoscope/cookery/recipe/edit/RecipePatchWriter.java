package net.kaleidoscope.cookery.recipe.edit;

import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.ConfigValue;
import net.momirealms.craftengine.core.plugin.config.template.ArgumentString;
import net.momirealms.craftengine.core.plugin.config.template.TemplateManager;
import net.momirealms.craftengine.core.plugin.config.template.argument.PlainStringTemplateArgument;
import net.momirealms.craftengine.core.plugin.config.template.argument.TemplateArgument;
import net.momirealms.craftengine.core.plugin.config.template.argument.TemplateArguments;
import net.momirealms.craftengine.core.util.Key;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds a candidate YAML tree and verifies its generated output before the file is replaced. */
final class RecipePatchWriter {
    private static final Pattern PARAMETER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)(?::-.*)?}");

    record Saved(Map<String, Object> root, RecipeFileStore.SourceTarget target, Map<String, Object> expanded) {}
    private record Emission(String section, String id, Object value) {}

    private RecipePatchWriter() {}

    static Saved apply(Map<String, Object> original, RecipeFileStore.SourceTarget target,
                       String newNode, Key id, Map<String, Object> changes) throws IOException {
        Map<String, Object> root = copyMap(original);
        if (target == null) {
            if (at(root, newNode) != null) throw new IOException("配方位置已被占用，请重新打开编辑菜单");
            put(root, newNode, new LinkedHashMap<>(changes));
            Map<String, Object> raw = map(at(root, newNode));
            return new Saved(root, direct(newNode, raw), expandRecipe(newNode, raw, Map.of(), id));
        }
        if (!target.resolved()) throw new IOException("无法唯一定位配方的原始配置，请手动编辑原文件");
        if (!target.factory()) {
            Map<String, Object> raw = map(at(root, target.generatedNode()));
            if (raw.isEmpty()) throw new IOException("配方已不存在，请重新打开编辑菜单");
            verifySource(target, raw);
            requirePure(raw);
            Map<String, Object> patched = patchNode(raw, changes,
                    expandRecipe(target.generatedNode(), raw, Map.of(), id), isTeapotNode(newNode));
            if (!target.generatedNode().equals(newNode)) {
                if (at(root, newNode) != null) throw new IOException("新的配方位置已被占用");
                put(root, target.generatedNode(), null);
            }
            put(root, newNode, patched);
            Map<String, Object> expanded = expandRecipe(newNode, patched, Map.of(), id);
            return new Saved(root, direct(newNode, patched), expanded);
        }
        if (target.blueprintKey() == null || target.recipeKey() == null) {
            throw new IOException("工厂来源信息不完整，请重载后重新打开编辑菜单");
        }
        Map<String, Object> factory = map(root.get(target.factoryKey()));
        root.put(target.factoryKey(), factory);
        Map<String, Object> metadata = copyMap(factory);
        metadata.remove(target.instancesKey());
        verifySource(target, metadata);
        requirePure(factory);
        List<Object> instances = list(factory.get(target.instancesKey()));
        int selected = uniqueInstance(instances, target.instance());
        Map<String, Object> instance = map(instances.get(selected));
        Map<String, Object> blueprint = map(factory.get(target.blueprintKey()));
        Map<String, Object> raw = map(at(blueprint, target.recipeKey()));
        if (raw.isEmpty()) throw new IOException("无法唯一定位工厂蓝图中的配方");
        List<Emission> before = emissions(root, id.namespace());

        // The small, common case: a single scalar parameter used by this recipe only.
        if (newNode.equals(target.generatedNode()) && changes.size() == 1) {
            var change = changes.entrySet().iterator().next();
            Object value = raw.get(change.getKey());
            if (change.getValue() != null && value instanceof String text) {
                Matcher parameter = PARAMETER.matcher(text);
                if (parameter.matches() && instance.containsKey(parameter.group(1))) {
                    Map<String, Object> replacement = copyMap(instance);
                    replacement.put(parameter.group(1), change.getValue());
                    List<Object> updated = new ArrayList<>(instances);
                    updated.set(selected, replacement);
                    factory.put(target.instancesKey(), updated);
                    if (onlyExpectedChange(before, emissions(root, id.namespace()), target.generatedNode(), newNode, changes)) {
                        var savedTarget = new RecipeFileStore.SourceTarget(newNode, target.factoryKey(),
                                target.instancesKey(), selected, replacement, target.blueprintKey(),
                                target.recipeKey(), metadata);
                        return new Saved(root, savedTarget, expandRecipe(newNode, raw, replacement, id));
                    }
                    factory.put(target.instancesKey(), instances);
                }
            }
        }

        // Split in the original position, retaining every output and registration order.
        Map<String, Object> editedFactory = copyMap(factory);
        Map<String, Object> editedBlueprint = map(editedFactory.get(target.blueprintKey()));
        String oldRawPath = target.recipeKey();
        String editedRawPath = oldRawPath;
        if (!newNode.equals(target.generatedNode())) {
            int split = oldRawPath.lastIndexOf('.');
            editedRawPath = oldRawPath.substring(0, split + 1) + id.asString();
            if (at(editedBlueprint, editedRawPath) != null) throw new IOException("工厂蓝图中新的配方位置已存在");
            put(editedBlueprint, oldRawPath, null);
        }
        put(editedBlueprint, editedRawPath, patchNode(raw, changes,
                expandRecipe(target.generatedNode(), raw, instance, id), isTeapotNode(newNode)));
        editedFactory.put(target.blueprintKey(), editedBlueprint);
        editedFactory.put(target.instancesKey(), new ArrayList<>(List.of(copyMap(instance))));

        String editedKey = uniqueKey(root, splitKey(target.factoryKey(), "recipe_edit"));
        String afterKey = uniqueKey(root, splitKey(target.factoryKey(), "recipe_after"));
        LinkedHashMap<String, Object> replacementRoot = new LinkedHashMap<>();
        for (var entry : root.entrySet()) {
            if (!entry.getKey().equals(target.factoryKey())) {
                replacementRoot.put(entry.getKey(), entry.getValue());
                continue;
            }
            if (selected > 0) {
                Map<String, Object> prefix = copyMap(factory);
                prefix.put(target.instancesKey(), new ArrayList<>(instances.subList(0, selected)));
                replacementRoot.put(target.factoryKey(), prefix);
            }
            replacementRoot.put(editedKey, editedFactory);
            if (selected + 1 < instances.size()) {
                Map<String, Object> suffix = copyMap(factory);
                suffix.put(target.instancesKey(), new ArrayList<>(instances.subList(selected + 1, instances.size())));
                replacementRoot.put(afterKey, suffix);
            }
        }
        if (!onlyExpectedChange(before, emissions(replacementRoot, id.namespace()), target.generatedNode(), newNode, changes)) {
            throw new IOException("无法确认修改仅影响目标配方，请手动编辑原文件");
        }
        Map<String, Object> savedMetadata = copyMap(editedFactory);
        savedMetadata.remove(target.instancesKey());
        var savedTarget = new RecipeFileStore.SourceTarget(newNode, editedKey, target.instancesKey(), 0,
                instance, target.blueprintKey(), editedRawPath, savedMetadata);
        return new Saved(replacementRoot, savedTarget,
                expandRecipe(newNode, map(at(editedBlueprint, editedRawPath)), instance, id));
    }

    private static void verifySource(RecipeFileStore.SourceTarget target, Map<String, Object> actual) throws IOException {
        if (target.originalSource().isEmpty() || !target.originalSource().equals(actual)) {
            throw new IOException("原配方配置已变化，请重载并重新打开编辑菜单");
        }
    }

    static RecipeFileStore.SourceTarget direct(String node, Map<String, Object> raw) {
        return new RecipeFileStore.SourceTarget(node, null, null, -1, Map.of(), null, null, raw);
    }

    private static int uniqueInstance(List<Object> instances, Map<String, Object> expected) throws IOException {
        int selected = -1;
        for (int index = 0; index < instances.size(); index++) {
            if (!map(instances.get(index)).equals(expected)) continue;
            if (selected >= 0) throw new IOException("存在多个相同工厂实例，无法安全定位，请手动编辑原文件");
            selected = index;
        }
        if (selected < 0) throw new IOException("工厂实例已变化，请重新打开编辑菜单");
        return selected;
    }

    private static String uniqueKey(Map<String, Object> root, String base) {
        int index = 1;
        String key = base;
        while (root.containsKey(key)) key = base + "_" + index++;
        return key;
    }

    private static String splitKey(String original, String suffix) {
        return original + (original.indexOf('#') < 0 ? "#" : "_") + suffix;
    }

    static String[] aliases(String key) {
        return switch (key) {
            case "cooking_time" -> new String[]{"cooking_time", "cooking-time"};
            case "time" -> new String[]{"time", "cooking_time", "cooking-time"};
            case "stir_fry_count" -> new String[]{"stir_fry_count", "stir-fry-count"};
            case "result_count" -> new String[]{"result_count", "result-count"};
            case "use_equivalent_foods" -> new String[]{"use_equivalent_foods", "use-equivalent-foods"};
            case "use_seasonings" -> new String[]{"use_seasonings", "use-seasonings"};
            default -> new String[]{key};
        };
    }

    private static boolean isTeapotNode(String node) {
        return node.startsWith("teapot_result.") || node.startsWith("teapot-result.");
    }

    private static String[] aliases(String key, boolean teapot) {
        return teapot && key.equals("cooking_time") ? aliases("time") : aliases(key);
    }

    private static Map<String, Object> patchNode(Map<String, Object> source, Map<String, Object> changes,
                                                 Map<String, Object> expandedOriginal, boolean teapot) {
        Map<String, Object> result = copyMap(source);
        boolean template = result.containsKey("template") || result.containsKey("templates");
        Map<String, Object> overrides = map(result.get("overrides"));
        Map<String, Object> merges = map(result.get("merges"));
        for (var change : changes.entrySet()) {
            for (String alias : aliases(change.getKey(), teapot)) {
                result.remove(alias);
                overrides.remove(alias);
                merges.remove(alias);
            }
            if (change.getValue() != null) {
                (template ? overrides : result).put(change.getKey(), normalize(change.getValue()));
                if (template) {
                    for (String alias : aliases(change.getKey(), teapot)) {
                        if (expandedOriginal.containsKey(alias)) overrides.put(alias, normalize(change.getValue()));
                    }
                }
            }
        }
        if (template) {
            if (overrides.isEmpty()) result.remove("overrides"); else result.put("overrides", overrides);
            if (merges.isEmpty()) result.remove("merges"); else result.put("merges", merges);
        }
        return result;
    }

    private static boolean onlyExpectedChange(List<Emission> before, List<Emission> after, String oldNode,
                                               String newNode, Map<String, Object> changes) {
        if (before.size() != after.size()) return false;
        int changed = 0;
        Set<String> allowed = new HashSet<>();
        changes.keySet().forEach(key -> allowed.addAll(List.of(aliases(key, isTeapotNode(newNode)))));
        for (int index = 0; index < before.size(); index++) {
            Emission first = before.get(index), next = after.get(index);
            if (first.equals(next)) continue;
            if (!(first.section() + "." + first.id()).equals(oldNode)
                    || !(next.section() + "." + next.id()).equals(newNode)) return false;
            Map<String, Object> firstBody = map(first.value()), nextBody = map(next.value());
            allowed.forEach(key -> { firstBody.remove(key); nextBody.remove(key); });
            if (!firstBody.equals(nextBody)) return false;
            changed++;
        }
        return changed <= 1;
    }

    private static List<Emission> emissions(Map<String, Object> root, String namespace) throws IOException {
        List<Emission> result = new ArrayList<>();
        for (var rootEntry : root.entrySet()) {
            String base = rootEntry.getKey().split("#", 2)[0].replace('-', '_');
            if (!Set.of("config_factory", "config_factories").contains(base)) continue;
            Map<String, Object> factory = map(rootEntry.getValue());
            String instanceKey = first(factory, "instances", "instance", "inputs", "input");
            String blueprintKey = first(factory, "blueprint", "prototype", "schema");
            if (instanceKey == null || blueprintKey == null) throw new IOException("工厂结构不完整");
            requirePure(factory);
            for (Object instanceObject : list(factory.get(instanceKey))) {
                Map<String, Object> instance = map(instanceObject);
                Map<String, TemplateArgument> arguments = arguments(instance);
                for (var section : map(factory.get(blueprintKey)).entrySet()) {
                    if (!(section.getValue() instanceof Map<?, ?>)) {
                        result.add(new Emission(section.getKey(), "", TemplateManager.INSTANCE.applyTemplates(
                                ConfigValue.of(section.getKey(), section.getValue()), arguments)));
                        continue;
                    }
                    for (var entry : map(section.getValue()).entrySet()) {
                        Object expandedKey = ArgumentString.preParse(section.getKey(), entry.getKey()).get(section.getKey(), arguments);
                        if (expandedKey == null) throw new IOException("无法定位条件生成的工厂键");
                        Key id = Key.withDefaultNamespace(expandedKey.toString(), namespace);
                        Map<String, TemplateArgument> itemArguments = new HashMap<>(arguments);
                        itemArguments.put("__NAMESPACE__", PlainStringTemplateArgument.plain(id.namespace()));
                        itemArguments.put("__ID__", PlainStringTemplateArgument.plain(id.value()));
                        result.add(new Emission(section.getKey(), expandedKey.toString(), TemplateManager.INSTANCE.applyTemplates(
                                ConfigValue.of(section.getKey() + "." + expandedKey, entry.getValue()), itemArguments)));
                    }
                }
            }
        }
        return result;
    }

    private static Map<String, TemplateArgument> arguments(Map<String, Object> instance) throws IOException {
        Map<String, TemplateArgument> arguments = new HashMap<>();
        for (var entry : instance.entrySet()) {
            requireKnownArgument(entry.getValue());
            arguments.put(entry.getKey(), TemplateArguments.fromConfig(ConfigValue.of(entry.getKey(), entry.getValue())));
        }
        return arguments;
    }

    private static Map<String, Object> expandRecipe(String node, Map<String, Object> raw,
                                                    Map<String, Object> instance, Key id) throws IOException {
        Map<String, TemplateArgument> arguments = arguments(instance);
        arguments.put("__NAMESPACE__", PlainStringTemplateArgument.plain(id.namespace()));
        arguments.put("__ID__", PlainStringTemplateArgument.plain(id.value()));
        Map<String, Object> expanded = map(TemplateManager.INSTANCE.applyTemplates(ConfigValue.of(node, raw), arguments));
        ConfigSection section = ConfigSection.of(node, expanded);
        net.kaleidoscope.cookery.recipe.RecipeProcessingFields.optionalPositive(section,
                aliases("cooking_time", isTeapotNode(node)));
        net.kaleidoscope.cookery.recipe.RecipeProcessingFields.optionalPositive(section, "stir_fry_count", "stir-fry-count");
        return expanded;
    }

    private static void requirePure(Object value) throws IOException {
        if (value instanceof Map<?, ?> object) {
            Object type = object.get("type");
            // A typed template argument is allowed only when its evaluation is demonstrably pure.
            if (type instanceof String text && (text.contains("self_increase") || text.equals("expression"))) {
                throw new IOException("含状态型或表达式模板参数，无法安全自动保存，请手动编辑原文件");
            }
            Object arguments = object.get("arguments");
            if (arguments instanceof Map<?, ?> parameters) {
                for (Object parameter : parameters.values()) requireKnownArgument(parameter);
            }
            for (Object child : object.values()) requirePure(child);
        } else if (value instanceof List<?> list) {
            for (Object child : list) requirePure(child);
        }
    }

    private static void requireKnownArgument(Object value) throws IOException {
        if (!(value instanceof Map<?, ?> argument)) return;
        Object rawType = argument.get("type");
        if (rawType == null) return;
        String type = rawType.toString();
        if (type.startsWith("craftengine:")) type = type.substring("craftengine:".length());
        if (!Set.of("plain", "map", "list", "null", "to_upper_case", "to_lower_case", "capitalize", "object", "when", "condition").contains(type)) {
            throw new IOException("模板参数类型 " + rawType + " 无法安全重放，请手动编辑原文件");
        }
        requirePure(value);
    }

    private static String first(Map<String, Object> map, String... keys) {
        for (String key : keys) if (map.containsKey(key)) return key;
        return null;
    }

    private static Object at(Map<String, Object> root, String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(part);
        }
        return current;
    }

    private static void put(Map<String, Object> root, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = root;
        for (int index = 0; index < parts.length - 1; index++) {
            Object child = current.get(parts[index]);
            if (!(child instanceof Map<?, ?>)) current.put(parts[index], new LinkedHashMap<String, Object>());
            current = (Map<String, Object>) current.get(parts[index]);
        }
        if (value == null) current.remove(parts[parts.length - 1]);
        else current.put(parts[parts.length - 1], normalize(value));
    }

    private static List<Object> list(Object value) {
        if (!(value instanceof List<?> list)) return new ArrayList<>();
        List<Object> result = new ArrayList<>();
        list.forEach(item -> result.add(normalize(item)));
        return result;
    }

    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> map)) return new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(key.toString(), normalize(item)));
        return result;
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) { return map(source); }

    private static Object normalize(Object value) {
        if (value instanceof Map<?, ?>) return map(value);
        if (value instanceof List<?>) return list(value);
        return value;
    }
}
