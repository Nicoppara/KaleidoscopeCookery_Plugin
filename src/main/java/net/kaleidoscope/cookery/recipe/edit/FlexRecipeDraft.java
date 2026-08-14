package net.kaleidoscope.cookery.recipe.edit;

import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.FlexFoodRecipe;
import net.momirealms.craftengine.core.util.Key;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 模糊配方的可变编辑态 perfect 同时声明必需食材和理想配比
// 只在单个玩家的 UI 会话内存活 不共享不并发
public final class FlexRecipeDraft {
    private final ApplianceType cook;
    private final Key originalId;
    private FlexFoodRecipe originalRecipe;
    private Key id;
    private Key result;
    private final Map<Key, Integer> perfect = new LinkedHashMap<>();
    private final List<Key> liquids = new ArrayList<>();
    // 盛装容器 null 表示空手就能取
    private Key carrier;
    private boolean useEquivalentFoods = true;
    private boolean useSeasonings = true;

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
        draft.useEquivalentFoods = recipe.useEquivalentFoods();
        draft.useSeasonings = recipe.useSeasonings();
        return draft;
    }

    public boolean isNew() {
        return originalId == null;
    }

    public Key originalId() {
        return originalId;
    }

    public FlexFoodRecipe originalRecipe() {
        return originalRecipe;
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

    public Key result() {
        return result;
    }

    public void result(Key value) {
        this.result = value;
    }

    public Map<Key, Integer> perfect() {
        return perfect;
    }

    public List<Key> liquids() {
        return liquids;
    }

    public Key carrier() {
        return carrier;
    }

    public void carrier(Key value) {
        this.carrier = value;
    }

    public boolean useEquivalentFoods() {
        return useEquivalentFoods;
    }

    public void useEquivalentFoods(boolean value) {
        this.useEquivalentFoods = value;
    }

    public boolean useSeasonings() {
        return useSeasonings;
    }

    public void useSeasonings(boolean value) {
        this.useSeasonings = value;
    }

    public FlexFoodRecipe toRecipe() {
        return FlexFoodRecipe.of(id, result, cook, perfect, liquids, carrier,
                useEquivalentFoods, useSeasonings);
    }
}
