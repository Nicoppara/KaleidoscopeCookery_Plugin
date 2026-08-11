package net.kaleidoscope.cookery.recipe;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.kaleidoscope.cookery.recipe.edit.RecipeFileStore;
import net.kaleidoscope.cookery.recipe.edit.RecipeSourceIndex;
import net.kaleidoscope.cookery.util.ConsoleMessages;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.IdSectionConfigParser;
import net.momirealms.craftengine.core.plugin.config.SectionConfigParser;
import net.momirealms.craftengine.core.plugin.config.lifecycle.LoadingStage;
import net.momirealms.craftengine.core.plugin.config.lifecycle.LoadingStages;
import net.momirealms.craftengine.core.util.Key;
import org.jetbrains.annotations.NotNull;

public final class FoodRecipeManager {
   private static final Key AIR = Key.of("minecraft:air");
   public static final LoadingStage POT_FOOD_RAW = new LoadingStage("pot food raw");
   public static final LoadingStage STOCK_FOOD_RAW = new LoadingStage("stock food raw");
   public static final LoadingStage POT_FLEX_FOODS = new LoadingStage("pot flex foods");
   public static final LoadingStage STOCK_FLEX_FOODS = new LoadingStage("stock flex foods");
   public static final LoadingStage ACCURATE_FOODS = new LoadingStage("accurate foods");
   public static final LoadingStage CHOPPING_BOARD_RAWS = new LoadingStage("chopping board raws");
   public static final LoadingStage TEAPOT_LIQUID = new LoadingStage("teapot liquid");
   public static final LoadingStage TEA_CUP = new LoadingStage("tea cup");
   public static final LoadingStage TEAPOT_RESULT = new LoadingStage("teapot result");

   private FoodRecipeManager() {
   }

   public static void registerParsers() {
      CraftEngine.instance().packManager().registerConfigSectionParser(new FoodRecipeManager.PotFoodRawParser());
      CraftEngine.instance().packManager().registerConfigSectionParser(new FoodRecipeManager.StockFoodRawParser());
      CraftEngine.instance().packManager().registerConfigSectionParser(new FoodRecipeManager.PotFlexFoodsParser());
      CraftEngine.instance().packManager().registerConfigSectionParser(new FoodRecipeManager.StockFlexFoodsParser());
      CraftEngine.instance().packManager().registerConfigSectionParser(new FoodRecipeManager.AccurateFoodsParser());
      CraftEngine.instance().packManager().registerConfigSectionParser(new FoodRecipeManager.ChoppingBoardRawsParser());
      CraftEngine.instance().packManager().registerConfigSectionParser(new FoodRecipeManager.TeapotLiquidParser());
      CraftEngine.instance().packManager().registerConfigSectionParser(new FoodRecipeManager.TeaCupParser());
      CraftEngine.instance().packManager().registerConfigSectionParser(new FoodRecipeManager.TeapotResultParser());
   }

   private static ItemRequirement parseAmount(String raw) {
      String[] parts = raw.trim().split("\\s+", 2);
      int count = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
      return new ItemRequirement(Key.of(parts[0]), count);
   }

   private static int registerRaw(ConfigSection section, ApplianceType cook, String skip) {
      int count = 0;

      for (String key : section.keySet()) {
         if (!key.equals(skip)) {
            for (String itemStr : section.getStringList(key)) {
               ApplianceFoodRegistry.instance().register(cook, Key.of(itemStr));
               count++;
            }
         }
      }

      return count;
   }

