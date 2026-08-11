package net.kaleidoscope.cookery.recipe.edit;

import java.util.ArrayList;
import java.util.List;
import net.kaleidoscope.cookery.recipe.AccurateFoodRecipe;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.WeightedResult;
import net.momirealms.craftengine.core.util.Key;

public final class AccurateRecipeDraft {
   public static final int DEFAULT_WEIGHT = 100;
   private final ApplianceType cook;
   private final Key originalId;
   private AccurateFoodRecipe originalRecipe;
   private Key id;
   private Key input;
   private final List<WeightedResult> results = new ArrayList<>();
   private int rotations;
   private int resultCount = 1;
   private final List<String> lore = new ArrayList<>();

   private AccurateRecipeDraft(ApplianceType cook, Key originalId, Key id) {
      this.cook = cook;
      this.originalId = originalId;
      this.id = id;
   }

   public static AccurateRecipeDraft creating(ApplianceType cook, Key id) {
      return new AccurateRecipeDraft(cook, null, id);
   }

   public static AccurateRecipeDraft editing(AccurateFoodRecipe recipe) {
      AccurateRecipeDraft draft = new AccurateRecipeDraft(recipe.cook(), recipe.id(), recipe.id());
      draft.originalRecipe = recipe;
      draft.input = recipe.input();
      draft.results.addAll(recipe.results());
      draft.rotations = recipe.rotations();
      draft.resultCount = recipe.resultCount();
      draft.lore.addAll(recipe.lore());
      return draft;
   }

   public boolean isNew() {
      return this.originalId == null;
   }

   public Key originalId() {
      return this.originalId;
   }

   public AccurateFoodRecipe originalRecipe() {
      return this.originalRecipe;
   }

   public ApplianceType cook() {
      return this.cook;
   }

   public Key id() {
      return this.id;
   }

   public void id(Key value) {
      this.id = value;
   }

   public Key input() {
      return this.input;
   }

   public void input(Key value) {
      this.input = value;
   }

   public List<WeightedResult> results() {
      return this.results;
   }

   public int rotations() {
      return this.rotations;
   }

   public void rotations(int value) {
      this.rotations = Math.max(0, value);
   }

   public int resultCount() {
      return this.resultCount;
   }

   public void resultCount(int value) {
      this.resultCount = Math.max(1, value);
   }

   public List<String> lore() {
      return this.lore;
   }

   public boolean isCertain() {
      return this.results.size() == 1;
   }

   public AccurateFoodRecipe toRecipe() {
      return new AccurateFoodRecipe(this.id, this.input, List.copyOf(this.results), this.cook, this.rotations, this.resultCount, List.copyOf(this.lore));
   }
}
