package net.kaleidoscope.cookery.block.behavior;
import net.kaleidoscope.cookery.block.entity.ChoppingBoardController;
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
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import net.kaleidoscope.cookery.util.BehaviorConfig;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.item.ItemKeys;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;

public final class ChoppingBoardBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<ChoppingBoardBehavior> FACTORY = new Factory();

    // 四把厨房菜刀
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

        // 领地权限校验
        Location agLoc = new Location((org.bukkit.World) level.platformWorld(), pos.x, pos.y, pos.z);
        if (!KaleidoscopeCookeryPlugin.antiGrief().test((org.bukkit.entity.Player) player.platformPlayer(), Flag.INTERACT, agLoc)) {
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

        InteractionHand hand = context.getHand();
        Item itemInHand = player.getItemInHand(hand);

        // 手持菜刀：切一刀
        if (!itemInHand.isEmpty() && knives.contains(itemInHand.id())) {
            return handleCut(context, controller);
        }

        // 空手：取回原料
        if (itemInHand.isEmpty()) {
            return handleTakeBack(context, controller);
        }

        // 手持其它物品：放上原料
        return handlePlaceRaw(context, controller, itemInHand);
    }

    // 推进一个切割阶段，切完则产出
    private InteractionResult handleCut(UseOnContext context, ChoppingBoardController controller) {
        Player player = context.getPlayer();
        World level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        InteractionHand hand = context.getHand();
        Vec3d center = Vec3d.atCenterOf(pos);

        ChoppingBoardController.CutResult result = controller.cut();
        if (result == ChoppingBoardController.CutResult.NOTHING) {
            return InteractionResult.PASS;
        }
        player.swingHand(hand);
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

    // 空手取回未切完的原料
    private InteractionResult handleTakeBack(UseOnContext context, ChoppingBoardController controller) {
        Player player = context.getPlayer();
        World level = context.getLevel();
        InteractionHand hand = context.getHand();
        Vec3d center = Vec3d.atCenterOf(context.getClickedPos());

        Item taken = controller.takeBack();
        if (taken != null && !taken.isEmpty()) {
            InventoryUtils.giveOrHold(player, hand, taken);
            player.swingHand(hand);
            playSound(level, center, TAKE_SOUND, 1.2f + (float) Math.random() * 0.2f);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        return InteractionResult.PASS;
    }

    // 砧板为空时把手持物品作为原料放上
    private InteractionResult handlePlaceRaw(UseOnContext context, ChoppingBoardController controller, Item itemInHand) {
        if (!controller.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!controller.canChop(itemInHand)) {
            return InteractionResult.PASS;
        }
        if (controller.place(itemInHand)) {
            Player player = context.getPlayer();
            if (!player.canInstabuild()) {
                itemInHand.shrink(1);
            }
            player.swingHand(context.getHand());
            playSound(context.getLevel(), Vec3d.atCenterOf(context.getClickedPos()), PLACE_SOUND, 1.2f);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        return InteractionResult.PASS;
    }

    private void playSound(World world, Vec3d pos, Key sound, float pitch) {
        world.playSound(pos, sound, DEFAULT_VOLUME, pitch, SoundSource.BLOCK);
    }

    // 菜刀耐久 -1，耗尽自动损坏
    private void damageHeldKnife(org.bukkit.entity.Player bukkitPlayer, InteractionHand hand) {
        EquipmentSlot slot = hand == InteractionHand.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        ItemStack stack = bukkitPlayer.getInventory().getItem(slot);
        if (stack == null || stack.getType().getMaxDurability() <= 0) {
            return;
        }
        bukkitPlayer.getInventory().setItem(slot, stack.damage(1, bukkitPlayer));
    }

    private void spawnCutParticles(CEWorld world, BlockPos pos) {
        try {
            org.bukkit.World bw = (org.bukkit.World) world.world().platformWorld();
            double x = pos.x() + 0.25 + Math.random() / 2;
            double y = pos.y() + 0.25;
            double z = pos.z() + 0.25 + Math.random() / 2;
            bw.spawnParticle(Particle.CRIT, new Location(bw, x, y, z), 2, 0, 0, 0, 0.1);
        } catch (Exception ignored) {}
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
            knives.add(Key.of(BehaviorConfig.getString(section, ItemKeys.DIAMOND_KITCHEN_KNIFE.toString(), "diamond_knife_item", "diamond-knife-item")));
            knives.add(Key.of(BehaviorConfig.getString(section, ItemKeys.GOLD_KITCHEN_KNIFE.toString(), "gold_knife_item", "gold-knife-item")));
            knives.add(Key.of(BehaviorConfig.getString(section, ItemKeys.IRON_KITCHEN_KNIFE.toString(), "iron_knife_item", "iron-knife-item")));
            knives.add(Key.of(BehaviorConfig.getString(section, ItemKeys.NETHERITE_KITCHEN_KNIFE.toString(), "netherite_knife_item", "netherite-knife-item")));
            behavior.knives = knives;
            return behavior;
        }
    }
}
