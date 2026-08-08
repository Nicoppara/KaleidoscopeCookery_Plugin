package net.kaleidoscope.cookery.block.behavior;
import net.kaleidoscope.cookery.block.entity.MillstoneController;

import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.core.entity.furniture.FurnitureDefinition;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureBehaviorFactory;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureBehaviorTemplate;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureController;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.kaleidoscope.cookery.api.MillstoneAnimals;
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

    // 磨杆自磨心伸出的长度 与生物的默认绕磨半径取齐 玩家的接触判定按它划环带
    public float pushBarLength = (float) MillstoneAnimals.DEFAULT_ORBIT_RADIUS;
    // 接触弧在玩家体宽之外额外放宽的角度 磨杆已扫过玩家的那一侧
    public float pushContactTolerance = 20f;
    // 玩家跑到磨杆前方那一侧的容差 玩家总会跑过头 这侧放宽才拽得住磨杆
    public float pushLeadTolerance = 60f;
    // 玩家推磨的角速度上限换算成秒每圈 默认按半径 2.5 上的原版步行速度定 想跑得比这快就会脱手
    public float pushMaxSeconds = 3.5f;
    // 生物拉磨时磨杆扫开挡道玩家的切向速度
    public double pushShoveStrength = 0.45;
    // 玩家推磨时磨杆的最大回顶力 按越到杆前方的角度渐强 贴着杆推时几乎为 0
    // 给太大会变成橡皮筋回弹 setVelocity 对玩家只是建议 拦不住一直按 W 的人
    public double pushResistStrength = 0.3;
    // 磨杆模型朝向与轨道角的固定偏差 换模型时用它对齐 不用改代码
    public float pushAngleOffset = 0f;


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
            b.pushBarLength = BehaviorConfig.getFloat(section, b.pushBarLength, "push_bar_length", "push-bar-length");
            b.pushContactTolerance = BehaviorConfig.getFloat(section, b.pushContactTolerance, "push_contact_tolerance", "push-contact-tolerance");
            b.pushLeadTolerance = BehaviorConfig.getFloat(section, b.pushLeadTolerance, "push_lead_tolerance", "push-lead-tolerance");
            b.pushMaxSeconds = BehaviorConfig.getFloat(section, b.pushMaxSeconds, "push_max_seconds", "push-max-seconds");
            b.pushShoveStrength = BehaviorConfig.getDouble(section, b.pushShoveStrength, "push_shove_strength", "push-shove-strength");
            b.pushResistStrength = BehaviorConfig.getDouble(section, b.pushResistStrength, "push_resist_strength", "push-resist-strength");
            b.pushAngleOffset = BehaviorConfig.getFloat(section, b.pushAngleOffset, "push_angle_offset", "push-angle-offset");
            return b;
        }
    }
}
