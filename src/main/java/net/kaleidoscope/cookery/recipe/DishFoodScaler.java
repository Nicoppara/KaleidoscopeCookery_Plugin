package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.item.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent;

// 按品质倍率缩放成品的食物属性 出锅时就写进 minecraft:food 组件
// 之后它就是一个普通食物 不需要在进食时再拦一道
public final class DishFoodScaler {
    private DishFoodScaler() {}

    public static Item scale(Item item, double ratio) {
        if (item == null || ratio == 1.0) {
            return item;
        }
        try {
            ItemStack stack = ItemStackUtils.getBukkitStack(item.minecraftItem());
            ItemMeta meta = stack.getItemMeta();
            if (meta == null || !meta.hasFood()) {
                return item;
            }
            FoodComponent food = meta.getFood();
            // 原本能回一格的菜别缩到 0 那等于不能吃
            food.setNutrition(Math.max(1, (int) Math.round(food.getNutrition() * ratio)));
            food.setSaturation((float) (food.getSaturation() * ratio));
            meta.setFood(food);
            stack.setItemMeta(meta);
            return BukkitItemManager.instance().wrap(stack);
        } catch (Exception e) {
            // 缩放失败不该让整锅菜出不来 退回未缩放的成品
            return item;
        }
    }
}
