package net.kaleidoscope.cookery.block.behavior;

import net.kaleidoscope.cookery.block.entity.FruitBasketController;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.bukkit.world.BukkitWorldManager;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelProxy;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.EntityBlock;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.UseOnContext;

import java.lang.reflect.Method;

public final class FruitBasketBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<FruitBasketBehavior> FACTORY = new Factory();

    private static final Key PUT_SOUND = Key.of("minecraft:entity.item_frame.add_item");
    private static final Key TAKE_SOUND = Key.of("minecraft:entity.item_frame.remove_item");

    private int controllerId;
    private Property<Direction> facingProperty;

    private FruitBasketBehavior(BlockDefinition blockDefinition) {
        super(blockDefinition);
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
        return new FruitBasketController(blockEntity, this);
    }

    @Override
    public Object playerWillDestroy(Object thisBlock, Object[] args) {
        Object nmsPlayer = args.length > 3 ? args[3] : null;
        if (isCreativePlayer(nmsPlayer)) {
            CEWorld ceWorld = BukkitWorldManager.instance().getWorld(LevelProxy.INSTANCE.getWorld(args[0]).getUID());
            BlockEntity be = ceWorld.getBlockEntityAtIfLoaded(LocationUtils.fromBlockPos(args[1]));
            if (be != null) {
                be.controller.let(FruitBasketController.class, this.controllerId, FruitBasketController::markCreativeBreak);
            }
        }
        return args[2];
    }

    private static volatile Method GET_BUKKIT_ENTITY;

    private static boolean isCreativePlayer(Object nmsPlayer) {
        if (nmsPlayer == null) return false;
        try {
            if (GET_BUKKIT_ENTITY == null) {
                GET_BUKKIT_ENTITY = nmsPlayer.getClass().getMethod("getBukkitEntity");
            }
            Object bukkit = GET_BUKKIT_ENTITY.invoke(nmsPlayer);
            return bukkit instanceof org.bukkit.entity.Player p && p.getGameMode() == org.bukkit.GameMode.CREATIVE;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        // 只认主手 避免双手各触发一次
        if (context.getHand() == InteractionHand.OFF_HAND) {
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

        InteractionHand hand = context.getHand();
        Item itemInHand = player.getItemInHand(hand);

        return blockEntity.controller.let(FruitBasketController.class, this.controllerId, controller -> {
            // 潜行 取出
            if (player.isSecondaryUseActive()) {
                Item taken = controller.takeOut();
                if (taken.isEmpty()) {
                    return InteractionResult.PASS;
                }
                InventoryUtils.giveOrHold(player, hand, taken);
                player.swingHand(hand);
                world.playBlockSound(Vec3d.atCenterOf(pos), TAKE_SOUND, 1.0f, 1.0f);
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            // 手持物品 放入 按实际放入数量扣主手 创造不消耗
            if (!itemInHand.isEmpty()) {
                int placed = controller.putOn(itemInHand);
                if (placed > 0) {
                    InventoryUtils.shrinkHeld(player, itemInHand, placed);
                    player.swingHand(hand);
                    world.playBlockSound(Vec3d.atCenterOf(pos), PUT_SOUND, 1.0f, 1.0f);
                    return InteractionResult.SUCCESS_AND_CANCEL;
                }
            }
            return InteractionResult.PASS;
        });
    }

    private static class Factory implements BlockBehaviorFactory<FruitBasketBehavior> {
        @Override
        public FruitBasketBehavior create(BlockDefinition block, ConfigSection section) {
            FruitBasketBehavior behavior = new FruitBasketBehavior(block);
            behavior.facingProperty = BlockBehaviorFactory.getOptionalProperty(block, "facing", Direction.class);
            return behavior;
        }
    }
}