   private static boolean parseFlexRecipe(
      Key id,
      Path path,
      ConfigSection section,
      ApplianceType cook,
      List<Key> liquids,
      RecipeSourceIndex.Kind kind,
      boolean duplicate,
      RecipeFileStore.SourceTarget target
   ) {
      Key result = section.getNonNullIdentifier("result");
      Map<Key, Integer> perfect = new LinkedHashMap<>();
      ConfigSection perfectSection = section.getSection("perfect");
      if (perfectSection != null) {
         for (String itemStr : perfectSection.keySet()) {
            int weight = perfectSection.getInt(itemStr, 1);
            if (weight > 0) {
               perfect.put(Key.of(itemStr), weight);
            }
         }
      } else {
         for (String raw : section.getStringList("perfect")) {
            ItemRequirement req = parseAmount(raw);
            perfect.put(req.item(), req.count());
         }
      }

      if (perfect.isEmpty()) {
         KaleidoscopeCookeryPlugin.instance().getLogger().warning(ConsoleMessages.t("food.flex.empty_perfect", id.asString()));
         return false;
      }

      String carrierId = section.getString("carrier", (String)null);
      Key carrier = carrierId != null && !carrierId.isEmpty() && !AIR.asString().equals(carrierId) ? Key.of(carrierId) : null;
      FlexFoodRecipe recipe = FlexFoodRecipe.of(id, result, cook, perfect, liquids, carrier);
      FoodRecipeRegistry.instance().registerMenuFlex(recipe);
      RecipeSourceIndex.instance().put(kind, id, path, target, recipe, duplicate);
      if (duplicate) {
         return false;
      }

      FlexFoodRecipe clash = FoodRecipeRegistry.instance().findSameDirection(recipe);
      if (clash != null) {
         KaleidoscopeCookeryPlugin.instance().getLogger().warning(ConsoleMessages.t("food.flex.duplicate_perfect", id.asString(), clash.id().asString()));
         return false;
      }

      FoodRecipeRegistry.instance().registerFlex(recipe);

      for (Key ingredient : perfect.keySet()) {
         ApplianceFoodRegistry.instance().register(cook, ingredient);
      }

      return true;
   }

   static final class AccurateFoodsParser extends FoodRecipeManager.CookeryIdParser {
      AccurateFoodsParser() {
         super(FoodRecipeManager.ACCURATE_FOODS, List.of(LoadingStages.ITEM), RecipeSourceIndex.Kind.ACCURATE, "accurate_foods", "accurate-foods");
      }

      @Override
      protected void reset() {
         RecipeSourceIndex.instance().clearKind(this.kind());
         FoodRecipeRegistry.instance().clearAccurate();
         ApplianceFoodRegistry.instance().clear(ApplianceType.STEAMER);
         ApplianceFoodRegistry.instance().clear(ApplianceType.SHAWARMA);
         ApplianceFoodRegistry.instance().clear(ApplianceType.MILLSTONE);
      }

      @Override
      protected int parseAndCount(Pack pack, Path path, Key id, ConfigSection section, RecipeFileStore.SourceTarget target) {
         Key input = section.getNonNullIdentifier("require");
         List<WeightedResult> results = new ArrayList<>();
         Object rawResult = section.get("result");
         if (rawResult instanceof List) {
            for (Object o : (List)rawResult) {
               String[] parts = String.valueOf(o).trim().split("\\s+", 2);
               int weight = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 100;
               results.add(new WeightedResult(Key.of(parts[0]), weight));
            }
         } else {
            results.add(new WeightedResult(section.getNonNullIdentifier("result"), 100));
         }

         ApplianceType cook = ApplianceType.valueOf(section.getNonNullString("cook").toUpperCase());
         int resultCount = Math.max(1, section.getInt(new String[]{"result_count", "result-count"}, 1));
         int rotations = 0;
         if (section.get("rotations") != null) {
            if (cook != ApplianceType.MILLSTONE) {
               KaleidoscopeCookeryPlugin.instance().getLogger().warning(ConsoleMessages.t("food.accurate.rotations_millstone_only", id.asString()));
               return 0;
            }

            rotations = section.getInt("rotations", 0);
         }

         List<String> lore = section.getStringList("lore");
         AccurateFoodRecipe recipe = new AccurateFoodRecipe(id, input, results, cook, rotations, resultCount, lore);
         boolean duplicate = this.duplicated(id);
         FoodRecipeRegistry.instance().registerMenuAccurate(recipe);
         RecipeSourceIndex.instance().put(this.kind(), id, path, target, recipe, duplicate);
         if (duplicate) {
            return 0;
         }

         FoodRecipeRegistry.instance().registerAccurate(recipe);
         ApplianceFoodRegistry.instance().register(cook, input);
         return 1;
      }
   }

   static final class ChoppingBoardRawsParser extends FoodRecipeManager.CookeryIdParser {
      ChoppingBoardRawsParser() {
         super(
            FoodRecipeManager.CHOPPING_BOARD_RAWS, List.of(LoadingStages.ITEM), RecipeSourceIndex.Kind.CHOPPING, "chopping_board_raws", "chopping-board-raws"
         );
      }

