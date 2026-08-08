package net.kaleidoscope.cookery.block.behavior;

import net.kaleidoscope.cookery.block.entity.OilPotController;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.item.ItemMatch;
import net.kaleidoscope.cookery.util.BehaviorConfig;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.EntityBlock;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.UseOnContext;

import java.util.concurrent.ThreadLocalRandom;

// 放置的油壶 空手右键取油 手持油脂右键加油 与模组 OilPotBlock#use 同一套口径
public final class OilPotBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<OilPotBehavior> FACTORY = new Factory();

    // 模组一次最多掏出一组
    private static final int TAKE_PER_USE = 64;
    private static final Key SOUND = Key.of("minecraft:block.lantern.hit");
    private static final float VOLUME = 1.0f;
    private static final float PITCH_TAKE = 0.8f;
    private static final float PITCH_ADD = 0.4f;
    private static final float PITCH_RANGE = 0.2f;

    // 默认值取自模组 OilPotBlockEntity.MAX_OIL_COUNT
    public int maxOil = 256;
    public Key oilItem = ItemKeys.OIL;
    public Key potItem = ItemKeys.OIL_POT;
    public Key emptyPotItem = ItemKeys.OIL_POT_EMPTY;

    private int controllerId;

    private OilPotBehavior(BlockDefinition blockDefinition) {
        super(blockDefinition);
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        // 模组只认主手 副手那次调用直接放过 免得一次右键跑两遍
        if (player == null || context.getHand() != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        World world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!InteractGuard.canInteract(player, world, pos)) {
            return InteractionResult.PASS;
        }
        BlockEntity blockEntity = world.storageWorld().getBlockEntityAtIfLoaded(pos);
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }
        OilPotController controller = blockEntity.controller.get(OilPotController.class, this.controllerId);
        if (controller == null) {
            return InteractionResult.PASS;
        }

        Item held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (ItemUtils.isEmpty(held)) {
            return takeOil(controller, player, world, pos);
        }
        if (ItemMatch.is(held, this.oilItem)) {
            return addOil(controller, player, world, pos, held);
        }
        return InteractionResult.PASS;
    }

    private InteractionResult takeOil(OilPotController controller, Player player, World world, BlockPos pos) {
        Item oil = InventoryUtils.createOrEmpty(this.oilItem);
        if (ItemUtils.isEmpty(oil)) {
            return InteractionResult.PASS;
        }
        int taken = controller.removeOil(Math.min(controller.oilCount(), TAKE_PER_USE));
        if (taken <= 0) {
            return InteractionResult.PASS;
        }
        InventoryUtils.giveOrHold(player, InteractionHand.MAIN_HAND, oil.copyWithCount(taken));
        playSound(world, pos, PITCH_TAKE);
        player.swingHand(InteractionHand.MAIN_HAND);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    private InteractionResult addOil(OilPotController controller, Player player, World world, BlockPos pos, Item held) {
        int added = controller.addOil(held.count());
        if (added <= 0) {
            return InteractionResult.PASS;
        }
        InventoryUtils.shrinkHeld(player, held, added);
        playSound(world, pos, PITCH_ADD);
        player.swingHand(InteractionHand.MAIN_HAND);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    private void playSound(World world, BlockPos pos, float basePitch) {
        float pitch = basePitch + ThreadLocalRandom.current().nextFloat() * PITCH_RANGE;
        world.playSound(Vec3d.atCenterOf(pos), SOUND, VOLUME, pitch, SoundSource.BLOCK);
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new OilPotController(blockEntity, this);
    }

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    private static class Factory implements BlockBehaviorFactory<OilPotBehavior> {
        @Override
        public OilPotBehavior create(BlockDefinition block, ConfigSection section) {
            OilPotBehavior b = new OilPotBehavior(block);
            b.maxOil = BehaviorConfig.getInt(section, b.maxOil, "max_oil", "max-oil");
            b.oilItem = Key.of(BehaviorConfig.getString(section, b.oilItem.asString(), "oil_item", "oil-item"));
            b.potItem = Key.of(BehaviorConfig.getString(section, b.potItem.asString(), "pot_item", "pot-item"));
            b.emptyPotItem = Key.of(BehaviorConfig.getString(section, b.emptyPotItem.asString(), "empty_pot_item", "empty-pot-item"));
            return b;
        }
    }
}
