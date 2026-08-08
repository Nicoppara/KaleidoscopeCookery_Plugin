package net.kaleidoscope.cookery.recipe.edit;

import net.kaleidoscope.cookery.recipe.AccurateFoodRecipe;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.WeightedResult;
import net.momirealms.craftengine.core.util.Key;

import java.util.ArrayList;
import java.util.List;

// 精准配方的可变编辑态 只在单个玩家的 UI 会话内存活 不共享不并发
public final class AccurateRecipeDraft {
    // 新加成品的默认权重 精准配方的权重是相对值 findAccurate 按总和归一化后必出其一
    public static final int DEFAULT_WEIGHT = 100;

    private final ApplianceType cook;
    private final Key originalId;
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
        draft.input = recipe.input();
        draft.results.addAll(recipe.results());
        draft.rotations = recipe.rotations();
        draft.resultCount = recipe.resultCount();
        draft.lore.addAll(recipe.lore());
        return draft;
    }

    public boolean isNew() {
        return originalId == null;
    }

    public Key originalId() {
        return originalId;
    }

    public ApplianceType cook() {
        return cook;
    }

    public Key id() {
        return id;
    }

    public void id(Key value) {
        this.id = value;
    }

    public Key input() {
        return input;
    }

    public void input(Key value) {
        this.input = value;
    }

    public List<WeightedResult> results() {
        return results;
    }

    public int rotations() {
        return rotations;
    }

    public void rotations(int value) {
        this.rotations = Math.max(0, value);
    }

    public int resultCount() {
        return resultCount;
    }

    public void resultCount(int value) {
        this.resultCount = Math.max(1, value);
    }

    public List<String> lore() {
        return lore;
    }

    // 精准配方一定会出东西 只有成品多于一个时权重才起作用 所以单成品即百分百
    public boolean isCertain() {
        return results.size() == 1;
    }

    public AccurateFoodRecipe toRecipe() {
        return new AccurateFoodRecipe(id, input, List.copyOf(results), cook,
                rotations, resultCount, List.copyOf(lore));
    }
}
