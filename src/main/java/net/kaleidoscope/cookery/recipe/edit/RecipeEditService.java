package net.kaleidoscope.cookery.recipe.edit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.kaleidoscope.cookery.recipe.AccurateFoodRecipe;
import net.kaleidoscope.cookery.recipe.ApplianceFoodRegistry;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.ChoppingBoardRecipe;
import net.kaleidoscope.cookery.recipe.ChoppingMode;
import net.kaleidoscope.cookery.recipe.ChoppingResult;
import net.kaleidoscope.cookery.recipe.FlexFoodRecipe;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.SoupBaseRegistry;
import net.kaleidoscope.cookery.recipe.TeapotRecipe;
import net.kaleidoscope.cookery.recipe.WeightedResult;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.util.Key;

public final class RecipeEditService {
   private RecipeEditService() {
   }

   public static String validate(AccurateRecipeDraft draft) {
      String idError = validateId(draft.id());
      if (idError != null) {
         return idError;
      } else if (draft.input() == null) {
         return "请先设置原料";
      } else if (draft.results().isEmpty()) {
         return "请先设置至少一个成品";
      } else {
         return isTakenId(draft.id(), draft.originalId(), k -> RecipeSourceIndex.instance().hasSource(RecipeSourceIndex.Kind.ACCURATE, k))
            ? "该 id 已被占用"
            : null;
      }
   }

   public static String validate(FlexRecipeDraft draft) {
      String idError = validateId(draft.id());
      if (idError != null) {
         return idError;
      } else if (draft.result() == null) {
         return "请先设置成品";
      } else if (draft.perfect().isEmpty()) {
         return "请先设置至少一种原料";
      } else {
         return isTakenId(
               draft.id(),
               draft.originalId(),
               k -> RecipeSourceIndex.instance()
                  .hasSource(draft.cook() == ApplianceType.STOCKPOT ? RecipeSourceIndex.Kind.STOCK_FLEX : RecipeSourceIndex.Kind.POT_FLEX, k)
            )
            ? "该 id 已被占用"
            : null;
      }
   }

   private static boolean isTakenId(Key id, Key originalId, Predicate<Key> exists) {
      return originalId != null && originalId.equals(id) ? false : exists.test(id);
   }

   private static String validateId(Key id) {
      if (id == null) {
         return "配方 id 无效";
      } else {
         return id.value().indexOf(46) < 0 && id.namespace().indexOf(46) < 0 ? null : "配方 id 不能包含点号";
      }
   }

   public static String saveAccurate(AccurateRecipeDraft draft) {
      String error = validate(draft);
      if (error != null) {
         return error;
      }

      AccurateFoodRecipe recipe = draft.toRecipe();
      Key oldId = draft.originalId();
      Path file = resolveFile(draft.originalRecipe(), RecipeFileStore::defaultAccurateFile);
      if (file == null) {
         return "找不到可写入的配方文件";
      }

      String oldNode = RecipeSourceIndex.instance().nodePath(draft.originalRecipe());
      RecipeFileStore.SourceTarget oldTarget = RecipeSourceIndex.instance().target(draft.originalRecipe());
      String nodePath = resolveNodePath(oldNode, RecipeFileStore.accurateSections(), recipe.id());
      AccurateFoodRecipe old = draft.originalRecipe();
      if (old != null) {
         FoodRecipeRegistry.instance().removeAccurate(oldId);
         FoodRecipeRegistry.instance().removeMenuAccurate(old);
         RecipeSourceIndex.instance().remove(old);
         if (!old.input().equals(recipe.input())) {
            dropInputIfUnused(old.cook(), old.input(), k -> accurateUses(old.cook(), k));
         }
      }

      RecipeSourceIndex.Kind kind = RecipeSourceIndex.Kind.ACCURATE;
      RecipeSourceIndex.instance().restore(kind, recipe.id(), file, nodePath);
      boolean duplicate = RecipeSourceIndex.instance().hasOtherSource(kind, recipe.id(), file, nodePath);
      FoodRecipeRegistry.instance().registerMenuAccurate(recipe);
      RecipeSourceIndex.instance().put(kind, recipe.id(), file, nodePath, recipe, duplicate);
      if (!duplicate) {
         FoodRecipeRegistry.instance().registerAccurate(recipe);
         ApplianceFoodRegistry.instance().register(recipe.cook(), recipe.input());
      }

      Map<String, Object> node = accurateNode(recipe);
      runAsync(() -> {
         try {
            RecipeFileStore.replaceTarget(file, oldTarget, nodePath, node);
         } catch (Exception e) {
            RecipeFileStore.logFailure("保存", recipe.id(), e);
         }
      });
      return null;
   }

