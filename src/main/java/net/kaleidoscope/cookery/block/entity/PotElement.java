package net.kaleidoscope.cookery.block.entity;

import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.scheduler.SchedulerTask;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.core.world.chunk.CEChunk;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundBundlePacketProxy;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplayPackets;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplaySet;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class PotElement implements BlockEntityElement {
    private static final int SLOTS = 8;

    private final PotController controller;
    private final WorldPosition basePos;
    private final ItemDisplaySet display = new ItemDisplaySet(SLOTS);
    private final float[] randomAngles = new float[SLOTS];
    private final List<SchedulerTask> animationTasks = new ArrayList<>();

    public PotElement(@NotNull PotController controller, @NotNull WorldPosition position) {
        this.controller = controller;
        this.basePos = position;
        refreshPackets();
    }

    @Override
    public void activate() {
        refreshPackets();
    }

    @Override
    public void deactivate() {
        cancelAnimationTasks();
    }

    @Override
    public void update(@NotNull Player player) {
    }

    @Override
    public void show(@NotNull Player player) {
        for (int i = 0; i < SLOTS; i++) display.showSlot(player, i);
    }

    @Override
    public void hide(@NotNull Player player) {
        cancelAnimationTasks();
        display.removeAll(player);
    }

    public void showIndex(Player player, int index) {
        display.showSlot(player, index);
    }

    public void hideIndex(Player player, int index) {
        if (index >= 0 && index < SLOTS) display.removeSlot(player, index);
    }

    public void hideAll(Player player) {
        display.removeAll(player);
    }

    // 烧焦时降低亮度
    public void updateBrightness(Player player, int brightness) {
        if (controller.stage() != PotStage.BURNT) return;
        List<Object> packets = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            if (display.spawn(i) != null) {
                packets.add(ItemDisplayPackets.builder().brightness(brightness).meta(display.id(i)));
            }
        }
        if (!packets.isEmpty()) player.sendPacket(ClientboundBundlePacketProxy.INSTANCE.newInstance(packets), false);
    }

    public void refreshSlotPacket(int index) {
        List<Item> items = controller.getIngredients();
        if (index >= items.size()) {
            display.clear(index);
            return;
        }

        Random random = new Random(controller.getSeed() + index);
        float stackHeight = index * 0.025f;
        float angle = (float) Math.toRadians(index * 45 + random.nextFloat() * 90);
        this.randomAngles[index] = angle;

        ItemDisplayPackets packets = ItemDisplayPackets.at(basePos)
                .item(items.get(index))
                .scale(0.5f)
                .translation(0, stackHeight, 0)
                .rotation(angle)
                .itemTransform((byte) 8);

        if (controller.stage() == PotStage.BURNT) packets.brightness(burntBrightness(controller.getCurrentTick(), controller.burntToCharcoalTime()));
        display.setPackets(index, packets.spawn(display.id(index), display.uuid(index)), packets.meta(display.id(index)));
    }

    public void refreshPackets() {
        for (int i = 0; i < SLOTS; i++) refreshSlotPacket(i);
    }

    public static int burntBrightness(int currentTick, int totalBurnTime) {
        int brightness = Math.max(0, 15 - Math.min(15, (totalBurnTime - currentTick) / 26));
        return (brightness << 4) | (brightness << 20);
    }

    public void playStirFryAnimation(Runnable onComplete) {
        CEChunk chunk = controller.blockEntity().world.getChunkAtIfLoaded(controller.blockEntity().pos.x >> 4, controller.blockEntity().pos.z >> 4);
        int ingredientCount = controller.getIngredients().size();
        if (chunk == null || ingredientCount == 0) {
            onComplete.run();
            return;
        }
        cancelAnimationTasks();

        // 动画开始时快照一次接收者 整段 8 帧复用 远处玩家不渲染动画就不发
        List<Player> recipients = TrackedPlayers.snapshotInRange(controller.blockEntity(), controller.animChunkRadius());

        Random random = new Random();
        float[] jumpH = new float[ingredientCount];
        for (int i = 0; i < ingredientCount; i++) jumpH[i] = (i * 0.025f) + (0.3f + random.nextFloat() * 0.6f);
        int stepDuration = 3;
        var platformScheduler = BukkitCraftEngine.instance().scheduler().platform();
        var world = controller.blockEntity().world.world();
        int px = controller.blockEntity().pos.x;
        int pz = controller.blockEntity().pos.z;

        for (int step = 1; step <= 8; step++) {
            final int currentStep = step;
            long delay = step == 1 ? 0 : stepDuration * (step - 1L);
            Runnable phaseRunnable = () -> {
                List<Object> packetList = new ArrayList<>();
                for (int i = 0; i < ingredientCount; i++) {
                    float stackHeight = i * 0.025f;
                    float h = getParabolaHeightForStep(currentStep, stackHeight, jumpH[i], jumpH[i] - stackHeight);
                    float xRot = currentStep == 8 ? -90 : -90 - (90 * currentStep);
                    Object metaPacket = ItemDisplayPackets.builder()
                            .translation(0, h, 0)
                            .leftRotation(new Quaternionf().rotateY(this.randomAngles[i]).rotateX((float) Math.toRadians(xRot)))
                            .interpolation(stepDuration, 0)
                            .meta(display.id(i));
                    packetList.add(metaPacket);
                }
                Object bundle = ClientboundBundlePacketProxy.INSTANCE.newInstance(packetList);
                recipients.forEach(p -> p.sendPacket(bundle, false));
            };
            if (delay == 0) phaseRunnable.run();
            else animationTasks.add(platformScheduler.runLater(phaseRunnable, delay, world, px, pz));
        }
        animationTasks.add(platformScheduler.runLater(() -> {
            onComplete.run();
            animationTasks.clear();
        }, stepDuration * 8L, world, px, pz));
    }

    private float getParabolaHeightForStep(int step, float baseH, float topH, float range) {
        return switch (step) {
            case 1, 7 -> baseH + range * 0.4375f;
            case 2, 6 -> baseH + range * 0.7500f;
            case 3, 5 -> baseH + range * 0.9375f;
            case 4 -> topH;
            default -> baseH;
        };
    }

    private void cancelAnimationTasks() {
        animationTasks.forEach(task -> {
            if (task != null && !task.cancelled()) task.cancel();
        });
        animationTasks.clear();
    }
}