      @Override
      protected void reset() {
         RecipeSourceIndex.instance().clearKind(this.kind());
         FoodRecipeRegistry.instance().clearChopping();
         ApplianceFoodRegistry.instance().clear(ApplianceType.CHOPPING_BOARD);
      }

      @Override
      protected int parseAndCount(Pack pack, Path path, Key id, ConfigSection section, RecipeFileStore.SourceTarget target) {
         Key input = section.getNonNullIdentifier("require");
         int stage = section.getInt("stage", 1);
         String prefix = section.getString("values", (String)null);
         List<String> values = new ArrayList<>(prefix == null ? 0 : stage);
         if (prefix != null && !prefix.isEmpty()) {
            for (int i = 0; i < stage; i++) {
               values.add(prefix + "/" + i);
            }
         }

         int modelCount = values.isEmpty() ? stage : 0;

         for (int i = 0;
            !values.isEmpty() && i < stage + 16 && CraftEngine.instance().itemManager().getItemDefinition(Key.of(prefix + "/" + i)).isPresent();
            i++
         ) {
            modelCount++;
         }

         if (modelCount != stage) {
            KaleidoscopeCookeryPlugin.instance()
               .getLogger()
               .warning(ConsoleMessages.t("food.chopping.model_stage_mismatch", id.asString(), modelCount, stage, prefix, stage - 1));
         }

         ChoppingMode mode = ChoppingMode.fromConfig(section.getString("mode"));
         List<ChoppingResult> results = parseChoppingResults(section.get("result"));
         List<ChoppingResult> extras = parseChoppingResults(section.get("extra"));
         if ((mode == ChoppingMode.SINGLE || mode == ChoppingMode.SINGLE_EXTRA) && results.size() > 1) {
            KaleidoscopeCookeryPlugin.instance()
               .getLogger()
               .warning(ConsoleMessages.t("food.chopping.single_result_too_many", id.asString(), mode, results.size()));
            return 0;
         }

         ChoppingBoardRecipe recipe = new ChoppingBoardRecipe(id, input, stage, values, mode, results, extras);
         boolean duplicate = this.duplicated(id);
         FoodRecipeRegistry.instance().registerMenuChopping(recipe);
         RecipeSourceIndex.instance().put(this.kind(), id, path, target, recipe, duplicate);
         if (duplicate) {
            return 0;
         }

         FoodRecipeRegistry.instance().registerChopping(recipe);
         ApplianceFoodRegistry.instance().register(ApplianceType.CHOPPING_BOARD, input);
         return 1;
      }

      private static List<ChoppingResult> parseChoppingResults(Object rawResult) {
         List<ChoppingResult> results = new ArrayList<>();
         if (rawResult instanceof List) {
            for (Object o : (List)rawResult) {
               results.add(parseChoppingResult(String.valueOf(o)));
            }
         } else if (rawResult != null) {
            results.add(parseChoppingResult(String.valueOf(rawResult)));
         }

         return results;
      }

      private static ChoppingResult parseChoppingResult(String raw) {
         String[] parts = raw.trim().split("\\s+");
         Key key = Key.of(parts[0]);
         int cnt = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
         int weight = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 100;
         return new ChoppingResult(key, cnt, weight);
      }
   }

   private abstract static class CookeryIdParser extends IdSectionConfigParser {
      private final LoadingStage stage;
      private final List<LoadingStage> dependencies;
      private final String[] sectionIds;
      private final RecipeSourceIndex.Kind kind;
      private final Map<Key, List<Path>> occurrences = new LinkedHashMap<>();
      private final Set<FoodRecipeManager.CookeryIdParser.ClaimedTarget> claimedTargets = new LinkedHashSet<>();
      private boolean loadActive;
      private int count;

      CookeryIdParser(LoadingStage stage, List<LoadingStage> dependencies, RecipeSourceIndex.Kind kind, String... sectionIds) {
         this.stage = stage;
         this.dependencies = dependencies;
         this.kind = kind;
         this.sectionIds = sectionIds;
      }

      public String[] sectionId() {
         return this.sectionIds;
      }

      public LoadingStage loadingStage() {
         return this.stage;
      }

      public List<LoadingStage> dependencies() {
         return this.dependencies;
      }

      public int count() {
         return this.count;
      }

