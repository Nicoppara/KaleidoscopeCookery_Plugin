package net.kaleidoscope.cookery.recipe;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToIntFunction;

public final class WeightedPicker {
   public static final int FULL_WEIGHT = 100;

   private WeightedPicker() {
   }

   public static boolean roll(int weight) {
      return weight <= 0 ? false : weight >= 100 || ThreadLocalRandom.current().nextInt(100) < weight;
   }

   public static <T> T pick(List<T> entries, ToIntFunction<T> weight) {
      int size = entries.size();
      if (size == 0) {
         return null;
      }

      if (size == 1) {
         return entries.getFirst();
      }

      int total = 0;

      for (T entry : entries) {
         int w = weight.applyAsInt(entry);
         if (w > 0) {
            total += w;
         }
      }

      if (total <= 0) {
         return entries.getFirst();
      }

      int roll = ThreadLocalRandom.current().nextInt(total);

      for (T entry : entries) {
         int w = weight.applyAsInt(entry);
         if (w > 0) {
            if (roll < w) {
               return entry;
            }

            roll -= w;
         }
      }

      return entries.getLast();
   }
}
