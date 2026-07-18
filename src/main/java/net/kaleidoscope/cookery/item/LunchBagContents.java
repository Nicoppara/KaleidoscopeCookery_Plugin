package net.kaleidoscope.cookery.item;

import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.item.DataComponentTypes;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.libraries.nbt.ListTag;
import net.momirealms.craftengine.libraries.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

// 嬗变饭袋内容物 直接存在 bundle_contents 组件里 客户端的格子预览就是渲染它 不另存一份避免不同步
public final class LunchBagContents {
    private LunchBagContents() {}

    public static final int MAX_SLOTS = 16;

    public static boolean isLunchBag(Item item) {
        return ItemMatch.is(item, ItemKeys.TRANSMUTATION_LUNCH_BAG);
    }

    public static boolean isEatingForm(Item item) {
        return ItemMatch.is(item, ItemKeys.TRANSMUTATION_LUNCH_BAG_EATING);
    }

    // 收纳态与进食态互换 内容物随组件一起搬走 进食态本身就是完整状态 崩溃时跟背包一起落盘
    public static Item toEatingForm(Item bag) {
        return switchForm(bag, ItemKeys.TRANSMUTATION_LUNCH_BAG_EATING);
    }

    public static Item toBagForm(Item eating) {
        return switchForm(eating, ItemKeys.TRANSMUTATION_LUNCH_BAG);
    }

    private static Item switchForm(Item from, Key targetId) {
        Item target = InventoryUtils.createOrEmpty(targetId);
        if (ItemUtils.isEmpty(target)) {
            return Item.empty();
        }
        Tag tag = from.getComponentAsSparrowTag(DataComponentTypes.BUNDLE_CONTENTS);
        if (tag instanceof ListTag list) {
            target.setSparrowTagComponent(DataComponentTypes.BUNDLE_CONTENTS, list);
        }
        return target;
    }

    // 目前只收熟牛肉 后续放开走配置
    public static boolean canAdd(Item food) {
        return ItemMatch.is(food, ItemKeys.COOKED_BEEF);
    }

    public static List<Item> read(Item bag) {
        List<Item> items = new ArrayList<>(MAX_SLOTS);
        if (ItemUtils.isEmpty(bag)) {
            return items;
        }
        Tag tag = bag.getComponentAsSparrowTag(DataComponentTypes.BUNDLE_CONTENTS);
        if (!(tag instanceof ListTag list)) {
            return items;
        }
        BlockEntityNbt.loadItems(list, VersionHelper.WORLD_VERSION, items);
        items.removeIf(ItemUtils::isEmpty);
        return items;
    }

    public static void write(Item bag, List<Item> items) {
        items.removeIf(ItemUtils::isEmpty);
        if (items.isEmpty()) {
            bag.removeComponent(DataComponentTypes.BUNDLE_CONTENTS);
            return;
        }
        bag.setSparrowTagComponent(DataComponentTypes.BUNDLE_CONTENTS, BlockEntityNbt.saveItems(items));
    }

    public static boolean isEmpty(Item bag) {
        return read(bag).isEmpty();
    }

    // 返回实际放入的数量 先并已有堆叠再开新槽
    public static int add(Item bag, Item food) {
        if (!canAdd(food)) {
            return 0;
        }
        List<Item> items = read(bag);
        int remaining = food.count();
        int max = food.maxStackSize();

        for (Item stored : items) {
            if (remaining <= 0) {
                break;
            }
            if (!stored.id().equals(food.id()) || stored.count() >= max) {
                continue;
            }
            int toAdd = Math.min(max - stored.count(), remaining);
            stored.grow(toAdd);
            remaining -= toAdd;
        }

        while (remaining > 0 && items.size() < MAX_SLOTS) {
            int toAdd = Math.min(max, remaining);
            items.add(food.copyWithCount(toAdd));
            remaining -= toAdd;
        }

        int added = food.count() - remaining;
        if (added > 0) {
            write(bag, items);
        }
        return added;
    }

    // 取出第一格的一个 袋空返回 Item.empty
    public static Item removeOne(Item bag) {
        List<Item> items = read(bag);
        if (items.isEmpty()) {
            return Item.empty();
        }
        Item first = items.get(0);
        Item taken = first.copyWithCount(1);
        first.shrink(1);
        write(bag, items);
        return taken;
    }

    // 倒出全部 返回被倒出的物品 袋空返回空列表
    public static List<Item> removeAll(Item bag) {
        List<Item> items = read(bag);
        if (!items.isEmpty()) {
            bag.removeComponent(DataComponentTypes.BUNDLE_CONTENTS);
        }
        return items;
    }
}
