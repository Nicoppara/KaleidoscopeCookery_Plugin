package net.kaleidoscope.cookery.recipe.edit;

import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.FoodGroups;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.ConfigValue;
import net.momirealms.craftengine.core.plugin.config.template.ArgumentString;
import net.momirealms.craftengine.core.plugin.config.template.argument.PlainStringTemplateArgument;
import net.momirealms.craftengine.core.plugin.config.template.argument.TemplateArgument;
import net.momirealms.craftengine.core.plugin.config.template.argument.TemplateArguments;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// 配方 YAML 的读改写 只碰目标配方那一个节点 兄弟节点与其注释原样保留
// 全部方法都做磁盘 IO 必须在 async 调度器上调用 见 RecipeEditService
public final class RecipeFileStore {
    private static final String RECIPE_FOLDER = "recipe";
    private static final String TAG_FOLDER = "tag";
    private static final String FOOD_GROUP_FILE = "food_groups.yml";
    private static final String ITEM_TAGS_SECTION = "item_tags";
    private static final String EQUIVALENT_SECTION = "equivalent_foods";
    private static final String SEASONING_SECTION = "seasonings";
    private static final String GROUP_TAGS_KEY = "tags";

    private static final String[] ACCURATE_SECTIONS = {"accurate_foods", "accurate-foods"};
    private static final String[] POT_FLEX_SECTIONS = {"pot_flex_foods", "pot-flex-foods"};
    private static final String[] STOCK_FLEX_SECTIONS = {"stock_flex_foods", "stock-flex-foods"};
    private static final String[] CHOPPING_SECTIONS = {"chopping_board_raws", "chopping-board-raws"};
    private static final String[] TEAPOT_SECTIONS = {"teapot_result", "teapot-result"};
    private static final String[] STOCK_RAW_SECTIONS = {"stock_food_raw", "stock-food-raw"};
    private static final String LIQUID_KEY = "liquid";

    private static final String ACCURATE_FILE = "accurate.yml";
    private static final String POT_FILE = "pot.yml";
    private static final String STOCKPOT_FILE = "stockpot.yml";
    private static final String CHOPPING_FILE = "chopping_board.yml";
    private static final String TEAPOT_FILE = "teapot.yml";
    private static final Map<Path, Object> FILE_LOCKS = new ConcurrentHashMap<>();
    private static final Map<Path, CachedTargets> TARGET_CACHE = new ConcurrentHashMap<>();

    private static final String[] FACTORY_SECTIONS = {
            "config_factory", "config-factory", "config_factories", "config-factories"
    };
    private static final String[] FACTORY_INSTANCES = {"instances", "instance", "inputs", "input"};
    private static final String[] FACTORY_BLUEPRINTS = {"blueprint", "prototype", "schema"};

    // generatedNode 是 CE 展开后交给食谱解析器的节点 工厂目标额外保存真实实例位置与内容
    public record SourceTarget(String generatedNode, String factoryKey, String instancesKey,
                               int instanceIndex, Map<String, Object> instance) {
        public SourceTarget {
            instance = instance == null ? Map.of() : immutableMap(instance);
        }

        public static SourceTarget direct(String nodePath) {
            return new SourceTarget(nodePath, null, null, -1, Map.of());
        }

        public static SourceTarget factory(String generatedNode, String factoryKey, String instancesKey,
                                           int instanceIndex, Map<String, Object> instance) {
            return new SourceTarget(generatedNode, factoryKey, instancesKey, instanceIndex, instance);
        }

        public static SourceTarget unresolved(String generatedNode) {
            return new SourceTarget(generatedNode, "", null, -1, Map.of());
        }

        public boolean factory() {
            return factoryKey != null && !factoryKey.isEmpty();
        }

        public boolean resolved() {
            return factoryKey == null || !factoryKey.isEmpty();
        }
    }

    private record FactoryTarget(RecipeSourceIndex.Kind kind, String generatedId, SourceTarget target,
                                 Map<String, Object> blueprint) {
        private FactoryTarget {
            blueprint = immutableMap(blueprint);
        }
    }

