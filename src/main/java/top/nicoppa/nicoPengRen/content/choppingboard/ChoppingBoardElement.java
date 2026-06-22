package top.nicoppa.nicoPengRen.content.choppingboard;

import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.WorldPosition;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import top.nicoppa.nicoPengRen.common.render.ItemDisplayPackets;
import top.nicoppa.nicoPengRen.common.render.ItemDisplaySet;

/**
 * 砧板渲染元素 显示当前切割阶段的模型
 * 数据来源 {@link ChoppingBoardController}，交互入口见 {@link ChoppingBoardBehavior}
 */
public final class ChoppingBoardElement implements BlockEntityElement {

    private final ChoppingBoardController controller;
    private final WorldPosition basePos;
    private final ItemDisplaySet display = new ItemDisplaySet(1);

    private boolean lastVisible = false;
    private boolean currentVisible = false;
    private String currentModel = null;

    public ChoppingBoardElement(@NotNull ChoppingBoardController controller, @NotNull WorldPosition position) {
        this.controller = controller;
        this.basePos = position;
        refreshPackets();
    }

    @Override
    public void activate() {
        refreshPackets();
    }

    public void refreshPackets() {
        String model = controller.currentStageModel();
        this.currentModel = model;
        if (model == null) {
            display.clear(0);
            this.currentVisible = false;
            return;
        }
        Item item = BukkitItemManager.instance().createWrappedItem(Key.of(model), null);
        if (item == null) { display.clear(0); this.currentVisible = false; return; }

        ItemDisplayPackets packets = ItemDisplayPackets.at(basePos)
                .item(item)
                .scale(1.0f)
                .itemTransform((byte) 0)
                .leftRotation(new Quaternionf().rotateY(controller.facingYawRadians()));
        display.setPackets(0, packets.spawn(display.id(0), display.uuid(0)), packets.meta(display.id(0)));
        this.currentVisible = true;
    }

    public void prepareUpdate() {
        this.lastVisible = this.currentVisible;
        refreshPackets();
    }

    @Override
    public void update(@NotNull Player player) {
        if (currentVisible && !lastVisible) {
            display.showSlot(player, 0);
        } else if (!currentVisible && lastVisible) {
            display.removeSlot(player, 0);
        } else if (currentVisible) {
            Object meta = display.meta(0);
            if (meta != null) player.sendPacket(meta, false);
        }
    }

    @Override
    public void show(@NotNull Player player) {
        if (currentVisible) display.showSlot(player, 0);
    }

    @Override
    public void hide(@NotNull Player player) {
        display.removeAll(player);
    }
}