   public static String saveFlex(FlexRecipeDraft draft) {
      String error = validate(draft);
      if (error != null) {
         return error;
      }

      FlexFoodRecipe recipe = draft.toRecipe();
      Key oldId = draft.originalId();
      String[] sections = RecipeFileStore.flexSections(recipe.cook());
      Path file = resolveFile(draft.originalRecipe(), () -> RecipeFileStore.defaultFlexFile(recipe.cook()));
      if (file == null) {
         return "找不到可写入的配方文件";
      }

      String oldNode = RecipeSourceIndex.instance().nodePath(draft.originalRecipe());
      RecipeFileStore.SourceTarget oldTarget = RecipeSourceIndex.instance().target(draft.originalRecipe());
      String nodePath = resolveNodePath(oldNode, sections, recipe.id());
      FlexFoodRecipe old = draft.originalRecipe();
      boolean oldDuplicate = old != null && RecipeSourceIndex.instance().isDuplicate(old);
      if (old != null) {
         FoodRecipeRegistry.instance().removeFlex(oldId);
         FoodRecipeRegistry.instance().removeMenuFlex(old);
         RecipeSourceIndex.instance().remove(old);
      }

      RecipeSourceIndex.Kind kind = recipe.cook() == ApplianceType.STOCKPOT ? RecipeSourceIndex.Kind.STOCK_FLEX : RecipeSourceIndex.Kind.POT_FLEX;
      RecipeSourceIndex.instance().restore(kind, recipe.id(), file, nodePath);
      boolean duplicate = RecipeSourceIndex.instance().hasOtherSource(kind, recipe.id(), file, nodePath);
      FlexFoodRecipe clash = duplicate ? null : FoodRecipeRegistry.instance().findSameDirection(recipe);
      if (clash != null) {
         if (old != null) {
            FoodRecipeRegistry.instance().registerMenuFlex(old);
            RecipeSourceIndex.instance().put(kind, old.id(), file, oldNode, old, oldDuplicate);
            if (!oldDuplicate) {
               FoodRecipeRegistry.instance().registerFlex(old);
            }
         }

         return "配比方向与 " + clash.id().asString() + " 相同 会永远打平";
      } else {
         FoodRecipeRegistry.instance().registerMenuFlex(recipe);
         RecipeSourceIndex.instance().put(kind, recipe.id(), file, nodePath, recipe, duplicate);
         if (!duplicate) {
            FoodRecipeRegistry.instance().registerFlex(recipe);
         }

         if (!duplicate) {
            for (Key ingredient : recipe.perfect().keySet()) {
               ApplianceFoodRegistry.instance().register(recipe.cook(), ingredient);
            }
         }

         Map<String, Object> node = flexNode(draft);
         runAsync(() -> {
            try {
               RecipeFileStore.replaceTarget(file, oldTarget, nodePath, node);
            } catch (Exception e) {
               RecipeFileStore.logFailure("保存", recipe.id(), e);
            }
         });
         return null;
      }
   }

   public static String validate(ChoppingRecipeDraft draft) {
      String idError = validateId(draft.id());
      if (idError != null) {
         return idError;
      } else if (isTakenId(draft.id(), draft.originalId(), k -> RecipeSourceIndex.instance().hasSource(RecipeSourceIndex.Kind.CHOPPING, k))) {
         return "该 id 已被占用";
      } else if (draft.input() == null) {
         return "还没设置原料";
      } else {
         return draft.results().isEmpty() ? "至少要有一个成品" : null;
      }
   }

