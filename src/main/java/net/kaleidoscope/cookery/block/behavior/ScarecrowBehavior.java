package net.kaleidoscope.cookery.block.behavior;

import net.kaleidoscope.cookery.block.entity.ScarecrowController;
import net.kaleidoscope.cookery.block.entity.ScarecrowElement;
import net.kaleidoscope.cookery.util.BehaviorConfig;
import net.kaleidoscope.cookery.util.LightBlocks;
import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.core.entity.furniture.FurnitureDefinition;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureBehaviorFactory;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureBehaviorTemplate;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureController;
import net.momirealms.craftengine.core.entity.furniture.hitbox.FurnitureHitBox;
import net.momirealms.craftengine.core.entity.furniture.hitbox.FurnitureHitBoxConfig;
import net.momirealms.craftengine.core.entity.furniture.hitbox.FurnitureHitBoxConfigs;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public final class ScarecrowBehavior extends FurnitureBehaviorTemplate {
    public static final FurnitureBehaviorFactory<ScarecrowBehavior> FACTORY = new Factory();

    public final int protectionRadius;
    public final boolean parrotPerch;
    public final int parrotScanInterval;
    public final double parrotPickupRange;
    public final Vector3f shoulderOffset;
    public final int lanternLightLevel;
    public final long interactCooldownNanos;
    public final Vector3f bodyOffset;
    public final SlotConfig[] slots;

    private ScarecrowBehavior(FurnitureDefinition furniture, int protectionRadius, boolean parrotPerch,
                              int parrotScanInterval, double parrotPickupRange, Vector3f shoulderOffset,
                              int lanternLightLevel, int interactCooldownTicks, Vector3f bodyOffset,
                              SlotConfig[] slots) {
        super(furniture);
        this.protectionRadius = protectionRadius;
        this.parrotPerch = parrotPerch;
        this.parrotScanInterval = parrotScanInterval;
        this.parrotPickupRange = parrotPickupRange;
        this.shoulderOffset = shoulderOffset;
        this.lanternLightLevel = Math.max(0, Math.min(LightBlocks.MAX_LEVEL, lanternLightLevel));
        this.interactCooldownNanos = interactCooldownTicks * 50L * 1_000_000L;
        this.bodyOffset = bodyOffset;
        this.slots = slots;
    }

    @Override
    public FurnitureController createController(Furniture furniture) {
        return new ScarecrowController(furniture, this);
    }

    public record SlotConfig(Vector3f position, float scale, float yawOffsetDegrees, byte itemTransform,
                             FurnitureHitBoxConfig<? extends FurnitureHitBox> hitbox) {
    }

    private static class Factory implements FurnitureBehaviorFactory<ScarecrowBehavior> {
        private static final String[] PROTECTION_RADIUS = {"protection_radius", "protection-radius"};
        private static final String[] PARROT_PERCH = {"parrot_perch", "parrot-perch"};
        private static final String[] PARROT_SCAN_INTERVAL = {"parrot_scan_interval", "parrot-scan-interval"};
        private static final String[] PARROT_PICKUP_RANGE = {"parrot_pickup_range", "parrot-pickup-range"};
        private static final String[] SHOULDER_OFFSET = {"shoulder_offset", "shoulder-offset"};
        private static final String[] LANTERN_LIGHT = {"lantern_light", "lantern-light"};
        private static final String[] INTERACT_COOLDOWN = {"interact_cooldown", "interact-cooldown"};
        private static final String[] BODY_OFFSET = {"body_offset", "body-offset"};
        private static final String[] ITEM_TRANSFORM = {"item_transform", "item-transform"};
        private static final String[] YAW_OFFSET = {"yaw_offset", "yaw-offset"};

        private static final int DEFAULT_PROTECTION_RADIUS = 16;
        private static final int DEFAULT_PARROT_SCAN_INTERVAL = 40;
        private static final double DEFAULT_PARROT_PICKUP_RANGE = 2.0;
        // 鹦鹉停的肩膀位 x 是离中轴的横向偏移 y 是肩高
        private static final Vector3f DEFAULT_SHOULDER_OFFSET = new Vector3f(0.72f, 1.62f, 0);
        private static final int DEFAULT_LANTERN_LIGHT = 15;
        private static final int DEFAULT_INTERACT_COOLDOWN = 5;
        private static final Vector3f DEFAULT_BODY_OFFSET = new Vector3f(0, 0.5f, 0);

        private static final String[] SLOT_NAMES = {"head", "main_hand", "off_hand"};
        private static final Vector3f[] DEFAULT_POSITIONS = {
                new Vector3f(0, 1.969f, 0),
                new Vector3f(0.75f, 1.611f, 0),
                new Vector3f(-0.75f, 1.236f, 0)
        };
        private static final float[] DEFAULT_SCALES = {1.125f, 0.75f, 0.75f};
        private static final byte[] DEFAULT_TRANSFORMS = {(byte) 5, (byte) 2, (byte) 0};
        private static final float[] DEFAULT_YAW_OFFSETS = {180f, 0f, 0f};
        private static final float[] DEFAULT_HITBOX_HEIGHTS = {0.6f, 0.55f, 0.55f};
        private static final float[] DEFAULT_HITBOX_WIDTHS = {0.7f, 0.5f, 0.5f};

        @Override
        public ScarecrowBehavior create(FurnitureDefinition furniture, ConfigSection section) {
            ConfigSection slotsSection = section.getSection("slots");
            SlotConfig[] slots = new SlotConfig[ScarecrowElement.SLOTS];
            for (int slot = 0; slot < ScarecrowElement.SLOTS; slot++) {
                ConfigSection slotSection = slotsSection == null ? null : slotsSection.getSection(SLOT_NAMES[slot]);
                slots[slot] = slotConfig(slot, slotSection);
            }
            return new ScarecrowBehavior(
                    furniture,
                    BehaviorConfig.getInt(section, DEFAULT_PROTECTION_RADIUS, PROTECTION_RADIUS),
                    BehaviorConfig.getBoolean(section, true, PARROT_PERCH),
                    BehaviorConfig.getInt(section, DEFAULT_PARROT_SCAN_INTERVAL, PARROT_SCAN_INTERVAL),
                    BehaviorConfig.getDouble(section, DEFAULT_PARROT_PICKUP_RANGE, PARROT_PICKUP_RANGE),
                    BehaviorConfig.getVector3f(section, DEFAULT_SHOULDER_OFFSET, SHOULDER_OFFSET),
                    BehaviorConfig.getInt(section, DEFAULT_LANTERN_LIGHT, LANTERN_LIGHT),
                    BehaviorConfig.getInt(section, DEFAULT_INTERACT_COOLDOWN, INTERACT_COOLDOWN),
                    BehaviorConfig.getVector3f(section, DEFAULT_BODY_OFFSET, BODY_OFFSET),
                    slots
            );
        }

        private static SlotConfig slotConfig(int slot, ConfigSection section) {
            if (section == null) {
                return new SlotConfig(DEFAULT_POSITIONS[slot], DEFAULT_SCALES[slot], DEFAULT_YAW_OFFSETS[slot],
                        DEFAULT_TRANSFORMS[slot], defaultHitbox(slot, DEFAULT_POSITIONS[slot]));
            }
            Vector3f position = section.getVector3f("position", DEFAULT_POSITIONS[slot]);
            ConfigSection hitboxSection = section.getSection("hitbox");
            return new SlotConfig(
                    position,
                    section.getFloat("scale", DEFAULT_SCALES[slot]),
                    BehaviorConfig.getFloat(section, DEFAULT_YAW_OFFSETS[slot], YAW_OFFSET),
                    (byte) BehaviorConfig.getInt(section, DEFAULT_TRANSFORMS[slot], ITEM_TRANSFORM),
                    hitboxSection == null ? defaultHitbox(slot, position) : FurnitureHitBoxConfigs.fromConfig(hitboxSection));
        }

        private static FurnitureHitBoxConfig<? extends FurnitureHitBox> defaultHitbox(int slot, Vector3f position) {
            float height = DEFAULT_HITBOX_HEIGHTS[slot];
            Map<String, Object> values = new HashMap<>();
            values.put("type", "interaction");
            values.put("position", position.x + "," + (position.y - height * 0.5f) + "," + position.z);
            values.put("width", DEFAULT_HITBOX_WIDTHS[slot]);
            values.put("height", height);
            values.put("can_use_item_on", true);
            values.put("can_be_hit_by_projectile", false);
            // 交互箱占住的地方不让放方块 否则能把灯笼那格塞满 光源就点不亮
            values.put("blocks_building", true);
            values.put("invisible", true);
            return FurnitureHitBoxConfigs.fromConfig(ConfigSection.ofRoot(values));
        }
    }
}
