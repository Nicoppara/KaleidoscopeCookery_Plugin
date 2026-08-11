package net.kaleidoscope.cookery.recipe.edit;

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
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.kaleidoscope.cookery.recipe.ApplianceType;
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

public final class RecipeFileStore {
   private static final String PACK_NAMESPACE = "kaleidoscopecookery";
   private static final String RECIPE_FOLDER = "recipe";
   private static final String[] ACCURATE_SECTIONS = new String[]{"accurate_foods", "accurate-foods"};
   private static final String[] POT_FLEX_SECTIONS = new String[]{"pot_flex_foods", "pot-flex-foods"};
   private static final String[] STOCK_FLEX_SECTIONS = new String[]{"stock_flex_foods", "stock-flex-foods"};
   private static final String[] CHOPPING_SECTIONS = new String[]{"chopping_board_raws", "chopping-board-raws"};
   private static final String[] TEAPOT_SECTIONS = new String[]{"teapot_result", "teapot-result"};
   private static final String[] STOCK_RAW_SECTIONS = new String[]{"stock_food_raw", "stock-food-raw"};
   private static final String LIQUID_KEY = "liquid";
   private static final String ACCURATE_FILE = "accurate.yml";
   private static final String POT_FILE = "pot.yml";
   private static final String STOCKPOT_FILE = "stockpot.yml";
   private static final String CHOPPING_FILE = "chopping_board.yml";
   private static final String TEAPOT_FILE = "teapot.yml";
   private static final Map<Path, Object> FILE_LOCKS = new ConcurrentHashMap<>();
   private static final Map<Path, RecipeFileStore.CachedTargets> TARGET_CACHE = new ConcurrentHashMap<>();
   private static final String[] FACTORY_SECTIONS = new String[]{"config_factory", "config-factory", "config_factories", "config-factories"};
   private static final String[] FACTORY_INSTANCES = new String[]{"instances", "instance", "inputs", "input"};
   private static final String[] FACTORY_BLUEPRINTS = new String[]{"blueprint", "prototype", "schema"};

   private RecipeFileStore() {
   }

   public static String[] accurateSections() {
      return ACCURATE_SECTIONS;
   }

   public static String[] flexSections(ApplianceType cook) {
      return cook == ApplianceType.STOCKPOT ? STOCK_FLEX_SECTIONS : POT_FLEX_SECTIONS;
   }

   public static Path defaultAccurateFile() {
      return recipeFolder().resolve("accurate.yml");
   }

   public static String[] choppingSections() {
      return CHOPPING_SECTIONS;
   }

   public static String[] teapotSections() {
      return TEAPOT_SECTIONS;
   }

   public static Path defaultChoppingFile() {
      return recipeFolder().resolve("chopping_board.yml");
   }

   public static Path defaultTeapotFile() {
      return recipeFolder().resolve("teapot.yml");
   }

   public static Path defaultFlexFile(ApplianceType cook) {
      return recipeFolder().resolve(cook == ApplianceType.STOCKPOT ? "stockpot.yml" : "pot.yml");
   }

   public static void writeSoupBase(Key bucket, Key show) throws IOException {
      Path file = defaultFlexFile(ApplianceType.STOCKPOT).toAbsolutePath().normalize();
      synchronized (fileLock(file)) {
         Files.createDirectories(file.getParent());
         File target = file.toFile();
         YamlConfiguration config = YamlConfiguration.loadConfiguration(target);
         ConfigurationSection section = resolveNamedSection(config, STOCK_RAW_SECTIONS, true);
         List<Map<?, ?>> list = new ArrayList<>(section.getMapList("liquid"));
         list.removeIf(entry -> bucket.asString().equals(String.valueOf(entry.get("item"))));
         Map<String, Object> node = new LinkedHashMap<>();
         node.put("item", bucket.asString());
         node.put("show", show.asString());
         list.add(node);
         section.set("liquid", list);
         saveAtomic(config, file);
      }
   }

   public static void deleteSoupBase(Key bucket) throws IOException {
      Path file = defaultFlexFile(ApplianceType.STOCKPOT).toAbsolutePath().normalize();
      synchronized (fileLock(file)) {
         File target = file.toFile();
         if (target.isFile()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(target);
            ConfigurationSection section = resolveNamedSection(config, STOCK_RAW_SECTIONS, false);
            if (section != null) {
               List<Map<?, ?>> list = new ArrayList<>(section.getMapList("liquid"));
               if (list.removeIf(entry -> bucket.asString().equals(String.valueOf(entry.get("item"))))) {
                  section.set("liquid", list);
                  saveAtomic(config, file);
               }
            }
         }
      }
   }

