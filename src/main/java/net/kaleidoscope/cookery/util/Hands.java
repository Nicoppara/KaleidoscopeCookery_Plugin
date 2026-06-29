package net.kaleidoscope.cookery.util;

import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.function.Predicate;

// 主副手处理
// 工具操作副手优先 取放食材只认主手
public final class Hands {
    private Hands() {}

    // 工具操作的手 副手拿着满足 isTool 的物品就用副手 否则主手
    public static InteractionHand toolHand(Player player, Predicate<Item> isTool) {
        Item off = player.getItemInHand(InteractionHand.OFF_HAND);
        if (!off.isEmpty() && isTool.test(off)) {
            return InteractionHand.OFF_HAND;
        }
        return InteractionHand.MAIN_HAND;
    }

    // Bukkit 侧的工具手 副手拿着满足 isTool 的物品就用副手 否则主手
    public static EquipmentSlot toolHandBukkit(org.bukkit.entity.Player player, Predicate<ItemStack> isTool) {
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && !off.getType().isAir() && isTool.test(off)) {
            return EquipmentSlot.OFF_HAND;
        }
        return EquipmentSlot.HAND;
    }

    // 挥动指定 Bukkit 手
    public static void swing(org.bukkit.entity.Player player, EquipmentSlot slot) {
        if (slot == EquipmentSlot.OFF_HAND) {
            player.swingOffHand();
        } else {
            player.swingMainHand();
        }
    }
}
