package net.kaleidoscope.cookery.block.entity;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.momirealms.craftengine.bukkit.entity.data.DisplayData;
import net.momirealms.craftengine.bukkit.util.ComponentUtils;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.entity.display.Billboard;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.MiscUtils;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundAddEntityPacketProxy;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacketProxy;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundSetEntityDataPacketProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.EntityProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.EntityTypeProxy;
import net.momirealms.craftengine.proxy.minecraft.world.phys.Vec3Proxy;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplayPackets;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplaySet;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.util.InventoryUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TeapotElement implements BlockEntityElement {
    private static final double BASE_Y_OFFSET = 0.5;
    private static final double TEXT_Y_OFFSET = 1.0;
    private static final int BODY_SLOT = 0;
    private static final int LID_SLOT = 1;

    private final TeapotController controller;
    private final ItemDisplaySet display = new ItemDisplaySet(2);
    private boolean built;

    private final int textId = EntityProxy.ENTITY_COUNTER.incrementAndGet();
    private final UUID textUuid = UUID.randomUUID();
    private final Object textSpawn;
    private final Object textRemove;
    private Object textData;
    private boolean textVisible;

    public TeapotElement(@NotNull TeapotController controller) {
        this.controller = controller;
        this.textSpawn = ClientboundAddEntityPacketProxy.INSTANCE.newInstance(
                textId, textUuid,
                controller.getPos().x() + 0.5, controller.getPos().y() + TEXT_Y_OFFSET, controller.getPos().z() + 0.5,
                0, 0, EntityTypeProxy.TEXT_DISPLAY, 0, Vec3Proxy.ZERO, 0);
        this.textRemove = ClientboundRemoveEntitiesPacketProxy.INSTANCE.newInstance(
                MiscUtils.init(new IntArrayList(), a -> a.add(textId)));
        buildTextData(Component.empty());
    }

    private WorldPosition basePos() {
        return new WorldPosition(controller.getWorld().world(),
                controller.getPos().x() + 0.5,
                controller.getPos().y() + BASE_Y_OFFSET,
                controller.getPos().z() + 0.5);
    }

    private float yawRad() {
        return (float) Math.toRadians(controller.facingYaw());
    }

    private void refreshPart(Key key, int slot, WorldPosition pos) {
        Item item = InventoryUtils.createOrEmpty(key);
        if (ItemUtils.isEmpty(item)) {
            display.clear(slot);
            return;
        }
        ItemDisplayPackets packets = ItemDisplayPackets.at(pos)
                .item(item)
                .scale(1.0f)
                .itemTransform((byte) 0)
                .leftRotation(new Quaternionf().rotateY(yawRad()));
        display.setPackets(slot, packets.spawn(display.id(slot), display.uuid(slot)), packets.meta(display.id(slot)));
    }

    public void rebuildPackets() {
        WorldPosition pos = basePos();
        refreshPart(ItemKeys.TEAPOT_BODY, BODY_SLOT, pos);
        refreshPart(ItemKeys.TEAPOT_LID, LID_SLOT, pos);
        built = true;
    }

    public Object lidBounceMeta(float posYModel, int durationTicks) {
        return bounceMeta(LID_SLOT, posYModel, durationTicks);
    }

    public Object bodyBounceMeta(float posYModel, int durationTicks) {
        return bounceMeta(BODY_SLOT, posYModel, durationTicks);
    }

    private Object bounceMeta(int slot, float posYModel, int durationTicks) {
        return ItemDisplayPackets.builder()
                .translation(0f, posYModel / 16f, 0f)
                .leftRotation(new Quaternionf().rotateY(yawRad()))
                .interpolation(durationTicks, 0)
                .meta(display.id(slot));
    }

    public Object setText(Component component, boolean visible) {
        this.textVisible = visible;
        buildTextData(component);
        return textData;
    }

    public Object textRemovePacket() {
        return textRemove;
    }

    public Object textSpawnPacket() {
        return textSpawn;
    }

    public Object textDataPacket() {
        return textData;
    }

    private void buildTextData(Component component) {
        List<Object> dv = new ArrayList<>();
        DisplayData.TextDisplayData.Text.addEntityData(ComponentUtils.adventureToMinecraft(component), dv);
        DisplayData.TextDisplayData.Scale.addEntityData(new Vector3f(0.7f, 0.7f, 0.7f), dv);
        DisplayData.TextDisplayData.BillboardConstraints.addEntityData(Billboard.CENTER.id(), dv);
        DisplayData.TextDisplayData.BackgroundColor.addEntityData(0, dv);
        DisplayData.TextDisplayData.LineWidth.addEntityData(1000, dv);
        DisplayData.TextDisplayData.Flags.addEntityData((byte) 0x00, dv);
        textData = ClientboundSetEntityDataPacketProxy.INSTANCE.newInstance(textId, dv);
    }

    @Override
    public void show(@NotNull Player player) {
        if (!built) {
            rebuildPackets();
        }
        display.showSlot(player, BODY_SLOT);
        display.showSlot(player, LID_SLOT);
        if (textVisible) {
            player.sendPacket(textSpawn, false);
            player.sendPacket(textData, false);
        }
    }

    @Override
    public void hide(@NotNull Player player) {
        display.removeAll(player);
        player.sendPacket(textRemove, false);
    }

    @Override
    public void update(@NotNull Player player) {

    }
}