      public void preProcess() {
         RecipeSourceIndex.instance().beginLoad(this.kind);
         this.loadActive = true;

         try {
            this.count = 0;
            this.occurrences.clear();
            this.claimedTargets.clear();
            this.reset();
         } catch (RuntimeException | Error error) {
            this.finishLoad();
            throw error;
         }
      }

      public void loadAll() {
         try {
            super.loadAll();
         } catch (RuntimeException | Error error) {
            this.finishLoad();
            throw error;
         }
      }

      public void postProcess() {
         this.finishLoad();
      }

      private void finishLoad() {
         if (this.loadActive) {
            this.loadActive = false;
            RecipeSourceIndex.instance().endLoad(this.kind);
         }
      }

      protected boolean isDuplicate(Key id, Path filePath, String currentNode) {
         List<Path> files = this.occurrences.computeIfAbsent(id, ignored -> new ArrayList<>());
         files.add(filePath.toAbsolutePath().normalize());
         if (files.size() > 1) {
            KaleidoscopeCookeryPlugin.instance()
               .getLogger()
               .severe("食谱 ID 重复，所有重复定义均不加载: " + id.asString());
         }

         return false;
      }

      protected final boolean duplicated(Key id) {
         List<Path> files = this.occurrences.get(id);
         return files != null && files.size() > 1;
      }

      protected final RecipeSourceIndex.Kind kind() {
         return this.kind;
      }

      protected final void parseSection(@NotNull Pack pack, @NotNull Path path, @NotNull Key id, @NotNull ConfigSection section) {
         List<RecipeFileStore.SourceTarget> targets = new ArrayList<>(RecipeFileStore.resolveTargets(this.kind, id, path, section.path(), section.values()));

         for (RecipeFileStore.SourceTarget deleted : RecipeSourceIndex.instance().deletedTargets(this.kind, id, path)) {
            if (deleted.generatedNode().equals(section.path()) && !targets.contains(deleted)) {
               targets.add(deleted);
            }
         }

         RecipeFileStore.SourceTarget target = null;

         for (RecipeFileStore.SourceTarget candidate : targets) {
            if (this.claimedTargets.add(new FoodRecipeManager.CookeryIdParser.ClaimedTarget(path, candidate))) {
               target = candidate;
               break;
            }
         }

         if (target == null) {
            target = RecipeFileStore.SourceTarget.unresolved(section.path());
            this.claimedTargets.add(new FoodRecipeManager.CookeryIdParser.ClaimedTarget(path, target));
         }

         if (!RecipeSourceIndex.instance().isDeleted(this.kind, id, path, target)) {
            this.count = this.count + this.parseAndCount(pack, path, id, section, target);
         }
      }

      protected abstract void reset();

      protected abstract int parseAndCount(Pack var1, Path var2, Key var3, ConfigSection var4, RecipeFileStore.SourceTarget var5);

      private record ClaimedTarget(Path file, RecipeFileStore.SourceTarget target) {
         private ClaimedTarget {
            file = file.toAbsolutePath().normalize();
         }
      }
   }

   private abstract static class CookerySectionParser extends SectionConfigParser {
      private final LoadingStage stage;
      private final List<LoadingStage> dependencies;
      private final String[] sectionIds;
      private int count;

      CookerySectionParser(LoadingStage stage, List<LoadingStage> dependencies, String... sectionIds) {
         this.stage = stage;
         this.dependencies = dependencies;
         this.sectionIds = sectionIds;
      }

      public String[] sectionId() {
         return this.sectionIds;
      }

      public LoadingStage loadingStage() {
         return this.stage;
      }

      public List<LoadingStage> dependencies() {
         return this.dependencies;
      }

      public int count() {
         return this.count;
      }

      public void preProcess() {
         this.count = 0;
         this.reset();
      }

      protected final void parseSection(Pack pack, Path path, ConfigSection section) {
         this.count = this.count + this.parseAndCount(pack, path, section);
      }

      protected abstract void reset();

      protected abstract int parseAndCount(Pack var1, Path var2, ConfigSection var3);
   }

   static final class PotFlexFoodsParser extends FoodRecipeManager.CookeryIdParser {
      PotFlexFoodsParser() {
         super(FoodRecipeManager.POT_FLEX_FOODS, List.of(FoodRecipeManager.POT_FOOD_RAW), RecipeSourceIndex.Kind.POT_FLEX, "pot_flex_foods", "pot-flex-foods");
      }

