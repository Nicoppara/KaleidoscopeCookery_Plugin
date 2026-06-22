package top.nicoppa.nicoPengRen.common.item;

import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Key;

/**
 * 物品类型判断 物品非空且 id 等于给定 key
 */
public final class ItemMatch {
    private ItemMatch() {}

    public static boolean is(Item item, Key key) {
        return item != null && !item.isEmpty() && item.id().equals(key);
    }
}