    private record CachedTargets(long modified, long size, Set<String> directNodes,
                                 List<FactoryTarget> factories) {
    }

    private RecipeFileStore() {
    }

    public static String[] accurateSections() {
        return ACCURATE_SECTIONS;
    }

    public static String[] flexSections(ApplianceType cook) {
        return cook == ApplianceType.STOCKPOT ? STOCK_FLEX_SECTIONS : POT_FLEX_SECTIONS;
    }

    // 新建配方落到哪个文件 已有配方原地改写走 RecipeSourceIndex
    public static Path defaultAccurateFile() {
        return recipeFolder().resolve(ACCURATE_FILE);
    }

    public static String[] choppingSections() {
        return CHOPPING_SECTIONS;
    }

    public static String[] teapotSections() {
        return TEAPOT_SECTIONS;
    }

    public static Path defaultChoppingFile() {
        return recipeFolder().resolve(CHOPPING_FILE);
    }

    public static Path defaultTeapotFile() {
        return recipeFolder().resolve(TEAPOT_FILE);
    }

    public static Path defaultFlexFile(ApplianceType cook) {
        return recipeFolder().resolve(cook == ApplianceType.STOCKPOT ? STOCKPOT_FILE : POT_FILE);
    }

    public static Path defaultFoodGroupFile() {
        return pack().configurationFolder().resolve(TAG_FOLDER).resolve(FOOD_GROUP_FILE);
    }

    // 一个分组要同时改三处 标签成员 以及两张用途表里的归属
    // 三处都在同一个文件同一把锁内改完 免得只写了一半
    public static void writeFoodGroup(Key tag, List<Key> members, FoodGroups.Kind kind) throws IOException {
        writeFoodGroup(defaultFoodGroupFile().toAbsolutePath().normalize(), tag, members, kind);
    }

    static void writeFoodGroup(Path file, Key tag, List<Key> members, FoodGroups.Kind kind) throws IOException {
        synchronized (fileLock(file)) {
            Files.createDirectories(file.getParent());
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
            ConfigurationSection tags = config.getConfigurationSection(ITEM_TAGS_SECTION);
            if (tags == null) {
                tags = config.createSection(ITEM_TAGS_SECTION);
            }
            tags.set(tag.asString(), members.stream().map(Key::asString).toList());
            setGroupMembership(config, tag, kind);
            saveAtomic(config, file);
        }
    }

    public static void deleteFoodGroup(Key tag) throws IOException {
        deleteFoodGroup(defaultFoodGroupFile().toAbsolutePath().normalize(), tag);
    }

