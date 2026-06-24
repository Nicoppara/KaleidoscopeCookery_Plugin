package net.kaleidoscope.cookery.block.entity.render;
import net.kaleidoscope.cookery.block.entity.BarStoolController;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.MiscUtils;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacketProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.EntityProxy;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplayPackets;

import java.util.List;
import java.util.UUID;

public final class BarStoolElement implements BlockEntityElement {

    private final BarStoolController controller;
    private final WorldPosition basePos;

    private final int baseId, seatId;
    private final UUID baseUuid, seatUuid;

    private Object baseSpawnPacket, baseMetaPacket;
    private Object seatSpawnPacket, seatMetaPacket;

    public BarStoolElement(@NotNull BarStoolController controller, @NotNull WorldPosition position) {
        this.controller = controller;
        this.basePos = position;
        this.baseId = EntityProxy.ENTITY_COUNTER.incrementAndGet();
        this.seatId = EntityProxy.ENTITY_COUNTER.incrementAndGet();
        this.baseUuid = UUID.randomUUID();
        this.seatUuid = UUID.randomUUID();
        refreshPackets();
    }

    public void refreshPackets() {
        // TODO: 座椅外观写死，需在行为配置中暴露
        Item baseItem = BukkitItemManager.instance().createWrappedItem(Key.of("show:bar_stool_stand_black"), null);
        Item seatItem = BukkitItemManager.instance().createWrappedItem(Key.of("show:bar_stool_sofa_black"), null);

        // 读取控制器中的最新偏航角：刚放下为初始朝向，被转过则为转过后的朝向
        float currentYaw = controller.getLastYaw();
        float radYaw = (float) Math.toRadians(-currentYaw);

        float pitchRad = (float) Math.toRadians(0);
        Vector3f translation = new Vector3f(0f, 0.5f, 0f);

        // 底座（朝向固定，但仍随 yaw 一起旋转以与椅面对齐）
        ItemDisplayPackets basePackets = ItemDisplayPackets.at(basePos)
                .item(baseItem)
                .scale(1.0f, 1.0f, 1.0f)
                .translation(translation.x, translation.y, translation.z)
                .leftRotation(new Quaternionf().rotateY(radYaw).rotateX(pitchRad))
                .itemTransform((byte) 0);
        this.baseSpawnPacket = basePackets.spawn(baseId, baseUuid);
        this.baseMetaPacket = basePackets.meta(baseId);

        // 椅面（与当前朝向保持一致）
        ItemDisplayPackets seatPackets = ItemDisplayPackets.at(basePos)
                .item(seatItem)
                .scale(1.0f, 1.0f, 1.0f)
                .translation(translation.x, translation.y, translation.z)
                .leftRotation(new Quaternionf().rotateY(radYaw).rotateX(pitchRad))
                .itemTransform((byte) 0);
        this.seatSpawnPacket = seatPackets.spawn(seatId, seatUuid);
        this.seatMetaPacket = seatPackets.meta(seatId);
    }

    public void updateSeatYaw(Player player, float yaw, float delta) {
        float radYaw = (float) Math.toRadians(-yaw);
        float pitchRad = (float) Math.toRadians(0);

        // 转头幅度超过 15 度时插值时长降到 1 tick，避免落后导致客户端反向转圈
        int duration = (delta > 15f) ? 1 : 2;

        Object metaPacket = ItemDisplayPackets.builder()
                .leftRotation(new Quaternionf().rotateY(radYaw).rotateX(pitchRad))
                .interpolation(duration, 0)
                .meta(seatId);
        player.sendPacket(metaPacket, false);
    }

    @Override
    public void show(@NotNull Player player) {
        player.sendPackets(List.of(baseSpawnPacket, baseMetaPacket, seatSpawnPacket, seatMetaPacket), false);
    }

    @Override
    public void hide(@NotNull Player player) {
        IntList ids = MiscUtils.init(new IntArrayList(), a -> {
            a.add(baseId);
            a.add(seatId);
        });
        player.sendPacket(ClientboundRemoveEntitiesPacketProxy.INSTANCE.newInstance(ids), false);
    }
}
