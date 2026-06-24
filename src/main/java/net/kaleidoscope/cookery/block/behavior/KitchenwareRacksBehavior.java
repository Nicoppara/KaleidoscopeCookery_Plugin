package net.kaleidoscope.cookery.block.behavior;
import net.kaleidoscope.cookery.block.entity.KitchenwareRacksController;
import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;

import net.momirealms.antigrieflib.Flag;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
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
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;
import net.kaleidoscope.cookery.util.InventoryUtils;

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

        // 领地权限校验
        Location agLoc = new Location((org.bukkit.World) world.platformWorld(), pos.x, pos.y, pos.z);
        if (!KaleidoscopeCookeryPlugin.antiGrief().test((org.bukkit.entity.Player) player.platformPlayer(), Flag.INTERACT, agLoc)) {
            return InteractionResult.PASS;
        }

        InteractionHand hand = context.getHand();
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
        if (this.takeSound != null) {
            world.playBlockSound(Vec3d.atCenterOf(pos), this.takeSound);
        }
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    private InteractionResult handlePut(Player player, InteractionHand hand, World world, BlockPos pos,
                                        KitchenwareRacksController controller, boolean isLeftClick, Item itemInHand) {
        Item toPut = itemInHand.copyWithCount(1);
        itemInHand.shrink(1);
        if (isLeftClick) {
            controller.putLeft(toPut);
        } else {
            controller.putRight(toPut);
        }
        player.swingHand(hand);
        if (this.putSound != null) {
            world.playBlockSound(Vec3d.atCenterOf(pos), this.putSound);
        }
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

    // TODO: 改为可配置 tag 或白名单形式
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
            String customDataKey = section.getString("data_key", "kaleidoscopecookery:kitchenware_racks");

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