    static void deleteFoodGroup(Path file, Key tag) throws IOException {
        synchronized (fileLock(file)) {
            if (!file.toFile().isFile()) {
                return;
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
            ConfigurationSection tags = config.getConfigurationSection(ITEM_TAGS_SECTION);
            if (tags != null) {
                tags.set(tag.asString(), null);
            }
            setGroupMembership(config, tag, null);
            saveAtomic(config, file);
        }
    }

    // kind 为空表示从两张表里都摘掉
    private static void setGroupMembership(YamlConfiguration config, Key tag, FoodGroups.Kind kind) {
        String entry = "#" + tag.asString();
        for (FoodGroups.Kind candidate : FoodGroups.Kind.values()) {
            String section = candidate == FoodGroups.Kind.EQUIVALENT
                    ? EQUIVALENT_SECTION : SEASONING_SECTION;
            ConfigurationSection group = config.getConfigurationSection(section);
            List<String> list = group == null
                    ? new ArrayList<>() : new ArrayList<>(group.getStringList(GROUP_TAGS_KEY));
            list.remove(entry);
            if (candidate == kind) {
                list.add(entry);
            }
            if (list.isEmpty() && group == null) {
                continue;
            }
            if (group == null) {
                group = config.createSection(section);
            }
            group.set(GROUP_TAGS_KEY, list);
        }
    }

    // 汤底表是 stock_food_raw.liquid 下的一个列表 不是 id 键控的节点
    // 所以走不了 write/delete 那套 单开一对方法整段重写这个列表
    public static void writeSoupBase(Key bucket, Key show) throws IOException {
        Path file = defaultFlexFile(ApplianceType.STOCKPOT).toAbsolutePath().normalize();
        synchronized (fileLock(file)) {
            Files.createDirectories(file.getParent());
            File target = file.toFile();
            YamlConfiguration config = YamlConfiguration.loadConfiguration(target);
            ConfigurationSection section = resolveNamedSection(config, STOCK_RAW_SECTIONS, true);
            List<Map<?, ?>> list = new ArrayList<>(section.getMapList(LIQUID_KEY));
            list.removeIf(entry -> bucket.asString().equals(String.valueOf(entry.get("item"))));
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("item", bucket.asString());
            node.put("show", show.asString());
            list.add(node);
            section.set(LIQUID_KEY, list);
            saveAtomic(config, file);
        }
    }

    public static void deleteSoupBase(Key bucket) throws IOException {
        Path file = defaultFlexFile(ApplianceType.STOCKPOT).toAbsolutePath().normalize();
        synchronized (fileLock(file)) {
            File target = file.toFile();
            if (!target.isFile()) {
                return;
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(target);
            ConfigurationSection section = resolveNamedSection(config, STOCK_RAW_SECTIONS, false);
            if (section == null) {
                return;
            }
            List<Map<?, ?>> list = new ArrayList<>(section.getMapList(LIQUID_KEY));
            if (!list.removeIf(entry -> bucket.asString().equals(String.valueOf(entry.get("item"))))) {
                return;
            }
            section.set(LIQUID_KEY, list);
            saveAtomic(config, file);
        }
    }

    // 按顶层段名找 不带 id 匹配 同名多段时取第一个已含 liquid 的
    private static ConfigurationSection resolveNamedSection(YamlConfiguration config, String[] aliases,
                                                            boolean createIfAbsent) {
        ConfigurationSection first = null;
        for (String rootKey : config.getKeys(false)) {
            int hash = rootKey.indexOf('#');
            String base = hash < 0 ? rootKey : rootKey.substring(0, hash);
            if (!matchesAlias(base, aliases)) {
                continue;
            }
            ConfigurationSection candidate = config.getConfigurationSection(rootKey);
            if (candidate == null) {
                continue;
            }
            if (candidate.contains(LIQUID_KEY)) {
                return candidate;
            }
            if (first == null) {
                first = candidate;
            }
        }
        if (first != null) {
            return first;
        }
        return createIfAbsent ? config.createSection(aliases[0]) : null;
    }

    // 同步菜品退还容器要写 item 目录 那不在 recipe 下 单独暴露一个入口
    static Path configurationFolder() {
        return pack().configurationFolder();
    }

    private static Path recipeFolder() {
        return pack().configurationFolder().resolve(RECIPE_FOLDER);
    }

    // 本插件资源包只按 pack.yml 的命名空间认 禁止误写其它包
    private static Pack pack() {
        return findPack(CraftEngine.instance().packManager().loadedPacks());
    }

    static Pack findPack(Collection<Pack> packs) {
        Pack matched = null;
        for (Pack pack : packs) {
            if (!pack.enabled() || !ItemKeys.NAMESPACE.equals(pack.namespace())) {
                continue;
            }
            if (matched != null) {
                throw new IllegalStateException("存在多个 namespace 为 " + ItemKeys.NAMESPACE
                        + " 的 CraftEngine 资源包: " + matched.name() + ", " + pack.name());
            }
            matched = pack;
        }
        if (matched == null) {
            throw new IllegalStateException("找不到已启用且 pack.yml 中 namespace 为 "
                    + ItemKeys.NAMESPACE + " 的 CraftEngine 资源包 无法写入配方");
        }
        return matched;
    }

    // 写入或覆盖一个配方节点 返回实际写入的文件
    public static void write(Path file, String[] sectionAliases, Key id, Map<String, Object> node) throws IOException {
        Path targetPath = file.toAbsolutePath().normalize();
        synchronized (fileLock(targetPath)) {
            Files.createDirectories(targetPath.getParent());
            File target = targetPath.toFile();
            YamlConfiguration config = YamlConfiguration.loadConfiguration(target);
            ConfigurationSection section = resolveSection(config, sectionAliases, id, true);
            section.set(id.asString(), node);
            saveAtomic(config, targetPath);
        }
    }

    public static void writeNode(Path file, String nodePath, Map<String, Object> node) throws IOException {
        Path targetPath = file.toAbsolutePath().normalize();
        synchronized (fileLock(targetPath)) {
            Files.createDirectories(targetPath.getParent());
            File target = targetPath.toFile();
            YamlConfiguration config = YamlConfiguration.loadConfiguration(target);
            config.set(nodePath, node);
            saveAtomic(config, targetPath);
        }
    }

    // 编辑工厂食谱时先删除对应实例 再写成独立食谱 两步在同一次原子替换中完成
    public static void replaceTarget(Path file, SourceTarget oldTarget,
                                     String newNodePath, Map<String, Object> node) throws IOException {
        replaceTarget(file, oldTarget, newNodePath, node, () -> {});
    }

    public static void replaceTarget(Path file, SourceTarget oldTarget,
                                     String newNodePath, Map<String, Object> node,
                                     Runnable afterSave) throws IOException {
        Path targetPath = file.toAbsolutePath().normalize();
        synchronized (fileLock(targetPath)) {
            Files.createDirectories(targetPath.getParent());
            YamlConfiguration config = YamlConfiguration.loadConfiguration(targetPath.toFile());
            if (oldTarget != null && !oldTarget.resolved()) {
                throw new IOException("无法定位该食谱在配置中的真实来源");
            }
            if (oldTarget != null) {
                boolean moving = !oldTarget.generatedNode().equals(newNodePath) || oldTarget.factory();
                if (moving && !removeTarget(config, oldTarget)) {
                    throw new IOException("找不到该食谱在配置中的真实来源");
                }
                if (!moving && !config.contains(oldTarget.generatedNode())) {
                    throw new IOException("找不到该食谱在配置中的真实来源");
                }
            }
            config.set(newNodePath, node);
            saveAtomic(config, targetPath);
            afterSave.run();
        }
    }

    // 删除一个配方节点 节点不存在时静默返回
    public static void delete(Path file, String[] sectionAliases, Key id) throws IOException {
        Path targetPath = file.toAbsolutePath().normalize();
        synchronized (fileLock(targetPath)) {
            File target = targetPath.toFile();
            if (!target.isFile()) {
                return;
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(target);
            ConfigurationSection section = resolveSection(config, sectionAliases, id, false);
            if (section == null || !section.contains(id.asString())) {
                return;
            }
            section.set(id.asString(), null);
            saveAtomic(config, targetPath);
        }
    }

    public static void deleteNode(Path file, String nodePath) throws IOException {
        Path targetPath = file.toAbsolutePath().normalize();
        synchronized (fileLock(targetPath)) {
            File target = targetPath.toFile();
            if (!target.isFile()) {
                return;
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(target);
            if (!config.contains(nodePath)) {
                return;
            }
            config.set(nodePath, null);
            saveAtomic(config, targetPath);
        }
    }

    public static boolean deleteTarget(Path file, SourceTarget source) throws IOException {
        Path targetPath = file.toAbsolutePath().normalize();
        synchronized (fileLock(targetPath)) {
            File target = targetPath.toFile();
            if (!target.isFile() || !source.resolved()) {
                return false;
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(target);
            if (!removeTarget(config, source)) {
                return false;
            }
            saveAtomic(config, targetPath);
            return true;
        }
    }

    private static boolean removeTarget(YamlConfiguration config, SourceTarget source) {
        if (!source.factory()) {
            if (!config.contains(source.generatedNode())) {
                return false;
            }
            config.set(source.generatedNode(), null);
            return true;
        }

        ConfigurationSection factory = config.getConfigurationSection(source.factoryKey());
        if (factory == null) {
            return false;
        }
        List<?> stored = factory.getList(source.instancesKey());
        if (stored == null) {
            return false;
        }
        List<Object> instances = new ArrayList<>(stored);
        int index = matchingInstance(instances, source);
        if (index < 0) {
            return false;
        }
        instances.remove(index);
        factory.set(source.instancesKey(), instances);
        return true;
    }

    private static int matchingInstance(List<?> instances, SourceTarget source) {
        int preferred = source.instanceIndex();
        if (preferred >= 0 && preferred < instances.size()
                && source.instance().equals(asStringMap(instances.get(preferred)))) {
            return preferred;
        }
        for (int i = 0; i < instances.size(); i++) {
            if (source.instance().equals(asStringMap(instances.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    // 解析器拿到的是工厂展开节点 这里反查它在 YAML 中对应的真实目标
    public static List<SourceTarget> resolveTargets(RecipeSourceIndex.Kind kind, Key id,
                                                    Path file, String generatedNode) {
        return resolveTargets(kind, id, file, generatedNode, null);
    }

    public static List<SourceTarget> resolveTargets(RecipeSourceIndex.Kind kind, Key id,
                                                    Path file, String generatedNode,
                                                    Map<String, Object> expandedNode) {
        Path targetPath = file.toAbsolutePath().normalize();
        CachedTargets cached;
        try {
            long modified = Files.getLastModifiedTime(targetPath).toMillis();
            long size = Files.size(targetPath);
            cached = TARGET_CACHE.get(targetPath);
            if (cached == null || cached.modified() != modified || cached.size() != size) {
                cached = scanTargets(targetPath, modified, size);
                TARGET_CACHE.put(targetPath, cached);
            }
        } catch (IOException | RuntimeException error) {
            return List.of();
        }

        SourceTarget direct = cached.directNodes().contains(generatedNode)
                ? SourceTarget.direct(generatedNode) : null;
        List<SourceTarget> result = new ArrayList<>();
        List<SourceTarget> fallback = new ArrayList<>();
        List<SourceTarget> idMatches = new ArrayList<>();
        for (FactoryTarget candidate : cached.factories()) {
            if (candidate.kind() != kind) {
                continue;
            }
            Key generatedId = Key.withDefaultNamespace(candidate.generatedId(), id.namespace());
            if (generatedId.equals(id) && candidate.target().generatedNode().equals(generatedNode)) {
                idMatches.add(candidate.target());
                if (expandedNode == null) {
                    result.add(candidate.target());
                    continue;
                }
                try {
                    if (expandedNode.equals(expandFactoryTarget(candidate, id))) {
                        result.add(candidate.target());
                    }
                } catch (RuntimeException ignored) {
                    fallback.add(candidate.target());
                }
            }
        }
        if (!result.isEmpty()) {
            return withDirect(direct, result);
        }
        if (!fallback.isEmpty()) {
            return withDirect(direct, fallback);
        }
        if (idMatches.size() == 1) {
            return withDirect(direct, idMatches);
        }
        return direct == null ? List.of() : List.of(direct);
    }

    private static List<SourceTarget> withDirect(SourceTarget direct, List<SourceTarget> factories) {
        if (direct == null) {
            return List.copyOf(factories);
        }
        List<SourceTarget> result = new ArrayList<>(factories.size() + 1);
        result.add(direct);
        result.addAll(factories);
        return List.copyOf(result);
    }

    private static Map<String, Object> expandFactoryTarget(FactoryTarget candidate, Key id) {
        SourceTarget target = candidate.target();
        Map<String, TemplateArgument> arguments = factoryArguments(
                target.factoryKey(), target.instancesKey(), target.instanceIndex(), target.instance());
        arguments.put("__NAMESPACE__", PlainStringTemplateArgument.plain(id.namespace()));
        arguments.put("__ID__", PlainStringTemplateArgument.plain(id.value()));
        Object expanded = expandValue(target.generatedNode(), candidate.blueprint(), arguments);
        return asStringMap(expanded);
    }

    private static Object expandValue(String path, Object value, Map<String, TemplateArgument> arguments) {
        if (value instanceof String text && text.contains("$")) {
            return ArgumentString.preParse(path, text).get(path, arguments);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String rawKey = String.valueOf(entry.getKey());
                String key = rawKey.contains("$")
                        ? ArgumentString.preParse(path, rawKey).get(path, arguments).toString()
                        : rawKey;
                result.put(key, expandValue(path + "." + key, entry.getValue(), arguments));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                result.add(expandValue(path + "[" + i + "]", list.get(i), arguments));
            }
            return result;
        }
        return value;
    }

    private static CachedTargets scanTargets(Path file, long modified, long size) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
        Set<String> directNodes = new HashSet<>();
        List<FactoryTarget> factories = new ArrayList<>();
        for (String rootKey : config.getKeys(false)) {
            String base = baseKey(rootKey);
            RecipeSourceIndex.Kind directKind = kindOfSection(base);
            ConfigurationSection root = config.getConfigurationSection(rootKey);
            if (root == null) {
                continue;
            }
            if (directKind != null) {
                for (String id : root.getKeys(false)) {
                    directNodes.add(rootKey + "." + id);
                }
                continue;
            }
            if (!matchesAlias(base, FACTORY_SECTIONS)) {
                continue;
            }
            scanFactory(rootKey, root, factories);
        }
        return new CachedTargets(modified, size, Set.copyOf(directNodes), List.copyOf(factories));
    }

    private static void scanFactory(String factoryKey, ConfigurationSection factory,
                                    List<FactoryTarget> targets) {
        String instancesKey = firstExistingKey(factory, FACTORY_INSTANCES);
        String blueprintKey = firstExistingKey(factory, FACTORY_BLUEPRINTS);
        if (instancesKey == null || blueprintKey == null) {
            return;
        }
        List<?> instances = factory.getList(instancesKey);
        ConfigurationSection blueprint = factory.getConfigurationSection(blueprintKey);
        if (instances == null || blueprint == null) {
            return;
        }
        for (int index = 0; index < instances.size(); index++) {
            Map<String, Object> instance = asStringMap(instances.get(index));
            if (instance.isEmpty()) {
                continue;
            }
            Map<String, TemplateArgument> arguments = factoryArguments(factoryKey, instancesKey, index, instance);
            for (String parserKey : blueprint.getKeys(false)) {
                RecipeSourceIndex.Kind kind = kindOfSection(baseKey(parserKey));
                ConfigurationSection recipes = blueprint.getConfigurationSection(parserKey);
                if (kind == null || recipes == null) {
                    continue;
                }
                for (String recipeKey : recipes.getKeys(false)) {
                    try {
                        String path = parserKey + "." + recipeKey;
                        String generatedId = ArgumentString.preParse(path, recipeKey)
                                .get(path, arguments).toString();
                        SourceTarget target = SourceTarget.factory(
                                parserKey + "." + generatedId,
                                factoryKey, instancesKey, index, instance);
                        Map<String, Object> recipeNode = asStringMap(recipes.get(recipeKey));
                        targets.add(new FactoryTarget(kind, generatedId, target, recipeNode));
                    } catch (RuntimeException ignored) {
                        // CE 自己会报告模板参数错误 这里不重复刷日志
                    }
                }
            }
        }
    }

    private static Map<String, TemplateArgument> factoryArguments(String factoryKey, String instancesKey,
                                                                   int index, Map<String, Object> instance) {
        Map<String, TemplateArgument> result = new HashMap<>();
        String path = factoryKey + "." + instancesKey + "[" + index + "]";
        ConfigSection section = ConfigSection.of(path, instance);
        for (String key : section.keySet()) {
            ConfigValue value = section.getValue(key);
            result.put(key, TemplateArguments.fromConfig(value));
        }
        return result;
    }

    private static String firstExistingKey(ConfigurationSection section, String[] aliases) {
        for (String alias : aliases) {
            if (section.contains(alias)) {
                return alias;
            }
        }
        return null;
    }

    private static RecipeSourceIndex.Kind kindOfSection(String section) {
        if (matchesAlias(section, ACCURATE_SECTIONS)) {
            return RecipeSourceIndex.Kind.ACCURATE;
        }
        if (matchesAlias(section, POT_FLEX_SECTIONS)) {
            return RecipeSourceIndex.Kind.POT_FLEX;
        }
        if (matchesAlias(section, STOCK_FLEX_SECTIONS)) {
            return RecipeSourceIndex.Kind.STOCK_FLEX;
        }
        if (matchesAlias(section, CHOPPING_SECTIONS)) {
            return RecipeSourceIndex.Kind.CHOPPING;
        }
        if (matchesAlias(section, TEAPOT_SECTIONS)) {
            return RecipeSourceIndex.Kind.TEAPOT;
        }
        return null;
    }

    private static String baseKey(String key) {
        int hash = key.indexOf('#');
        return hash < 0 ? key : key.substring(0, hash);
    }

    private static Map<String, Object> asStringMap(Object value) {
        if (value instanceof ConfigurationSection section) {
            return sectionMap(section);
        }
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue()));
        }
        return result;
    }

    private static Map<String, Object> sectionMap(ConfigurationSection section) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            result.put(key, normalizeValue(section.get(key)));
        }
        return result;
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof ConfigurationSection section) {
            return sectionMap(section);
        }
        if (value instanceof Map<?, ?>) {
            return asStringMap(value);
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object element : list) {
                result.add(normalizeValue(element));
            }
            return result;
        }
        return value;
    }

    private static Map<String, Object> immutableMap(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            result.put(entry.getKey(), immutableValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return immutableMap(asStringMap(map));
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object element : list) {
                result.add(immutableValue(element));
            }
            return Collections.unmodifiableList(result);
        }
        return value;
    }

