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
import net.kaleidoscope.cookery.item.ItemKeys;

public final class MillstoneBehavior extends FurnitureBehaviorTemplate {
    public static final FurnitureBehaviorFactory<MillstoneBehavior> FACTORY = new Factory();

    public int playerGrindTime = 600;
    public int boostedGrindTime = 300;
    public int animalGrindTime = 300;
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
            b.playerGrindTime = BehaviorConfig.getInt(section, b.playerGrindTime, "player_grind_time", "player-grind-time");
            b.boostedGrindTime = BehaviorConfig.getInt(section, b.boostedGrindTime, "boosted_grind_time", "boosted-grind-time");
            b.animalGrindTime = BehaviorConfig.getInt(section, b.animalGrindTime, "animal_grind_time", "animal-grind-time");
            b.stickItem = Key.of(BehaviorConfig.getString(section, b.stickItem.toString(), "stick_item", "stick-item"));
            b.stick2Item = Key.of(BehaviorConfig.getString(section, b.stick2Item.toString(), "stick2_item", "stick2-item"));
            b.stoneItem = Key.of(BehaviorConfig.getString(section, b.stoneItem.toString(), "stone_item", "stone-item"));
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
