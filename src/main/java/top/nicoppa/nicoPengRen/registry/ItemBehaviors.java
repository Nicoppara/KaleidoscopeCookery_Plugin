package top.nicoppa.nicoPengRen.registry;

import net.momirealms.craftengine.core.item.behavior.ItemBehaviorType;
import net.momirealms.craftengine.core.util.Key;
import top.nicoppa.nicoPengRen.content.noodle.DoughPullingBehavior;

/**
 * 物品行为注册中心
 * 注册细节委托给 {@link RegistryUtils}。
 */
public final class ItemBehaviors {
    public static ItemBehaviorType<DoughPullingBehavior> DOUGH_PULLING;

    private ItemBehaviors() {}

    public static void register() {
        if (DOUGH_PULLING == null) {
            DOUGH_PULLING = RegistryUtils.registerItemBehavior(
                    Key.of("nicopengren:dough_pulling"),
                    DoughPullingBehavior.FACTORY
            );
        }
    }
}