   private static ConfigurationSection resolveNamedSection(YamlConfiguration config, String[] aliases, boolean createIfAbsent) {
      ConfigurationSection first = null;

      for (String rootKey : config.getKeys(false)) {
         int hash = rootKey.indexOf(35);
         String base = hash < 0 ? rootKey : rootKey.substring(0, hash);
         if (matchesAlias(base, aliases)) {
            ConfigurationSection candidate = config.getConfigurationSection(rootKey);
            if (candidate != null) {
               if (candidate.contains("liquid")) {
                  return candidate;
               }

               if (first == null) {
                  first = candidate;
               }
            }
         }
      }

      if (first != null) {
         return first;
      } else {
         return createIfAbsent ? config.createSection(aliases[0]) : null;
      }
   }

   static Path configurationFolder() {
      return pack().configurationFolder();
   }

   private static Path recipeFolder() {
      return pack().configurationFolder().resolve("recipe");
   }

   private static Pack pack() {
      Pack fallback = null;

      for (Pack p : CraftEngine.instance().packManager().loadedPacks()) {
         if ("kaleidoscopecookery".equals(p.namespace())) {
            return p;
         }

         if (fallback == null) {
            fallback = p;
         }
      }

      if (fallback == null) {
         throw new IllegalStateException("CraftEngine 未加载任何资源包 无法写入配方");
      } else {
         return fallback;
      }
   }

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

   public static void replaceTarget(Path file, RecipeFileStore.SourceTarget oldTarget, String newNodePath, Map<String, Object> node) throws IOException {
      Path targetPath = file.toAbsolutePath().normalize();
      synchronized (fileLock(targetPath)) {
         Files.createDirectories(targetPath.getParent());
         YamlConfiguration config = YamlConfiguration.loadConfiguration(targetPath.toFile());
         if (oldTarget != null && !oldTarget.resolved()) {
            throw new IOException("无法定位该食谱在配置中的真实来源");
         }

         if (oldTarget != null && !oldTarget.generatedNode().equals(newNodePath)) {
            removeTarget(config, oldTarget);
         } else if (oldTarget != null && oldTarget.factory()) {
            removeTarget(config, oldTarget);
         }

         config.set(newNodePath, node);
         saveAtomic(config, targetPath);
      }
   }

   public static void delete(Path file, String[] sectionAliases, Key id) throws IOException {
      Path targetPath = file.toAbsolutePath().normalize();
      synchronized (fileLock(targetPath)) {
         File target = targetPath.toFile();
         if (target.isFile()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(target);
            ConfigurationSection section = resolveSection(config, sectionAliases, id, false);
            if (section != null && section.contains(id.asString())) {
               section.set(id.asString(), null);
               saveAtomic(config, targetPath);
            }
         }
      }
   }

   public static void deleteNode(Path file, String nodePath) throws IOException {
      Path targetPath = file.toAbsolutePath().normalize();
      synchronized (fileLock(targetPath)) {
         File target = targetPath.toFile();
         if (target.isFile()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(target);
            if (config.contains(nodePath)) {
               config.set(nodePath, null);
               saveAtomic(config, targetPath);
            }
         }
      }
   }

   public static boolean deleteTarget(Path file, RecipeFileStore.SourceTarget source) throws IOException {
      Path targetPath = file.toAbsolutePath().normalize();
      synchronized (fileLock(targetPath)) {
         File target = targetPath.toFile();
         if (target.isFile() && source.resolved()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(target);
            if (!removeTarget(config, source)) {
               return false;
            }

            saveAtomic(config, targetPath);
            return true;
         } else {
            return false;
         }
      }
   }

   private static boolean removeTarget(YamlConfiguration config, RecipeFileStore.SourceTarget source) {
      if (!source.factory()) {
         if (!config.contains(source.generatedNode())) {
            return false;
         }

         config.set(source.generatedNode(), null);
         return true;
      } else {
         ConfigurationSection factory = config.getConfigurationSection(source.factoryKey());
         if (factory == null) {
            return false;
         }

         List<?> stored = factory.getList(source.instancesKey());
         if (stored == null) {
            return false;
         }

         List<Object> instances = new ArrayList<>((Collection<? extends Object>)stored);
         int index = matchingInstance(instances, source);
         if (index < 0) {
            return false;
         }

         instances.remove(index);
         factory.set(source.instancesKey(), instances);
         return true;
      }
   }

