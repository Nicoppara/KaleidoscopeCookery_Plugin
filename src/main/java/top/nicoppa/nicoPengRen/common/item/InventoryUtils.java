package top.nicoppa.nicoPengRen.common.item;

import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;

/**
 * 厨具通用给予/取出物品 合并堆叠、放手、掉落及空间预检查。
 */
public final class InventoryUtils {
    private InventoryUtils() {}

    /**
     * 把物品交给玩家 先并入背包中已有的同类堆叠，再填空格，背包满则在玩家身前掉落
     */
    public static void give(Player player, Item item) {
        give(player, item, true);
    }

    public static void give(Player player, Item item, boolean spawnPickupAnimation) {
        if (player == null || item == null || item.isEmpty()) return;
        player.giveItem(item, spawnPickupAnimation);
    }

    /**
     * 统计玩家背包中指定 id 物品的总数量（不消耗）
     */
    public static int countItem(Player player, Key itemId) {
        return player.clearOrCountMatchingInventoryItems(itemId, 0);
    }

    /**
     * 从玩家背包中消耗指定数量的指定物品
     * 如果背包中数量不足，返回 false 且不消耗任何物品
     */
    public static boolean consumeItem(Player player, Key itemId, int amount) {
        if (amount <= 0) return true;
        if (countItem(player, itemId) < amount) return false;
        player.clearOrCountMatchingInventoryItems(itemId, amount);
        return true;
    }

    /**
     * 智能取出
     * 取出的物品只会进主手或背包，不会进副手
     * 即使调用方传入的 hand 是副手，方法内部也会忽略它、固定使用主手
     *
     * 1. 优先扫描背包，尝试与已有的同类物品合并
     * 2. 如果合并后还有剩余（或者原本就没有同类物品可以合并），且主手是空的，优先放在主手上
     * 3. 如果主手不是空的，再走默认的 give 逻辑找其他空位或掉落
     */
    public static void giveOrHold(Player player, InteractionHand hand, Item item) {
        if (player == null || item == null || item.isEmpty()) return;

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

                if (remaining <= 0) break;
            }
        }

        if (remaining <= 0) return;

        Item leftover = item.copyWithCount(remaining);
        Item inHand = player.getItemInHand(InteractionHand.MAIN_HAND);

        if (inHand.isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, leftover);
        } else {
            give(player, leftover, true);
        }
    }

    /**
     * 预检查 玩家背包（含手持/快捷栏的同类堆叠）能否完整容纳该物品
     */
    public static boolean hasSpaceFor(Player player, Item item) {
        if (item == null || item.isEmpty()) return true;
        org.bukkit.entity.Player bukkit = (org.bukkit.entity.Player) player.platformPlayer();
        if (bukkit.getGameMode() == GameMode.CREATIVE) return true;

        ItemStack stack = ItemStackUtils.getBukkitStack(item);

        int remaining = item.count();
        int max = item.maxStackSize();
        for (ItemStack slot : bukkit.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                remaining -= max;
            } else if (slot.isSimilar(stack)) {
                remaining -= Math.max(0, max - slot.getAmount());
            }
            if (remaining <= 0) return true;
        }
        return remaining <= 0;
    }

    public static boolean isSameType(Item a, Item b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        return a.id().equals(b.id());
    }
}