package net.kaleidoscope.cookery.recipe.edit;

import net.kaleidoscope.cookery.recipe.TeapotRecipe;
import net.momirealms.craftengine.core.util.Key;

public final class TeapotRecipeDraft {
   public static final int DEFAULT_TIME = 200;
   public static final int MAX_TIME = 72000;
   public static final int MAX_COUNT = 64;
   private final Key originalId;
   private TeapotRecipe originalRecipe;
   private Key id;
   private Key fluid;
   private Key input;
   private int ingredientCount = 1;
   private Key result;
   private int resultCount = 1;
   private int time = 200;

   private TeapotRecipeDraft(Key originalId, Key id) {
      this.originalId = originalId;
      this.id = id;
   }

   public static TeapotRecipeDraft creating(Key id) {
      return new TeapotRecipeDraft(null, id);
   }

   public static TeapotRecipeDraft editing(TeapotRecipe recipe) {
      TeapotRecipeDraft draft = new TeapotRecipeDraft(recipe.id(), recipe.id());
      draft.originalRecipe = recipe;
      draft.fluid = recipe.fluid();
      draft.input = recipe.input();
      draft.ingredientCount = recipe.ingredientCount();
      draft.result = recipe.result();
      draft.resultCount = recipe.resultCount();
      draft.time = recipe.time();
      return draft;
   }

   public boolean isNew() {
      return this.originalId == null;
   }

   public Key originalId() {
      return this.originalId;
   }

   public TeapotRecipe originalRecipe() {
      return this.originalRecipe;
   }

   public Key id() {
      return this.id;
   }

   public void id(Key value) {
      this.id = value;
   }

   public Key fluid() {
      return this.fluid;
   }

   public void fluid(Key value) {
      this.fluid = value;
   }

   public Key input() {
      return this.input;
   }

   public void input(Key value) {
      this.input = value;
   }

   public int ingredientCount() {
      return this.ingredientCount;
   }

   public void ingredientCount(int value) {
      this.ingredientCount = Math.max(1, Math.min(64, value));
   }

   public Key result() {
      return this.result;
   }

   public void result(Key value) {
      this.result = value;
   }

   public int resultCount() {
      return this.resultCount;
   }

   public void resultCount(int value) {
      this.resultCount = Math.max(1, Math.min(64, value));
   }

   public int time() {
      return this.time;
   }

   public void time(int value) {
      this.time = Math.max(1, Math.min(72000, value));
   }

   public TeapotRecipe toRecipe() {
      return new TeapotRecipe(this.id, this.fluid, this.input, this.ingredientCount, this.result, this.resultCount, this.time);
   }
}