   private static int matchingInstance(List<?> instances, RecipeFileStore.SourceTarget source) {
      int preferred = source.instanceIndex();
      if (preferred >= 0 && preferred < instances.size() && source.instance().equals(asStringMap(instances.get(preferred)))) {
         return preferred;
      }

      for (int i = 0; i < instances.size(); i++) {
         if (source.instance().equals(asStringMap(instances.get(i)))) {
            return i;
         }
      }

      return -1;
   }

   public static List<RecipeFileStore.SourceTarget> resolveTargets(RecipeSourceIndex.Kind kind, Key id, Path file, String generatedNode) {
      return resolveTargets(kind, id, file, generatedNode, null);
   }

   public static List<RecipeFileStore.SourceTarget> resolveTargets(
      RecipeSourceIndex.Kind kind, Key id, Path file, String generatedNode, Map<String, Object> expandedNode
   ) {
      Path targetPath = file.toAbsolutePath().normalize();

      RecipeFileStore.CachedTargets cached;
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

      RecipeFileStore.SourceTarget direct = cached.directNodes().contains(generatedNode) ? RecipeFileStore.SourceTarget.direct(generatedNode) : null;
      List<RecipeFileStore.SourceTarget> result = new ArrayList<>();
      List<RecipeFileStore.SourceTarget> fallback = new ArrayList<>();
      List<RecipeFileStore.SourceTarget> idMatches = new ArrayList<>();

      for (RecipeFileStore.FactoryTarget candidate : cached.factories()) {
         if (candidate.kind() == kind) {
            Key generatedId = Key.withDefaultNamespace(candidate.generatedId(), id.namespace());
            if (generatedId.equals(id) && candidate.target().generatedNode().equals(generatedNode)) {
               idMatches.add(candidate.target());
               if (expandedNode == null) {
                  result.add(candidate.target());
               } else {
                  try {
                     if (expandedNode.equals(expandFactoryTarget(candidate, id))) {
                        result.add(candidate.target());
                     }
                  } catch (RuntimeException ignored) {
                     fallback.add(candidate.target());
                  }
               }
            }
         }
      }

      if (!result.isEmpty()) {
         return withDirect(direct, result);
      } else if (!fallback.isEmpty()) {
         return withDirect(direct, fallback);
      } else if (idMatches.size() == 1) {
         return withDirect(direct, idMatches);
      } else {
         return direct == null ? List.of() : List.of(direct);
      }
   }

   private static List<RecipeFileStore.SourceTarget> withDirect(RecipeFileStore.SourceTarget direct, List<RecipeFileStore.SourceTarget> factories) {
      if (direct == null) {
         return List.copyOf(factories);
      }

      List<RecipeFileStore.SourceTarget> result = new ArrayList<>(factories.size() + 1);
      result.add(direct);
      result.addAll(factories);
      return List.copyOf(result);
   }

   private static Map<String, Object> expandFactoryTarget(RecipeFileStore.FactoryTarget candidate, Key id) {
      RecipeFileStore.SourceTarget target = candidate.target();
      Map<String, TemplateArgument> arguments = factoryArguments(target.factoryKey(), target.instancesKey(), target.instanceIndex(), target.instance());
      arguments.put("__NAMESPACE__", PlainStringTemplateArgument.plain(id.namespace()));
      arguments.put("__ID__", PlainStringTemplateArgument.plain(id.value()));
      Object expanded = expandValue(target.generatedNode(), candidate.blueprint(), arguments);
      return asStringMap(expanded);
   }

