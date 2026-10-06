package net.kaleidoscope.cookery.recipe.edit;

import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.pack.CachedConfigSection;
import net.momirealms.craftengine.core.pack.PackManager;
import net.momirealms.craftengine.core.plugin.config.ConfigParser;
import net.momirealms.craftengine.core.plugin.config.ResourceException;
import net.momirealms.craftengine.core.plugin.config.lifecycle.LoadingStage;
import net.momirealms.craftengine.core.plugin.config.lifecycle.LoadingStages;
import net.momirealms.craftengine.core.plugin.config.template.TemplateManager;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/** Raw-data checks only. Called by the asynchronous editor while it holds CE's resource lease. */
public final class RecipeTemplateReplayGuard {
    public static final LoadingStage TEMPLATE_SOURCES = new LoadingStage("cookery template source snapshot");
    record DefinitionSource(String namespace, Map<String, Object> root) {}
    private record Index(Map<String, List<Object>> definitions, Set<String> generated, boolean dynamicDefinitions) {}
    private static final Set<String> KNOWN_ARGUMENTS = Set.of("plain", "map", "list", "null",
            "to_upper_case", "to_lower_case", "capitalize", "object", "when", "condition");
    private static final Object TRACKING_LOCK = new Object();
    private static final List<DefinitionSource> STAGED = new ArrayList<>();
    private static volatile Index loadedDefinitions;
    private static volatile boolean manifestReady;
    private static boolean templateLoadFinished;
    private static boolean templateLoadFailed;
    private static boolean pendingNewGeneration = true;
    private static boolean trackerRegistered;

    private RecipeTemplateReplayGuard() {}

    /** Observe CE's public parser input without changing its behavior, stage, or arguments. */
    public static synchronized void registerTracker(PackManager manager) {
        if (trackerRegistered) return;
        ConfigParser delegate = TemplateManager.INSTANCE.parser();
        manager.unregisterConfigSectionParser(delegate);
        if (!manager.registerConfigSectionParser(tracking(delegate))) {
            // The false result means a section alias is already occupied; re-registering the
            // original type would collide with CE's permanent constant-bound registry entry.
            throw new IllegalStateException("无法登记模板来源检查器");
        }
        trackerRegistered = true;
    }

    static ConfigParser tracking(ConfigParser delegate) { return new TrackingParser(delegate); }

    /** Called with the recipe snapshot commit/rollback; a failed CE generation remains non-editable. */
    public static void finishConfigurationLoad(boolean success) {
        synchronized (TRACKING_LOCK) {
            manifestReady = false;
            if (!success || !templateLoadFinished || templateLoadFailed) return;
            try {
                loadedDefinitions = index(List.copyOf(STAGED));
                manifestReady = true;
            } catch (IOException invalid) {
                // CE already reports invalid template identifiers. Refuse replay until a successful reload.
            }
        }
    }

    private static final class TrackingParser implements ConfigParser {
        private final ConfigParser delegate;
        TrackingParser(ConfigParser delegate) { this.delegate = delegate; }
        @Override public Key type() { return Key.of("kaleidoscopecookery:template_source_tracker"); }
        @Override public String[] sectionId() { return delegate.sectionId(); }
        @Override public LoadingStage loadingStage() { return TEMPLATE_SOURCES; }
        @Override public List<LoadingStage> dependencies() { return List.of(LoadingStages.TEMPLATE); }
        @Override public int count() { return 0; }
        @Override public boolean silentIfNotExists() { return delegate.silentIfNotExists(); }
        @Override public boolean async() { return delegate.async(); }
        @Override public void preProcess() { beginStagingIfCleared(); }
        @Override public void postProcess() {}
        @Override public void clearConfigs() {
            // CE clears cached configurations after successful loading as well as before it.
            // Discard the previous staging only when the next generation actually starts.
            synchronized (TRACKING_LOCK) { pendingNewGeneration = true; }
        }
        @Override public void addConfig(CachedConfigSection cached) {
            beginStagingIfCleared();
            @SuppressWarnings("unchecked") Map<String, Object> raw = (Map<String, Object>) plain(cached.config.values());
            synchronized (TRACKING_LOCK) {
                STAGED.add(new DefinitionSource(cached.pack.namespace(), Map.of("templates", raw)));
            }
            delegate.addConfig(cached);
        }
        @Override public void loadAll() {
            beginStagingIfCleared();
            synchronized (TRACKING_LOCK) {
                // The original IdConfigParser remains the sole loader, including CE's own
                // clearIdToPath step. This stage only observes its completed registration.
                long expected = STAGED.stream().mapToLong(source -> ((Map<?, ?>) source.root().get("templates")).size()).sum();
                templateLoadFailed |= expected != delegate.count();
                templateLoadFinished = true;
            }
        }
        @Override public void setErrorHandler(Consumer<ResourceException> handler) {
            delegate.setErrorHandler(error -> {
                synchronized (TRACKING_LOCK) { templateLoadFailed = true; }
                handler.accept(error);
            });
        }
    }

