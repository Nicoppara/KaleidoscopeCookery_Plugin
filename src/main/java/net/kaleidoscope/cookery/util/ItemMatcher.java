package net.kaleidoscope.cookery.util;

import net.kaleidoscope.cookery.api.ItemTags;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Item allow-list built from a config string list. Every entry is either
 * {@code #namespace:tag} for every member of an {@link ItemTags} tag,
 * {@code craftengine:namespace:id} for a CraftEngine custom item, or
 * {@code namespace:id} for a vanilla or other-plugin registry item.
 * Tags are resolved on every test rather than at parse time, so tag files can
 * be reloaded without reloading the behaviors that reference them.
 */
public final class ItemMatcher {
    private static final String CRAFTENGINE_PREFIX = Key.CRAFTENGINE_NAMESPACE + ":";
    private static final ItemMatcher EMPTY = new ItemMatcher(Set.of(), Set.of(), Set.of());

    private final Set<String> registryIds;
    private final Set<String> craftEngineIds;
    private final Set<Key> tags;

    private ItemMatcher(Set<String> registryIds, Set<String> craftEngineIds, Set<Key> tags) {
        this.registryIds = registryIds;
        this.craftEngineIds = craftEngineIds;
        this.tags = tags;
    }

    public static ItemMatcher empty() {
        return EMPTY;
    }

    public static ItemMatcher of(Collection<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return EMPTY;
        }
        Set<String> registryIds = new LinkedHashSet<>();
        Set<String> craftEngineIds = new LinkedHashSet<>();
        Set<Key> tags = new LinkedHashSet<>();
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.charAt(0) == '#') {
                String tag = trimmed.substring(1).trim();
                if (!tag.isEmpty()) {
                    tags.add(Key.of(tag));
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
        if (registryIds.isEmpty() && craftEngineIds.isEmpty() && tags.isEmpty()) {
            return EMPTY;
        }
        return new ItemMatcher(Set.copyOf(registryIds), Set.copyOf(craftEngineIds), Set.copyOf(tags));
    }

    /**
     * Reads an item list from a behavior config section.
     *
     * @param section the behavior section
     * @param def entries used when none of the keys are present
     * @param keys accepted config keys, first match wins
     * @return a matcher over the configured entries
     */
    public static ItemMatcher fromConfig(ConfigSection section, List<String> def, String... keys) {
        return of(section.getStringList(keys, def));
    }

    public boolean isEmpty() {
        return this.registryIds.isEmpty() && this.craftEngineIds.isEmpty() && this.tags.isEmpty();
    }

    public boolean matches(Item item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        if (!this.registryIds.isEmpty()
                && (this.registryIds.contains(item.id().asString())
                || this.registryIds.contains(item.vanillaId().asString()))) {
            return true;
        }
        if (!this.craftEngineIds.isEmpty() && item.isCustomItem()
                && (this.craftEngineIds.contains(item.id().asString())
                || item.customId().map(Key::asString).filter(this.craftEngineIds::contains).isPresent())) {
            return true;
        }
        if (this.tags.isEmpty()) {
            return false;
        }
        ItemTags itemTags = ItemTags.instance();
        for (Key tag : this.tags) {
            if (itemTags.matches(tag, item)) {
                return true;
            }
        }
        return false;
    }
}