   private static Object expandValue(String path, Object value, Map<String, TemplateArgument> arguments) {
      if (value instanceof String text && text.contains("$")) {
         return ArgumentString.preParse(path, text).get(path, arguments);
      } else if (value instanceof Map<?, ?> map) {
         Map<String, Object> result = new LinkedHashMap<>();

         for (Entry<?, ?> entry : map.entrySet()) {
            String rawKey = String.valueOf(entry.getKey());
            String key = rawKey.contains("$") ? ArgumentString.preParse(path, rawKey).get(path, arguments).toString() : rawKey;
            result.put(key, expandValue(path + "." + key, entry.getValue(), arguments));
         }

         return result;
      } else if (!(value instanceof List<?> list)) {
         return value;
      } else {
         List<Object> result = new ArrayList<>(list.size());

         for (int i = 0; i < list.size(); i++) {
            result.add(expandValue(path + "[" + i + "]", list.get(i), arguments));
         }

         return result;
      }
   }

   private static RecipeFileStore.CachedTargets scanTargets(Path file, long modified, long size) {
      YamlConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
      Set<String> directNodes = new HashSet<>();
      List<RecipeFileStore.FactoryTarget> factories = new ArrayList<>();

      for (String rootKey : config.getKeys(false)) {
         String base = baseKey(rootKey);
         RecipeSourceIndex.Kind directKind = kindOfSection(base);
         ConfigurationSection root = config.getConfigurationSection(rootKey);
         if (root != null) {
            if (directKind != null) {
               for (String id : root.getKeys(false)) {
                  directNodes.add(rootKey + "." + id);
               }
            } else if (matchesAlias(base, FACTORY_SECTIONS)) {
               scanFactory(rootKey, root, factories);
            }
         }
      }

      return new RecipeFileStore.CachedTargets(modified, size, Set.copyOf(directNodes), List.copyOf(factories));
   }