      @Override
      protected void reset() {
         RecipeSourceIndex.instance().clearKind(this.kind());
         FoodRecipeRegistry.instance().clearFlex(ApplianceType.POT);
      }

      @Override
      protected int parseAndCount(Pack pack, Path path, Key id, ConfigSection section, RecipeFileStore.SourceTarget target) {
         return FoodRecipeManager.parseFlexRecipe(id, path, section, ApplianceType.POT, List.of(), this.kind(), this.duplicated(id), target) ? 1 : 0;
      }
   }

   static final class PotFoodRawParser extends FoodRecipeManager.CookerySectionParser {
      PotFoodRawParser() {
         super(FoodRecipeManager.POT_FOOD_RAW, List.of(LoadingStages.ITEM), "pot_food_raw", "pot-food-raw");
      }

      @Override
      protected void reset() {
         ApplianceFoodRegistry.instance().clear(ApplianceType.POT);
      }

      @Override
      protected int parseAndCount(Pack pack, Path path, ConfigSection section) {
         return FoodRecipeManager.registerRaw(section, ApplianceType.POT, null);
      }
   }

   static final class StockFlexFoodsParser extends FoodRecipeManager.CookeryIdParser {
      StockFlexFoodsParser() {
         super(
            FoodRecipeManager.STOCK_FLEX_FOODS,
            List.of(FoodRecipeManager.STOCK_FOOD_RAW),
            RecipeSourceIndex.Kind.STOCK_FLEX,
            "stock_flex_foods",
            "stock-flex-foods"
         );
      }

      @Override
      protected void reset() {
         RecipeSourceIndex.instance().clearKind(this.kind());
         FoodRecipeRegistry.instance().clearFlex(ApplianceType.STOCKPOT);
      }

      @Override
      protected int parseAndCount(Pack pack, Path path, Key id, ConfigSection section, RecipeFileStore.SourceTarget target) {
         List<Key> liquids = section.getStringList("liquid").stream().<Key>map(Key::of).toList();
         return FoodRecipeManager.parseFlexRecipe(id, path, section, ApplianceType.STOCKPOT, liquids, this.kind(), this.duplicated(id), target) ? 1 : 0;
      }
   }

   static final class StockFoodRawParser extends FoodRecipeManager.CookerySectionParser {
      StockFoodRawParser() {
         super(FoodRecipeManager.STOCK_FOOD_RAW, List.of(LoadingStages.ITEM), "stock_food_raw", "stock-food-raw");
      }

      @Override
      protected void reset() {
         ApplianceFoodRegistry.instance().clear(ApplianceType.STOCKPOT);
         SoupBaseRegistry.instance().clear();
      }

      @Override
      protected int parseAndCount(Pack pack, Path path, ConfigSection section) {
         int raws = FoodRecipeManager.registerRaw(section, ApplianceType.STOCKPOT, "liquid");
         return raws
            + section.getSectionList(
                  "liquid",
                  s -> {
                     String show = s.getString(new String[]{"show"}, (String)null);
                     SoupBaseRegistry.instance()
                        .register(s.getNonNullIdentifier("item"), show != null && !show.isBlank() ? Key.of(show) : SoupBaseRegistry.DEFAULT_SHOW);
                     return s;
                  }
               )
               .size();
      }
   }

   static final class TeaCupParser extends FoodRecipeManager.CookerySectionParser {
      TeaCupParser() {
         super(FoodRecipeManager.TEA_CUP, List.of(LoadingStages.ITEM), "tea_cup", "tea-cup");
      }

      @Override
      protected void reset() {
         FoodRecipeRegistry.instance().clearTeaCup();
      }

      @Override
      protected int parseAndCount(Pack pack, Path path, ConfigSection section) {
         int count = 0;

         for (String teaStr : section.keySet()) {
            ConfigSection sub = section.getSection(teaStr);
            if (sub != null) {
               Key tea = Key.of(teaStr);
               Key item = Key.of(sub.getString(new String[]{"item"}, teaStr));
               Object raw = sub.get("display_model");
               if (raw == null) {
                  raw = sub.get("display-model");
               }

               List<Key> models = new ArrayList<>();
               if (raw instanceof List) {
                  for (Object o : (List)raw) {
                     models.add(Key.of(String.valueOf(o)));
                  }
               } else if (raw != null) {
                  models.add(Key.of(String.valueOf(raw)));
               }

               if (!models.isEmpty()) {
                  FoodRecipeRegistry.instance().registerTeaCup(new TeaCup(tea, item, models));
                  count++;
               }
            }
         }

         return count;
      }
   }