    private static void beginStagingIfCleared() {
        synchronized (TRACKING_LOCK) {
            if (!pendingNewGeneration) return;
            STAGED.clear();
            manifestReady = false;
            templateLoadFinished = false;
            templateLoadFailed = false;
            pendingNewGeneration = false;
        }
    }

    static void verify(Map<String, Object> root, RecipeFileStore.SourceTarget target,
                       Map<String, Object> changes) throws IOException {
        List<Object> evaluated = new ArrayList<>();
        evaluated.add(changes);
        if (target != null) {
            if (target.factory()) {
                // Factory verification replays every emission in the original file, including other items.
                root.forEach((key, value) -> {
                    if (!factorySection(key) || !(value instanceof Map<?, ?> factory)) return;
                    Object blueprint = first(factory, "blueprint", "prototype", "schema");
                    if (!(blueprint instanceof Map<?, ?> sections)) return;
                    for (Object section : sections.values()) {
                        if (section instanceof Map<?, ?> nodes) evaluated.addAll(nodes.values());
                        else evaluated.add(section);
                    }
                });
            } else evaluated.add(at(root, target.generatedNode()));
        }
        if (!hasReference(evaluated)) return;
        requireManifest();
        List<DefinitionSource> sources = new ArrayList<>();
        Set<Path> scanned = new HashSet<>();
        for (var pack : CraftEngine.instance().packManager().loadedPacks()) {
            if (!pack.enabled()) continue;
            for (Path folder : pack.configurationFolders()) {
                if (!Files.isDirectory(folder)) continue;
                try (var files = Files.walk(folder)) {
                    for (Path file : files.filter(Files::isRegularFile).filter(RecipeTemplateReplayGuard::yaml).toList()) {
                        if (!scanned.add(file.toAbsolutePath().normalize())) continue;
                        YamlConfiguration configuration = new YamlConfiguration();
                        try { configuration.load(file.toFile()); }
                        catch (InvalidConfigurationException malformed) {
                            throw new IOException("无法检查模板来源，配置格式错误：" + file, malformed);
                        }
                        sources.add(new DefinitionSource(pack.namespace(), map(configuration)));
                    }
                }
            }
        }
        verifyLoaded(evaluated, sources);
    }

    /** Pure-data entry for tests: no server, template evaluation, or file IO. */
    static void verify(Object evaluated, List<DefinitionSource> sources) throws IOException {
        if (!hasReference(evaluated)) return;
        Index index = index(sources);
        check(evaluated, index, null, new HashSet<>(), new HashSet<>());
    }

    static void verify(Object evaluated, List<DefinitionSource> current, List<DefinitionSource> loaded) throws IOException {
        if (!hasReference(evaluated)) return;
        check(evaluated, index(current), index(loaded), new HashSet<>(), new HashSet<>());
    }

    static void verifyLoaded(Object evaluated, List<DefinitionSource> current) throws IOException {
        check(evaluated, index(current), requireManifest(), new HashSet<>(), new HashSet<>());
    }

    private static Index requireManifest() throws IOException {
        Index loaded = loadedDefinitions;
        if (!manifestReady || loaded == null) throw new IOException("模板来源尚未完成成功加载，请先成功重载 CraftEngine 再保存");
        return loaded;
    }

    private static Index index(List<DefinitionSource> sources) throws IOException {
        Map<String, List<Object>> definitions = new HashMap<>();
        Set<String> generated = new HashSet<>();
        boolean dynamic = false;
        for (DefinitionSource source : sources) {
            for (var entry : source.root().entrySet()) {
                if (templateSection(entry.getKey()) && entry.getValue() instanceof Map<?, ?> templates) {
                    for (var template : templates.entrySet()) {
                        String rawId = String.valueOf(template.getKey());
                        if (rawId.contains("${")) { dynamic = true; continue; }
                        String id = definitionId(rawId, source.namespace());
                        definitions.computeIfAbsent(id, ignored -> new ArrayList<>()).add(template.getValue());
                    }
                }
                if (!factorySection(entry.getKey()) || !(entry.getValue() instanceof Map<?, ?> factory)) continue;
                Object blueprint = first(factory, "blueprint", "prototype", "schema");
                if (!(blueprint instanceof Map<?, ?> sections)) continue;
                for (var section : sections.entrySet()) {
                    if (!templateSection(String.valueOf(section.getKey()))) continue;
                    if (!(section.getValue() instanceof Map<?, ?> templates)) { dynamic = true; continue; }
                    for (Object key : templates.keySet()) {
                        String rawId = String.valueOf(key);
                        if (rawId.contains("${")) dynamic = true;
                        else generated.add(definitionId(rawId, source.namespace()));
                    }
                }
            }
        }
        Map<String, List<Object>> immutable = new HashMap<>();
        definitions.forEach((key, values) -> immutable.put(key,
                Collections.unmodifiableList(new ArrayList<>(values))));
        return new Index(Map.copyOf(immutable), Set.copyOf(generated), dynamic);
    }

