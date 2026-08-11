package net.kaleidoscope.cookery.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.AdventureHelper;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;

public final class FoodRecipeRegistry {
   private static final double DEFAULT_MIN_FLEX_SCORE = 0.0;
   private static final FoodRecipeRegistry INSTANCE = new FoodRecipeRegistry();
   private final List<FlexFoodRecipe> flexRecipes = new CopyOnWriteArrayList<>();
   private final List<FlexFoodRecipe> menuFlexRecipes = new CopyOnWriteArrayList<>();
   private volatile double minFlexScore = 0.0;
   private final List<AccurateFoodRecipe> accurateRecipes = new CopyOnWriteArrayList<>();
   private final List<AccurateFoodRecipe> menuAccurateRecipes = new CopyOnWriteArrayList<>();
   private final Map<FoodRecipeRegistry.AccurateKey, AccurateFoodRecipe> accurateIndex = new ConcurrentHashMap<>();
   private final List<ChoppingBoardRecipe> choppingRecipes = new CopyOnWriteArrayList<>();
   private final List<ChoppingBoardRecipe> menuChoppingRecipes = new CopyOnWriteArrayList<>();
   private final List<TeapotRecipe> teapotRecipes = new CopyOnWriteArrayList<>();
   private final List<TeapotRecipe> menuTeapotRecipes = new CopyOnWriteArrayList<>();
   private final Map<Key, TeapotLiquid> teapotLiquids = new ConcurrentHashMap<>();
   private volatile TeapotLiquid defaultLiquid;
   private final Map<Key, TeaCup> teaCups = new ConcurrentHashMap<>();
   private final Map<Key, TeaCup> teaCupsByItem = new ConcurrentHashMap<>();

   private FoodRecipeRegistry() {
   }

   public static FoodRecipeRegistry instance() {
      return INSTANCE;
   }

   public int totalRecipeCount() {
      return this.flexRecipeCount() + this.accurateRecipeCount() + this.choppingRecipeCount() + this.teapotRecipeCount();
   }

   public int recipeCount(ApplianceType cook) {
      int count = this.flexRecipeCount(cook) + this.accurateRecipeCount(cook);
      if (cook == ApplianceType.CHOPPING_BOARD) {
         count += this.choppingRecipeCount();
      } else if (cook == ApplianceType.TEAPOT) {
         count += this.teapotRecipeCount();
      }

      return count;
   }

   public int flexRecipeCount() {
      return this.flexRecipes.size();
   }

   public int flexRecipeCount(ApplianceType cook) {
      int count = 0;

      for (FlexFoodRecipe recipe : this.flexRecipes) {
         if (recipe.cook() == cook) {
            count++;
         }
      }

      return count;
   }

   public int accurateRecipeCount() {
      return this.accurateRecipes.size();
   }

   public int accurateRecipeCount(ApplianceType cook) {
      int count = 0;

      for (AccurateFoodRecipe recipe : this.accurateRecipes) {
         if (recipe.cook() == cook) {
            count++;
         }
      }

      return count;
   }

   public int choppingRecipeCount() {
      return this.choppingRecipes.size();
   }

   public int teapotRecipeCount() {
      return this.teapotRecipes.size();
   }

   public int teapotLiquidCount() {
      return this.teapotLiquids.size();
   }

   public int teaCupCount() {
      return this.teaCups.size();
   }

   public void minFlexScore(double value) {
      this.minFlexScore = value;
   }

   public FlexFoodRecipe findSameDirection(FlexFoodRecipe candidate) {
      for (FlexFoodRecipe r : this.flexRecipes) {
         if (r.cook() == candidate.cook() && r.perfect().size() == candidate.perfect().size() && liquidsOverlap(r.liquids(), candidate.liquids())) {
            Double scale = null;
            boolean same = true;

            for (Entry<Key, Integer> e : candidate.perfect().entrySet()) {
               Integer other = r.perfect().get(e.getKey());
               if (other == null) {
                  same = false;
                  break;
               }

               double ratio = (double)other.intValue() / e.getValue().intValue();
               if (scale == null) {
                  scale = ratio;
               } else if (Math.abs(scale - ratio) > 1.0E-6) {
                  same = false;
                  break;
               }
            }

            if (same) {
               return r;
            }
         }
      }

      return null;
   }