   static final class TeapotLiquidParser extends FoodRecipeManager.CookerySectionParser {
      TeapotLiquidParser() {
         super(FoodRecipeManager.TEAPOT_LIQUID, List.of(LoadingStages.ITEM), "teapot_liquid", "teapot-liquid");
      }

      @Override
      protected void reset() {
         FoodRecipeRegistry.instance().clearTeapotLiquid();
      }

      @Override
      protected int parseAndCount(Pack pack, Path path, ConfigSection section) {
         int count = 0;

         for (String fluidStr : section.keySet()) {
            ConfigSection sub = section.getSection(fluidStr);
            if (sub != null) {
               String name = sub.getString(new String[]{"display_name", "display-name"}, fluidStr);
               String left = sub.getString(new String[]{"bar_left", "bar-left"}, "");
               String right = sub.getString(new String[]{"bar_right", "bar-right"}, "");
               String empty = sub.getString(new String[]{"bar_empty", "bar-empty"}, "");
               String full = findFullGlyph(sub);
               FoodRecipeRegistry.instance().registerTeapotLiquid(new TeapotLiquid(Key.of(fluidStr), name, left, right, empty, full));
               count++;
            }
         }

         return count;
      }

      private static String findFullGlyph(ConfigSection sub) {
         for (String key : sub.keySet()) {
            String norm = key.replace('-', '_');
            if (!norm.equals("bar_left") && !norm.equals("bar_right") && !norm.equals("bar_empty") && norm.startsWith("bar_")) {
               String value = sub.getString(new String[]{key}, "");
               if (value != null && !value.isEmpty()) {
                  return value;
               }
            }
         }

         return "";
      }
   }

   static final class TeapotResultParser extends FoodRecipeManager.CookeryIdParser {
      TeapotResultParser() {
         super(
            FoodRecipeManager.TEAPOT_RESULT,
            List.of(LoadingStages.ITEM, FoodRecipeManager.TEAPOT_LIQUID, FoodRecipeManager.TEA_CUP),
            RecipeSourceIndex.Kind.TEAPOT,
            "teapot_result",
            "teapot-result"
         );
      }

      @Override
      protected void reset() {
         RecipeSourceIndex.instance().clearKind(this.kind());
         FoodRecipeRegistry.instance().clearTeapot();
         ApplianceFoodRegistry.instance().clear(ApplianceType.TEAPOT);
      }

      @Override
      protected int parseAndCount(Pack pack, Path path, Key id, ConfigSection section, RecipeFileStore.SourceTarget target) {
         Key fluid = section.getNonNullIdentifier("fluid");
         if (!FoodRecipeRegistry.instance().hasTeapotLiquid(fluid)) {
            KaleidoscopeCookeryPlugin.instance().getLogger().warning(ConsoleMessages.t("food.teapot.unregistered_liquid", id.asString(), fluid.asString()));
            return 0;
         }

         ItemRequirement ingredient = FoodRecipeManager.parseAmount(section.getNonNullString("require"));
         ItemRequirement result = FoodRecipeManager.parseAmount(section.getNonNullString("result"));
         if (!FoodRecipeRegistry.instance().hasTeaCup(result.item())) {
            KaleidoscopeCookeryPlugin.instance().getLogger().warning(ConsoleMessages.t("food.teapot.missing_tea_cup", id.asString(), result.item().asString()));
            return 0;
         }

         int time = section.getInt("time", 200);
         TeapotRecipe recipe = new TeapotRecipe(id, fluid, ingredient.item(), ingredient.count(), result.item(), result.count(), time);
         boolean duplicate = this.duplicated(id);
         FoodRecipeRegistry.instance().registerMenuTeapot(recipe);
         RecipeSourceIndex.instance().put(this.kind(), id, path, target, recipe, duplicate);
         if (duplicate) {
            return 0;
         }

         FoodRecipeRegistry.instance().registerTeapot(recipe);
         ApplianceFoodRegistry.instance().register(ApplianceType.TEAPOT, ingredient.item());
         return 1;
      }
   }
}
