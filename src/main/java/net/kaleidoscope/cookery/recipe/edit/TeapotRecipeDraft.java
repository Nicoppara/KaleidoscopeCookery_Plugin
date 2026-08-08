package net.kaleidoscope.cookery.recipe.edit;

import net.kaleidoscope.cookery.recipe.TeapotRecipe;
import net.momirealms.craftengine.core.util.Key;

// 茶壶配方的可变编辑态 只在单个玩家的 UI 会话内存活 不共享不并发
// fluid 与 input 共同决定产物 同一液体下 input 不能重复 否则先注册的赢
public final class TeapotRecipeDraft {
    public static final int DEFAULT_TIME = 200;
    public static final int MAX_TIME = 72000;
    public static final int MAX_COUNT = 64;

    private final Key originalId;
    private Key id;
    private Key fluid;
    private Key input;
    private int ingredientCount = 1;
    private Key result;
    private int resultCount = 1;
    private int time = DEFAULT_TIME;

    private TeapotRecipeDraft(Key originalId, Key id) {
        this.originalId = originalId;
        this.id = id;
    }

    public static TeapotRecipeDraft creating(Key id) {
        return new TeapotRecipeDraft(null, id);
    }

    public static TeapotRecipeDraft editing(TeapotRecipe recipe) {
        TeapotRecipeDraft draft = new TeapotRecipeDraft(recipe.id(), recipe.id());
        draft.fluid = recipe.fluid();
        draft.input = recipe.input();
        draft.ingredientCount = recipe.ingredientCount();
        draft.result = recipe.result();
        draft.resultCount = recipe.resultCount();
        draft.time = recipe.time();
        return draft;
    }

    public boolean isNew() {
        return originalId == null;
    }

    public Key originalId() {
        return originalId;
    }

    public Key id() {
        return id;
    }

    public void id(Key value) {
        this.id = value;
    }

    public Key fluid() {
        return fluid;
    }

    public void fluid(Key value) {
        this.fluid = value;
    }

    public Key input() {
        return input;
    }

    public void input(Key value) {
        this.input = value;
    }

    public int ingredientCount() {
        return ingredientCount;
    }

    public void ingredientCount(int value) {
        this.ingredientCount = Math.max(1, Math.min(MAX_COUNT, value));
    }

    public Key result() {
        return result;
    }

    public void result(Key value) {
        this.result = value;
    }

    public int resultCount() {
        return resultCount;
    }

    public void resultCount(int value) {
        this.resultCount = Math.max(1, Math.min(MAX_COUNT, value));
    }

    public int time() {
        return time;
    }

    public void time(int value) {
        this.time = Math.max(1, Math.min(MAX_TIME, value));
    }

    public TeapotRecipe toRecipe() {
        return new TeapotRecipe(id, fluid, input, ingredientCount, result, resultCount, time);
    }
}
