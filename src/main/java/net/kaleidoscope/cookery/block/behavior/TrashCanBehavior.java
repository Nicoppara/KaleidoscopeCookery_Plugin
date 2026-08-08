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
    public Key helmetItem = ItemKeys.TRASHCAN_HELMET;
    // 躲桶要把玩家切成旁观 等于一个无冷却的无敌避难所 PvP 服可以整个关掉
    public boolean allowHiding = true;
    // 开着也可以只放给有权限的人 留空表示所有人都能躲
    public String hidePermission = "";

    private TrashCanBehavior(FurnitureDefinition furniture) {
        super(furniture);
    }

    @Override
    public FurnitureController createController(Furniture furniture) {
        return new TrashCanController(furniture, this);
    }

    private static class Factory implements FurnitureBehaviorFactory<TrashCanBehavior> {
        @Override
        public TrashCanBehavior create(FurnitureDefinition furniture, ConfigSection section) {
            TrashCanBehavior b = new TrashCanBehavior(furniture);
            b.animChunkRadius = BehaviorConfig.getInt(section, b.animChunkRadius, "animation_view_distance", "animation-view-distance");
            b.helmetItem = Key.of(BehaviorConfig.getString(section, b.helmetItem.asString(), "helmet_item", "helmet-item"));
            b.allowHiding = BehaviorConfig.getBoolean(section, b.allowHiding, "allow_hiding", "allow-hiding");
            b.hidePermission = BehaviorConfig.getString(section, b.hidePermission, "hide_permission", "hide-permission");
            return b;
        }
    }
}
