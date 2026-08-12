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
        return ItemMatch.is(item, shovelItem)
                || shovelItem.equals(ItemKeys.KITCHEN_SHOVEL)
                && isLegacy(item);
    }

    public static boolean isLegacy(Item item) {
        return ItemMatch.is(item, ItemKeys.KITCHEN_SHOVEL_OIL_MODEL);
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

    // 旧沾油锅铲兼容，1.1.9 删除
    public static void migrateLegacy(Player player, InteractionHand hand, Item item,
                                     Key shovelItem, Key oilModel, boolean hasOil) {
        if (!isLegacy(item)) {
            return;
        }
        Item shovel = InventoryUtils.createOrEmpty(shovelItem);
        if (!ItemUtils.isEmpty(shovel)) {
            setHasOil(shovel, hasOil, shovelItem, oilModel);
            player.setItemInHand(hand, shovel);
        }
    }
}
