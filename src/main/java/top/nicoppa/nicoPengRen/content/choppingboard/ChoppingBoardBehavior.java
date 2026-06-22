package top.nicoppa.nicoPengRen.content.choppingboard;

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
import top.nicoppa.nicoPengRen.common.config.BehaviorConfig;
import top.nicoppa.nicoPengRen.common.item.InventoryUtils;
import top.nicoppa.nicoPengRen.common.item.ItemKeys;

import java.util.Set;

/**
 * 砧板方块行为 右键放原料、手持菜刀逐刀切割、切满产出加权成品、空手取回未切完的料
 * 数据与切割状态机见 {@link ChoppingBoardController}，渲染见 {@link ChoppingBoardElement}
 *
 * 可在 behaviors 配置里覆盖（默认值即下方所示，键名支持下划线/连字符）
 * behaviors:
 *   - type: nicopengren:chopping_board
 *     diamond_knife_item: "custom:diamond_kitchen_knife"
 *     gold_knife_item: "custom:gold_kitchen_knife"
 *     iron_knife_item: "custom:iron_kitchen_knife"
 *     netherite_knife_item: "custom:netherite_kitchen_knife"
 */
public final class ChoppingBoardBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<ChoppingBoardBehavior> FACTORY = new Factory();

    // 四把厨房菜刀 手持任一把右键即推进一个切割阶段
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
        if (player == null) return InteractionResult.PASS;

        World level = context.getLevel();
        CEWorld world = level.storageWorld();
        BlockEntity blockEntity = world.getBlockEntityAtIfLoaded(context.getClickedPos());
        if (blockEntity == null) return InteractionResult.PASS;
        ChoppingBoardController controller = blockEntity.controller.get(ChoppingBoardController.class, this.controllerId);
        if (controller == null) return InteractionResult.PASS;

        InteractionHand hand = context.getHand();
        Item itemInHand = player.getItemInHand(hand);
        Vec3d center = Vec3d.atCenterOf(context.getClickedPos());

        // 切一刀
        if (!itemInHand.isEmpty() && knives.contains(itemInHand.id())) {
            ChoppingBoardController.CutResult result = controller.cut();
            if (result == ChoppingBoardController.CutResult.NOTHING) return InteractionResult.PASS;
            player.swingHand(hand);
            if (!player.canInstabuild()) damageHeldKnife((org.bukkit.entity.Player) player.platformPlayer(), hand);
            if (result == ChoppingBoardController.CutResult.FINISHED) {
                playSound(level, center, PLACE_SOUND, 2.0f + (float) Math.random() * 0.2f);
            } else {
                spawnCutParticles(world, context.getClickedPos());
                playSound(level, center, PLACE_SOUND, 1.5f + (float) Math.random() * 0.4f);
            }
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        // 空手取回未切完的原料
        if (itemInHand.isEmpty()) {
            Item taken = controller.takeBack();
            if (taken != null && !taken.isEmpty()) {
                InventoryUtils.giveOrHold(player, hand, taken);
                player.swingHand(hand);
                playSound(level, center, TAKE_SOUND, 1.2f + (float) Math.random() * 0.2f);
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            return InteractionResult.PASS;
        }

        // 手持其他物品且砧板为空 尝试作为原料放上
        if (controller.isEmpty()) {
            if (!controller.canChop(itemInHand)) return InteractionResult.PASS;
            if (controller.place(itemInHand)) {
                if (!player.canInstabuild()) itemInHand.shrink(1);
                player.swingHand(hand);
                playSound(level, center, PLACE_SOUND, 1.2f);
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
        }

        return InteractionResult.PASS;
    }

    private void playSound(World world, Vec3d pos, Key sound, float pitch) {
        world.playSound(pos, sound, DEFAULT_VOLUME, pitch, SoundSource.BLOCK);
    }

    // 手中菜刀耐久 -1，走原版损耗逻辑），耐久耗尽自动损坏
    private void damageHeldKnife(org.bukkit.entity.Player bukkitPlayer, InteractionHand hand) {
        org.bukkit.inventory.EquipmentSlot slot = hand == InteractionHand.OFF_HAND
                ? org.bukkit.inventory.EquipmentSlot.OFF_HAND : org.bukkit.inventory.EquipmentSlot.HAND;
        org.bukkit.inventory.ItemStack stack = bukkitPlayer.getInventory().getItem(slot);
        if (stack == null || stack.getType().getMaxDurability() <= 0) return;
        bukkitPlayer.getInventory().setItem(slot, stack.damage(1, bukkitPlayer));
    }

    //切菜 CRIT 粒子
    private void spawnCutParticles(CEWorld world, BlockPos pos) {
        try {
            org.bukkit.World bw = (org.bukkit.World) world.world().platformWorld();
            double x = pos.x() + 0.25 + Math.random() / 2;
            double y = pos.y() + 0.25;
            double z = pos.z() + 0.25 + Math.random() / 2;
            bw.spawnParticle(org.bukkit.Particle.CRIT, new org.bukkit.Location(bw, x, y, z), 2, 0, 0, 0, 0.1);
        } catch (Exception ignored) {}
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new ChoppingBoardController(blockEntity, this);
    }

    @Override
    public void initControllerId(int id) { this.controllerId = id; }

    public int getControllerId() { return this.controllerId; }
    public Property<Direction> getFacingProperty() { return facingProperty; }

    private static class Factory implements BlockBehaviorFactory<ChoppingBoardBehavior> {
        @Override
        public ChoppingBoardBehavior create(BlockDefinition block, ConfigSection section) {
            ChoppingBoardBehavior behavior = new ChoppingBoardBehavior(block);
            behavior.facingProperty = BlockBehaviorFactory.getProperty(section.path(), block, "facing", Direction.class);

            java.util.Set<Key> knives = new java.util.HashSet<>();
            knives.add(Key.of(BehaviorConfig.getString(section, ItemKeys.DIAMOND_KITCHEN_KNIFE.toString(), "diamond_knife_item", "diamond-knife-item")));
            knives.add(Key.of(BehaviorConfig.getString(section, ItemKeys.GOLD_KITCHEN_KNIFE.toString(), "gold_knife_item", "gold-knife-item")));
            knives.add(Key.of(BehaviorConfig.getString(section, ItemKeys.IRON_KITCHEN_KNIFE.toString(), "iron_knife_item", "iron-knife-item")));
            knives.add(Key.of(BehaviorConfig.getString(section, ItemKeys.NETHERITE_KITCHEN_KNIFE.toString(), "netherite_knife_item", "netherite-knife-item")));
            behavior.knives = knives;
            return behavior;
        }
    }
}