   private static void scanFactory(String factoryKey, ConfigurationSection factory, List<RecipeFileStore.FactoryTarget> targets) {
      String instancesKey = firstExistingKey(factory, FACTORY_INSTANCES);
      String blueprintKey = firstExistingKey(factory, FACTORY_BLUEPRINTS);
      if (instancesKey != null && blueprintKey != null) {
         List<?> instances = factory.getList(instancesKey);
         ConfigurationSection blueprint = factory.getConfigurationSection(blueprintKey);
         if (instances != null && blueprint != null) {
            for (int index = 0; index < instances.size(); index++) {
               Map<String, Object> instance = asStringMap(instances.get(index));
               if (!instance.isEmpty()) {
                  Map<String, TemplateArgument> arguments = factoryArguments(factoryKey, instancesKey, index, instance);

                  for (String parserKey : blueprint.getKeys(false)) {
                     RecipeSourceIndex.Kind kind = kindOfSection(baseKey(parserKey));
                     ConfigurationSection recipes = blueprint.getConfigurationSection(parserKey);
                     if (kind != null && recipes != null) {
                        for (String recipeKey : recipes.getKeys(false)) {
                           try {
                              String path = parserKey + "." + recipeKey;
                              String generatedId = ArgumentString.preParse(path, recipeKey).get(path, arguments).toString();
                              RecipeFileStore.SourceTarget target = RecipeFileStore.SourceTarget.factory(
                                 parserKey + "." + generatedId, factoryKey, instancesKey, index, instance
                              );
                              Map<String, Object> recipeNode = asStringMap(recipes.get(recipeKey));
                              targets.add(new RecipeFileStore.FactoryTarget(kind, generatedId, target, recipeNode));
                           } catch (RuntimeException var20) {
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static Map<String, TemplateArgument> factoryArguments(String factoryKey, String instancesKey, int index, Map<String, Object> instance) {
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
      } else if (matchesAlias(section, POT_FLEX_SECTIONS)) {
         return RecipeSourceIndex.Kind.POT_FLEX;
      } else if (matchesAlias(section, STOCK_FLEX_SECTIONS)) {
         return RecipeSourceIndex.Kind.STOCK_FLEX;
      } else if (matchesAlias(section, CHOPPING_SECTIONS)) {
         return RecipeSourceIndex.Kind.CHOPPING;
      } else {
         return matchesAlias(section, TEAPOT_SECTIONS) ? RecipeSourceIndex.Kind.TEAPOT : null;
      }
   }

   private static String baseKey(String key) {
      int hash = key.indexOf(35);
      return hash < 0 ? key : key.substring(0, hash);
   }

   private static Map<String, Object> asStringMap(Object value) {
      if (value instanceof ConfigurationSection section) {
         return sectionMap(section);
      } else if (!(value instanceof Map<?, ?> map)) {
         return Map.of();
      } else {
         LinkedHashMap result = new LinkedHashMap();

         for (Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue()));
         }

         return result;
      }
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
      } else if (value instanceof Map) {
         return asStringMap(value);
      } else if (!(value instanceof List<?> list)) {
         return value;
      } else {
         List<Object> result = new ArrayList<>(list.size());

         for (Object element : list) {
            result.add(normalizeValue(element));
         }

         return result;
      }
   }

   private static Map<String, Object> immutableMap(Map<String, Object> input) {
      Map<String, Object> result = new LinkedHashMap<>();

      for (Entry<String, Object> entry : input.entrySet()) {
         result.put(entry.getKey(), immutableValue(entry.getValue()));
      }

      return Collections.unmodifiableMap(result);
   }

   private static Object immutableValue(Object value) {
      if (value instanceof Map<?, ?> map) {
         return immutableMap(asStringMap(map));
      } else if (!(value instanceof List<?> list)) {
         return value;
      } else {
         List<Object> result = new ArrayList<>(list.size());

         for (Object element : list) {
            result.add(immutableValue(element));
         }

         return Collections.unmodifiableList(result);
      }
   }

   private static Object fileLock(Path file) {
      return FILE_LOCKS.computeIfAbsent(file.toAbsolutePath().normalize(), ignored -> new Object());
   }

   private static void saveAtomic(YamlConfiguration config, Path target) throws IOException {
      Files.createDirectories(target.getParent());
      Path temporary = target.resolveSibling(target.getFileName() + ".cookery.tmp");
      config.save(temporary.toFile());

      try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
         channel.force(true);
      }

      try {
         Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ignored) {
         Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }

      TARGET_CACHE.remove(target.toAbsolutePath().normalize());
   }

   private static ConfigurationSection resolveSection(YamlConfiguration config, String[] aliases, Key id, boolean createIfAbsent) {
      ConfigurationSection first = null;

      for (String rootKey : config.getKeys(false)) {
         int hash = rootKey.indexOf(35);
         String base = hash < 0 ? rootKey : rootKey.substring(0, hash);
         if (matchesAlias(base, aliases)) {
            ConfigurationSection candidate = config.getConfigurationSection(rootKey);
            if (candidate != null) {
               if (candidate.contains(id.asString())) {
                  return candidate;
               }

               if (first == null) {
                  first = candidate;
               }
            }
         }
      }

      if (first != null) {
         return first;
      } else {
         return createIfAbsent ? config.createSection(aliases[0]) : null;
      }
   }

   private static boolean matchesAlias(String base, String[] aliases) {
      for (String alias : aliases) {
         if (alias.equals(base)) {
            return true;
         }
      }

      return false;
   }

   public static void logFailure(String action, Key id, Throwable error) {
      KaleidoscopeCookeryPlugin.instance().getLogger().warning("配方 " + id.asString() + " " + action + " 失败: " + error.getMessage());
   }

   private record CachedTargets(long modified, long size, Set<String> directNodes, List<RecipeFileStore.FactoryTarget> factories) {
   }

   private record FactoryTarget(RecipeSourceIndex.Kind kind, String generatedId, RecipeFileStore.SourceTarget target, Map<String, Object> blueprint) {
      private FactoryTarget {
         blueprint = RecipeFileStore.immutableMap(blueprint);
      }
   }

   public record SourceTarget(String generatedNode, String factoryKey, String instancesKey, int instanceIndex, Map<String, Object> instance) {
      public SourceTarget {
         instance = instance == null ? Map.of() : RecipeFileStore.immutableMap(instance);
      }

      public static RecipeFileStore.SourceTarget direct(String nodePath) {
         return new RecipeFileStore.SourceTarget(nodePath, null, null, -1, Map.of());
      }

      public static RecipeFileStore.SourceTarget factory(
         String generatedNode, String factoryKey, String instancesKey, int instanceIndex, Map<String, Object> instance
      ) {
         return new RecipeFileStore.SourceTarget(generatedNode, factoryKey, instancesKey, instanceIndex, instance);
      }

      public static RecipeFileStore.SourceTarget unresolved(String generatedNode) {
         return new RecipeFileStore.SourceTarget(generatedNode, "", null, -1, Map.of());
      }

      public boolean factory() {
         return this.factoryKey != null && !this.factoryKey.isEmpty();
      }

      public boolean resolved() {
         return this.factoryKey == null || !this.factoryKey.isEmpty();
      }
   }
}
