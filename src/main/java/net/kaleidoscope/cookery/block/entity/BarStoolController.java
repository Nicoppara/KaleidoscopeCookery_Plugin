package net.kaleidoscope.cookery.block.entity;
import net.kaleidoscope.cookery.block.behavior.BarStoolBehavior;
import net.kaleidoscope.cookery.block.entity.render.BarStoolElement;

import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.block.entity.tick.BlockEntityTicker;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BarStoolController extends BlockEntityController {
    private static final String CHAIR_TAG = "nico_stool_sit";

    // TODO: 座椅高度硬编码，若需多种高度座椅在此调整
    private static final double SEAT_Y_OFFSET = -0.15;

    private final BarStoolElement element;
    private ArmorStand sitEntity = null;
    private float lastYaw;
    private boolean needsRecoveryCheck = false;
    private boolean pendingPassenger = false;

    public BarStoolController(BlockEntity blockEntity, BarStoolBehavior behavior) {
        super(blockEntity);
        Direction facing = behavior.getFacingProperty() != null
                ? blockEntity.blockState.get(behavior.getFacingProperty())
                : Direction.NORTH;
        this.lastYaw = facingToYaw(facing);
        this.element = new BarStoolElement(this, new WorldPosition(
                null,
                (float) super.blockEntity.pos.x() + 0.5f,
                (float) super.blockEntity.pos.y(),
                (float) super.blockEntity.pos.z() + 0.5f
        ));
    }

    @Override
    public <C extends BlockEntityController> BlockEntityTicker<C> createBlockEntityTicker(CEWorld world, ImmutableBlockState blockState) {
        return createTickerHelper((w, pos, state, controller) -> this.tick());
    }

    public void tick() {
        if (needsRecoveryCheck) {
            needsRecoveryCheck = false;
            // 加载后清理残留坐骑：扫描座位处旧 ArmorStand 并移除
            Location checkLoc = new Location(
                    (World) super.blockEntity.world.world().platformWorld(),
                    super.blockEntity.pos.x() + 0.5,
                    super.blockEntity.pos.y() + SEAT_Y_OFFSET,
                    super.blockEntity.pos.z() + 0.5
            );
            for (Entity nearby : checkLoc.getWorld().getNearbyEntities(checkLoc, 0.1, 0.5, 0.1)) {
                if (nearby instanceof ArmorStand && nearby.getScoreboardTags().contains(CHAIR_TAG)) {
                    nearby.remove();
                }
            }
        }

        if (sitEntity != null) {
            if (sitEntity.isValid() && !sitEntity.getPassengers().isEmpty()) {
                Entity passenger = sitEntity.getPassengers().get(0);
                float currentYaw = passenger.getLocation().getYaw();

                float delta = currentYaw - lastYaw;
                while (delta <= -180) delta += 360;
                while (delta > 180) delta -= 360;
                float absDelta = Math.abs(delta);

                if (absDelta > 0.2f) {
                    lastYaw = currentYaw;
                    sitEntity.setRotation(currentYaw, sitEntity.getLocation().getPitch());
                    refreshDynamicElement((element, p) -> element.updateSeatYaw(p, currentYaw, absDelta));
                }
            } else {
                // 玩家离座且不在等待落座 → 回收坐骑
                if (!pendingPassenger) {
                    sitEntity.remove();
                    sitEntity = null;
                }
            }
        }
    }

    public void sit(Player cePlayer) {
        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) cePlayer.platformPlayer();
        float currentPitch = bukkitPlayer.getLocation().getPitch();
        Location loc = new Location(
                (World) super.blockEntity.world.world().platformWorld(),
                super.blockEntity.pos.x() + 0.5,
                super.blockEntity.pos.y() + SEAT_Y_OFFSET,
                super.blockEntity.pos.z() + 0.5,
                lastYaw, 0f
        );

        for (Entity nearby : loc.getWorld().getNearbyEntities(loc, 0.2, 0.5, 0.2)) {
            if (nearby.getScoreboardTags().contains(CHAIR_TAG)) nearby.remove();
        }

        sitEntity = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setSmall(true);
            as.setGravity(false);
            as.setPersistent(false);
            as.setCanTick(false);
            as.setBasePlate(false);
            as.setInvulnerable(true);
            as.setAI(false);
            as.addScoreboardTag(CHAIR_TAG);
            as.setRotation(lastYaw, 0f);
            Objects.requireNonNull(as.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(0.01);
        });

        bukkitPlayer.setRotation(lastYaw, currentPitch);

        // 下一 tick 再上座，避开生成同帧的乘客占用竞态
        pendingPassenger = true;
        ArmorStand pendingSeat = sitEntity;
        BukkitCraftEngine.instance().scheduler().platform().runDelayed(() -> {
            pendingPassenger = false;
            if (pendingSeat != null && pendingSeat.isValid() && pendingSeat.getPassengers().isEmpty()) {
                pendingSeat.addPassenger(bukkitPlayer);
            }
        }, null, bukkitPlayer);
    }

    public void refreshDynamicElement(BiConsumer<BarStoolElement, Player> consumer) {
        TrackedPlayers.forEach(super.blockEntity, player -> consumer.accept(this.element, player));
    }

    private float facingToYaw(Direction facing) {
        return switch (facing) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> -90f;
            default -> 0f;
        };
    }

    // TODO: 领地权限校验（破坏）—— onRemove 无玩家上下文，破坏权限需在破坏入口处校验
    @Override
    public void onRemove() {
        if (sitEntity != null && sitEntity.isValid()) {
            sitEntity.remove();
        }
        Location loc = new Location(
                (World) super.blockEntity.world.world().platformWorld(),
                super.blockEntity.pos.x() + 0.5,
                super.blockEntity.pos.y() + SEAT_Y_OFFSET,
                super.blockEntity.pos.z() + 0.5
        );
        for (Entity nearby : loc.getWorld().getNearbyEntities(loc, 0.5, 1.0, 0.5)) {
            if (nearby.getScoreboardTags().contains(CHAIR_TAG)) nearby.remove();
        }
        super.onRemove();
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        tag.putFloat("last_yaw", this.lastYaw);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        if (tag.containsKey("last_yaw")) {
            this.lastYaw = tag.getFloat("last_yaw");
            this.element.refreshPackets();
        }
        this.needsRecoveryCheck = true;
    }

    public ArmorStand getSitEntity() {
        return sitEntity;
    }

    @Override
    public boolean hasElement() {
        return true;
    }

    @Override
    public void gatherElements(Consumer<BlockEntityElement> consumer) {
        consumer.accept(this.element);
    }

    public float getLastYaw() {
        return lastYaw;
    }
}
