package net.kaleidoscope.cookery.api;

import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.SectionConfigParser;
import net.momirealms.craftengine.core.plugin.config.lifecycle.LoadingStage;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Vanilla block tag registry, backing the {@code block_tags} config section.
 * A member is either a block id ({@code minecraft:dirt}) or a vanilla block tag
 * ({@code #minecraft:dirt}) whose blocks are all pulled in.
 * Covers vanilla blocks only — CraftEngine custom blocks carry their tags in
 * {@code settings.tags}, and behaviours check both under the same tag key.
 */
@SuppressWarnings("unused")
public final class BlockTags {
    public static final LoadingStage BLOCK_TAGS = new LoadingStage("block tags");

    /** Blocks a hoe can till underwater. CE custom blocks use the same key in {@code settings.tags}. */
    public static final Key TILLABLE = Key.of("kaleidoscopecookery:tillable");

    private static final BlockTags INSTANCE = new BlockTags();

    // Members expand to a Material set at parse time so the interact path is one contains
    private final Map<Key, Set<Material>> tags = new ConcurrentHashMap<>();

    private BlockTags() {
    }

    public static BlockTags instance() {
        return INSTANCE;
    }

    /**
     * Replaces the members of a tag.
     *
     * @param tag the tag key, without the leading {@code #}
     * @param members block ids, or {@code #tag} to pull in a vanilla block tag
     */
    public void register(@NotNull Key tag, @NotNull Collection<String> members) {
        this.tags.put(tag, resolve(tag, members));
    }

    /**
     * Adds members to a tag, keeping whatever it already contains.
     *
     * @param tag the tag key, without the leading {@code #}
     * @param members block ids, or {@code #tag} to pull in a vanilla block tag
     */
    public void add(@NotNull Key tag, @NotNull Collection<String> members) {
        Set<Material> resolved = resolve(tag, members);
        this.tags.merge(tag, resolved, (first, second) -> {
            Set<Material> merged = EnumSet.noneOf(Material.class);
            merged.addAll(first);
            merged.addAll(second);
            return merged;
        });
    }

    /**
     * Removes a tag entirely.
     *
     * @param tag the tag key, without the leading {@code #}
     * @return {@code true} if the tag existed
     */
    public boolean remove(@NotNull Key tag) {
        return this.tags.remove(tag) != null;
    }

    /**
     * @param tag the tag key, without the leading {@code #}
     * @return {@code true} if the tag has been declared, even with no members
     */
    public boolean exists(@NotNull Key tag) {
        return this.tags.containsKey(tag);
    }

    /**
     * @param tag the tag key, without the leading {@code #}
     * @return an immutable snapshot of the resolved materials, empty if unknown
     */
    public @NotNull Set<Material> materials(@NotNull Key tag) {
        Set<Material> entry = this.tags.get(tag);
        return entry == null ? Set.of() : Set.copyOf(entry);
    }

    /**
     * @return an immutable snapshot of every declared tag key
     */
    public @NotNull Set<Key> keys() {
        return Set.copyOf(this.tags.keySet());
    }

    /**
     * Tests whether a vanilla block belongs to a tag.
     *
     * @param tag the tag key, without the leading {@code #}
     * @param block the block to test, may be {@code null}
     * @return {@code true} if the block's material is a member
     */
    public boolean matches(@NotNull Key tag, @Nullable Block block) {
        return block != null && matches(tag, block.getType());
    }

    /**
     * @param tag the tag key, without the leading {@code #}
     * @param material the material to test, may be {@code null}
     * @return {@code true} if the material is a member
     */
    public boolean matches(@NotNull Key tag, @Nullable Material material) {
        if (material == null) {
            return false;
        }
        Set<Material> entry = this.tags.get(tag);
        return entry != null && entry.contains(material);
    }

    public static void registerParser() {
        CraftEngine.instance().packManager().registerConfigSectionParser(new BlockTagsParser());
    }

    // Unresolvable entries are reported, not dropped silently; the rest still apply
    private static Set<Material> resolve(Key tag, Collection<String> members) {
        Set<Material> result = EnumSet.noneOf(Material.class);
        for (String member : members) {
            if (member == null) {
                continue;
            }
            String trimmed = member.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.charAt(0) == '#') {
                addVanillaTag(result, tag, trimmed.substring(1).trim());
                continue;
            }
            Material material = Material.matchMaterial(trimmed);
            if (material == null || !material.isBlock()) {
                warn(tag, "未知的方块 " + trimmed);
            } else {
                result.add(material);
            }
        }
        return result;
    }

    private static void addVanillaTag(Set<Material> result, Key tag, String id) {
        NamespacedKey key = NamespacedKey.fromString(id.toLowerCase(Locale.ROOT));
        Tag<Material> vanilla = key == null ? null
                : Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class);
        if (vanilla == null) {
            warn(tag, "未知的原版方块标签 #" + id);
            return;
        }
        result.addAll(vanilla.getValues());
    }

    private static void warn(Key tag, String message) {
        KaleidoscopeCookeryPlugin.instance().getLogger()
                .warning("[block_tags] " + tag.asString() + " " + message);
    }

    private static final class BlockTagsParser extends SectionConfigParser {
        private int count;

        @Override
        public String[] sectionId() {
            return new String[]{"block_tags", "block-tags", "block_tag", "block-tag"};
        }

        @Override
        public LoadingStage loadingStage() {
            return BLOCK_TAGS;
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
                Key tag = Key.of(tagId.trim());
                // An empty list is meaningful: matches no vanilla block, unlike omitting the tag
                List<String> members = new ArrayList<>(section.getStringList(tagId));
                INSTANCE.add(tag, members);
                this.count++;
            }
        }
    }
}
