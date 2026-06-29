package net.kaleidoscope.cookery.item;

import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;

// 物品类型判断 物品非空且 id 等于给定 key
public final class ItemMatch {
    private ItemMatch() {}

    public static boolean is(Item item, Key key) {
        return !ItemUtils.isEmpty(item) && item.id().equals(key);
    }
}
