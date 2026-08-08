package net.kaleidoscope.cookery.block.behavior;

import net.kaleidoscope.cookery.block.entity.ChairController;
import net.kaleidoscope.cookery.util.BehaviorConfig;
import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.core.entity.furniture.FurnitureDefinition;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureBehaviorFactory;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureBehaviorTemplate;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureController;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import org.joml.Vector3f;

// 椅子铺羊毛地毯
public final class ChairBehavior extends FurnitureBehaviorTemplate {
    public static final FurnitureBehaviorFactory<ChairBehavior> FACTORY = new Factory();

    public final Vector3f carpetOffset;
    public final float carpetScale;
    public final float yawOffsetDegrees;

    private ChairBehavior(FurnitureDefinition furniture, Vector3f carpetOffset, float carpetScale, float yawOffsetDegrees) {
        super(furniture);
        this.carpetOffset = carpetOffset;
        this.carpetScale = carpetScale;
        this.yawOffsetDegrees = yawOffsetDegrees;
    }

    @Override
    public FurnitureController createController(Furniture furniture) {
        return new ChairController(furniture, this);
    }

    private static class Factory implements FurnitureBehaviorFactory<ChairBehavior> {
        private static final String[] CARPET_OFFSET = {"carpet_offset", "carpet-offset"};
        private static final String[] CARPET_SCALE = {"carpet_scale", "carpet-scale"};
        private static final String[] YAW_OFFSET = {"yaw_offset", "yaw-offset"};

        private static final Vector3f DEFAULT_OFFSET = new Vector3f(0, 0.5f, 0);
        private static final float DEFAULT_SCALE = 1f;
        private static final float DEFAULT_YAW_OFFSET = 0f;

        @Override
        public ChairBehavior create(FurnitureDefinition furniture, ConfigSection section) {
            return new ChairBehavior(
                    furniture,
                    BehaviorConfig.getVector3f(section, DEFAULT_OFFSET, CARPET_OFFSET),
                    BehaviorConfig.getFloat(section, DEFAULT_SCALE, CARPET_SCALE),
                    BehaviorConfig.getFloat(section, DEFAULT_YAW_OFFSET, YAW_OFFSET));
        }
    }
}