   public static String validate(TeapotRecipeDraft draft) {
      String idError = validateId(draft.id());
      if (idError != null) {
         return idError;
      } else if (isTakenId(draft.id(), draft.originalId(), k -> RecipeSourceIndex.instance().hasSource(RecipeSourceIndex.Kind.TEAPOT, k))) {
         return "该 id 已被占用";
      } else if (draft.fluid() == null) {
         return "还没设置液体";
      } else if (!FoodRecipeRegistry.instance().hasTeapotLiquid(draft.fluid())) {
         return "该液体没在 teapot_liquid 里登记";
      } else if (draft.input() == null) {
         return "还没设置原料";
      } else if (draft.result() == null) {
         return "还没设置成品";
      } else {
         return !FoodRecipeRegistry.instance().hasTeaCup(draft.result()) ? "该成品没在 tea_cup 里定义模型" : null;
      }
   }

   public static String saveChopping(ChoppingRecipeDraft draft) {
      String error = validate(draft);
      if (error != null) {
         return error;
      }

      ChoppingBoardRecipe recipe = draft.toRecipe();
      Key oldId = draft.originalId();
      Path file = resolveFile(draft.originalRecipe(), RecipeFileStore::defaultChoppingFile);
      if (file == null) {
         return "找不到可写入的配方文件";
      }

      String oldNode = RecipeSourceIndex.instance().nodePath(draft.originalRecipe());
      RecipeFileStore.SourceTarget oldTarget = RecipeSourceIndex.instance().target(draft.originalRecipe());
      String nodePath = resolveNodePath(oldNode, RecipeFileStore.choppingSections(), recipe.id());
      ChoppingBoardRecipe oldChopping = draft.originalRecipe();
      if (oldChopping != null) {
         FoodRecipeRegistry.instance().removeChopping(oldId);
         FoodRecipeRegistry.instance().removeMenuChopping(oldChopping);
         RecipeSourceIndex.instance().remove(oldChopping);
      }

      RecipeSourceIndex.Kind kind = RecipeSourceIndex.Kind.CHOPPING;
      RecipeSourceIndex.instance().restore(kind, recipe.id(), file, nodePath);
      boolean duplicate = RecipeSourceIndex.instance().hasOtherSource(kind, recipe.id(), file, nodePath);
      FoodRecipeRegistry.instance().registerMenuChopping(recipe);
      RecipeSourceIndex.instance().put(kind, recipe.id(), file, nodePath, recipe, duplicate);
      if (!duplicate) {
         FoodRecipeRegistry.instance().registerChopping(recipe);
         ApplianceFoodRegistry.instance().register(ApplianceType.CHOPPING_BOARD, recipe.input());
      }

      if (oldChopping != null && !oldChopping.input().equals(recipe.input())) {
         dropInputIfUnused(ApplianceType.CHOPPING_BOARD, oldChopping.input(), RecipeEditService::choppingUses);
      }

      Map<String, Object> node = choppingNode(draft);
      String[] sections = RecipeFileStore.choppingSections();
      runAsync(() -> {
         try {
            RecipeFileStore.replaceTarget(file, oldTarget, nodePath, node);
         } catch (Exception e) {
            RecipeFileStore.logFailure("保存", recipe.id(), e);
         }
      });
      return null;
   }

   public static String saveTeapot(TeapotRecipeDraft draft) {
      String error = validate(draft);
      if (error != null) {
         return error;
      }

      TeapotRecipe recipe = draft.toRecipe();
      Key oldId = draft.originalId();
      Path file = resolveFile(draft.originalRecipe(), RecipeFileStore::defaultTeapotFile);
      if (file == null) {
         return "找不到可写入的配方文件";
      }

      String oldNode = RecipeSourceIndex.instance().nodePath(draft.originalRecipe());
      RecipeFileStore.SourceTarget oldTarget = RecipeSourceIndex.instance().target(draft.originalRecipe());
      String nodePath = resolveNodePath(oldNode, RecipeFileStore.teapotSections(), recipe.id());
      TeapotRecipe oldTeapot = draft.originalRecipe();
      if (oldTeapot != null) {
         FoodRecipeRegistry.instance().removeTeapot(oldId);
         FoodRecipeRegistry.instance().removeMenuTeapot(oldTeapot);
         RecipeSourceIndex.instance().remove(oldTeapot);
      }

      RecipeSourceIndex.Kind kind = RecipeSourceIndex.Kind.TEAPOT;
      RecipeSourceIndex.instance().restore(kind, recipe.id(), file, nodePath);
      boolean duplicate = RecipeSourceIndex.instance().hasOtherSource(kind, recipe.id(), file, nodePath);
      FoodRecipeRegistry.instance().registerMenuTeapot(recipe);
      RecipeSourceIndex.instance().put(kind, recipe.id(), file, nodePath, recipe, duplicate);
      if (!duplicate) {
         FoodRecipeRegistry.instance().registerTeapot(recipe);
         ApplianceFoodRegistry.instance().register(ApplianceType.TEAPOT, recipe.input());
      }

      if (oldTeapot != null && !oldTeapot.input().equals(recipe.input())) {
         dropInputIfUnused(ApplianceType.TEAPOT, oldTeapot.input(), RecipeEditService::teapotUses);
      }

      Map<String, Object> node = teapotNode(draft);
      String[] sections = RecipeFileStore.teapotSections();
      runAsync(() -> {
         try {
            RecipeFileStore.replaceTarget(file, oldTarget, nodePath, node);
         } catch (Exception e) {
            RecipeFileStore.logFailure("保存", recipe.id(), e);
         }
      });
      return null;
   }

