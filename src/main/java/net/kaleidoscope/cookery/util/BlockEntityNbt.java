package net.kaleidoscope.cookery.util;

import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.ListTag;
import net.momirealms.craftengine.libraries.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

// 方块实体 NBT 的物品存读
// 空物品与序列化失败的一律不写 null 进不了 ListTag 也进不了 CompoundTag
public final class BlockEntityNbt {
    private BlockEntityNbt() {}

    private static final String K_DATA_VERSION = "data_version";
    private static final String K_SLOT = "slot";
    private static final String K_ITEM = "item";

    // 自己的子标签 数据版本一并写好
    public static CompoundTag newData() {
        CompoundTag data = new CompoundTag();
        data.putInt(K_DATA_VERSION, VersionHelper.WORLD_VERSION);
        return data;
    }

    // 缺省走 CE 的 fallback 不是 WORLD_VERSION 老档没这个字段时按老版本解析才不会解错
    public static int dataVersion(CompoundTag data) {
        return data.getInt(K_DATA_VERSION, Config.itemDataFixerUpperFallbackVersion());
    }


    public static ListTag saveItems(List<Item> items) {
        ListTag list = new ListTag();
        for (Item item : items) {
            addItem(list, item);
        }
        return list;
    }

    public static ListTag saveItems(Item[] items) {
        return saveItems(items, items.length);
    }

    public static ListTag saveItems(Item[] items, int count) {
        ListTag list = new ListTag();
        for (int slot = 0; slot < count; slot++) {
            addItem(list, items[slot]);
        }
        return list;
    }

    private static void addItem(ListTag list, Item item) {
        Tag itemTag = itemTag(item);
        if (itemTag != null) {
            list.add(itemTag);
        }
    }

    // 槽位条目还带别的字段时用它 返回 null 表示这条记录整条都别写
    @Nullable
    public static Tag itemTag(Item item) {
        return ItemUtils.isEmpty(item) ? null : ItemStackUtils.saveMinecraftItemStackAsTag(item.minecraftItem());
    }

    // 从 tag[key] 读出物品列表 写入 out
    public static void loadItems(CompoundTag tag, String key, int dataVersion, List<Item> out) {
        out.clear();
        if (!tag.containsKey(key)) {
            return;
        }
        loadItems(tag.getList(key), dataVersion, out);
    }

    public static void loadItems(ListTag list, int dataVersion, List<Item> out) {
        out.clear();
        if (list == null) {
            return;
        }
        for (Tag itemTag : list) {
            Object parsed = ItemStackUtils.parseMinecraftItem(itemTag, dataVersion);
            if (parsed != null) {
                out.add(ItemStackUtils.wrap(parsed));
            }
        }
    }

    // 填进 out 的前缀 其余置空 返回读到的件数
    public static int loadItems(ListTag list, int dataVersion, Item[] out) {
        Arrays.fill(out, Item.empty());
        if (list == null) {
            return 0;
        }
        int count = 0;
        for (Tag itemTag : list) {
            if (count >= out.length) {
                break;
            }
            Object parsed = ItemStackUtils.parseMinecraftItem(itemTag, dataVersion);
            if (parsed != null) {
                out[count++] = ItemStackUtils.wrap(parsed);
            }
        }
        return count;
    }


    public static ListTag saveSlots(Item[] items) {
        ListTag list = new ListTag();
        for (int slot = 0; slot < items.length; slot++) {
            Tag itemTag = itemTag(items[slot]);
            if (itemTag == null) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt(K_SLOT, slot);
            entry.put(K_ITEM, itemTag);
            list.add(entry);
        }
        return list;
    }

    public static void loadSlots(ListTag list, int dataVersion, Item[] out) {
        Arrays.fill(out, Item.empty());
        if (list == null) {
            return;
        }
        for (Tag element : list) {
            if (!(element instanceof CompoundTag entry)) {
                continue;
            }
            int slot = entry.getInt(K_SLOT, -1);
            if (slot < 0 || slot >= out.length) {
                continue;
            }
            Object parsed = ItemStackUtils.parseMinecraftItem(entry.getCompound(K_ITEM), dataVersion);
            if (parsed != null) {
                out[slot] = ItemStackUtils.wrap(parsed);
            }
        }
    }


    public static void putItem(CompoundTag data, String key, Item item) {
        Tag itemTag = itemTag(item);
        if (itemTag != null) {
            data.put(key, itemTag);
        }
    }

    public static Item getItem(CompoundTag data, String key, int dataVersion) {
        Tag itemTag = data.get(key);
        if (itemTag == null) {
            return Item.empty();
        }
        Object parsed = ItemStackUtils.parseMinecraftItem(itemTag, dataVersion);
        return parsed == null ? Item.empty() : ItemStackUtils.wrap(parsed);
    }
}
