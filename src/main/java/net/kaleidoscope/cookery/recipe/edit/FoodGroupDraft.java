package net.kaleidoscope.cookery.recipe.edit;

import net.kaleidoscope.cookery.api.ItemTags;
import net.kaleidoscope.cookery.recipe.FoodGroups;
import net.momirealms.craftengine.core.util.Key;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// 食材分组的可变编辑态 只在单个玩家的 UI 会话内存活 不共享不并发
public final class FoodGroupDraft {
    private static final String CRAFTENGINE_PREFIX = Key.CRAFTENGINE_NAMESPACE + ":";

    private final Key originalTag;
    private Key tag;
    private FoodGroups.Kind kind;
    private final Set<Key> members = new LinkedHashSet<>();

    private FoodGroupDraft(Key originalTag, Key tag, FoodGroups.Kind kind) {
        this.originalTag = originalTag;
        this.tag = tag;
        this.kind = kind;
    }

    public static FoodGroupDraft creating(Key tag) {
        return new FoodGroupDraft(null, tag, FoodGroups.Kind.EQUIVALENT);
    }

    public static FoodGroupDraft editing(Key tag) {
        FoodGroups.Kind kind = FoodGroups.instance().kindOf(tag);
        FoodGroupDraft draft = new FoodGroupDraft(tag, tag,
                kind == null ? FoodGroups.Kind.EQUIVALENT : kind);
        // 成员存的是原始写法 craftengine: 前缀标记 CE 自定义物品 编辑时按纯 id 呈现
        // 嵌套标签这里带不出来 保存时会被整体替换掉 分组用不上嵌套
        for (String member : ItemTags.instance().members(tag)) {
            String trimmed = member.trim();
            if (trimmed.charAt(0) == '#') {
                continue;
            }
            draft.members.add(Key.of(trimmed.startsWith(CRAFTENGINE_PREFIX)
                    ? trimmed.substring(CRAFTENGINE_PREFIX.length()) : trimmed));
        }
        return draft;
    }

    public boolean isNew() {
        return this.originalTag == null;
    }

    public Key originalTag() {
        return this.originalTag;
    }

    public Key tag() {
        return this.tag;
    }

    public void tag(Key value) {
        this.tag = value;
    }

    public FoodGroups.Kind kind() {
        return this.kind;
    }

    public void kind(FoodGroups.Kind value) {
        this.kind = value;
    }

    public Set<Key> members() {
        return this.members;
    }

    public List<Key> memberList() {
        return new ArrayList<>(this.members);
    }
}
