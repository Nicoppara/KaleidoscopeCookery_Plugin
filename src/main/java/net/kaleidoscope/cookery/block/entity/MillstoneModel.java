package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.entity.render.ItemDisplayPackets;

import net.kaleidoscope.cookery.block.behavior.MillstoneBehavior;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.world.WorldPosition;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

final class MillstoneModel {

    private final MillstoneBehavior behavior;

    MillstoneModel(MillstoneBehavior behavior) {
        this.behavior = behavior;
    }

    Object spawn(WorldPosition basePos, int entityId, UUID uuid) {
        return ItemDisplayPackets.at(basePos).spawn(entityId, uuid);
    }

    Object stick1Meta(int entityId, float baseYawRad, float angleY, int durationTicks) {
        Item stickItem = InventoryUtils.createOrEmpty(behavior.stickItem);
        Vector3f translation = MillstoneAnimation.stick1Translation(baseYawRad);
        Quaternionf rotation = MillstoneAnimation.stick1Rotation(baseYawRad, angleY);

        ItemDisplayPackets packets = ItemDisplayPackets.builder()
                .item(stickItem)
                .scale(1.0f, 1.0f, 1.0f)
                .translation(translation.x, translation.y, translation.z)
                .leftRotation(rotation)
                .itemTransform((byte) 8);
        if (durationTicks > 0) {
            packets.interpolation(durationTicks, 0);
        }
        return packets.meta(entityId);
    }

    Object stick2Meta(int entityId, float baseYawRad, float angleY, int durationTicks) {
        Item stickItem = InventoryUtils.createOrEmpty(behavior.stick2Item);
        Vector3f translation = MillstoneAnimation.stick2Translation(baseYawRad, angleY);
        Quaternionf rotation = MillstoneAnimation.stick2Rotation(baseYawRad, angleY);

        ItemDisplayPackets packets = ItemDisplayPackets.builder()
                .item(stickItem)
                .translation(translation.x, translation.y, translation.z)
                .scale(1.0f, 1.0f, 1.0f)
                .leftRotation(rotation)
                .itemTransform((byte) 8);
        if (durationTicks > 0) {
            packets.interpolation(durationTicks, 0);
        }
        return packets.meta(entityId);
    }

    Object stoneMeta(int entityId, float baseYawRad, float angleY, int durationTicks) {
        Item stoneItem = InventoryUtils.createOrEmpty(behavior.stoneItem);
        Vector3f translation = MillstoneAnimation.stoneTranslation(baseYawRad, angleY);
        Quaternionf rotation = MillstoneAnimation.stoneRotation(baseYawRad, angleY);

        ItemDisplayPackets packets = ItemDisplayPackets.builder()
                .item(stoneItem)
                .translation(translation.x, translation.y, translation.z)
                .leftRotation(rotation)
                .scale(1f, 1f, 1f)
                .itemTransform((byte) 8);
        if (durationTicks > 0) {
            packets.interpolation(durationTicks, 0);
        }
        return packets.meta(entityId);
    }

    Object grindStaticMeta(int entityId, Item item) {
        return ItemDisplayPackets.builder()
                .item(item)
                .scale(0.65f)
                .itemTransform((byte) 8)
                .meta(entityId);
    }

    Object grindRotMeta(int entityId, int slot) {
        Vector3f translation = MillstoneAnimation.grindTranslation(slot);
        Quaternionf rotation = MillstoneAnimation.grindRotation(slot);
        return ItemDisplayPackets.builder()
                .translation(translation.x, translation.y, translation.z)
                .leftRotation(rotation)
                .meta(entityId);
    }
}