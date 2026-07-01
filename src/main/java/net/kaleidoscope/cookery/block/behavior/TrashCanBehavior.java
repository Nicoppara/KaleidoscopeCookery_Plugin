package net.kaleidoscope.cookery.block.behavior;
import net.kaleidoscope.cookery.block.entity.TrashCanController;

import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.core.entity.furniture.FurnitureDefinition;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureBehaviorFactory;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureBehaviorTemplate;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureController;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.util.BehaviorConfig;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;

public final class TrashCanBehavior extends FurnitureBehaviorTemplate {
    public static final FurnitureBehaviorFactory<TrashCanBehavior> FACTORY = new Factory();

    public int animChunkRadius = TrackedPlayers.DEFAULT_ANIM_CHUNK_RADIUS;
    // 进桶时给玩家戴的头盔物品 用带 camera_overlay 组件的自定义皮革帽做遮罩
    public Key helmetItem = ItemKeys.TRASHCAN_HELMET;

    private TrashCanBehavior(FurnitureDefinition furniture) {
        super(furniture);
    }

    @Override
    public FurnitureController createController(Furniture furniture) {
        return new TrashCanController(furniture, animChunkRadius, helmetItem);
    }

    private static class Factory implements FurnitureBehaviorFactory<TrashCanBehavior> {
        @Override
        public TrashCanBehavior create(FurnitureDefinition furniture, ConfigSection section) {
            TrashCanBehavior b = new TrashCanBehavior(furniture);
            b.animChunkRadius = BehaviorConfig.getInt(section, b.animChunkRadius, "animation_view_distance", "animation-view-distance");
            b.helmetItem = Key.of(BehaviorConfig.getString(section, b.helmetItem.asString(), "helmet_item", "helmet-item"));
            return b;
        }
    }
}
