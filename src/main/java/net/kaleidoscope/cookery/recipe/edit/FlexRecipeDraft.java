package net.kaleidoscope.cookery.recipe.edit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.FlexFoodRecipe;
import net.momirealms.craftengine.core.util.Key;

public final class FlexRecipeDraft {
   private final ApplianceType cook;
   private final Key originalId;
   private FlexFoodRecipe originalRecipe;
   private Key id;
   private Key result;
   private final Map<Key, Integer> perfect = new LinkedHashMap<>();
   private final List<Key> liquids = new ArrayList<>();
   private Key carrier;

   private FlexRecipeDraft(ApplianceType cook, Key originalId, Key id) {
      this.cook = cook;
      this.originalId = originalId;
      this.id = id;
   }

   public static FlexRecipeDraft creating(ApplianceType cook, Key id) {
      return new FlexRecipeDraft(cook, null, id);
   }

   public static FlexRecipeDraft editing(FlexFoodRecipe recipe) {
      FlexRecipeDraft draft = new FlexRecipeDraft(recipe.cook(), recipe.id(), recipe.id());
      draft.originalRecipe = recipe;
      draft.result = recipe.result();
      draft.perfect.putAll(recipe.perfect());
      draft.liquids.addAll(recipe.liquids());
      draft.carrier = recipe.carrier();
      return draft;
   }

   public boolean isNew() {
      return this.originalId == null;
   }

   public Key originalId() {
      return this.originalId;
   }

   public FlexFoodRecipe originalRecipe() {
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

   public Key result() {
      return this.result;
   }

   public void result(Key value) {
      this.result = value;
   }

   public Map<Key, Integer> perfect() {
      return this.perfect;
   }

   public List<Key> liquids() {
      return this.liquids;
   }

   public Key carrier() {
      return this.carrier;
   }

   public void carrier(Key value) {
      this.carrier = value;
   }

   public FlexFoodRecipe toRecipe() {
      return FlexFoodRecipe.of(this.id, this.result, this.cook, this.perfect, this.liquids, this.carrier);
   }
}
