package net.kaleidoscope.cookery.recipe.edit;

import net.kaleidoscope.cookery.recipe.ChoppingBoardRecipe;
import net.kaleidoscope.cookery.recipe.ChoppingMode;
import net.kaleidoscope.cookery.recipe.ChoppingResult;
import net.momirealms.craftengine.core.util.Key;

import java.util.ArrayList;
import java.util.List;

// 砧板配方的可变编辑态 只在单个玩家的 UI 会话内存活 不共享不并发
// values 是分阶段展示模型的 id 前缀 留空表示不换模型 直接展示放上去的物品
public final class ChoppingRecipeDraft {
    public static final int DEFAULT_STAGE = 5;
    public static final int DEFAULT_WEIGHT = 100;
    public static final int MAX_STAGE = 16;

    private final Key originalId;
    private Key id;
    private Key input;
    private int stage = DEFAULT_STAGE;
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
        draft.input = recipe.input();
        draft.stage = recipe.stage();
        draft.mode = recipe.mode();
        draft.results.addAll(recipe.results());
        draft.extras.addAll(recipe.extras());
        // 解析期把前缀展开成了 prefix/0..n 这里反推回前缀 没模型就留空
        if (!recipe.values().isEmpty()) {
            String first = recipe.values().get(0);
            int slash = first.lastIndexOf('/');
            draft.modelPrefix = slash > 0 ? first.substring(0, slash) : first;
        }
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

    public Key input() {
        return input;
    }

    public void input(Key value) {
        this.input = value;
    }

    public int stage() {
        return stage;
    }

    public void stage(int value) {
        this.stage = Math.max(1, Math.min(MAX_STAGE, value));
    }

    public String modelPrefix() {
        return modelPrefix;
    }

    public void modelPrefix(String value) {
        this.modelPrefix = value == null || value.isBlank() ? null : value.trim();
    }

    public ChoppingMode mode() {
        return mode;
    }

    public void mode(ChoppingMode value) {
        this.mode = value;
    }

    public List<ChoppingResult> results() {
        return results;
    }

    // 附带产物只有 SINGLE_EXTRA 用得上 其它模式下存着也不会生效
    public List<ChoppingResult> extras() {
        return extras;
    }

    public ChoppingBoardRecipe toRecipe() {
        List<String> values = new ArrayList<>();
        if (modelPrefix != null) {
            for (int i = 0; i < stage; i++) {
                values.add(modelPrefix + "/" + i);
            }
        }
        return new ChoppingBoardRecipe(id, input, stage, values, mode,
                List.copyOf(results), List.copyOf(extras));
    }
}
