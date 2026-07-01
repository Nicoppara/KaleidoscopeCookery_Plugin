package net.kaleidoscope.cookery.block.behavior;
import net.kaleidoscope.cookery.block.entity.ChoppingBoardController;

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
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import net.kaleidoscope.cookery.block.entity.render.Particles;
import net.kaleidoscope.cookery.util.BehaviorConfig;
import net.kaleidoscope.cookery.util.Hands;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.item.ItemKeys;
import org.bukkit.Particle;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;

public final class ChoppingBoardBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<ChoppingBoardBehavior> FACTORY = new Factory();

    public Set<Key> knives = Set.of(
            ItemKeys.DIAMOND_KITCHEN_KNIFE,
            ItemKeys.GOLD_KITCHEN_KNIFE,
            ItemKeys.IRON_KITCHEN_KNIFE,
            ItemKeys.NETHERITE_KITCHEN_KNIFE);

    private static final float DEFAULT_VOLUME = 1.0f;
    private static final Key PLACE_SOUND = Key.of("minecraft:block.wood.place");
    private static final Key TAKE_SOUND = Key.of("minecraft:entity.item_frame.remove_item");

    private int controllerId;
    private Property<Direction> facingProperty;

    private ChoppingBoardBehavior(BlockDefinition blockDefinition) {
        super(blockDefinition);
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        World level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (!InteractGuard.canInteract(player, level, pos)) {
            return InteractionResult.PASS;
        }

        CEWorld world = level.storageWorld();
        BlockEntity blockEntity = world.getBlockEntityAtIfLoaded(pos);
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }
        ChoppingBoardController controller = blockEntity.controller.get(ChoppingBoardController.class, this.controllerId);
        if (controller == null) {
            return InteractionResult.PASS;
        }

        // 只处理主手那次调用 避免主副手各触发一次
        if (context.getHand() == InteractionHand.OFF_HAND) {
            return InteractionResult.PASS;
        }

        // 切菜的刀副手优先
        InteractionHand toolHand = Hands.toolHand(player, it -> knives.contains(it.id()));
        Item toolItem = player.getItemInHand(toolHand);
        if (!toolItem.isEmpty() && knives.contains(toolItem.id())) {
            InteractionResult cut = handleCut(context, controller, toolHand);
            if (cut != InteractionResult.PASS) {
                return cut;
            }
        }

        // 取放食材只认主手
        Item mainItem = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (mainItem.isEmpty()) {
            return handleTakeBack(context, controller, InteractionHand.MAIN_HAND);
        }
        return handlePlaceRaw(context, controller, mainItem, InteractionHand.MAIN_HAND);
    }

    // 处理切菜逻辑
    private InteractionResult handleCut(UseOnContext context, ChoppingBoardController controller, InteractionHand hand) {
        Player player = context.getPlayer();
        World level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Vec3d center = Vec3d.atCenterOf(pos);

        ChoppingBoardController.CutResult result = controller.cut();
        if (result == ChoppingBoardController.CutResult.NOTHING) {
            return InteractionResult.PASS;
        }
        player.swingHand(hand);

        // 扣菜刀耐久
        if (!player.canInstabuild()) {
            damageHeldKnife((org.bukkit.entity.Player) player.platformPlayer(), hand);
        }
        if (result == ChoppingBoardController.CutResult.FINISHED) {
            playSound(level, center, PLACE_SOUND, 2.0f + (float) Math.random() * 0.2f);
        } else {
            spawnCutParticles(level.storageWorld(), pos);
            playSound(level, center, PLACE_SOUND, 1.5f + (float) Math.random() * 0.4f);
        }
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 拿回砧板上的原料
    private InteractionResult handleTakeBack(UseOnContext context, ChoppingBoardController controller, InteractionHand hand) {
        Player player = context.getPlayer();
        World level = context.getLevel();
        Vec3d center = Vec3d.atCenterOf(context.getClickedPos());

        Item taken = controller.takeBack();
        if (!taken.isEmpty()) {
            InventoryUtils.giveOrHold(player, hand, taken);
            player.swingHand(hand);
            playSound(level, center, TAKE_SOUND, 1.2f + (float) Math.random() * 0.2f);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        return InteractionResult.PASS;
    }

    // 放置原料
    private InteractionResult handlePlaceRaw(UseOnContext context, ChoppingBoardController controller, Item itemInHand, InteractionHand hand) {
        if (!controller.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!controller.canChop(itemInHand)) {
            return InteractionResult.PASS;
        }
        if (controller.place(itemInHand)) {
            Player player = context.getPlayer();
            InventoryUtils.shrinkHeld(player, itemInHand, 1);
            player.swingHand(hand);
            playSound(context.getLevel(), Vec3d.atCenterOf(context.getClickedPos()), PLACE_SOUND, 1.2f);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        return InteractionResult.PASS;
    }

    private void playSound(World world, Vec3d pos, Key sound, float pitch) {
        world.playSound(pos, sound, DEFAULT_VOLUME, pitch, SoundSource.BLOCK);
    }

    // 菜刀扣 1 点耐久
    private void damageHeldKnife(org.bukkit.entity.Player bukkitPlayer, InteractionHand hand) {
        EquipmentSlot slot = hand == InteractionHand.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        ItemStack stack = bukkitPlayer.getInventory().getItem(slot);
        if (stack == null || stack.getType().getMaxDurability() <= 0) {
            return;
        }
        bukkitPlayer.getInventory().setItem(slot, stack.damage(1, bukkitPlayer));
    }

    // 剁菜时的粒子
    private void spawnCutParticles(CEWorld world, BlockPos pos) {
        double x = pos.x() + 0.25 + Math.random() / 2;
        double y = pos.y() + 0.25;
        double z = pos.z() + 0.25 + Math.random() / 2;
        Particles.emit(world, Particle.CRIT, x, y, z, 2, 0, 0, 0, 0.1, null);
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new ChoppingBoardController(blockEntity, this);
    }

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    public int getControllerId() {
        return this.controllerId;
    }

    public Property<Direction> getFacingProperty() {
        return facingProperty;
    }

    private static class Factory implements BlockBehaviorFactory<ChoppingBoardBehavior> {
        @Override
        public ChoppingBoardBehavior create(BlockDefinition block, ConfigSection section) {
            ChoppingBoardBehavior behavior = new ChoppingBoardBehavior(block);
            behavior.facingProperty = BlockBehaviorFactory.getProperty(section.path(), block, "facing", Direction.class);

            Set<Key> knives = new HashSet<>();
            knives.add(Key.of(BehaviorConfig.getString(section, ItemKeys.DIAMOND_KITCHEN_KNIFE.asString(), "diamond_knife_item", "diamond-knife-item")));
            knives.add(Key.of(BehaviorConfig.getString(section, ItemKeys.GOLD_KITCHEN_KNIFE.asString(), "gold_knife_item", "gold-knife-item")));
            knives.add(Key.of(BehaviorConfig.getString(section, ItemKeys.IRON_KITCHEN_KNIFE.asString(), "iron_knife_item", "iron-knife-item")));
            knives.add(Key.of(BehaviorConfig.getString(section, ItemKeys.NETHERITE_KITCHEN_KNIFE.asString(), "netherite_knife_item", "netherite-knife-item")));
            behavior.knives = knives;
            return behavior;
        }
    }
}