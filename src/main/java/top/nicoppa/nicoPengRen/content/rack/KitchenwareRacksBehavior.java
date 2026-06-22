package top.nicoppa.nicoPengRen.content.rack;

import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.EntityBlock;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.sound.SoundData;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.jetbrains.annotations.Nullable;
import top.nicoppa.nicoPengRen.common.item.InventoryUtils;

/**
 * 厨具架方块行为 处理左右两槽厨具的挂放与取下交互，并据点击位置与朝向判定左右侧
 * 数据见 {@link KitchenwareRacksController}，渲染见 {@link KitchenwareRacksElement}
 */
public final class KitchenwareRacksBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<KitchenwareRacksBehavior> FACTORY = new Factory();

    private final SoundData putSound;
    private final SoundData takeSound;
    @Nullable
    private final Property<Direction> facingProperty;
    private int controllerId;
    public final String customDataKey;

    private KitchenwareRacksBehavior(BlockDefinition blockDefinition,
                                     SoundData putSound,
                                     SoundData takeSound,
                                     @Nullable Property<Direction> facingProperty,
                                     String customDataKey) {
        super(blockDefinition);
        this.putSound = putSound;
        this.takeSound = takeSound;
        this.facingProperty = facingProperty;
        this.customDataKey = customDataKey;
    }

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    public Property<Direction> getFacingProperty() {
        return this.facingProperty;
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new KitchenwareRacksController(blockEntity, this);
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        World world = context.getLevel();
        CEWorld ceWorld = world.storageWorld();
        BlockPos pos = context.getClickedPos();

        BlockEntity blockEntity = ceWorld.getBlockEntityAtIfLoaded(pos);
        if (blockEntity == null) return InteractionResult.PASS;

        InteractionHand hand = context.getHand();
        Item itemInHand = player.getItemInHand(hand);

        return blockEntity.controller.let(KitchenwareRacksController.class, this.controllerId, controller -> {
            boolean isLeftClick = isLeftSideClick(context, state);
            Item itemInSlot = isLeftClick ? controller.getItemLeft() : controller.getItemRight();

            // 空手点击非空槽 取下厨具
            if (itemInHand.isEmpty() && !itemInSlot.isEmpty()) {
                Item takenItem = isLeftClick ? controller.takeLeft() : controller.takeRight();
                InventoryUtils.giveOrHold(player, hand, takenItem);
                player.swingHand(hand);
                if (this.takeSound != null) world.playBlockSound(Vec3d.atCenterOf(pos), this.takeSound);
                return InteractionResult.SUCCESS_AND_CANCEL;
            }

            // 持厨具点击空槽 挂放
            if (!itemInHand.isEmpty() && itemInSlot.isEmpty() && isTool(itemInHand)) {
                Item toPut = itemInHand.copyWithCount(1);
                itemInHand.shrink(1);
                if (isLeftClick) {
                    controller.putLeft(toPut);
                } else {
                    controller.putRight(toPut);
                }
                player.swingHand(hand);
                if (this.putSound != null) world.playBlockSound(Vec3d.atCenterOf(pos), this.putSound);
                return InteractionResult.SUCCESS_AND_CANCEL;
            }

            return InteractionResult.PASS;
        });
    }

    private boolean isLeftSideClick(UseOnContext context, ImmutableBlockState state) {
        Direction facing = this.facingProperty != null ? state.get(this.facingProperty) : Direction.NORTH;
        Vec3d clickPos = context.getClickedLocation();
        double x = clickPos.x() - context.getClickedPos().x();
        double z = clickPos.z() - context.getClickedPos().z();

        return switch (facing) {
            case NORTH -> x < 0.5;
            case SOUTH -> x > 0.5;
            case EAST -> z > 0.5;
            case WEST -> z < 0.5;
            default -> x < 0.5;
        };
    }

    // TODO 正式版这里改成可配置tag或者白名单形式，目前应该暂时够用
    private boolean isTool(Item item) {
        String itemId = item.vanillaId().asString();
        return itemId.contains("sword") || itemId.contains("axe") ||
                itemId.contains("pickaxe") || itemId.contains("shovel") ||
                itemId.contains("hoe") || itemId.contains("shears");
    }

    private static class Factory implements BlockBehaviorFactory<KitchenwareRacksBehavior> {
        @Override
        public KitchenwareRacksBehavior create(BlockDefinition block, ConfigSection section) {
            ConfigSection soundSection = section.getSection("sounds");
            SoundData putSound = null;
            SoundData takeSound = null;
            if (soundSection != null) {
                putSound = soundSection.getValue("put", v -> SoundData.fromConfig(v, SoundData.SoundValue.FIXED_1, SoundData.SoundValue.RANGED_0_9_1));
                takeSound = soundSection.getValue("take", v -> SoundData.fromConfig(v, SoundData.SoundValue.FIXED_1, SoundData.SoundValue.RANGED_0_9_1));
            }
            Property<Direction> facingProperty = BlockBehaviorFactory.getOptionalProperty(block, "facing", Direction.class);
            String customDataKey = section.getString("data_key", "nicopengren:kitchenware_racks");

            return new KitchenwareRacksBehavior(
                    block,
                    putSound,
                    takeSound,
                    facingProperty,
                    customDataKey
            );
        }
    }
}
