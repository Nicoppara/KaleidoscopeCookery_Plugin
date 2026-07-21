package net.kaleidoscope.cookery.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
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
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

// 饭袋进食态的生命周期
// 原版收纳袋没有进食动画 所以右键时把手里的物品临时换成可食用的进食态 由原版驱动动画与中断 吃完再换回收纳态
public final class LunchBagEating {
    private LunchBagEating() {}

    private static final int MAX_FOOD_LEVEL = 20;
    private static final float BURP_VOLUME = 0.5f;
    private static final float BURP_PITCH = 1.0f;
    private static final float DROP_VOLUME = 1.0f;
    private static final float DROP_PITCH = 1.0f;
    // 换成进食态后客户端要隔一两 tick 才会重新发起使用 超过这个宽限还没抬手就说明只是点了一下 换回收纳态
    private static final int START_GRACE_TICKS = 20;
    // CE 的 DataComponentTypes 没暴露 FOOD/CONSUMABLE 走 byId 查一次缓存
    private static final Object FOOD_COMPONENT = DataComponentTypes.byId(DataComponentKeys.FOOD);
    private static final Object CONSUMABLE_COMPONENT = DataComponentTypes.byId(DataComponentKeys.CONSUMABLE);
    private static final String APPLY_EFFECTS_TYPE = "minecraft:apply_effects";
    private static final int DEFAULT_EFFECT_TICKS = 20;

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

    // 判据只能是手有没有抬起 别再加"正在用的是不是进食态"之类的附加条件
    // 宽限期(20t)短于进食时长(40t) 这个兜底必然在进食中途触发一次
    // 任何附加条件一旦判失败就会当场 restore 打断进食 玩家永远吃不完
    // 举盾拉弓导致进食态残留属于边缘情况 交给 onStopUsing/onHeldChange/onDrop/onJoin 四个钩子兜底
    private static void cancelIfNotEating(org.bukkit.entity.Player bukkitPlayer) {
        if (bukkitPlayer.isOnline() && !bukkitPlayer.isHandRaised()) {
            restore(bukkitPlayer);
        }
    }

    // food 组件里的 saturation 是饱和度绝对值 不是倍率 结算方式与原版 FoodData.eat 一致
    private static void applyNutrition(org.bukkit.entity.Player bukkitPlayer, Item food) {
        JsonElement json = food.getComponentAsJson(FOOD_COMPONENT);
        if (!(json instanceof JsonObject properties)) {
            return;
        }
        bukkitPlayer.playSound(bukkitPlayer.getLocation(), Sound.ENTITY_PLAYER_BURP, BURP_VOLUME, BURP_PITCH);
        int nutrition = properties.has("nutrition") ? properties.get("nutrition").getAsInt() : 0;
        float saturation = properties.has("saturation") ? properties.get("saturation").getAsFloat() : 0f;
        int level = Math.min(bukkitPlayer.getFoodLevel() + nutrition, MAX_FOOD_LEVEL);
        bukkitPlayer.setFoodLevel(level);
        bukkitPlayer.setSaturation(Math.min(bukkitPlayer.getSaturation() + saturation, level));
        applyConsumeEffects(bukkitPlayer, food);
    }

    // 1.21.2 起进食附带的效果从 food.effects 移到了 consumable.on_consume_effects
    // 这里只处理 apply_effects 其余类型(清除效果 随机传送等)原版语义复杂 用到再补
    private static void applyConsumeEffects(org.bukkit.entity.Player bukkitPlayer, Item food) {
        if (CONSUMABLE_COMPONENT == null) {
            return;
        }
        if (!(food.getComponentAsJson(CONSUMABLE_COMPONENT) instanceof JsonObject consumable)) {
            return;
        }
        JsonElement onConsume = consumable.get("on_consume_effects");
        if (!(onConsume instanceof JsonArray entries)) {
            return;
        }
        for (JsonElement entry : entries) {
            if (!(entry instanceof JsonObject effectEntry)) {
                continue;
            }
            if (!APPLY_EFFECTS_TYPE.equals(asString(effectEntry.get("type")))) {
                continue;
            }
            float probability = effectEntry.has("probability") ? effectEntry.get("probability").getAsFloat() : 1f;
            if (probability < 1f && ThreadLocalRandom.current().nextFloat() >= probability) {
                continue;
            }
            if (effectEntry.get("effects") instanceof JsonArray effects) {
                effects.forEach(e -> applySingleEffect(bukkitPlayer, e));
            }
        }
    }

    private static void applySingleEffect(org.bukkit.entity.Player bukkitPlayer, JsonElement element) {
        if (!(element instanceof JsonObject effect)) {
            return;
        }
        String id = asString(effect.get("id"));
        if (id == null) {
            return;
        }
        NamespacedKey key = NamespacedKey.fromString(id);
        PotionEffectType type = key == null ? null : Registry.EFFECT.get(key);
        if (type == null) {
            return;
        }
        // duration 单位是 tick 缺省取原版默认的 1 秒
        int duration = effect.has("duration") ? effect.get("duration").getAsInt() : DEFAULT_EFFECT_TICKS;
        int amplifier = effect.has("amplifier") ? effect.get("amplifier").getAsInt() : 0;
        boolean ambient = !effect.has("ambient") || effect.get("ambient").getAsBoolean();
        boolean particles = !effect.has("show_particles") || effect.get("show_particles").getAsBoolean();
        boolean icon = !effect.has("show_icon") || effect.get("show_icon").getAsBoolean();
        bukkitPlayer.addPotionEffect(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
    }

    private static String asString(JsonElement element) {
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    // 倒出全部 供潜行左键用
    public static void dropContents(Player player, InteractionHand hand, Item bag) {
        List<Item> contents = LunchBagContents.removeAll(bag);
        if (contents.isEmpty()) {
            return;
        }
        // 内容物已经发给玩家了 袋子必须写回 否则倒一次刷一次
        player.setItemInHand(hand, bag);
        contents.forEach(item -> InventoryUtils.give(player, item));
        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) player.platformPlayer();
        bukkitPlayer.playSound(bukkitPlayer.getLocation(), Sound.ITEM_BUNDLE_DROP_CONTENTS, DROP_VOLUME, DROP_PITCH);
    }
}
