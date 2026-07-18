package net.kaleidoscope.cookery.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.item.DataComponentTypes;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.component.DataComponentKeys;
import net.momirealms.craftengine.core.util.ItemUtils;
import org.bukkit.Sound;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;

// 饭袋进食态的生命周期
// 原版收纳袋没有进食动画 所以右键时把手里的物品临时换成可食用的进食态 由原版驱动动画与中断 吃完再换回收纳态
public final class LunchBagEating {
    private LunchBagEating() {}

    private static final int MAX_FOOD_LEVEL = 20;
    // 换成进食态后客户端要隔一两 tick 才会重新发起使用 超过这个宽限还没抬手就说明只是点了一下 换回收纳态
    private static final int START_GRACE_TICKS = 20;
    // CE 的 DataComponentTypes 没暴露 FOOD 走 byId 查一次缓存
    private static final Object FOOD_COMPONENT = DataComponentTypes.byId(DataComponentKeys.FOOD);

    // 换成进食态 玩家按住右键时客户端会自行重新发起使用 原版据 consumable 组件播放进食动画
    public static void begin(Player player, InteractionHand hand, Item bag) {
        Item eating = LunchBagContents.toEatingForm(bag);
        if (ItemUtils.isEmpty(eating)) {
            return;
        }
        player.setItemInHand(hand, eating);

        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) player.platformPlayer();
        BukkitCraftEngine.instance().scheduler().platform().runLater(
                () -> cancelIfNotEating(bukkitPlayer), null, START_GRACE_TICKS, bukkitPlayer);
    }

    // 吃完一口 扣掉第一格的一个并结算营养 直接把收纳态写回手上
    // 不走 restore 扫背包 免得和原版正在进行的物品栏改动抢同一个物品
    public static void finish(org.bukkit.entity.Player bukkitPlayer, EquipmentSlot hand, Item eating) {
        Item food = LunchBagContents.removeOne(eating);
        if (!ItemUtils.isEmpty(food)) {
            applyNutrition(bukkitPlayer, food);
        }
        Item bag = LunchBagContents.toBagForm(eating);
        if (ItemUtils.isEmpty(bag)) {
            return;
        }
        bukkitPlayer.getInventory().setItem(hand, ItemStackUtils.getBukkitStack(bag));
        bukkitPlayer.updateInventory();
    }

    // 把玩家身上所有进食态换回收纳态 松手 切槽 重进服都走这里
    public static void restore(org.bukkit.entity.Player bukkitPlayer) {
        PlayerInventory inventory = bukkitPlayer.getInventory();
        ItemStack[] contents = inventory.getContents();
        boolean changed = false;

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            Item wrapped = BukkitItemManager.instance().wrap(stack);
            if (!LunchBagContents.isEatingForm(wrapped)) {
                continue;
            }
            Item bag = LunchBagContents.toBagForm(wrapped);
            if (ItemUtils.isEmpty(bag)) {
                continue;
            }
            inventory.setItem(slot, ItemStackUtils.getBukkitStack(bag));
            changed = true;
        }

        if (changed) {
            bukkitPlayer.updateInventory();
        }
    }

    private static void cancelIfNotEating(org.bukkit.entity.Player bukkitPlayer) {
        if (bukkitPlayer.isOnline() && !bukkitPlayer.isHandRaised()) {
            restore(bukkitPlayer);
        }
    }

    // food 组件里的 saturation 是饱和度绝对值 不是倍率 结算方式与原版 FoodData.eat 一致
    private static void applyNutrition(org.bukkit.entity.Player bukkitPlayer, Item food) {
        bukkitPlayer.playSound(bukkitPlayer.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.5f, 1.0f);

        JsonElement json = food.getComponentAsJson(FOOD_COMPONENT);
        if (!(json instanceof JsonObject properties)) {
            return;
        }
        int nutrition = properties.has("nutrition") ? properties.get("nutrition").getAsInt() : 0;
        float saturation = properties.has("saturation") ? properties.get("saturation").getAsFloat() : 0f;
        int level = Math.min(bukkitPlayer.getFoodLevel() + nutrition, MAX_FOOD_LEVEL);
        bukkitPlayer.setFoodLevel(level);
        bukkitPlayer.setSaturation(Math.min(bukkitPlayer.getSaturation() + saturation, level));
    }

    // 倒出全部 供潜行左键用
    public static void dropContents(org.bukkit.entity.Player bukkitPlayer, Player player, Item bag) {
        List<Item> contents = LunchBagContents.removeAll(bag);
        if (contents.isEmpty()) {
            return;
        }
        contents.forEach(item -> InventoryUtils.give(player, item));
        bukkitPlayer.playSound(bukkitPlayer.getLocation(), Sound.ITEM_BUNDLE_DROP_CONTENTS, 1.0f, 1.0f);
    }
}
