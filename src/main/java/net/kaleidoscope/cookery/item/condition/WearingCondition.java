package net.kaleidoscope.cookery.item.condition;

import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.context.Condition;
import net.momirealms.craftengine.core.plugin.context.Context;
import net.momirealms.craftengine.core.plugin.context.condition.ConditionFactory;
import net.momirealms.craftengine.core.plugin.context.parameter.DirectContextParameters;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

// 玩家指定装备槽穿着的是不是列表里的物品
// CE 内置条件里 match_item 只看手上 inventory_has_item 只看背包 没有装备槽判定
public final class WearingCondition<CTX extends Context> implements Condition<CTX> {
    private final EquipmentSlot slot;
    private final Set<Key> items;

    private WearingCondition(EquipmentSlot slot, Set<Key> items) {
        this.slot = slot;
        this.items = items;
    }

    @Override
    public boolean test(CTX ctx) {
        Optional<Player> player = ctx.getOptionalParameter(DirectContextParameters.PLAYER);
        if (player.isEmpty()) {
            return false;
        }
        if (!(player.get().platformPlayer() instanceof org.bukkit.entity.Player bukkitPlayer)) {
            return false;
        }
        ItemStack worn = bukkitPlayer.getInventory().getItem(this.slot);
        Item item = BukkitItemManager.instance().wrap(worn);
        return !ItemUtils.isEmpty(item) && this.items.contains(item.id());
    }

    public static <CTX extends Context> ConditionFactory<CTX, WearingCondition<CTX>> factory() {
        return new Factory<>();
    }

    private static class Factory<CTX extends Context> implements ConditionFactory<CTX, WearingCondition<CTX>> {
        private static final String[] SLOT = {"slot"};
        private static final String[] ITEMS = {"items", "item", "id"};

        @Override
        public WearingCondition<CTX> create(ConfigSection section) {
            Set<Key> keys = new HashSet<>();
            for (String id : section.getNonNullStringList(ITEMS)) {
                keys.add(Key.of(id));
            }
            return new WearingCondition<>(slot(section.getString(SLOT[0], "head")), Set.copyOf(keys));
        }

        private static EquipmentSlot slot(String name) {
            return switch (name.trim().toLowerCase(Locale.ROOT)) {
                case "head", "helmet" -> EquipmentSlot.HEAD;
                case "chest", "chestplate", "body" -> EquipmentSlot.CHEST;
                case "legs", "leggings" -> EquipmentSlot.LEGS;
                case "feet", "boots" -> EquipmentSlot.FEET;
                case "hand", "main_hand", "main-hand" -> EquipmentSlot.HAND;
                case "off_hand", "off-hand", "offhand" -> EquipmentSlot.OFF_HAND;
                default -> throw new IllegalArgumentException("未知的装备槽 " + name);
            };
        }
    }
}