   public static CompletableFuture<Boolean> deleteChopping(ChoppingBoardRecipe recipe) {
      return deleteRecipe(recipe, recipe.id());
   }

   public static CompletableFuture<Boolean> deleteTeapot(TeapotRecipe recipe) {
      return deleteRecipe(recipe, recipe.id());
   }

   public static CompletableFuture<Boolean> deleteAccurate(AccurateFoodRecipe recipe) {
      return deleteRecipe(recipe, recipe.id());
   }

   public static CompletableFuture<Boolean> deleteFlex(FlexFoodRecipe recipe) {
      return deleteRecipe(recipe, recipe.id());
   }

   private static CompletableFuture<Boolean> deleteRecipe(Object recipe, Key id) {
      Path file = RecipeSourceIndex.instance().get(recipe);
      RecipeFileStore.SourceTarget target = RecipeSourceIndex.instance().target(recipe);
      RecipeSourceIndex.Kind kind = RecipeSourceIndex.instance().kind(recipe);
      if (file != null && target != null && kind != null) {
         CompletableFuture<Boolean> result = new CompletableFuture<>();
         runAsync(
            () -> {
               try {
                  if (!RecipeFileStore.deleteTarget(file, target)) {
                     RecipeFileStore.logFailure(
                        "删除",
                        id,
                        new IllegalStateException("找不到该食谱在配置中的真实来源")
                     );
                     result.complete(false);
                     return;
                  }
               } catch (Exception e) {
                  RecipeFileStore.logFailure("删除", id, e);
                  result.complete(false);
                  return;
               }

               RecipeSourceIndex sourceIndex = RecipeSourceIndex.instance();
               sourceIndex.markDeleted(kind, id, file, target);
               sourceIndex.afterCurrentLoad(kind, () -> {
                  try {
                     applyHotDeletion(recipe, kind, id, file, target);
                     result.complete(true);
                  } catch (RuntimeException error) {
                     RecipeFileStore.logFailure("热删除", id, error);
                     result.complete(true);
                  } finally {
                     RecipeSourceIndex.instance().restore(kind, id, file, target);
                  }
               });
            }
         );
         return result;
      } else {
         return CompletableFuture.completedFuture(false);
      }
   }

   private static void applyHotDeletion(Object selected, RecipeSourceIndex.Kind kind, Key id, Path file, RecipeFileStore.SourceTarget target) {
      RecipeSourceIndex sourceIndex = RecipeSourceIndex.instance();
      Object current = sourceIndex.removeSource(kind, id, file, target);
      sourceIndex.remove(selected);
      removeMenuRecipe(selected);
      if (current != selected) {
         removeMenuRecipe(current);
      }

      removeRuntime(kind, id);
      List<Object> remaining = sourceIndex.recipes(kind, id);
      boolean duplicate = remaining.size() > 1;

      for (Object candidate : remaining) {
         sourceIndex.duplicate(candidate, duplicate);
      }

      if (remaining.size() == 1) {
         registerRuntime(remaining.getFirst(), kind);
      }

      if (selected instanceof AccurateFoodRecipe accurate) {
         dropInputIfUnused(accurate.cook(), accurate.input(), input -> accurateUses(accurate.cook(), input));
      } else if (selected instanceof ChoppingBoardRecipe chopping) {
         dropInputIfUnused(ApplianceType.CHOPPING_BOARD, chopping.input(), RecipeEditService::choppingUses);
      } else if (selected instanceof TeapotRecipe teapot) {
         dropInputIfUnused(ApplianceType.TEAPOT, teapot.input(), RecipeEditService::teapotUses);
      }
   }

