package net.kaleidoscope.cookery.recipe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.AdventureHelper;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.libraries.adventure.text.TranslatableComponent;
import net.momirealms.craftengine.libraries.adventure.text.format.NamedTextColor;
import net.momirealms.craftengine.libraries.adventure.text.format.TextColor;
import net.momirealms.craftengine.libraries.adventure.text.format.TextDecoration;

public final class FlexMatcher {
   private FlexMatcher() {
   }

   public static FlexMatcher.Match bestMatch(List<FlexFoodRecipe> recipes, double minScore, ApplianceType type, List<Key> ingredientIds, Key liquid) {
      if (ingredientIds != null && !ingredientIds.isEmpty()) {
         Map<Key, Integer> counts = new HashMap<>();

         for (Key ingredient : ingredientIds) {
            counts.merge(ingredient, 1, Integer::sum);
         }

         double actualNorm = actualNorm(counts);
         if (actualNorm <= 0.0) {
            return null;
         }

         FlexFoodRecipe best = null;
         double bestCos = -1.0;
         int bestSpecificity = -1;

         for (FlexFoodRecipe recipe : recipes) {
            if (eligible(recipe, type, liquid, counts)) {
               double cos = cosine(recipe, counts, actualNorm);
               int specificity = recipe.perfect().size();
               if (specificity > bestSpecificity || specificity == bestSpecificity && cos > bestCos) {
                  bestSpecificity = specificity;
                  bestCos = cos;
                  best = recipe;
               }
            }
         }

         if (best != null && !(bestCos < minScore)) {
            int portions = portions(best, counts);
            int deviation = requiredDeviation(best, counts, portions) + extraIngredientCount(best, counts);
            return new FlexMatcher.Match(best, DishQuality.fromDeviation(deviation), portions);
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static double actualNorm(Map<Key, Integer> counts) {
      double square = 0.0;

      for (int count : counts.values()) {
         square += (double)count * count;
      }

      return Math.sqrt(square);
   }

   private static boolean eligible(FlexFoodRecipe recipe, ApplianceType type, Key liquid, Map<Key, Integer> counts) {
      if (recipe.cook() == type && !(recipe.norm() <= 0.0)) {
         if (recipe.liquids().isEmpty() || liquid != null && recipe.liquids().contains(liquid)) {
            for (Key ingredient : recipe.perfect().keySet()) {
               if (!counts.containsKey(ingredient)) {
                  return false;
               }
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static double cosine(FlexFoodRecipe recipe, Map<Key, Integer> counts, double actualNorm) {
      double dot = 0.0;

      for (Entry<Key, Integer> e : recipe.perfect().entrySet()) {
         dot += (double)e.getValue().intValue() * counts.get(e.getKey()).intValue();
      }

      return dot / (actualNorm * recipe.norm());
   }

   private static int portions(FlexFoodRecipe recipe, Map<Key, Integer> counts) {
      int portions = Integer.MAX_VALUE;

      for (Entry<Key, Integer> e : recipe.perfect().entrySet()) {
         portions = Math.min(portions, counts.get(e.getKey()) / e.getValue());
      }

      return Math.max(1, portions);
   }

   private static int requiredDeviation(FlexFoodRecipe recipe, Map<Key, Integer> counts, int portions) {
      int deviation = 0;

      for (Entry<Key, Integer> e : recipe.perfect().entrySet()) {
         deviation += Math.abs(counts.get(e.getKey()) - e.getValue() * portions);
      }

      return deviation;
   }

   private static int extraIngredientCount(FlexFoodRecipe recipe, Map<Key, Integer> counts) {
      int extras = 0;

      for (Entry<Key, Integer> e : counts.entrySet()) {
         if (!recipe.perfect().containsKey(e.getKey())) {
            extras += e.getValue();
         }
      }

      return extras;
   }

   public static Item buildDish(FlexMatcher.Match match) {
      Item item = InventoryUtils.createOrEmpty(match.recipe().result());
      if (ItemUtils.isEmpty(item)) {
         return null;
      }

      DishQuality quality = match.quality();
      Component base = item.hoverNameComponent().orElseGet(() -> Component.translatable(itemTranslationKey(match.recipe().result())));
      Component name = base.colorIfAbsent((TextColor)NamedTextColor.NAMES.value(quality.color())).decoration(TextDecoration.ITALIC, false);
      item.customNameJson(AdventureHelper.componentToJson(name));
      Component lore = ((TranslatableComponent)Component.translatable(quality.translationKey()).color((TextColor)NamedTextColor.NAMES.value(quality.color())))
         .decoration(TextDecoration.ITALIC, false);
      item.loreJson(List.of(AdventureHelper.componentToJson(lore)));
      return DishFoodScaler.scale(item, quality.ratio());
   }

   private static String itemTranslationKey(Key key) {
      return "item." + key.namespace() + "." + key.value();
   }

   public record Match(FlexFoodRecipe recipe, DishQuality quality, int portions) {
   }
}
