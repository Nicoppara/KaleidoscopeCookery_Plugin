package net.kaleidoscope.cookery.entity.furniture.element;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.momirealms.craftengine.bukkit.entity.data.DisplayData;
import net.momirealms.craftengine.bukkit.util.ComponentUtils;
import net.momirealms.craftengine.core.entity.display.Billboard;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.util.MiscUtils;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundAddEntityPacketProxy;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundBundlePacketProxy;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacketProxy;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundSetEntityDataPacketProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.EntityProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.EntityTypeProxy;
import net.momirealms.craftengine.proxy.minecraft.world.phys.Vec3Proxy;
import net.momirealms.craftengine.bukkit.util.EntityUtils;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 留言文本渲染元素：用一个 TextDisplay 悬浮展示留言，按最长行宽度做居中偏移补偿
public class MessageBoardCommentElement {
    private double x, y, z;
    private final String text;
    private float yaw;
    private final int entityId;
    private final UUID uuid;
    private final Object despawnPacket;
    private Object spawnPacket;
    private Object dataPacket;
    private Object updatePosPacket;

    public MessageBoardCommentElement(double x, double y, double z, String text, float yaw) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.text = text;
        this.yaw = yaw;
        this.entityId = EntityProxy.ENTITY_COUNTER.incrementAndGet();
        this.uuid = UUID.randomUUID();
        this.despawnPacket = ClientboundRemoveEntitiesPacketProxy.INSTANCE.newInstance(
                MiscUtils.init(new IntArrayList(), a -> a.add(entityId))
        );
        refreshPackets();
    }

    private void refreshPackets() {
        this.spawnPacket = ClientboundAddEntityPacketProxy.INSTANCE.newInstance(
                entityId, uuid, x, y, z, 0, yaw,
                EntityTypeProxy.TEXT_DISPLAY, 0, Vec3Proxy.ZERO, 0
        );
        this.updatePosPacket = EntityUtils.createUpdatePosPacket(
                entityId, x, y, z, yaw, 0, true
        );

        List<Object> dataValues = new ArrayList<>();
        Component component = Component.text(text);
        DisplayData.TextDisplayData.Text.addEntityData(
                ComponentUtils.adventureToMinecraft(component), dataValues
        );
        DisplayData.TextDisplayData.Scale.addEntityData(new Vector3f(1f, 1f, 1f), dataValues);
        DisplayData.TextDisplayData.BillboardConstraints.addEntityData(Billboard.VERTICAL.id(), dataValues);
        DisplayData.TextDisplayData.BackgroundColor.addEntityData(0x40000000, dataValues);
        DisplayData.TextDisplayData.LineWidth.addEntityData(1000, dataValues);
        DisplayData.TextDisplayData.Flags.addEntityData((byte) 0x08, dataValues);

        // 找到最长行并计算宽度
        String[] lines = text.split("\n");
        String longestLine = "";
        int maxLineUnits = 0;
        for (String line : lines) {
            int lineUnits = calculateCharUnits(line);
            if (lineUnits > maxLineUnits) {
                maxLineUnits = lineUnits;
                longestLine = line;
            }
        }

        // 最长行较长时向右偏移半个宽度做居中补偿
        if (maxLineUnits >= 10) {
            float translationX = calculateTextWidth(longestLine) * 0.5f;
            DisplayData.TextDisplayData.Translation.addEntityData(
                    new Vector3f(translationX, 0f, 0f), dataValues
            );
        }

        this.dataPacket = ClientboundSetEntityDataPacketProxy.INSTANCE.newInstance(entityId, dataValues);
    }

    // 字符单位数：CJK 计 2，ASCII 计 1
    private int calculateCharUnits(String text) {
        int units = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c > 127) {
                units += 2;
            } else {
                units += 1;
            }
        }
        return units;
    }

    // 文本宽度估算（只用于最长行）
    private float calculateTextWidth(String text) {
        float width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c > 127) {
                width += 0.15f;
            } else if (c >= '0' && c <= '9') {
                width += 0.06f;
            } else {
                width += 0.075f;
            }
        }
        return width;
    }

    public void updatePosition(Player player, double newX, double newY, double newZ, float newYaw) {
        this.x = newX;
        this.y = newY;
        this.z = newZ;
        this.yaw = newYaw;
        this.updatePosPacket = EntityUtils.createUpdatePosPacket(entityId, x, y, z, yaw, 0, true);
        player.sendPacket(updatePosPacket, false);
    }

    public void showInternal(Player player) {
        player.sendPacket(ClientboundBundlePacketProxy.INSTANCE.newInstance(List.of(
                spawnPacket, dataPacket
        )), false);
    }

    public void hide(Player player) {
        player.sendPacket(despawnPacket, false);
    }

    public void activate() {
    }

    public void deactivate() {
    }
}