   private static void removeMenuRecipe(Object recipe) {
      FoodRecipeRegistry registry = FoodRecipeRegistry.instance();
      if (recipe instanceof AccurateFoodRecipe accurate) {
         registry.removeMenuAccurate(accurate);
      } else if (recipe instanceof FlexFoodRecipe flex) {
         registry.removeMenuFlex(flex);
      } else if (recipe instanceof ChoppingBoardRecipe chopping) {
         registry.removeMenuChopping(chopping);
      } else if (recipe instanceof TeapotRecipe teapot) {
         registry.removeMenuTeapot(teapot);
      }
   }

   private static void removeRuntime(RecipeSourceIndex.Kind kind, Key id) {
      FoodRecipeRegistry registry = FoodRecipeRegistry.instance();
      switch (kind) {
         case ACCURATE:
            registry.removeAccurate(id);
            break;
         case POT_FLEX:
            registry.removeFlex(ApplianceType.POT, id);
            break;
         case STOCK_FLEX:
            registry.removeFlex(ApplianceType.STOCKPOT, id);
            break;
         case CHOPPING:
            registry.removeChopping(id);
            break;
         case TEAPOT:
            registry.removeTeapot(id);
      }
   }

   private static void registerRuntime(Object candidate, RecipeSourceIndex.Kind kind) {
      FoodRecipeRegistry registry = FoodRecipeRegistry.instance();
      switch (kind) {
         case ACCURATE: {
            AccurateFoodRecipe recipe = (AccurateFoodRecipe)candidate;
            registry.registerAccurate(recipe);
            ApplianceFoodRegistry.instance().register(recipe.cook(), recipe.input());
            break;
         }
         case POT_FLEX:
         case STOCK_FLEX: {
            FlexFoodRecipe recipe = (FlexFoodRecipe)candidate;
            if (registry.findSameDirection(recipe) == null) {
               registry.registerFlex(recipe);

               for (Key ingredient : recipe.perfect().keySet()) {
                  ApplianceFoodRegistry.instance().register(recipe.cook(), ingredient);
               }
            }
            break;
         }
         case CHOPPING: {
            ChoppingBoardRecipe recipe = (ChoppingBoardRecipe)candidate;
            registry.registerChopping(recipe);
            ApplianceFoodRegistry.instance().register(ApplianceType.CHOPPING_BOARD, recipe.input());
            break;
         }
         case TEAPOT: {
            TeapotRecipe recipe = (TeapotRecipe)candidate;
            registry.registerTeapot(recipe);
            ApplianceFoodRegistry.instance().register(ApplianceType.TEAPOT, recipe.input());
         }
      }
   }

   private static void dropInputIfUnused(ApplianceType cook, Key input, Predicate<Key> stillUsed) {
      if (!stillUsed.test(input)) {
         ApplianceFoodRegistry.instance().unregister(cook, input);
      }
   }

   private static boolean accurateUses(ApplianceType cook, Key input) {
      for (AccurateFoodRecipe r : FoodRecipeRegistry.instance().accurateRecipes(cook)) {
         if (r.input().equals(input)) {
            return true;
         }
      }

      return false;
   }

   private static boolean choppingUses(Key input) {
      for (ChoppingBoardRecipe r : FoodRecipeRegistry.instance().choppingRecipes()) {
         if (r.input().equals(input)) {
            return true;
         }
      }

      return false;
   }

   private static boolean teapotUses(Key input) {
      for (TeapotRecipe r : FoodRecipeRegistry.instance().teapotRecipes()) {
         if (r.input().equals(input)) {
            return true;
         }
      }

      return false;
   }

   private static Path resolveFile(Object existingRecipe, Supplier<Path> fallback) {
      Path known = existingRecipe == null ? null : RecipeSourceIndex.instance().get(existingRecipe);
      if (known != null) {
         return known;
      }

      try {
         return fallback.get();
      } catch (RuntimeException e) {
         return null;
      }
   }

