package net.kaleidoscope.cookery.recipe;

import net.kaleidoscope.cookery.api.ItemTags;
import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.SectionConfigParser;
import net.momirealms.craftengine.core.plugin.config.lifecycle.LoadingStage;
import net.momirealms.craftengine.core.util.Key;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// 等效食物表与调味品表 两段都只列 item_tags 里的标签 成员由标签定义
// 等效 同一标签内的食材互相顶替 匹配前统一归一成标签自身
// 调味品 匹配前整个剔除 不占配比 不算杂料 只在锅里占一格
public final class FoodGroups {
    // 每个解析器必须独占一个阶段 CE 的 LoadingPyramid 按阶段 put 任务 共用会被后注册的顶掉
    public static final LoadingStage EQUIVALENT_FOODS = new LoadingStage("equivalent foods");
    public static final LoadingStage SEASONINGS = new LoadingStage("seasonings");

    private static final FoodGroups INSTANCE = new FoodGroups();

    // 声明顺序即优先级 一个食材落在多个等效标签里时取先声明的
    private volatile List<Key> equivalentTags = List.of();
    private volatile List<Key> seasoningTags = List.of();

    private FoodGroups() {
    }

    public static FoodGroups instance() {
        return INSTANCE;
    }

    // 一个标签不会同时是两种用途
    public enum Kind {
        EQUIVALENT("等效食材"),
        SEASONING("调味品");

        private final String displayName;

        Kind(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return this.displayName;
        }
    }

    void equivalentTags(List<Key> tags) {
        this.equivalentTags = tags;
    }

    void seasoningTags(List<Key> tags) {
        this.seasoningTags = tags;
    }

    public List<Key> equivalentTags() {
        return this.equivalentTags;
    }

    public List<Key> seasoningTags() {
        return this.seasoningTags;
    }

    // 等效在前 调味品在后 组内按声明顺序
    public List<Key> tags() {
        List<Key> out = new ArrayList<>(this.equivalentTags);
        out.addAll(this.seasoningTags);
        return List.copyOf(out);
    }

    public Kind kindOf(Key tag) {
        if (this.equivalentTags.contains(tag)) {
            return Kind.EQUIVALENT;
        }
        return this.seasoningTags.contains(tag) ? Kind.SEASONING : null;
    }

    // 换用途要先从另一张表摘掉 否则一个标签会同时命中两边
    public synchronized void put(Key tag, Kind kind) {
        remove(tag);
        if (kind == Kind.EQUIVALENT) {
            this.equivalentTags = append(this.equivalentTags, tag);
        } else if (kind == Kind.SEASONING) {
            this.seasoningTags = append(this.seasoningTags, tag);
        }
    }

    public synchronized void remove(Key tag) {
        this.equivalentTags = without(this.equivalentTags, tag);
        this.seasoningTags = without(this.seasoningTags, tag);
    }

    private static List<Key> append(List<Key> source, Key tag) {
        List<Key> out = new ArrayList<>(source);
        out.add(tag);
        return List.copyOf(out);
    }

    private static List<Key> without(List<Key> source, Key tag) {
        if (!source.contains(tag)) {
            return source;
        }
        List<Key> out = new ArrayList<>(source);
        out.remove(tag);
        return List.copyOf(out);
    }

    public boolean hasEquivalents() {
        return !this.equivalentTags.isEmpty();
    }

    public boolean hasSeasonings() {
        return !this.seasoningTags.isEmpty();
    }

    public int equivalentTagCount() {
        return this.equivalentTags.size();
    }

    public int seasoningTagCount() {
        return this.seasoningTags.size();
    }

    public boolean isSeasoning(Key itemId) {
        for (Key tag : this.seasoningTags) {
            if (ItemTags.instance().matchesId(tag, itemId)) {
                return true;
            }
        }
        return false;
    }

    // 落在某个等效标签里就归一成该标签自身 归一后的键只用于匹配 不写进存档
    public Key canonical(Key itemId) {
        for (Key tag : this.equivalentTags) {
            if (ItemTags.instance().matchesId(tag, itemId)) {
                return tag;
            }
        }
        return itemId;
    }

    public static void registerParser() {
        CraftEngine.instance().packManager().registerConfigSectionParser(new EquivalentFoodsParser());
        CraftEngine.instance().packManager().registerConfigSectionParser(new SeasoningsParser());
    }

    private abstract static class TagListParser extends SectionConfigParser {
        private final LoadingStage stage;
        private final String label;
        // 同一段可以散在多个文件里 收齐再一次性换表 解析并发所以加锁
        private final Set<Key> collected = new LinkedHashSet<>();

        private TagListParser(LoadingStage stage, String label) {
            this.stage = stage;
            this.label = label;
        }

        protected abstract void apply(List<Key> tags);

        @Override
        public Key type() {
            return Key.of("kaleidoscopecookery:" + this.label);
        }

        @Override
        public LoadingStage loadingStage() {
            return this.stage;
        }

        @Override
        public List<LoadingStage> dependencies() {
            return List.of(ItemTags.ITEM_TAGS);
        }

        @Override
        public synchronized int count() {
            return this.collected.size();
        }

        @Override
        public boolean silentIfNotExists() {
            return true;
        }

        @Override
        public synchronized void preProcess() {
            this.collected.clear();
            apply(List.of());
        }

        @Override
        public synchronized void postProcess() {
            apply(List.copyOf(this.collected));
        }

        @Override
        protected synchronized void parseSection(Pack pack, Path path, ConfigSection section) {
            for (String raw : entries(section)) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String trimmed = raw.trim();
                // 只收标签 单个物品 id 表达不出组
                if (trimmed.charAt(0) != '#') {
                    KaleidoscopeCookeryPlugin.instance().getLogger().warning(
                            "[" + this.label + "] " + trimmed + " 不是标签 只能写 #namespace:tag 已跳过");
                    continue;
                }
                String tag = trimmed.substring(1).trim();
                if (tag.isEmpty()) {
                    continue;
                }
                Key key = Key.of(tag);
                if (!ItemTags.instance().exists(key)) {
                    KaleidoscopeCookeryPlugin.instance().getLogger().warning(
                            "[" + this.label + "] 标签 " + key.asString() + " 没有定义 已跳过");
                    continue;
                }
                this.collected.add(key);
            }
        }

        // 段的值必须是映射 CE 的 processConfigEntry 只分发 instanceof Map 的顶层键
        // 顶层写列表会被静默跳过 所以标签套在子键下 子键名不限 这里全并起来
        private static List<String> entries(ConfigSection section) {
            List<String> out = new ArrayList<>();
            for (String key : section.keySet()) {
                List<String> list = section.getStringList(key);
                if (list.isEmpty()) {
                    out.add(key);
                } else {
                    out.addAll(list);
                }
            }
            return out;
        }
    }

    private static final class EquivalentFoodsParser extends TagListParser {
        private EquivalentFoodsParser() {
            super(EQUIVALENT_FOODS, "equivalent_foods");
        }

        @Override
        public String[] sectionId() {
            return new String[]{"equivalent_foods", "equivalent-foods", "equivalent_food", "equivalent-food"};
        }

        @Override
        protected void apply(List<Key> tags) {
            INSTANCE.equivalentTags(tags);
        }
    }

    private static final class SeasoningsParser extends TagListParser {
        private SeasoningsParser() {
            super(SEASONINGS, "seasonings");
        }

        @Override
        public String[] sectionId() {
            return new String[]{"seasonings", "seasoning"};
        }

        @Override
        protected void apply(List<Key> tags) {
            INSTANCE.seasoningTags(tags);
        }
    }
}