    private static void check(Object value, Index index, Index loaded, Set<String> visiting, Set<String> verified) throws IOException {
        if (value instanceof Map<?, ?> object) {
            rejectStateful(object.get("type"));
            if (object.get("arguments") instanceof Map<?, ?> arguments) {
                for (Object argument : arguments.values()) knownArgument(argument);
            }
            Object references = object.get("template");
            if (references == null) references = object.get("templates");
            if (references != null) {
                for (Object reference : references instanceof Collection<?> list ? list : List.of(references)) {
                    if (!(reference instanceof String text) || text.isBlank() || text.contains("${")) {
                        throw new IOException("模板引用为动态值，无法安全自动保存，请手动编辑原文件");
                    }
                    String id = definitionId(text, "minecraft"); // TemplateManager resolves references through Key.of.
                    if (index.dynamicDefinitions()) throw new IOException("存在动态生成的模板定义，无法确认模板 " + id + " 的唯一来源");
                    if (index.generated().contains(id)) throw new IOException("模板 " + id + " 由工厂生成，无法唯一定位原定义，请手动编辑原文件");
                    List<Object> definitions = index.definitions().get(id);
                    if (definitions == null || definitions.size() != 1) {
                        throw new IOException("模板 " + id + " 的原定义缺失或重复，无法安全自动保存，请先重载或手动编辑原文件");
                    }
                    if (loaded != null && !definitions.equals(loaded.definitions().get(id))) {
                        throw new IOException("模板 " + id + " 的原文件与已加载定义不一致，请先成功重载 CraftEngine 再保存");
                    }
                    if (verified.contains(id)) continue;
                    if (!visiting.add(id)) throw new IOException("模板引用形成循环：" + id);
                    check(definitions.getFirst(), index, loaded, visiting, verified);
                    visiting.remove(id);
                    verified.add(id);
                }
            }
            for (Object child : object.values()) check(child, index, loaded, visiting, verified);
        } else if (value instanceof Collection<?> list) {
            for (Object child : list) check(child, index, loaded, visiting, verified);
        }
    }

    private static void knownArgument(Object value) throws IOException {
        if (value instanceof Map<?, ?> map) {
            Object rawType = map.get("type");
            if (rawType != null) {
                String type = normalizeType(rawType);
                if (!KNOWN_ARGUMENTS.contains(type)) throw new IOException("模板参数类型 " + rawType + " 无法安全重放，请手动编辑原文件");
            }
            for (Object child : map.values()) knownArgument(child);
        } else if (value instanceof Collection<?> list) {
            for (Object child : list) knownArgument(child);
        }
    }

    private static void rejectStateful(Object type) throws IOException {
        if (type == null) return;
        String text = normalizeType(type);
        if (text.contains("self_increase") || text.equals("expression")) {
            throw new IOException("模板中含状态型或表达式参数，无法安全自动保存，请手动编辑原文件");
        }
    }

    private static String normalizeType(Object type) {
        String text = String.valueOf(type);
        return text.startsWith("craftengine:") ? text.substring("craftengine:".length()) : text;
    }

    private static boolean hasReference(Object value) {
        if (value instanceof Map<?, ?> map) return map.get("template") != null || map.get("templates") != null
                || map.values().stream().anyMatch(RecipeTemplateReplayGuard::hasReference);
        return value instanceof Collection<?> list && list.stream().anyMatch(RecipeTemplateReplayGuard::hasReference);
    }

    private static Object at(Map<String, Object> root, String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(part);
        }
        return current;
    }

    private static Object first(Map<?, ?> map, String... keys) {
        for (String key : keys) if (map.containsKey(key)) return map.get(key);
        return null;
    }

    private static String definitionId(String id, String namespace) throws IOException {
        try { return Key.withDefaultNamespace(id, namespace).asString(); }
        catch (RuntimeException invalid) { throw new IOException("模板 ID 无效：" + id, invalid); }
    }

    private static String base(String key) { return key.split("#", 2)[0].replace('-', '_'); }
    private static boolean templateSection(String key) { return Set.of("template", "templates").contains(base(key)); }
    private static boolean factorySection(String key) { return Set.of("config_factory", "config_factories").contains(base(key)); }
    private static boolean yaml(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static Map<String, Object> map(ConfigurationSection section) {
        Map<String, Object> result = new LinkedHashMap<>();
        section.getValues(false).forEach((key, value) -> result.put(key, plain(value)));
        return result;
    }

    private static Object plain(Object value) {
        if (value instanceof ConfigurationSection section) return map(section);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), plain(item)));
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List<?> list) return list.stream().map(RecipeTemplateReplayGuard::plain).toList();
        return value;
    }
}