   private static boolean liquidsOverlap(List<Key> a, List<Key> b) {
      if (!a.isEmpty() && !b.isEmpty()) {
         for (Key k : a) {
            if (b.contains(k)) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   public void registerFlex(FlexFoodRecipe r) {
      this.flexRecipes.add(r);
      DishCarriers.rebuild(this.flexRecipes);
   }

   public void registerMenuFlex(FlexFoodRecipe r) {
      this.menuFlexRecipes.add(r);
   }

   public void registerAccurate(AccurateFoodRecipe r) {
      this.accurateRecipes.add(r);
      this.accurateIndex.putIfAbsent(new FoodRecipeRegistry.AccurateKey(r.cook(), r.input()), r);
   }

   public void registerMenuAccurate(AccurateFoodRecipe r) {
      this.menuAccurateRecipes.add(r);
   }

   public List<AccurateFoodRecipe> menuAccurateRecipes(ApplianceType cook) {
      List<AccurateFoodRecipe> out = new ArrayList<>();

      for (AccurateFoodRecipe r : this.menuAccurateRecipes) {
         if (r.cook() == cook) {
            out.add(r);
         }
      }

      return out;
   }

   public List<FlexFoodRecipe> menuFlexRecipes(ApplianceType cook) {
      List<FlexFoodRecipe> out = new ArrayList<>();

      for (FlexFoodRecipe r : this.menuFlexRecipes) {
         if (r.cook() == cook) {
            out.add(r);
         }
      }

      return out;
   }

   public List<AccurateFoodRecipe> accurateRecipes(ApplianceType cook) {
      List<AccurateFoodRecipe> out = new ArrayList<>();

      for (AccurateFoodRecipe r : this.accurateRecipes) {
         if (r.cook() == cook) {
            out.add(r);
         }
      }

      return out;
   }

   public List<FlexFoodRecipe> flexRecipes(ApplianceType cook) {
      List<FlexFoodRecipe> out = new ArrayList<>();

      for (FlexFoodRecipe r : this.flexRecipes) {
         if (r.cook() == cook) {
            out.add(r);
         }
      }

      return out;
   }

   public boolean removeAccurate(Key id) {
      if (!this.accurateRecipes.removeIf(r -> r.id().equals(id))) {
         return false;
      }

      this.rebuildAccurateIndex();
      return true;
   }

   public boolean removeFlex(Key id) {
      boolean removed = this.flexRecipes.removeIf(r -> r.id().equals(id));
      if (removed) {
         DishCarriers.rebuild(this.flexRecipes);
      }

      return removed;
   }

   public boolean removeFlex(ApplianceType cook, Key id) {
      boolean removed = this.flexRecipes.removeIf(r -> r.cook() == cook && r.id().equals(id));
      if (removed) {
         DishCarriers.rebuild(this.flexRecipes);
      }

      return removed;
   }

   public boolean removeChopping(Key id) {
      return this.choppingRecipes.removeIf(r -> r.id().equals(id));
   }

   public boolean removeTeapot(Key id) {
      return this.teapotRecipes.removeIf(r -> r.id().equals(id));
   }

   public List<ChoppingBoardRecipe> choppingRecipes() {
      return List.copyOf(this.choppingRecipes);
   }

   public List<ChoppingBoardRecipe> menuChoppingRecipes() {
      return List.copyOf(this.menuChoppingRecipes);
   }

   public List<TeapotRecipe> teapotRecipes() {
      return List.copyOf(this.teapotRecipes);
   }

   public List<TeapotRecipe> menuTeapotRecipes() {
      return List.copyOf(this.menuTeapotRecipes);
   }

   public void removeMenuAccurate(AccurateFoodRecipe recipe) {
      this.menuAccurateRecipes.removeIf(value -> value == recipe);
   }

   public void removeMenuFlex(FlexFoodRecipe recipe) {
      this.menuFlexRecipes.removeIf(value -> value == recipe);
   }

   public void removeMenuChopping(ChoppingBoardRecipe recipe) {
      this.menuChoppingRecipes.removeIf(value -> value == recipe);
   }

   public void removeMenuTeapot(TeapotRecipe recipe) {
      this.menuTeapotRecipes.removeIf(value -> value == recipe);
   }

   public ChoppingBoardRecipe findChoppingById(Key id) {
      for (ChoppingBoardRecipe r : this.choppingRecipes) {
         if (r.id().equals(id)) {
            return r;
         }
      }

      return null;
   }

   public TeapotRecipe findTeapotById(Key id) {
      for (TeapotRecipe r : this.teapotRecipes) {
         if (r.id().equals(id)) {
            return r;
         }
      }

      return null;
   }

   private void rebuildAccurateIndex() {
      this.accurateIndex.clear();

      for (AccurateFoodRecipe r : this.accurateRecipes) {
         this.accurateIndex.putIfAbsent(new FoodRecipeRegistry.AccurateKey(r.cook(), r.input()), r);
      }
   }

   public void registerChopping(ChoppingBoardRecipe r) {
      this.choppingRecipes.add(r);
   }

   public void registerMenuChopping(ChoppingBoardRecipe r) {
      this.menuChoppingRecipes.add(r);
   }

   public void clearFlex(ApplianceType cook) {
      this.flexRecipes.removeIf(r -> r.cook() == cook);
      this.menuFlexRecipes.removeIf(r -> r.cook() == cook);
      DishCarriers.rebuild(this.flexRecipes);
   }

   public void clearAccurate() {
      this.accurateRecipes.clear();
      this.menuAccurateRecipes.clear();
      this.accurateIndex.clear();
   }

   public void clearChopping() {
      this.choppingRecipes.clear();
      this.menuChoppingRecipes.clear();
   }

   public void registerTeapot(TeapotRecipe r) {
      this.teapotRecipes.add(r);
   }

   public void registerMenuTeapot(TeapotRecipe r) {
      this.menuTeapotRecipes.add(r);
   }

   public void clearTeapot() {
      this.teapotRecipes.clear();
      this.menuTeapotRecipes.clear();
   }

   public void registerTeapotLiquid(TeapotLiquid l) {
      this.teapotLiquids.put(l.fluid(), l);
      if (this.defaultLiquid == null) {
         this.defaultLiquid = l;
      }
   }

   public void clearTeapotLiquid() {
      this.teapotLiquids.clear();
      this.defaultLiquid = null;
   }

   public TeapotLiquid getTeapotLiquid(Key fluid) {
      return this.teapotLiquids.get(fluid);
   }

   public TeapotLiquid getTeapotLiquid(String fluid) {
      return this.getTeapotLiquid(Key.of(fluid));
   }

   public List<Key> teapotLiquidKeys() {
      List<Key> out = new ArrayList<>(this.teapotLiquids.keySet());
      out.sort(Comparator.comparing(Key::asString));
      return List.copyOf(out);
   }

   public boolean hasTeapotLiquid(Key fluid) {
      return this.teapotLiquids.containsKey(fluid);
   }

   public boolean hasTeapotLiquid(String fluid) {
      return this.hasTeapotLiquid(Key.of(fluid));
   }

   public TeapotLiquid defaultTeapotLiquid() {
      return this.defaultLiquid;
   }

   public void registerTeaCup(TeaCup c) {
      this.teaCups.put(c.tea(), c);
      this.teaCupsByItem.put(c.item(), c);
   }

   public void clearTeaCup() {
      this.teaCups.clear();
      this.teaCupsByItem.clear();
   }

   public boolean hasTeaCup(Key tea) {
      return this.teaCups.containsKey(tea);
   }

   public boolean hasTeaCup(String tea) {
      return this.hasTeaCup(Key.of(tea));
   }

   public TeaCup getTeaCup(Key tea) {
      return this.teaCups.get(tea);
   }

   public TeaCup getTeaCup(String tea) {
      return this.getTeaCup(Key.of(tea));
   }

   public TeaCup getTeaCupByItem(Key itemId) {
      return this.teaCupsByItem.get(itemId);
   }

   public TeaCup getTeaCupByItem(String itemId) {
      return this.getTeaCupByItem(Key.of(itemId));
   }

   public Key pickTeaModel(Key tea) {
      TeaCup c = this.teaCups.get(tea);
      if (c != null && !c.displayModels().isEmpty()) {
         List<Key> models = c.displayModels();
         return models.get(ThreadLocalRandom.current().nextInt(models.size()));
      } else {
         return null;
      }
   }

   public Key pickTeaModel(String tea) {
      return this.pickTeaModel(Key.of(tea));
   }

   public TeapotRecipe findTeapot(Key fluid, Key input) {
      for (TeapotRecipe r : this.teapotRecipes) {
         if (r.fluid().equals(fluid) && r.input().equals(input)) {
            return r;
         }
      }

      return null;
   }

   public TeapotRecipe findTeapot(String fluid, String input) {
      return this.findTeapot(Key.of(fluid), Key.of(input));
   }

   public ChoppingBoardRecipe findChoppingByInput(Key input) {
      for (ChoppingBoardRecipe r : this.choppingRecipes) {
         if (r.input().equals(input)) {
            return r;
         }
      }

      return null;
   }

   public ChoppingBoardRecipe findChoppingByInput(String input) {
      return this.findChoppingByInput(Key.of(input));
   }

   public List<Item> rollChoppingResults(ChoppingBoardRecipe recipe) {
      List<ChoppingResult> results = recipe.results();
      if (results.isEmpty()) {
         return List.of();
      }

      List<Item> out = new ArrayList<>();
      switch (recipe.mode()) {
         case SINGLE:
            this.addChoppingItem(out, WeightedPicker.pick(results, ChoppingResult::weight));
            break;
         case SINGLE_EXTRA:
            this.addChoppingItem(out, WeightedPicker.pick(results, ChoppingResult::weight));

            for (ChoppingResult extra : recipe.extras()) {
               if (WeightedPicker.roll(extra.weight())) {
                  this.addChoppingItem(out, extra);
               }
            }
            break;
         case MULTI_RANDOM:
            for (ChoppingResult r : results) {
               if (WeightedPicker.roll(r.weight())) {
                  this.addChoppingItem(out, r);
               }
            }

            if (out.isEmpty()) {
               this.addChoppingItem(out, WeightedPicker.pick(results, ChoppingResult::weight));
            }
      }

      return out;
   }

   private void addChoppingItem(List<Item> out, ChoppingResult result) {
      Item item = InventoryUtils.createOrEmpty(result.key());
      if (!ItemUtils.isEmpty(item)) {
         out.add(item.copyWithCount(Math.max(1, result.count())));
      }
   }

   public Optional<FoodRecipeResult> findAccurate(ApplianceType type, Key inputItem) {
      AccurateFoodRecipe recipe = this.accurateIndex.get(new FoodRecipeRegistry.AccurateKey(type, inputItem));
      if (recipe == null) {
         return Optional.empty();
      }

      WeightedResult chosen = WeightedPicker.pick(recipe.results(), WeightedResult::weight);
      if (chosen == null) {
         return Optional.empty();
      }

      Item item = InventoryUtils.createOrEmpty(chosen.key());
      if (ItemUtils.isEmpty(item)) {
         return Optional.empty();
      }

      if (!recipe.lore().isEmpty()) {
         item.loreJson(recipe.lore().stream().map(l -> AdventureHelper.componentToJson(AdventureHelper.miniMessage().deserialize("<!i>" + l))).toList());
      }

      return Optional.of(new FoodRecipeResult(item, recipe.resultCount(), null));
   }

   public Optional<FoodRecipeResult> findAccurate(ApplianceType type, String inputItem) {
      return this.findAccurate(type, Key.of(inputItem));
   }

   public int findGrindRotations(Key inputItem, int defaultRotations) {
      AccurateFoodRecipe recipe = this.accurateIndex.get(new FoodRecipeRegistry.AccurateKey(ApplianceType.MILLSTONE, inputItem));
      return recipe != null && recipe.rotations() > 0 ? recipe.rotations() : defaultRotations;
   }

   public int findGrindRotations(String inputItem, int defaultRotations) {
      return this.findGrindRotations(Key.of(inputItem), defaultRotations);
   }

   public Optional<FoodRecipeResult> cookFlex(ApplianceType type, List<Key> ingredientIds) {
      return this.cookFlex(type, ingredientIds, null);
   }

   public Optional<FoodRecipeResult> cookFlex(ApplianceType type, List<Key> ingredientIds, Key liquid) {
      FlexMatcher.Match match = FlexMatcher.bestMatch(this.flexRecipes, this.minFlexScore, type, ingredientIds, liquid);
      if (match == null) {
         return Optional.empty();
      }

      Item dish = FlexMatcher.buildDish(match);
      return dish == null ? Optional.empty() : Optional.of(new FoodRecipeResult(dish, match.portions(), match.recipe().carrier()));
   }

   public Optional<FlexFoodRecipe> findBestFlexRecipe(ApplianceType type, List<Key> ingredientIds) {
      return this.findBestFlexRecipe(type, ingredientIds, null);
   }

   public Optional<FlexFoodRecipe> findBestFlexRecipe(ApplianceType type, List<Key> ingredientIds, Key liquid) {
      FlexMatcher.Match match = FlexMatcher.bestMatch(this.flexRecipes, this.minFlexScore, type, ingredientIds, liquid);
      return Optional.ofNullable(match == null ? null : match.recipe());
   }

   public AccurateFoodRecipe findAccurateById(Key id) {
      for (AccurateFoodRecipe r : this.accurateRecipes) {
         if (r.id().equals(id)) {
            return r;
         }
      }

      return null;
   }

   public AccurateFoodRecipe findAccurateById(String id) {
      return this.findAccurateById(Key.of(id));
   }

   public FlexFoodRecipe findFlexById(Key id) {
      for (FlexFoodRecipe r : this.flexRecipes) {
         if (r.id().equals(id)) {
            return r;
         }
      }

      return null;
   }

   public FlexFoodRecipe findFlexById(String id) {
      return this.findFlexById(Key.of(id));
   }

   private record AccurateKey(ApplianceType cook, Key input) {
   }
}
