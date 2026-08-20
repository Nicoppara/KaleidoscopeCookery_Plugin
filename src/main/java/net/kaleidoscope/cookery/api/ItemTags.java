package net.kaleidoscope.cookery.api;

import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.SectionConfigParser;
import net.momirealms.craftengine.core.plugin.config.lifecycle.LoadingStage;
import net.momirealms.craftengine.core.util.Key;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Item tag registry, backing the {@code item_tags} config section. Tags group
 * item ids under a single key so behaviors can accept {@code #namespace:tag}
 * instead of repeating item lists.
 * A member is a registry id ({@code minecraft:iron_sword}), a CraftEngine
 * custom item ({@code craftengine:namespace:id}), or {@code #other:tag} to
 * include every member of another tag.
 */
@SuppressWarnings("unused")
public final class ItemTags {
    public static final LoadingStage ITEM_TAGS = new LoadingStage("item tags");

    private static final String CRAFTENGINE_PREFIX = Key.CRAFTENGINE_NAMESPACE + ":";
    /** Guards against a tag cycle turning {@link #matches} into infinite recursion. */
    private static final int MAX_TAG_DEPTH = 8;

    private static final ItemTags INSTANCE = new ItemTags();

    private final Map<Key, Tag> tags = new ConcurrentHashMap<>();

    private ItemTags() {
    }

    public static ItemTags instance() {
        return INSTANCE;
    }

    /**
     * Replaces the members of a tag.
     *
     * @param tag the tag key, without the leading {@code #}
     * @param members member ids accepted by this registry
     */
    public void register(Key tag, Collection<String> members) {
        this.tags.put(tag, Tag.of(members));
    }

    /**
     * Adds members to a tag, keeping whatever it already contains.
     *
     * @param tag the tag key, without the leading {@code #}
     * @param members member ids accepted by this registry
     */
    public void add(Key tag, Collection<String> members) {
        this.tags.merge(tag, Tag.of(members), Tag::merge);
    }

    /**
     * Removes a tag entirely.
     *
     * @param tag the tag key, without the leading {@code #}
     * @return {@code true} if the tag existed
     */
    public boolean remove(Key tag) {
        return this.tags.remove(tag) != null;
    }

    /**
     * @param tag the tag key, without the leading {@code #}
     * @return {@code true} if the tag has been declared
     */
    public boolean exists(Key tag) {
        return this.tags.containsKey(tag);
    }

    /**
     * Returns the raw member entries of a tag, exactly as configured.
     *
     * @param tag the tag key, without the leading {@code #}
     * @return an immutable snapshot, empty if the tag is unknown
     */
    public Set<String> members(Key tag) {
        Tag entry = this.tags.get(tag);
        return entry == null ? Set.of() : entry.rawMembers();
    }

    /**
     * @return an immutable snapshot of every declared tag key
     */
    public Set<Key> keys() {
        return Set.copyOf(this.tags.keySet());
    }

    /**
     * Tests whether an item belongs to a tag.
     *
     * @param tag the tag key, without the leading {@code #}
     * @param item the item to test, may be {@code null} or empty
     * @return {@code true} if the item is a member of the tag
     */
    public boolean matches(Key tag, Item item) {
        return matches(tag, item, 0);
    }

    /**
     * Tests membership by item id alone, for callers that only carry ids.
     * Unlike {@link #matches(Key, Item)} there is no vanilla-material fallback,
     * so a tag listing {@code minecraft:bowl} does not capture a custom item
     * that merely uses a bowl as its base material.
     *
     * @param tag the tag key, without the leading {@code #}
     * @param itemId the item id, may be {@code null}
     * @return {@code true} if the id is a member of the tag
     */
    public boolean matchesId(Key tag, Key itemId) {
        return matchesId(tag, itemId, 0);
    }

    private boolean matchesId(Key tag, Key itemId, int depth) {
        if (itemId == null || depth > MAX_TAG_DEPTH) {
            return false;
        }
        Tag entry = this.tags.get(tag);
        if (entry == null) {
            return false;
        }
        if (entry.containsId(itemId.asString())) {
            return true;
        }
        for (Key nested : entry.nestedTags()) {
            if (matchesId(nested, itemId, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(Key tag, Item item, int depth) {
        if (item == null || item.isEmpty() || depth > MAX_TAG_DEPTH) {
            return false;
        }
        Tag entry = this.tags.get(tag);
        if (entry == null) {
            return false;
        }
        if (entry.matchesDirectly(item)) {
            return true;
        }
        for (Key nested : entry.nestedTags()) {
            if (matches(nested, item, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    public static void registerParser() {
        CraftEngine.instance().packManager().registerConfigSectionParser(new ItemTagsParser());
    }

    /** One declared tag, pre-split so the hot path never re-parses strings. */
    private record Tag(Set<String> rawMembers, Set<String> registryIds, Set<String> craftEngineIds, Set<Key> nestedTags) {
        static Tag of(Collection<String> members) {
            Set<String> raw = new LinkedHashSet<>();
            Set<String> registryIds = new LinkedHashSet<>();
            Set<String> craftEngineIds = new LinkedHashSet<>();
            Set<Key> nested = new LinkedHashSet<>();
            for (String member : members) {
                if (member == null) {
                    continue;
                }
                String trimmed = member.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                raw.add(trimmed);
                if (trimmed.charAt(0) == '#') {
                    String nestedId = trimmed.substring(1).trim();
                    if (!nestedId.isEmpty()) {
                        nested.add(Key.of(nestedId));
                    }
                } else if (trimmed.startsWith(CRAFTENGINE_PREFIX)) {
                    String customId = trimmed.substring(CRAFTENGINE_PREFIX.length()).trim();
                    if (!customId.isEmpty()) {
                        craftEngineIds.add(customId);
                    }
                } else {
                    registryIds.add(trimmed);
                }
            }
            return new Tag(Set.copyOf(raw), Set.copyOf(registryIds), Set.copyOf(craftEngineIds), Set.copyOf(nested));
        }

        static Tag merge(Tag first, Tag second) {
            List<String> all = new ArrayList<>(first.rawMembers());
            all.addAll(second.rawMembers());
            return of(all);
        }

        boolean containsId(String id) {
            return this.registryIds.contains(id) || this.craftEngineIds.contains(id);
        }

        boolean matchesDirectly(Item item) {
            if (!this.registryIds.isEmpty()
                    && (this.registryIds.contains(item.id().asString())
                    || this.registryIds.contains(item.vanillaId().asString()))) {
                return true;
            }
            if (this.craftEngineIds.isEmpty() || !item.isCustomItem()) {
                return false;
            }
            return this.craftEngineIds.contains(item.id().asString())
                    || item.customId().map(Key::asString).filter(this.craftEngineIds::contains).isPresent();
        }
    }

    private static final class ItemTagsParser extends SectionConfigParser {
        private int count;

        @Override
        public Key type() {
            return Key.of("kaleidoscopecookery:item_tags");
        }

        @Override
        public String[] sectionId() {
            return new String[]{"item_tags", "item-tags", "item_tag", "item-tag"};
        }

        @Override
        public LoadingStage loadingStage() {
            return ITEM_TAGS;
        }

        @Override
        public List<LoadingStage> dependencies() {
            return List.of();
        }

        @Override
        public int count() {
            return this.count;
        }

        @Override
        public void preProcess() {
            this.count = 0;
            INSTANCE.tags.clear();
        }

        @Override
        protected void parseSection(Pack pack, Path path, ConfigSection section) {
            for (String tagId : section.keySet()) {
                List<String> members = section.getStringList(tagId);
                if (members.isEmpty()) {
                    KaleidoscopeCookeryPlugin.instance().getLogger().warning(
                            "[item_tags] 标签 " + tagId + " 没有任何成员 已跳过");
                    continue;
                }
                INSTANCE.add(Key.of(tagId.trim()), members);
                this.count++;
            }
        }
    }
}
