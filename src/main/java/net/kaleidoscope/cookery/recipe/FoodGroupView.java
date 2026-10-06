package net.kaleidoscope.cookery.recipe;

import net.kaleidoscope.cookery.api.ItemTags;
import net.momirealms.craftengine.core.util.Key;
import java.util.*;

/** Compiled id-only tag view. A match never consults a changing global tag registry. */
public record FoodGroupView(Map<Key, Key> equivalents, Set<Key> seasonings) {
    public FoodGroupView {
        equivalents = Map.copyOf(equivalents);
        seasonings = Set.copyOf(seasonings);
    }

    public static FoodGroupView empty() { return new FoodGroupView(Map.of(), Set.of()); }
    public Key canonical(Key key) { return equivalents.getOrDefault(key, key); }
    public boolean isSeasoning(Key key) { return seasonings.contains(key); }
    public boolean hasEquivalents() { return !equivalents.isEmpty(); }
    public boolean hasSeasonings() { return !seasonings.isEmpty(); }

    public static FoodGroupView capture() {
        Map<Key, Set<String>> tags = new LinkedHashMap<>();
        ItemTags registry = ItemTags.instance();
        for (Key key : registry.keys()) tags.put(key, registry.members(key));
        Map<Key, Key> equivalents = new LinkedHashMap<>();
        for (Key tag : FoodGroups.instance().equivalentTags()) {
            for (Key item : members(tags, tag, new HashSet<>(), 0)) equivalents.putIfAbsent(item, tag);
        }
        Set<Key> seasonings = new HashSet<>();
        for (Key tag : FoodGroups.instance().seasoningTags()) seasonings.addAll(members(tags, tag, new HashSet<>(), 0));
        return new FoodGroupView(equivalents, seasonings);
    }

    private static Set<Key> members(Map<Key, Set<String>> tags, Key tag, Set<Key> path, int depth) {
        if (depth > 8 || !path.add(tag)) return Set.of();
        Set<Key> items = new LinkedHashSet<>();
        for (String raw : tags.getOrDefault(tag, Set.of())) {
            String member = raw.trim();
            if (member.startsWith("#")) items.addAll(members(tags, Key.of(member.substring(1)), path, depth + 1));
            else {
                if (member.startsWith("craftengine:")) member = member.substring("craftengine:".length());
                items.add(Key.of(member));
            }
        }
        path.remove(tag);
        return items;
    }
}
