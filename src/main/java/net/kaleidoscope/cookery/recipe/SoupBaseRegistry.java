package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 高汤锅汤底表 唯一来源是配置 stock_food_raw.liquid
// 每条登记 桶物品 到 锅中液面展示模型 的映射 stock_flex_foods 的 liquid 条件即引用这些桶 id
@SuppressWarnings("unused")
public final class SoupBaseRegistry {
    private static final SoupBaseRegistry INSTANCE = new SoupBaseRegistry();
    private final Map<Key, Key> bucketToShow = new ConcurrentHashMap<>();

    private SoupBaseRegistry() {
    }

    public static SoupBaseRegistry instance() {
        return INSTANCE;
    }

    // 登记一个汤底 bucket 是倒入所需的桶物品 id showModel 是锅中液面展示的模型物品 id
    public void register(Key bucket, Key showModel) {
        bucketToShow.put(bucket, showModel);
    }

    public void register(String bucket, String showModel) {
        register(Key.of(bucket), Key.of(showModel));
    }

    public boolean isSoupBase(Key bucket) {
        return bucketToShow.containsKey(bucket);
    }

    public boolean isSoupBase(String bucket) {
        return isSoupBase(Key.of(bucket));
    }

    // 没配 show 时的液面 水是最常见的汤底
    public static final Key DEFAULT_SHOW = Key.of("show:stove_water");

    // 该桶汤底对应的液面展示模型 未登记也回落到水 否则灶口会整个空掉
    public Key showModel(Key bucket) {
        if (bucket == null) {
            return null;
        }
        return bucketToShow.getOrDefault(bucket, DEFAULT_SHOW);
    }

    public Key showModel(String bucket) {
        return showModel(Key.of(bucket));
    }

    // 取消登记 UI 删除汤底用 已煮上的锅不受影响 那边存的是 soupBaseId 不查这张表
    public void remove(Key bucket) {
        if (bucket != null) {
            bucketToShow.remove(bucket);
        }
    }

    // 该桶登记的液面 未登记返回 null 与 showModel 的兜底不同 UI 要区分这两种
    public Key registeredShow(Key bucket) {
        return bucket == null ? null : bucketToShow.get(bucket);
    }

    // 已登记的桶 按 id 排序 否则每次开菜单顺序都在跳
    // 已登记的汤底桶 编辑器列预设按钮用
    public java.util.List<Key> keys() {
        java.util.List<Key> out = new java.util.ArrayList<>(bucketToShow.keySet());
        out.sort(java.util.Comparator.comparing(Key::asString));
        return java.util.List.copyOf(out);
    }

    public void clear() {
        bucketToShow.clear();
    }
}
