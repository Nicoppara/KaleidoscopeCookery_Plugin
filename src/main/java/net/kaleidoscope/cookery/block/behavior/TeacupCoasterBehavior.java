package net.kaleidoscope.cookery.block.behavior;

import net.kaleidoscope.cookery.block.entity.TeacupCoasterController;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.util.BehaviorConfig;
import net.kaleidoscope.cookery.util.InteractGuard;
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
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.UseOnContext;

// 茶杯垫 放/取茶杯(空杯或茶) 杯垫本体由 entity_renderer 渲染 杯子由控制器假实体渲染
public final class TeacupCoasterBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<TeacupCoasterBehavior> FACTORY = new Factory();

    private int controllerId;
    private Property<Direction> facingProperty;
    public Key emptyCupModel = ItemKeys.EMPTY_CUP_MODEL;
    public Key emptyCupItem = ItemKeys.EMPTY_CUP;
    // 杯子假实体相对方块的竖直偏移与缩放 模型而定 可配置微调
    public double cupYOffset = 0.5;
    public float cupScale = 1.0f;

    private TeacupCoasterBehavior(BlockDefinition blockDefinition) {
        super(blockDefinition);
    }

    // 空手取杯 否则放杯 只认主手
    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        if (player == null || context.getHand() == InteractionHand.OFF_HAND) {
            return InteractionResult.PASS;
        }
        World world = context.getLevel();
        if (!InteractGuard.canInteract(player, world, context.getClickedPos())) {
            return InteractionResult.PASS;
        }
        BlockEntity blockEntity = world.storageWorld().getBlockEntityAtIfLoaded(context.getClickedPos());
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }
        TeacupCoasterController controller = blockEntity.controller.get(TeacupCoasterController.class, this.controllerId);
        if (controller == null) {
            return InteractionResult.PASS;
        }

        InteractionHand hand = InteractionHand.MAIN_HAND;
        Item held = player.getItemInHand(hand);
        boolean done = held.isEmpty()
                ? controller.takeCup(player, hand)
                : controller.placeCup(player, hand, held);
        if (done) {
            player.swingHand(hand);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        return InteractionResult.PASS;
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new TeacupCoasterController(blockEntity, this);
    }

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    public Property<Direction> getFacingProperty() {
        return facingProperty;
    }

    private static class Factory implements BlockBehaviorFactory<TeacupCoasterBehavior> {
        @Override
        public TeacupCoasterBehavior create(BlockDefinition block, ConfigSection section) {
            TeacupCoasterBehavior b = new TeacupCoasterBehavior(block);
            b.facingProperty = BlockBehaviorFactory.getProperty(section.path(), block, "facing", Direction.class);
            b.emptyCupModel = Key.of(BehaviorConfig.getString(section, b.emptyCupModel.asString(), "empty_cup_display_model", "empty-cup-display-model"));
            b.emptyCupItem = Key.of(BehaviorConfig.getString(section, b.emptyCupItem.asString(), "empty_cup_item", "empty-cup-item"));
            b.cupYOffset = BehaviorConfig.getDouble(section, b.cupYOffset, "cup_y_offset", "cup-y-offset");
            b.cupScale = (float) BehaviorConfig.getDouble(section, b.cupScale, "cup_scale", "cup-scale");
            return b;
        }
    }
}
