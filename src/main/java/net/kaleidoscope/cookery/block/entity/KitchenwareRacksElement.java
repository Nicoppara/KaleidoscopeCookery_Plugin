package net.kaleidoscope.cookery.block.entity;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.MiscUtils;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacketProxy;
import net.momirealms.craftengine.bukkit.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplayPackets;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class KitchenwareRacksElement implements BlockEntityElement {
    private final KitchenwareRacksController controller;

    public final int leftItemId;
    public final int rightItemId;
    public final UUID leftItemUUID = UUID.randomUUID();
    public final UUID rightItemUUID = UUID.randomUUID();
    public final Object despawnLeftPacket;
    public final Object despawnRightPacket;
    public final Object despawnAllPacket;
    private Object spawnLeftPacket;
    private Object spawnRightPacket;
    private Object changeLeftItemPacket;
    private Object changeRightItemPacket;
    private boolean leftSpawned;
    private boolean rightSpawned;

    public KitchenwareRacksElement(@NotNull KitchenwareRacksController controller,
                                   @Nullable WorldPosition leftPosition,
                                   @Nullable WorldPosition rightPosition) {
        this.controller = controller;
        this.leftItemId = EntityUtils.ENTITY_COUNTER.incrementAndGet();
        this.rightItemId = EntityUtils.ENTITY_COUNTER.incrementAndGet();

        this.despawnLeftPacket = ClientboundRemoveEntitiesPacketProxy.INSTANCE.newInstance(
                MiscUtils.init(new IntArrayList(), a -> a.add(leftItemId)));
        this.despawnRightPacket = ClientboundRemoveEntitiesPacketProxy.INSTANCE.newInstance(
                MiscUtils.init(new IntArrayList(), a -> a.add(rightItemId)));
        this.despawnAllPacket = ClientboundRemoveEntitiesPacketProxy.INSTANCE.newInstance(
                MiscUtils.init(new IntArrayList(), a -> {
                    a.add(leftItemId);
                    a.add(rightItemId);
                }));

        if (leftPosition != null && rightPosition != null) {
            this.refreshSpawnPackets(leftPosition, rightPosition);
        }
    }

    public void refreshLeftItem(Item item) {
        this.changeLeftItemPacket = ItemDisplayPackets.builder()
                .item(item)
                .scale(0.75f)
                .leftRotation(new Quaternionf().rotateY((float) Math.toRadians(25)).rotateX((float) Math.toRadians(-180)).rotateZ((float) Math.toRadians(45)))
                .itemTransform((byte) 8)
                .meta(leftItemId);
    }

    public void refreshRightItem(Item item) {
        this.changeRightItemPacket = ItemDisplayPackets.builder()
                .item(item)
                .scale(0.75f)
                .leftRotation(new Quaternionf().rotateY((float) Math.toRadians(25)).rotateX((float) Math.toRadians(-180)).rotateZ((float) Math.toRadians(45)))
                .itemTransform((byte) 8)
                .meta(rightItemId);
    }

    public void refreshSpawnPackets(WorldPosition leftPosition, WorldPosition rightPosition) {
        this.spawnLeftPacket = ItemDisplayPackets.at(leftPosition).spawn(leftItemId, leftItemUUID);
        this.spawnRightPacket = ItemDisplayPackets.at(rightPosition).spawn(rightItemId, rightItemUUID);
    }

    public void refreshPositions(WorldPosition leftPosition, WorldPosition rightPosition) {
        this.refreshSpawnPackets(leftPosition, rightPosition);
    }

    @Override
    public void show(@NotNull Player player) {
        controller.ensurePositionsInitialized();
        List<Object> packets = new ArrayList<>();
        if (!controller.getItemLeft().isEmpty()) {
            packets.add(spawnLeftPacket);
            packets.add(changeLeftItemPacket);
            leftSpawned = true;
        }
        if (!controller.getItemRight().isEmpty()) {
            packets.add(spawnRightPacket);
            packets.add(changeRightItemPacket);
            rightSpawned = true;
        }
        if (!packets.isEmpty()) {
            player.sendPackets(packets, false);
        }
    }

    @Override
    public void hide(@NotNull Player player) {
        player.sendPacket(despawnAllPacket, false);
        leftSpawned = false;
        rightSpawned = false;
    }

    @Override
    public void update(@NotNull Player player) {
        boolean leftChanged = controller.hasLeftChanged();
        boolean rightChanged = controller.hasRightChanged();

        if (!leftChanged && !rightChanged) {
            return;
        }

        List<Object> packets = new ArrayList<>();

        // 按槽差异分派 空变非空才发 spawn 内容变化只发 meta 重发 spawn 会让客户端重建实体丢插值
        if (leftChanged) {
            if (!controller.getItemLeft().isEmpty()) {
                if (!leftSpawned) {
                    packets.add(spawnLeftPacket);
                    leftSpawned = true;
                }
                packets.add(changeLeftItemPacket);
            } else if (leftSpawned) {
                packets.add(despawnLeftPacket);
                leftSpawned = false;
            }
        }

        if (rightChanged) {
            if (!controller.getItemRight().isEmpty()) {
                if (!rightSpawned) {
                    packets.add(spawnRightPacket);
                    rightSpawned = true;
                }
                packets.add(changeRightItemPacket);
            } else if (rightSpawned) {
                packets.add(despawnRightPacket);
                rightSpawned = false;
            }
        }

        if (!packets.isEmpty()) {
            player.sendPackets(packets, false);
        }
    }

    // 取下后由 refreshElement/update 统一补发移除包 此处无需操作
    public void hideLeft() {
    }

    public void hideRight() {
    }
}