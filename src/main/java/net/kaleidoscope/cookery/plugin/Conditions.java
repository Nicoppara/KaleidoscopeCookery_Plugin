package net.kaleidoscope.cookery.plugin;

import net.kaleidoscope.cookery.item.condition.ReturnCarrierFunction;
import net.kaleidoscope.cookery.item.condition.WearingCondition;
import net.momirealms.craftengine.core.plugin.context.CommonConditionType;
import net.momirealms.craftengine.core.util.Key;

// 条件注册 供战利品
public final class Conditions {
    private static final Key WEARING_ID = Key.of("kaleidoscopecookery:wearing");
    private static final Key RETURN_CARRIER_ID = Key.of("kaleidoscopecookery:return_carrier");

    private static volatile CommonConditionType<?> wearing;
    private static volatile Object returnCarrier;

    private Conditions() {}

    public static void register() {
        if (wearing == null) {
            wearing = RegistryUtils.registerCondition(WEARING_ID, WearingCondition.factory());
        }
        if (returnCarrier == null) {
            returnCarrier = RegistryUtils.registerFunction(RETURN_CARRIER_ID, ReturnCarrierFunction.factory());
        }
    }
}
