package net.kaleidoscope.cookery.recipe.edit;

import java.util.ArrayList;
import java.util.List;
import net.kaleidoscope.cookery.recipe.ChoppingBoardRecipe;
import net.kaleidoscope.cookery.recipe.ChoppingMode;
import net.kaleidoscope.cookery.recipe.ChoppingResult;
import net.momirealms.craftengine.core.util.Key;

public final class ChoppingRecipeDraft {
   public static final int DEFAULT_STAGE = 5;
   public static final int DEFAULT_WEIGHT = 100;
   public static final int MAX_STAGE = 16;
   private final Key originalId;
   private ChoppingBoardRecipe originalRecipe;
   private Key id;
   private Key input;
   private int stage = 5;
   private String modelPrefix;
   private ChoppingMode mode = ChoppingMode.SINGLE;
   private final List<ChoppingResult> results = new ArrayList<>();
   private final List<ChoppingResult> extras = new ArrayList<>();

   private ChoppingRecipeDraft(Key originalId, Key id) {
      this.originalId = originalId;
      this.id = id;
   }

   public static ChoppingRecipeDraft creating(Key id) {
      return new ChoppingRecipeDraft(null, id);
   }

   public static ChoppingRecipeDraft editing(ChoppingBoardRecipe recipe) {
      ChoppingRecipeDraft draft = new ChoppingRecipeDraft(recipe.id(), recipe.id());
      draft.originalRecipe = recipe;
      draft.input = recipe.input();
      draft.stage = recipe.stage();
      draft.mode = recipe.mode();
      draft.results.addAll(recipe.results());
      draft.extras.addAll(recipe.extras());
      if (!recipe.values().isEmpty()) {
         String first = recipe.values().get(0);
         int slash = first.lastIndexOf(47);
         draft.modelPrefix = slash > 0 ? first.substring(0, slash) : first;
      }

      return draft;
   }

   public boolean isNew() {
      return this.originalId == null;
   }

   public Key originalId() {
      return this.originalId;
   }

   public ChoppingBoardRecipe originalRecipe() {
      return this.originalRecipe;
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

   public int stage() {
      return this.stage;
   }

   public void stage(int value) {
      this.stage = Math.max(1, Math.min(16, value));
   }

   public String modelPrefix() {
      return this.modelPrefix;
   }

   public void modelPrefix(String value) {
      this.modelPrefix = value != null && !value.isBlank() ? value.trim() : null;
   }

   public ChoppingMode mode() {
      return this.mode;
   }

   public void mode(ChoppingMode value) {
      this.mode = value;
   }

   public List<ChoppingResult> results() {
      return this.results;
   }

   public List<ChoppingResult> extras() {
      return this.extras;
   }

   public ChoppingBoardRecipe toRecipe() {
      List<String> values = new ArrayList<>();
      if (this.modelPrefix != null) {
         for (int i = 0; i < this.stage; i++) {
            values.add(this.modelPrefix + "/" + i);
         }
      }

      return new ChoppingBoardRecipe(this.id, this.input, this.stage, values, this.mode, List.copyOf(this.results), List.copyOf(this.extras));
   }
}
