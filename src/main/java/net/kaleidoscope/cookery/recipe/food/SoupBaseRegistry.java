package net.kaleidoscope.cookery.recipe.food;

import net.momirealms.craftengine.core.util.Key;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 高汤锅汤底表：唯一来源是配置 stock_food_raw.liquid
// 每条登记 桶物品 → 锅中液面展示模型 的映射；stock_flex_foods 的 liquid 条件即引用这些桶 id
public final class SoupBaseRegistry {
    private static final SoupBaseRegistry INSTANCE = new SoupBaseRegistry();
    private final Map<Key, Key> bucketToShow = new ConcurrentHashMap<>();

    private SoupBaseRegistry() {
    }

    public static SoupBaseRegistry instance() {
        return INSTANCE;
    }

    // 登记一个汤底：bucket = 倒入所需的桶物品 id，showModel = 锅中液面展示的模型物品 id
    public void register(Key bucket, Key showModel) {
        bucketToShow.put(bucket, showModel);
    }

    public boolean isSoupBase(Key bucket) {
        return bucketToShow.containsKey(bucket);
    }

    // 该桶汤底对应的液面展示模型；未登记返回 null
    public Key showModel(Key bucket) {
        return bucketToShow.get(bucket);
    }

    public void clear() {
        bucketToShow.clear();
    }
}
