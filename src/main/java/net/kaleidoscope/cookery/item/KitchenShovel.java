package net.kaleidoscope.cookery.item;

import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.VersionHelper;

public final class KitchenShovel {
    private static final String OIL_TAG = "has_oil";

    private KitchenShovel() {}

    public static boolean is(Item item, Key shovelItem) {
        return ItemMatch.is(item, shovelItem) || isLegacy(item);
    }

    public static boolean isLegacy(Item item) {
        return ItemMatch.is(item, ItemKeys.KITCHEN_SHOVEL_LEGACY_OILED);
    }

    public static boolean hasOil(Item item, Key oilModel) {
        if (isLegacy(item)) {
            return true;
        }
        if (item.hasTag(ItemKeys.NAMESPACE, OIL_TAG)) {
            Object value = item.getTagAsJava(ItemKeys.NAMESPACE, OIL_TAG);
            return value instanceof Boolean hasOil ? hasOil : value instanceof Number number && number.byteValue() != 0;
        }
        if (VersionHelper.isOrAbove1_21_2) {
            if (item.itemModel().filter(oilModel.asString()::equals).isPresent()) {
                return true;
            }
        }
        return ItemMatch.is(item, oilModel);
    }

    public static void setHasOil(Item item, boolean hasOil, Key shovelModel, Key oilModel) {
        item.setTag(hasOil, ItemKeys.NAMESPACE, OIL_TAG);
        if (VersionHelper.isOrAbove1_21_2) {
            item.itemModel((hasOil ? oilModel : shovelModel).asString());
        }
    }

    // 兼容旧版沾油锅铲
    // 返回 false 表示该换铲但 shovelItem 无效 调用方不能继续扣料
    public static boolean migrateLegacy(Player player, InteractionHand hand, Item item,
                                        Key shovelItem, Key oilModel, boolean hasOil) {
        if (!isLegacy(item)) {
            return true;
        }
        Item shovel = InventoryUtils.createOrEmpty(shovelItem);
        if (ItemUtils.isEmpty(shovel)) {
            return false;
        }
        setHasOil(shovel, hasOil, shovelItem, oilModel);
        player.setItemInHand(hand, shovel);
        return true;
    }
}
