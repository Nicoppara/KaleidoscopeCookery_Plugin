package net.kaleidoscope.cookery.item.condition;

import net.kaleidoscope.cookery.recipe.DishCarriers;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.context.Context;
import net.momirealms.craftengine.core.plugin.context.function.Function;
import net.momirealms.craftengine.core.plugin.context.function.FunctionFactory;
import net.momirealms.craftengine.core.plugin.context.parameter.DirectContextParameters;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.WorldPosition;

import java.util.Optional;

// 家具菜吃完退还盛装容器 挂在 dish 模板的 eat_functions 上
// 容器查 DishCarriers 即配方的 carrier 改配方立刻生效 eaten_pools 只留额外掉落物
// 掉落点取上下文位置 与同一段里的 drop_loot 一致 碗和骨头落在一起
public final class ReturnCarrierFunction<CTX extends Context> implements Function<CTX> {

    // 家具菜上下文不包含成品物品，因此由配置提供菜品 ID
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
        Optional<WorldPosition> position = ctx.getOptionalParameter(DirectContextParameters.POSITION);
        if (position.isEmpty()) {
            return;
        }
        Item container = InventoryUtils.createOrEmpty(carrier);
        if (ItemUtils.isEmpty(container)) {
            return;
        }
        position.get().world().dropItemNaturally(position.get(), container.copyWithCount(1));
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
