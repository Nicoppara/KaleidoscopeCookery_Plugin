package net.kaleidoscope.cookery.block.behavior;

import net.kaleidoscope.cookery.api.ChoppingBoardKnives;
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
import java.util.Objects;
import java.util.Set;

public final class ChoppingBoardBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<ChoppingBoardBehavior> FACTORY = new Factory();

    public Set<String> knives = Set.of();
    public Set<String> craftEngineKnives = Set.of(
            ItemKeys.DIAMOND_KITCHEN_KNIFE.asString(),
            ItemKeys.GOLD_KITCHEN_KNIFE.asString(),
            ItemKeys.IRON_KITCHEN_KNIFE.asString(),
            ItemKeys.NETHERITE_KITCHEN_KNIFE.asString());

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
        InteractionHand toolHand = Hands.toolHand(player, this::isKnife);
        Item toolItem = player.getItemInHand(toolHand);
        if (isKnife(toolItem)) {
            InteractionResult cut = handleCut(context, controller, toolHand);
            if (cut != InteractionResult.PASS) {
                return cut;
            }
        }

        // 取放食材只认主手
        Item mainItem = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (mainItem.isEmpty()) {
            return handleTakeBack(context, controller);
        }
        return handlePlaceRaw(context, controller, mainItem);
    }

    private boolean isKnife(Item item) {
        if (item.isEmpty()) {
            return false;
        }
        if (ChoppingBoardKnives.instance().isKnife(item)) {
            return true;
        }
        if (knives.contains(item.id().asString()) || knives.contains(item.vanillaId().asString())) {
            return true;
        }
        return item.isCustomItem() && (craftEngineKnives.contains(item.id().asString())
                || item.customId().map(Key::asString).filter(craftEngineKnives::contains).isPresent());
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
        Objects.requireNonNull(player).swingHand(hand);

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
    private InteractionResult handleTakeBack(UseOnContext context, ChoppingBoardController controller) {
        Player player = context.getPlayer();
        World level = context.getLevel();
        Vec3d center = Vec3d.atCenterOf(context.getClickedPos());

        Item taken = controller.takeBack();
        if (!taken.isEmpty()) {
            InventoryUtils.giveOrHold(player, InteractionHand.MAIN_HAND, taken);
            Objects.requireNonNull(player).swingHand(InteractionHand.MAIN_HAND);
            playSound(level, center, TAKE_SOUND, 1.2f + (float) Math.random() * 0.2f);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        return InteractionResult.PASS;
    }

    // 放置原料
    private InteractionResult handlePlaceRaw(UseOnContext context, ChoppingBoardController controller, Item itemInHand) {
        if (!controller.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!controller.canChop(itemInHand)) {
            return InteractionResult.PASS;
        }
        if (controller.place(itemInHand)) {
            Player player = context.getPlayer();
            InventoryUtils.shrinkHeld(player, itemInHand, 1);
            Objects.requireNonNull(player).swingHand(InteractionHand.MAIN_HAND);
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
        if (stack.getType().getMaxDurability() <= 0) {
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

            Set<String> knives = new HashSet<>();
            Set<String> craftEngineKnives = new HashSet<>();
            addLegacyKnife(knives, craftEngineKnives, BehaviorConfig.getString(section, ItemKeys.DIAMOND_KITCHEN_KNIFE.asString(), "diamond_knife_item", "diamond-knife-item"));
            addLegacyKnife(knives, craftEngineKnives, BehaviorConfig.getString(section, ItemKeys.GOLD_KITCHEN_KNIFE.asString(), "gold_knife_item", "gold-knife-item"));
            addLegacyKnife(knives, craftEngineKnives, BehaviorConfig.getString(section, ItemKeys.IRON_KITCHEN_KNIFE.asString(), "iron_knife_item", "iron-knife-item"));
            addLegacyKnife(knives, craftEngineKnives, BehaviorConfig.getString(section, ItemKeys.NETHERITE_KITCHEN_KNIFE.asString(), "netherite_knife_item", "netherite-knife-item"));
            addKnives(knives, craftEngineKnives, section.getStringList("knives"));
            addKnives(knives, craftEngineKnives, section.getStringList("knife_items"));
            addKnives(knives, craftEngineKnives, section.getStringList("knife-items"));
            behavior.knives = knives;
            behavior.craftEngineKnives = craftEngineKnives;
            return behavior;
        }

        private static void addKnives(Set<String> knives, Set<String> craftEngineKnives, Iterable<String> ids) {
            for (String id : ids) {
                addKnife(knives, craftEngineKnives, id);
            }
        }

        private static void addLegacyKnife(Set<String> knives, Set<String> craftEngineKnives, String id) {
            addKnife(knives, craftEngineKnives, id);
            String trimmed = id.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith(Key.CRAFTENGINE_NAMESPACE + ":")) {
                addCraftEngineKnife(craftEngineKnives, trimmed);
            }
        }

        private static void addKnife(Set<String> knives, Set<String> craftEngineKnives, String id) {
            String trimmed = id.trim();
            if (trimmed.isEmpty()) {
                return;
            }
            String prefix = Key.CRAFTENGINE_NAMESPACE + ":";
            if (trimmed.startsWith(prefix)) {
                addCraftEngineKnife(craftEngineKnives, trimmed.substring(prefix.length()));
            } else {
                addPlainKnife(knives, trimmed);
            }
        }

        private static void addPlainKnife(Set<String> knives, String id) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) {
                knives.add(trimmed);
            }
        }

        private static void addCraftEngineKnife(Set<String> craftEngineKnives, String id) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) {
                craftEngineKnives.add(trimmed);
            }
        }
    }
}
