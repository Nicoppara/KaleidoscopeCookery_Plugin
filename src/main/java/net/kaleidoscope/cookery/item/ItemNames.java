package net.kaleidoscope.cookery.item;

import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.AdventureHelper;
import net.momirealms.craftengine.core.util.Key;

// 物品显示名工具：取某物品 id 的悬浮名，取不到则回退为 key 的路径值
public final class ItemNames {
    private ItemNames() {}

    public static String displayName(Key key) {
        Item item = BukkitItemManager.instance().createWrappedItem(key, null);
        if (item == null) {
            return key.value();
        }
        return item.hoverNameComponent().map(AdventureHelper::componentToMiniMessage).orElse(key.value());
    }
}
