package top.nicoppa.nicoPengRen.content.messageboard;

import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.core.entity.furniture.FurnitureDefinition;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureBehaviorFactory;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureBehaviorTemplate;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureController;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;

/**
 * 留言板家具行为 右键打开留言对话框，留言存入家具 NBT 并按玩家视角朝向悬浮展示最近若干条
 * 数据/tick 见 {@link MessageBoardController}，渲染见 {@link MessageBoardCommentElement}
 * TODO：正式版删除 临时项目
 */
public final class MessageBoardBehavior extends FurnitureBehaviorTemplate {
    private static class Factory implements FurnitureBehaviorFactory<MessageBoardBehavior> {
        @Override
        public MessageBoardBehavior create(FurnitureDefinition furniture, ConfigSection section) {
            return new MessageBoardBehavior(furniture);
        }
    }

    public static final FurnitureBehaviorFactory<MessageBoardBehavior> FACTORY = new Factory();

    private MessageBoardBehavior(FurnitureDefinition furniture) {
        super(furniture);
    }

    @Override
    public FurnitureController createController(Furniture furniture) {
        return new MessageBoardController(furniture);
    }

    public void load(ConfigSection section) {
    }
}
