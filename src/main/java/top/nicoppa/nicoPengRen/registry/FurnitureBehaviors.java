package top.nicoppa.nicoPengRen.registry;

import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureBehaviorType;
import net.momirealms.craftengine.core.util.Key;
import top.nicoppa.nicoPengRen.content.messageboard.MessageBoardBehavior;

/**
 * 家具行为注册
 * 注册细节委托给 {@link RegistryUtils}
 */
public final class FurnitureBehaviors {
    public static FurnitureBehaviorType<MessageBoardBehavior> MESSAGE_BOARD;

    private FurnitureBehaviors() {}

    public static void register() {
        if (MESSAGE_BOARD == null) {
            MESSAGE_BOARD = RegistryUtils.registerFurnitureBehavior(
                    Key.of("nicopengren:message_board"),
                    MessageBoardBehavior.FACTORY
            );
        }
    }
}