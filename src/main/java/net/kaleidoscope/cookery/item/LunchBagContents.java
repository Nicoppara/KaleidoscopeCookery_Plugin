package net.kaleidoscope.cookery.item;

import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.entity.player.Player;
import org.bukkit.inventory.ItemStack;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.item.DataComponentTypes;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.Config;
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

    // 只搬内容物 名称 lore 附魔等不跟随 数量必须带上 否则整摞饭袋会被烧成一个
    private static Item switchForm(Item from, Key targetId) {
        Item target = InventoryUtils.createOrEmpty(targetId);
        if (ItemUtils.isEmpty(target)) {
            return Item.empty();
        }
        target.count(from.count());
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

    // 底材是原版 minecraft:bundle 原版有多条塞入路径 逐个事件去拦容易漏
    // 这里做兜底 把不该在袋里的东西挑出来还给玩家 返回被剔除的物品
    public static List<Item> stripDisallowed(Item bag) {
        List<Item> items = read(bag);
        List<Item> removed = new ArrayList<>();
        List<Item> kept = new ArrayList<>(items.size());
        for (Item item : items) {
            if (canAdd(item)) {
                kept.add(item);
            } else {
                removed.add(item);
            }
        }
        if (!removed.isEmpty()) {
            write(bag, kept);
        }
        return removed;
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
        // 传当前版本号会让 CE 的 dataVersion != currentVersion 判定恒不成立 数据修复器永远不执行
        BlockEntityNbt.loadItems(list, Config.itemDataFixerUpperFallbackVersion(), items);
        items.removeIf(ItemUtils::isEmpty);
        return items;
    }

    public static void write(Item bag, List<Item> items) {
        List<Item> kept = new ArrayList<>(items.size());
        for (Item item : items) {
            if (!ItemUtils.isEmpty(item)) {
                kept.add(item);
            }
        }
        // 清空要写空列表 不能用 removeComponent
        // removeComponent 是把组件标记为显式移除 袋子从此没有 bundle_contents 就不再是个能收纳的收纳袋了
        bag.setSparrowTagComponent(DataComponentTypes.BUNDLE_CONTENTS,
                kept.isEmpty() ? new ListTag() : BlockEntityNbt.saveItems(kept));
    }

    // 只看组件 别为了判空把整个列表解析出来
    public static boolean isEmpty(Item bag) {
        if (ItemUtils.isEmpty(bag)) {
            return true;
        }
        Tag tag = bag.getComponentAsSparrowTag(DataComponentTypes.BUNDLE_CONTENTS);
        return !(tag instanceof ListTag list) || list.isEmpty();
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

    // 从末尾扣掉指定数量 供装填失败时回滚
    public static void remove(Item bag, int amount) {
        if (amount <= 0) {
            return;
        }
        List<Item> items = read(bag);
        int remaining = amount;
        for (int i = items.size() - 1; i >= 0 && remaining > 0; i--) {
            Item stored = items.get(i);
            int toRemove = Math.min(stored.count(), remaining);
            stored.shrink(toRemove);
            remaining -= toRemove;
        }
        write(bag, items);
    }

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
            write(bag, List.of());
        }
        return items;
    }

    // 底材是原版 minecraft:bundle 右键塞入和取出是同一个操作
    // 只有"往袋里塞不该收的东西"该拦 光标为空是取出 必须放行 否则玩家取不出自己的牛排
    public static boolean rejectsInsert(ItemStack incoming) {
        if (incoming == null || incoming.getType().isAir()) {
            return false;
        }
        return !canAdd(wrap(incoming));
    }

    // 原版塞入路径不止一条 逐个事件拦容易漏 事后扫一遍把不该在袋里的挑出来还给玩家
    public static void sanitizeInventory(org.bukkit.entity.Player bukkitPlayer) {
        if (!bukkitPlayer.isOnline()) {
            return;
        }
        ItemStack[] contents = bukkitPlayer.getInventory().getContents();
        Player cePlayer = null;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            Item bag = wrap(stack);
            if (!isLunchBag(bag) && !isEatingForm(bag)) {
                continue;
            }
            List<Item> removed = stripDisallowed(bag);
            if (removed.isEmpty()) {
                continue;
            }
            bukkitPlayer.getInventory().setItem(slot, ItemStackUtils.getBukkitStack(bag));
            if (cePlayer == null) {
                cePlayer = BukkitAdaptor.adapt(bukkitPlayer);
            }
            for (Item item : removed) {
                InventoryUtils.give(cePlayer, item);
            }
        }
    }
    private static Item wrap(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return Item.empty();
        }
        Item item = BukkitItemManager.instance().wrap(stack);
        return ItemUtils.isEmpty(item) ? Item.empty() : item;
    }
}
