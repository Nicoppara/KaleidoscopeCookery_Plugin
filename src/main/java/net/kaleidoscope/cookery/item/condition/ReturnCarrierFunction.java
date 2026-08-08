package net.kaleidoscope.cookery.item.condition;

import net.kaleidoscope.cookery.recipe.DishCarriers;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.context.Context;
import net.momirealms.craftengine.core.plugin.context.function.Function;
import net.momirealms.craftengine.core.plugin.context.function.FunctionFactory;
import net.momirealms.craftengine.core.plugin.context.parameter.DirectContextParameters;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;

import java.util.Optional;

// 家具菜吃完退还盛装容器 挂在 dish 模板的 eat_functions 上
// 容器查 DishCarriers 即配方的 carrier 改配方立刻生效 eaten_pools 只留额外掉落物
public final class ReturnCarrierFunction<CTX extends Context> implements Function<CTX> {

    // 菜品 id 从配置里写死 家具菜的上下文里没有成品物品这个参数
    private final Key dish;

    private ReturnCarrierFunction(Key dish) {
        this.dish = dish;
    }

    @Override
    public void run(CTX ctx) {
        Key carrier = DishCarriers.of(this.dish);
        if (carrier == null) {
            return;
        }
        Optional<Player> player = ctx.getOptionalParameter(DirectContextParameters.PLAYER);
        if (player.isEmpty()) {
            return;
        }
        Item container = InventoryUtils.createOrEmpty(carrier);
        if (ItemUtils.isEmpty(container)) {
            return;
        }
        InventoryUtils.give(player.get(), container.copyWithCount(1));
    }

    public static <CTX extends Context> FunctionFactory<CTX, ReturnCarrierFunction<CTX>> factory() {
        return section -> {
            String dish = section.getString(new String[]{"dish"}, (String) null);
            if (dish == null || dish.isBlank()) {
                throw new IllegalArgumentException("return_carrier 缺少 dish");
            }
            return new ReturnCarrierFunction<>(Key.of(dish));
        };
    }
}
