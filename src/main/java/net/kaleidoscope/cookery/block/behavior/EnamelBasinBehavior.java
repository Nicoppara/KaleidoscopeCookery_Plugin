package net.kaleidoscope.cookery.block.behavior;
import net.kaleidoscope.cookery.block.entity.EnamelBasinController;

import net.kaleidoscope.cookery.util.BehaviorConfig;
import net.kaleidoscope.cookery.util.Hands;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.item.ItemMatch;
import net.kaleidoscope.cookery.item.KitchenShovel;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.plugin.user.BukkitServerPlayer;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.EntityBlock;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class EnamelBasinBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<EnamelBasinBehavior> FACTORY = new Factory();

    private static final float DEFAULT_VOLUME = 0.8f;
    private static final Key OPEN_CLOSE_SOUND_KEY = Key.of("minecraft:block.lantern.break");
    private static final Key OIL_SOUND_KEY = Key.of("minecraft:block.honey_block.break");

    public int maxOil = 16;
    public Key oilItem = ItemKeys.OIL;
    public Key shovelItem = ItemKeys.KITCHEN_SHOVEL;
    public Key shovelOilModel = ItemKeys.KITCHEN_SHOVEL_OIL_MODEL;

    private int controllerId;

    public EnamelBasinBehavior(BlockDefinition blockDefinition) {
        super(blockDefinition);
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        BukkitServerPlayer player = (BukkitServerPlayer) context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        CEWorld world = context.getLevel().storageWorld();
        BlockPos pos = context.getClickedPos();
        BlockEntity blockEntity = world.getBlockEntityAtIfLoaded(pos);
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }

        Player bukkitPlayer = player.platformPlayer();

        if (!InteractGuard.canInteract(player, context.getLevel(), pos)) {
            return InteractionResult.PASS;
        }

        EnamelBasinController controller = blockEntity.controller.get(EnamelBasinController.class, this.controllerId);
        // 工具操作副手优先 取油只认主手
        ItemStack mainItem = bukkitPlayer.getInventory().getItemInMainHand();
        EquipmentSlot toolSlot =
                Hands.toolHandBukkit(bukkitPlayer, this::isBasinTool);
        ItemStack toolItem = bukkitPlayer.getInventory().getItem(toolSlot);
        boolean isSneaking = bukkitPlayer.isSneaking();

        // 敲击瓷盆 木棍或潜行厨铲 工具
        boolean shovelEasterEgg = isShovel(toolItem) && !shovelHasOil(toolItem) && isSneaking;
        if (toolItem != null && (toolItem.getType() == Material.STICK || shovelEasterEgg)) {
            InteractionResult result = handleEasterEgg(world, pos, bukkitPlayer, toolSlot);
            if (shovelEasterEgg) {
                migrateLegacy(toolItem, player, toolSlot, false);
            }
            return result;
        }

        // 没开盖子先开盖
        if (controller.isClosed()) {
            InteractionResult result = handleToggleOpen(controller, world, pos, bukkitPlayer);
            migrateLegacy(toolItem, player, toolSlot, false);
            return result;
        }

        // 工具手没触发动作时要落到下面的主手逻辑 别截断
        if (isShovel(toolItem)) {
            InteractionResult toolResult = handleShovel(toolItem, controller, world, pos, bukkitPlayer, player, toolSlot);
            if (toolResult != InteractionResult.PASS) {
                return toolResult;
            }
        }

        if (isCustomItem(toolItem, oilItem)) {
            InteractionResult toolResult = handleAddOil(toolItem, controller, world, pos, bukkitPlayer, player, toolSlot);
            if (toolResult != InteractionResult.PASS) {
                return toolResult;
            }
        }

        // 潜行空手 把油取出来 只认主手
        if (mainItem.getType() == Material.AIR && isSneaking) {
            return handleTakeOil(controller, world, pos, bukkitPlayer, player);
        }

        // 空手或其他 关盖子
        InteractionResult result = handleClose(controller, world, pos, bukkitPlayer);
        migrateLegacy(toolItem, player, toolSlot, false);
        return result;
    }

    // 瓷盆的工具类物品 厨铲 油瓶 木棍 走副手优先
    private boolean isBasinTool(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return false;
        }
        return stack.getType() == Material.STICK
                || isShovel(stack)
                || isCustomItem(stack, oilItem);
    }

    private InteractionResult handleEasterEgg(CEWorld world, BlockPos pos, Player bukkitPlayer,
                                              EquipmentSlot slot) {
        playSound(world, pos, OPEN_CLOSE_SOUND_KEY, DEFAULT_VOLUME, 0.8f);
        Hands.swing(bukkitPlayer, slot);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    private InteractionResult handleToggleOpen(EnamelBasinController controller, CEWorld world, BlockPos pos, Player bukkitPlayer) {
        playSound(world, pos, OPEN_CLOSE_SOUND_KEY, DEFAULT_VOLUME, 0.8f);
        controller.setClosed(false);
        bukkitPlayer.swingMainHand();
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    private InteractionResult handleClose(EnamelBasinController controller, CEWorld world, BlockPos pos, Player bukkitPlayer) {
        playSound(world, pos, OPEN_CLOSE_SOUND_KEY, DEFAULT_VOLUME, 0.4f);
        controller.setClosed(true);
        bukkitPlayer.swingMainHand();
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    private InteractionResult handleTakeOil(EnamelBasinController controller, CEWorld world, BlockPos pos,
                                            Player bukkitPlayer, BukkitServerPlayer player) {
        if (controller.getOilCount() <= 0) {
            return InteractionResult.PASS;
        }
        Item oil = InventoryUtils.createOrEmpty(this.oilItem);
        if (ItemUtils.isEmpty(oil)) {
            return InteractionResult.PASS;
        }
        controller.removeOil(1);
        InventoryUtils.give(player, oil.copyWithCount(1), true);
        playSound(world, pos, OIL_SOUND_KEY, DEFAULT_VOLUME, 1.2f);
        bukkitPlayer.swingMainHand();
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    private InteractionResult handleAddOil(ItemStack heldItem, EnamelBasinController controller,
                                           CEWorld world, BlockPos pos, Player bukkitPlayer,
                                           BukkitServerPlayer player,
                                           EquipmentSlot slot) {
        int canAdd = maxOil - controller.getOilCount();
        if (canAdd <= 0) {
            return InteractionResult.PASS;
        }
        int toAdd = Math.min(heldItem.getAmount(), canAdd);
        controller.addOil(toAdd);
        if (!player.canInstabuild()) {
            heldItem.setAmount(heldItem.getAmount() - toAdd);
            bukkitPlayer.getInventory().setItem(slot, heldItem);
        }
        playSound(world, pos, OIL_SOUND_KEY, DEFAULT_VOLUME, 0.8f);
        Hands.swing(bukkitPlayer, slot);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 厨铲无油从盆中沾油 有油把油倒入盆中
    private InteractionResult handleShovel(ItemStack heldItem, EnamelBasinController controller,
                                           CEWorld world, BlockPos pos, Player bukkitPlayer, BukkitServerPlayer player,
                                           EquipmentSlot slot) {
        Item shovel = BukkitItemManager.instance().wrap(heldItem);
        boolean dipping = !KitchenShovel.hasOil(shovel, shovelOilModel);
        if (dipping ? controller.getOilCount() <= 0 : controller.getOilCount() >= maxOil) {
            return InteractionResult.PASS;
        }
        // 先写铲子再动盆 换铲失败时不能扣掉油却不换铲
        if (KitchenShovel.isLegacy(shovel)) {
            if (!KitchenShovel.migrateLegacy(player, handOf(slot), shovel, shovelItem, shovelOilModel, dipping)) {
                return InteractionResult.PASS;
            }
        } else {
            KitchenShovel.setHasOil(shovel, dipping, shovelItem, shovelOilModel);
            bukkitPlayer.getInventory().setItem(slot, ItemStackUtils.getBukkitStack(shovel));
        }
        if (dipping) {
            controller.removeOil(1);
        } else {
            controller.addOil(1);
        }
        playSound(world, pos, OIL_SOUND_KEY, DEFAULT_VOLUME, 0.8f);
        Hands.swing(bukkitPlayer, slot);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    private boolean isShovel(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        return KitchenShovel.is(BukkitItemManager.instance().wrap(item), shovelItem);
    }

    private boolean shovelHasOil(ItemStack item) {
        return KitchenShovel.hasOil(BukkitItemManager.instance().wrap(item), shovelOilModel);
    }

    private void migrateLegacy(ItemStack item, BukkitServerPlayer player, EquipmentSlot slot, boolean hasOil) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        KitchenShovel.migrateLegacy(player, handOf(slot), BukkitItemManager.instance().wrap(item),
                shovelItem, shovelOilModel, hasOil);
    }

    private static InteractionHand handOf(EquipmentSlot slot) {
        return slot == EquipmentSlot.OFF_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private boolean isCustomItem(ItemStack item, Key expectedKey) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        Item ceItem = BukkitItemManager.instance().wrap(item);
        return ItemMatch.is(ceItem, expectedKey);
    }

    private void playSound(CEWorld world, BlockPos pos, Key soundKey, float volume, float pitch) {
        world.world().playSound(Vec3d.atCenterOf(pos), soundKey, volume, pitch, SoundSource.BLOCK);
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new EnamelBasinController(blockEntity, this);
    }

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    private static class Factory implements BlockBehaviorFactory<EnamelBasinBehavior> {
        @Override
        public EnamelBasinBehavior create(BlockDefinition block, ConfigSection section) {
            EnamelBasinBehavior b = new EnamelBasinBehavior(block);
            b.maxOil = BehaviorConfig.getInt(section, b.maxOil, "max_oil", "max-oil");
            b.oilItem = Key.of(BehaviorConfig.getString(section, b.oilItem.asString(), "oil_item", "oil-item"));
            b.shovelItem = Key.of(BehaviorConfig.getString(section, b.shovelItem.asString(), "shovel_item", "shovel-item", "shovel_no_oil_item", "shovel-no-oil-item"));
            b.shovelOilModel = Key.of(BehaviorConfig.getString(section, b.shovelOilModel.asString(), "shovel_oil_model", "shovel-oil-model", "shovel_has_oil_item", "shovel-has-oil-item"));
            return b;
        }
    }
}
