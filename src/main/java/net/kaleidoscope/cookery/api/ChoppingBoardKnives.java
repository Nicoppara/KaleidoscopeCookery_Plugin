package net.kaleidoscope.cookery.api;

import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Key;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime knife registry for chopping boards.
 *
 * <p>Accepted ids use the same format as the chopping board config:
 * {@code minecraft:iron_sword}, {@code othermod:steel_knife}, or
 * {@code craftengine:namespace:custom_knife}.</p>
 */
@SuppressWarnings("unused")
public final class ChoppingBoardKnives {
    private static final String CRAFTENGINE_PREFIX = Key.CRAFTENGINE_NAMESPACE + ":";
    private static final ChoppingBoardKnives INSTANCE = new ChoppingBoardKnives();

    private final Set<String> registryIds = ConcurrentHashMap.newKeySet();
    private final Set<String> craftEngineIds = ConcurrentHashMap.newKeySet();

    private ChoppingBoardKnives() {
    }

    /**
     * Returns the shared knife registry.
     *
     * @return the singleton registry
     */
    public static ChoppingBoardKnives instance() {
        return INSTANCE;
    }

    /**
     * Registers a knife item id.
     *
     * <p>Use {@code namespace:item} for vanilla or mod registry ids. Use
     * {@code craftengine:namespace:item} for CraftEngine custom item ids.</p>
     *
     * @param id the item id to register
     * @return {@code true} if the id was valid and was not already registered
     */
    public boolean register(String id) {
        ParsedId parsed = parse(id);
        if (parsed == null) {
            return false;
        }
        return parsed.craftEngine()
                ? craftEngineIds.add(parsed.id())
                : registryIds.add(parsed.id());
    }

    /**
     * Registers several knife item ids.
     *
     * @param ids item ids accepted by {@link #register(String)}
     */
    public void registerAll(String... ids) {
        for (String id : ids) {
            register(id);
        }
    }

    /**
     * Removes a knife item id from this runtime registry.
     *
     * @param id the item id to remove
     * @return {@code true} if the id was previously registered
     */
    public boolean unregister(String id) {
        ParsedId parsed = parse(id);
        if (parsed == null) {
            return false;
        }
        return parsed.craftEngine()
                ? craftEngineIds.remove(parsed.id())
                : registryIds.remove(parsed.id());
    }

    /**
     * Checks whether an item id was registered through this API.
     *
     * @param id the item id to check
     * @return {@code true} if this runtime registry contains the id
     */
    public boolean isRegistered(String id) {
        ParsedId parsed = parse(id);
        if (parsed == null) {
            return false;
        }
        return parsed.craftEngine()
                ? craftEngineIds.contains(parsed.id())
                : registryIds.contains(parsed.id());
    }

    /**
     * Checks whether a CraftEngine item is registered as a chopping board knife.
     *
     * @param item the CraftEngine item to test
     * @return {@code true} if the item matches a registered runtime knife id
     */
    public boolean isKnife(Item item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        if (registryIds.contains(item.id().asString()) || registryIds.contains(item.vanillaId().asString())) {
            return true;
        }
        return item.isCustomItem() && (craftEngineIds.contains(item.id().asString())
                || item.customId().map(Key::asString).filter(craftEngineIds::contains).isPresent());
    }

    /**
     * Returns all ids registered through this API.
     *
     * @return an immutable snapshot of registered ids
     */
    public Set<String> registeredIds() {
        Set<String> ids = new HashSet<>(registryIds);
        for (String id : craftEngineIds) {
            ids.add(CRAFTENGINE_PREFIX + id);
        }
        return Set.copyOf(ids);
    }

    private ParsedId parse(String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith(CRAFTENGINE_PREFIX)) {
            String craftEngineId = trimmed.substring(CRAFTENGINE_PREFIX.length()).trim();
            return craftEngineId.isEmpty() ? null : new ParsedId(craftEngineId, true);
        }
        return new ParsedId(trimmed, false);
    }

    private record ParsedId(String id, boolean craftEngine) {
    }
}