    // 同一文件的读改写必须串行 否则两个编辑操作可能互相覆盖
    private static Object fileLock(Path file) {
        return FILE_LOCKS.computeIfAbsent(file.toAbsolutePath().normalize(), ignored -> new Object());
    }

    // 先写同目录临时文件并强制刷盘 再原子替换 避免异常关服留下半份 YAML
    private static void saveAtomic(YamlConfiguration config, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".cookery.tmp");
        config.save(temporary.toFile());
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        try {
            Files.move(temporary, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
        TARGET_CACHE.remove(target.toAbsolutePath().normalize());
    }

    // CE 允许 blocks#1 这种带 # 后缀的重复顶层键 匹配时要按 # 前的部分比
    // 优先返回已经含有该 id 的那一段 否则返回首个同名段 都没有时按需新建
    private static ConfigurationSection resolveSection(YamlConfiguration config, String[] aliases,
                                                       Key id, boolean createIfAbsent) {
        ConfigurationSection first = null;
        for (String rootKey : config.getKeys(false)) {
            int hash = rootKey.indexOf('#');
            String base = hash < 0 ? rootKey : rootKey.substring(0, hash);
            if (!matchesAlias(base, aliases)) {
                continue;
            }
            ConfigurationSection candidate = config.getConfigurationSection(rootKey);
            if (candidate == null) {
                continue;
            }
            if (candidate.contains(id.asString())) {
                return candidate;
            }
            if (first == null) {
                first = candidate;
            }
        }
        if (first != null) {
            return first;
        }
        return createIfAbsent ? config.createSection(aliases[0]) : null;
    }

    private static boolean matchesAlias(String base, String[] aliases) {
        for (String alias : aliases) {
            if (alias.equals(base)) {
                return true;
            }
        }
        return false;
    }

    // 目标文件写坏时至少留下痕迹 调用方已在 async 线程 这里不再切换
    public static void logFailure(String action, Key id, Throwable error) {
        KaleidoscopeCookeryPlugin.instance().getLogger().warning(
                "配方 " + id.asString() + " " + action + " 失败: " + error.getMessage());
    }

}
