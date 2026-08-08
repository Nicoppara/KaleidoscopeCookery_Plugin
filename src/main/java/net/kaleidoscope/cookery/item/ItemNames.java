package net.kaleidoscope.cookery.item;

import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.AdventureHelper;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 物品显示名工具 取某物品 id 的悬浮名 取不到则回退为 key 的路径值
public final class ItemNames {
    private ItemNames() {}

    // 为了读一个名字要让 CE 完整构建一个物品 出一次菜每个风味 key 各来一遍
    // 名字在两次 reload 之间不会变 缓存住 重载时清空
    private static final Map<Key, String> CACHE = new ConcurrentHashMap<>();

    public static String displayName(Key key) {
        return CACHE.computeIfAbsent(key, ItemNames::resolve);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static String resolve(Key key) {
        Item item = InventoryUtils.createOrEmpty(key);
        if (ItemUtils.isEmpty(item)) {
            return key.value();
        }
        return item.hoverNameComponent().map(AdventureHelper::componentToMiniMessage).orElse(key.value());
    }
}
