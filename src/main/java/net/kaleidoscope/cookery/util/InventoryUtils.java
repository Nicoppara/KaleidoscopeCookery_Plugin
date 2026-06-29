package net.kaleidoscope.cookery.util;

import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;

// 厨具通用给予 取出物品 合并堆叠 放手 掉落及空间预检查
public final class InventoryUtils {
    private InventoryUtils() {}

    // createWrappedItem 对无效 key 返回 null 这里收口成空物品 调用方一律用 isEmpty 判断
    public static Item createOrEmpty(Key key) {
        Item item = BukkitItemManager.instance().createWrappedItem(key, null);
        return item == null ? Item.empty() : item;
    }

    // 把物品交给玩家 先并入已有同类堆叠 再填空格 背包满则掉落
    public static void give(Player player, Item item) {
        give(player, item, true);
    }

    public static void give(Player player, Item item, boolean spawnPickupAnimation) {
        if (player == null || item == null || item.isEmpty()) {
            return;
        }
        player.giveItem(item, spawnPickupAnimation);
    }

    // 统计玩家背包中指定 id 物品的总数量
    public static int countItem(Player player, Key itemId) {
        return player.clearOrCountMatchingInventoryItems(itemId, 0);
    }

    // 从玩家背包中消耗指定数量的物品 数量不足则返回 false 且不消耗 创造模式视为成功且不消耗
    public static boolean consumeItem(Player player, Key itemId, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (player.canInstabuild()) {
            return true;
        }
        if (countItem(player, itemId) < amount) {
            return false;
        }
        player.clearOrCountMatchingInventoryItems(itemId, amount);
        return true;
    }

    // 扣减玩家手中物品 创造模式不消耗 统一收口创造判定
    public static void shrinkHeld(Player player, Item item, int count) {
        if (item != null && !item.isEmpty() && !player.canInstabuild()) {
            item.shrink(count);
        }
    }

    // 智能取出 固定使用主手 先与背包同类堆叠合并 剩余物品优先放空主手 否则走 give
    public static void giveOrHold(Player player, InteractionHand hand, Item item) {
        if (player == null || item == null || item.isEmpty()) {
            return;
        }

        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) player.platformPlayer();
        ItemStack bukkitStack = ItemStackUtils.getBukkitStack(item);

        int remaining = item.count();
        int maxStack = item.maxStackSize();

        ItemStack[] contents = bukkitPlayer.getInventory().getStorageContents();
        for (ItemStack slot : contents) {
            if (slot != null && slot.isSimilar(bukkitStack) && slot.getAmount() < maxStack) {
                int space = maxStack - slot.getAmount();
                int toAdd = Math.min(space, remaining);

                slot.setAmount(slot.getAmount() + toAdd);
                remaining -= toAdd;

                if (remaining <= 0) {
                    break;
                }
            }
        }

        if (remaining <= 0) {
            return;
        }

        Item leftover = item.copyWithCount(remaining);
        Item inHand = player.getItemInHand(InteractionHand.MAIN_HAND);

        if (inHand.isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, leftover);
        } else {
            give(player, leftover, true);
        }
    }

    // 预检查 玩家背包能否完整容纳该物品
    public static boolean hasSpaceFor(Player player, Item item) {
        if (item == null || item.isEmpty()) {
            return true;
        }
        org.bukkit.entity.Player bukkit = (org.bukkit.entity.Player) player.platformPlayer();
        if (bukkit.getGameMode() == GameMode.CREATIVE) {
            return true;
        }

        ItemStack stack = ItemStackUtils.getBukkitStack(item);

        int remaining = item.count();
        int max = item.maxStackSize();
        for (ItemStack slot : bukkit.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                remaining -= max;
            } else if (slot.isSimilar(stack)) {
                remaining -= Math.max(0, max - slot.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return remaining <= 0;
    }

    public static boolean isSameType(Item a, Item b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return a.id().equals(b.id());
    }
}