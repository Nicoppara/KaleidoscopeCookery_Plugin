package net.kaleidoscope.cookery.block.entity.render;

import net.momirealms.craftengine.bukkit.entity.data.DisplayData;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundAddEntityPacketProxy;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundSetEntityDataPacketProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.EntityTypeProxy;
import net.momirealms.craftengine.proxy.minecraft.world.phys.Vec3Proxy;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// ItemDisplay 假实体发包构建器：链式设置数据后调用 spawn/meta；只更新数据时用 builder()
public final class ItemDisplayPackets {
    private final List<Object> data = new ArrayList<>();
    private double baseX, baseY, baseZ;

    private ItemDisplayPackets() {}

    // 仅构建元数据（不带生成位置），适合只更新已存在实体的场景
    public static ItemDisplayPackets builder() {
        return new ItemDisplayPackets();
    }

    // 带生成位置，适合需要 spawn 包的场景
    public static ItemDisplayPackets at(WorldPosition pos) {
        ItemDisplayPackets b = new ItemDisplayPackets();
        b.baseX = pos.x;
        b.baseY = pos.y;
        b.baseZ = pos.z;
        return b;
    }

    public ItemDisplayPackets item(Item item) {
        DisplayData.ItemDisplayData.ItemStack.addEntityData(item.minecraftItem(), data);
        return this;
    }

    public ItemDisplayPackets scale(float uniform) {
        return scale(uniform, uniform, uniform);
    }

    public ItemDisplayPackets scale(float x, float y, float z) {
        DisplayData.ItemDisplayData.Scale.addEntityData(new Vector3f(x, y, z), data);
        return this;
    }

    public ItemDisplayPackets translation(float x, float y, float z) {
        DisplayData.ItemDisplayData.Translation.addEntityData(new Vector3f(x, y, z), data);
        return this;
    }

    public ItemDisplayPackets rotation(float yawRad) {
        return leftRotation(new Quaternionf().rotateY(yawRad).rotateX((float) Math.toRadians(-90)));
    }

    public ItemDisplayPackets leftRotation(Quaternionf quaternion) {
        DisplayData.ItemDisplayData.LeftRotation.addEntityData(quaternion, data);
        return this;
    }

    public ItemDisplayPackets itemTransform(byte mode) {
        DisplayData.ItemDisplayData.ItemTransform.addEntityData(mode, data);
        return this;
    }

    public ItemDisplayPackets brightness(int packed) {
        DisplayData.ItemDisplayData.BrightnessOverride.addEntityData(packed, data);
        return this;
    }

    public ItemDisplayPackets interpolation(int duration, int delay) {
        DisplayData.ItemDisplayData.TransformationInterpolationDuration.addEntityData(duration, data);
        DisplayData.ItemDisplayData.TransformationInterpolationDelay.addEntityData(delay, data);
        return this;
    }

    public List<Object> data() {
        return data;
    }

    public Object meta(int entityId) {
        return ClientboundSetEntityDataPacketProxy.INSTANCE.newInstance(entityId, data);
    }

    public Object spawn(int entityId, UUID uuid) {
        return ClientboundAddEntityPacketProxy.INSTANCE.newInstance(
                entityId, uuid, baseX, baseY, baseZ, 0, 0, EntityTypeProxy.ITEM_DISPLAY, 0, Vec3Proxy.ZERO, 0);
    }
}
