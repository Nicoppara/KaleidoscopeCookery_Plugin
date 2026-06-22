package top.nicoppa.nicoPengRen.common.item;

import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.ListTag;
import net.momirealms.craftengine.libraries.nbt.Tag;

import java.util.List;

/**
 * 方块实体 NBT 统一 Item 列表的存读
 */
public final class BlockEntityNbt {
    private BlockEntityNbt() {}

    public static ListTag saveItems(List<Item> items) {
        ListTag list = new ListTag();
        for (Item item : items) {
            list.add(ItemStackUtils.saveMinecraftItemStackAsTag(item.minecraftItem()));
        }
        return list;
    }

    //从 tag[key] 读出物品列表，写入 out（先清空 out）
    public static void loadItems(CompoundTag tag, String key, int dataVersion, List<Item> out) {
        out.clear();
        if (!tag.containsKey(key)) return;
        for (Tag itemTag : tag.getList(key)) {
            out.add(ItemStackUtils.wrap(ItemStackUtils.parseMinecraftItem(itemTag, dataVersion)));
        }
    }
}
