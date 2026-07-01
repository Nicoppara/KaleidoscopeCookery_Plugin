package net.kaleidoscope.cookery.block.behavior;
import net.kaleidoscope.cookery.block.entity.KitchenwareRacksController;

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
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.jetbrains.annotations.Nullable;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.kaleidoscope.cookery.util.InventoryUtils;

public final class KitchenwareRacksBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<KitchenwareRacksBehavior> FACTORY = new Factory();

    // 挂放/取下音效 硬编码 无需配置
    private static final Key PUT_SOUND = Key.of("minecraft:entity.item_frame.add_item");
    private static final Key TAKE_SOUND = Key.of("minecraft:entity.item_frame.remove_item");
    private static final float SOUND_VOLUME = 1.0f;
    private static final float SOUND_PITCH = 1.0f;

    @Nullable
    private final Property<Direction> facingProperty;
    private int controllerId;

    private KitchenwareRacksBehavior(BlockDefinition blockDefinition,
                                     @Nullable Property<Direction> facingProperty) {
        super(blockDefinition);
        this.facingProperty = facingProperty;
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
        if (player == null) {
            return InteractionResult.PASS;
        }

        World world = context.getLevel();
        CEWorld ceWorld = world.storageWorld();
        BlockPos pos = context.getClickedPos();

        BlockEntity blockEntity = ceWorld.getBlockEntityAtIfLoaded(pos);
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }

        if (!InteractGuard.canInteract(player, world, pos)) {
            return InteractionResult.PASS;
        }

        // 挂放取下只认主手 副手触发直接放行
        if (context.getHand() == InteractionHand.OFF_HAND) {
            return InteractionResult.PASS;
        }
        InteractionHand hand = InteractionHand.MAIN_HAND;
        Item itemInHand = player.getItemInHand(hand);

        return blockEntity.controller.let(KitchenwareRacksController.class, this.controllerId, controller -> {
            boolean isLeftClick = isLeftSideClick(context, state);
            Item itemInSlot = isLeftClick ? controller.getItemLeft() : controller.getItemRight();

            // 空手点击非空槽 取下厨具
            if (itemInHand.isEmpty() && !itemInSlot.isEmpty()) {
                return handleTake(player, hand, world, pos, controller, isLeftClick);
            }
            // 持厨具点击空槽 挂放
            if (!itemInHand.isEmpty() && itemInSlot.isEmpty() && isTool(itemInHand)) {
                return handlePut(player, hand, world, pos, controller, isLeftClick, itemInHand);
            }
            return InteractionResult.PASS;
        });
    }

    private InteractionResult handleTake(Player player, InteractionHand hand, World world, BlockPos pos,
                                         KitchenwareRacksController controller, boolean isLeftClick) {
        Item takenItem = isLeftClick ? controller.takeLeft() : controller.takeRight();
        InventoryUtils.giveOrHold(player, hand, takenItem);
        player.swingHand(hand);
        world.playBlockSound(Vec3d.atCenterOf(pos), TAKE_SOUND, SOUND_VOLUME, SOUND_PITCH);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    private InteractionResult handlePut(Player player, InteractionHand hand, World world, BlockPos pos,
                                        KitchenwareRacksController controller, boolean isLeftClick, Item itemInHand) {
        Item toPut = itemInHand.copyWithCount(1);
        InventoryUtils.shrinkHeld(player, itemInHand, 1);
        if (isLeftClick) {
            controller.putLeft(toPut);
        } else {
            controller.putRight(toPut);
        }
        player.swingHand(hand);
        world.playBlockSound(Vec3d.atCenterOf(pos), PUT_SOUND, SOUND_VOLUME, SOUND_PITCH);
        return InteractionResult.SUCCESS_AND_CANCEL;
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

    // 按物品 id 后缀判定原版工具 用 endsWith 避免 waxed 等含子串的 id 误命中
    private boolean isTool(Item item) {
        String itemId = item.vanillaId().asString();
        return itemId.endsWith("sword") || itemId.endsWith("axe")
                || itemId.endsWith("shovel") || itemId.endsWith("hoe")
                || itemId.endsWith("shears");
    }

    private static class Factory implements BlockBehaviorFactory<KitchenwareRacksBehavior> {
        @Override
        public KitchenwareRacksBehavior create(BlockDefinition block, ConfigSection section) {
            Property<Direction> facingProperty = BlockBehaviorFactory.getOptionalProperty(block, "facing", Direction.class);
            return new KitchenwareRacksBehavior(block, facingProperty);
        }
    }
}