   private static String resolveNodePath(String oldNode, String[] sectionAliases, Key id) {
      if (oldNode != null && !oldNode.isBlank()) {
         int separator = oldNode.lastIndexOf(46);
         return (separator < 0 ? sectionAliases[0] : oldNode.substring(0, separator)) + "." + id.asString();
      } else {
         return sectionAliases[0] + "." + id.asString();
      }
   }

   public static String saveSoupBase(Key bucket, Key show) {
      if (bucket == null) {
         return "汤底物品 id 不能为空";
      }

      Key model = show == null ? SoupBaseRegistry.DEFAULT_SHOW : show;
      SoupBaseRegistry.instance().register(bucket, model);
      runAsync(() -> {
         try {
            RecipeFileStore.writeSoupBase(bucket, model);
         } catch (Exception e) {
            RecipeFileStore.logFailure("保存汤底", bucket, e);
         }
      });
      return null;
   }

   public static void deleteSoupBase(Key bucket) {
      if (bucket != null) {
         SoupBaseRegistry.instance().remove(bucket);
         runAsync(() -> {
            try {
               RecipeFileStore.deleteSoupBase(bucket);
            } catch (Exception e) {
               RecipeFileStore.logFailure("删除汤底", bucket, e);
            }
         });
      }
   }

   private static void runAsync(Runnable task) {
      CraftEngine.instance().scheduler().executeAsync(task);
   }

   private static Map<String, Object> accurateNode(AccurateFoodRecipe recipe) {
      Map<String, Object> node = new LinkedHashMap<>();
      node.put("require", recipe.input().asString());
      List<WeightedResult> results = recipe.results();
      if (results.size() == 1) {
         node.put("result", results.getFirst().key().asString());
      } else {
         List<String> lines = new ArrayList<>(results.size());

         for (WeightedResult r : results) {
            lines.add(r.key().asString() + " " + r.weight());
         }

         node.put("result", lines);
      }

      node.put("cook", recipe.cook().name().toLowerCase());
      if (recipe.resultCount() > 1) {
         node.put("result_count", recipe.resultCount());
      }

      if (recipe.cook() == ApplianceType.MILLSTONE && recipe.rotations() > 0) {
         node.put("rotations", recipe.rotations());
      }

      if (!recipe.lore().isEmpty()) {
         node.put("lore", new ArrayList<>(recipe.lore()));
      }

      return node;
   }

   private static Map<String, Object> choppingNode(ChoppingRecipeDraft draft) {
      Map<String, Object> node = new LinkedHashMap<>();
      node.put("require", draft.input().asString());
      node.put("stage", draft.stage());
      if (draft.modelPrefix() != null) {
         node.put("values", draft.modelPrefix());
      }

      node.put("mode", draft.mode().name().toLowerCase());
      node.put("result", choppingResults(draft.results()));
      if (draft.mode() == ChoppingMode.SINGLE_EXTRA && !draft.extras().isEmpty()) {
         node.put("extra", choppingResults(draft.extras()));
      }

      return node;
   }

   private static List<String> choppingResults(List<ChoppingResult> results) {
      List<String> out = new ArrayList<>(results.size());

      for (ChoppingResult r : results) {
         out.add(r.key().asString() + " " + r.count() + " " + r.weight());
      }

      return out;
   }

   private static Map<String, Object> teapotNode(TeapotRecipeDraft draft) {
      Map<String, Object> node = new LinkedHashMap<>();
      node.put("fluid", draft.fluid().asString());
      node.put("require", draft.input().asString() + " " + draft.ingredientCount());
      node.put("result", draft.result().asString() + " " + draft.resultCount());
      node.put("time", draft.time());
      return node;
   }

   private static Map<String, Object> flexNode(FlexRecipeDraft draft) {
      Map<String, Object> node = new LinkedHashMap<>();
      node.put("result", draft.result().asString());
      Map<String, Object> perfect = new LinkedHashMap<>();

      for (Entry<Key, Integer> e : draft.perfect().entrySet()) {
         perfect.put(e.getKey().asString(), e.getValue());
      }

      node.put("perfect", perfect);
      if (!draft.liquids().isEmpty()) {
         List<String> liquids = new ArrayList<>(draft.liquids().size());

         for (Key k : draft.liquids()) {
            liquids.add(k.asString());
         }

         node.put("liquid", liquids);
      }

      if (draft.carrier() != null) {
         node.put("carrier", draft.carrier().asString());
      }

      return node;
   }
}
