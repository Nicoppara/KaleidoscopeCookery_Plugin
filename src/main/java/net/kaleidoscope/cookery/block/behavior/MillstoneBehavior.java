package net.kaleidoscope.cookery.block.behavior;
import net.kaleidoscope.cookery.block.entity.MillstoneController;

import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.core.entity.furniture.FurnitureDefinition;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureBehaviorFactory;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureBehaviorTemplate;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureController;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.kaleidoscope.cookery.util.BehaviorConfig;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.item.ItemKeys;

public final class MillstoneBehavior extends FurnitureBehaviorTemplate {
    public static final FurnitureBehaviorFactory<MillstoneBehavior> FACTORY = new Factory();

    public int animChunkRadius = TrackedPlayers.DEFAULT_ANIM_CHUNK_RADIUS;
    // 默认每料研磨所需圈数 精准配方可各自用 rotations 覆盖 真实耗时由拉磨者转速(秒/圈)决定
    public int grindRotations = 4;
    public Key stickItem = ItemKeys.NEW_MILLSTONE_STICK;
    public Key stick2Item = ItemKeys.NEW_MILLSTONE_STICK2;
    public Key stoneItem = ItemKeys.NEW_MILLSTONE_STONE;
    public String msgAlreadyPushing = "§c你已经在推另一个石磨了";
    public String msgNeedGroundBelow = "§c起始位置脚下必须有方块";
    public String msgUneven = "§c起始位置不平整，无法推磨";
    public String msgNotSamePlane = "§c你与石磨不在同一平面，无法推磨";
    public String msgExitHint = "§e再次 潜行+右键石磨 退出推磨";
    public String msgStopAnimalHint = "§e手持剪刀 Shift+右键 石磨可停止生物拉磨";

    private MillstoneBehavior(FurnitureDefinition furniture) {
        super(furniture);
    }

    @Override
    public FurnitureController createController(Furniture furniture) {
        return new MillstoneController(furniture, this);
    }

    private static class Factory implements FurnitureBehaviorFactory<MillstoneBehavior> {
        @Override
        public MillstoneBehavior create(FurnitureDefinition furniture, ConfigSection section) {
            MillstoneBehavior b = new MillstoneBehavior(furniture);
            b.animChunkRadius = BehaviorConfig.getInt(section, b.animChunkRadius, "animation_view_distance", "animation-view-distance");
            b.grindRotations = BehaviorConfig.getInt(section, b.grindRotations, "grind_rotations", "grind-rotations");
            b.stickItem = Key.of(BehaviorConfig.getString(section, b.stickItem.asString(), "stick_item", "stick-item"));
            b.stick2Item = Key.of(BehaviorConfig.getString(section, b.stick2Item.asString(), "stick2_item", "stick2-item"));
            b.stoneItem = Key.of(BehaviorConfig.getString(section, b.stoneItem.asString(), "stone_item", "stone-item"));
            b.msgAlreadyPushing = BehaviorConfig.getString(section, b.msgAlreadyPushing, "msg_already_pushing", "msg-already-pushing");
            b.msgNeedGroundBelow = BehaviorConfig.getString(section, b.msgNeedGroundBelow, "msg_need_ground_below", "msg-need-ground-below");
            b.msgUneven = BehaviorConfig.getString(section, b.msgUneven, "msg_uneven", "msg-uneven");
            b.msgNotSamePlane = BehaviorConfig.getString(section, b.msgNotSamePlane, "msg_not_same_plane", "msg-not-same-plane");
            b.msgExitHint = BehaviorConfig.getString(section, b.msgExitHint, "msg_exit_hint", "msg-exit-hint");
            b.msgStopAnimalHint = BehaviorConfig.getString(section, b.msgStopAnimalHint, "msg_stop_animal_hint", "msg-stop-animal-hint");
            return b;
        }
    }
}
