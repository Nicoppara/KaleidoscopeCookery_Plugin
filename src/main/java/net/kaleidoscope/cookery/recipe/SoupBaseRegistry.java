package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 高汤锅汤底表 唯一来源是配置 stock_food_raw.liquid
// 每条登记 桶物品 到 锅中液面展示模型 的映射 stock_flex_foods 的 liquid 条件即引用这些桶 id
/**
 * Registry of stockpot soup bases.
 *
 * <p>A soup base maps the bucket item poured into a stockpot to the display
 * model used for the liquid surface in the pot.</p>
 */
@SuppressWarnings("unused")
public final class SoupBaseRegistry {
    private static final SoupBaseRegistry INSTANCE = new SoupBaseRegistry();
    private final Map<Key, Key> bucketToShow = new ConcurrentHashMap<>();

    private SoupBaseRegistry() {
    }

    /**
     * Returns the shared registry.
     *
     * @return the singleton registry
     */
    public static SoupBaseRegistry instance() {
        return INSTANCE;
    }

    // 登记一个汤底 bucket 是倒入所需的桶物品 id showModel 是锅中液面展示的模型物品 id
    /**
     * Registers a stockpot soup base.
     *
     * @param bucket item id of the bucket or container item
     * @param showModel item id used as the in-pot liquid display model
     */
    public void register(Key bucket, Key showModel) {
        bucketToShow.put(bucket, showModel);
    }

    /**
     * Registers a stockpot soup base.
     *
     * @param bucket item id such as {@code otherplugin:tomato_soup_bucket}
     * @param showModel item id used as the in-pot liquid display model
     */
    public void register(String bucket, String showModel) {
        register(Key.of(bucket), Key.of(showModel));
    }

    /**
     * Checks whether an item is a registered soup base.
     *
     * @param bucket bucket or container item id
     * @return {@code true} if the item is registered as a soup base
     */
    public boolean isSoupBase(Key bucket) {
        return bucketToShow.containsKey(bucket);
    }

    /**
     * Checks whether an item is a registered soup base.
     *
     * @param bucket item id such as {@code otherplugin:tomato_soup_bucket}
     * @return {@code true} if the item is registered as a soup base
     */
    public boolean isSoupBase(String bucket) {
        return isSoupBase(Key.of(bucket));
    }

    // 该桶汤底对应的液面展示模型 未登记返回 null
    /**
     * Returns the display model item for a soup base.
     *
     * @param bucket bucket or container item id
     * @return the display model item id, or {@code null} if not registered
     */
    public Key showModel(Key bucket) {
        return bucketToShow.get(bucket);
    }

    /**
     * Returns the display model item for a soup base.
     *
     * @param bucket item id such as {@code otherplugin:tomato_soup_bucket}
     * @return the display model item id, or {@code null} if not registered
     */
    public Key showModel(String bucket) {
        return showModel(Key.of(bucket));
    }

    /**
     * Clears all registered soup bases.
     */
    public void clear() {
        bucketToShow.clear();
    }
}
