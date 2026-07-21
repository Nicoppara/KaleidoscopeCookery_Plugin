package net.kaleidoscope.cookery.block.behavior;
import net.kaleidoscope.cookery.block.entity.EnamelBasinController;

import net.kaleidoscope.cookery.util.BehaviorConfig;
import net.kaleidoscope.cookery.util.Hands;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.item.ItemMatch;
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
import org.bukkit.inventory.ItemStack;

public class EnamelBasinBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<EnamelBasinBehavior> FACTORY = new Factory();

    private static final float DEFAULT_VOLUME = 0.8f;
    private static final Key OPEN_CLOSE_SOUND_KEY = Key.of("minecraft:block.lantern.break");
    private static final Key OIL_SOUND_KEY = Key.of("minecraft:block.honey_block.break");

    public int maxOil = 16;
    public Key oilItem = ItemKeys.OIL;
    public Key shovelNoOilItem = ItemKeys.KITCHEN_SHOVEL_NO_OIL;
    public Key shovelHasOilItem = ItemKeys.KITCHEN_SHOVEL_HAS_OIL;

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
        org.bukkit.inventory.EquipmentSlot toolSlot =
                Hands.toolHandBukkit(bukkitPlayer, this::isBasinTool);
        ItemStack toolItem = bukkitPlayer.getInventory().getItem(toolSlot);
        boolean isSneaking = bukkitPlayer.isSneaking();

        // 敲击瓷盆 木棍或潜行厨铲 工具
        if (toolItem != null && (toolItem.getType() == Material.STICK
                || (isCustomItem(toolItem, shovelNoOilItem) && isSneaking))) {
            return handleEasterEgg(world, pos, bukkitPlayer, toolSlot);
        }

        // 没开盖子先开盖
        if (controller.isClosed()) {
            return handleToggleOpen(controller, world, pos, bukkitPlayer);
        }

        // 工具手没触发动作时要落到下面的主手逻辑 别截断
        if (isCustomItem(toolItem, shovelNoOilItem) || isCustomItem(toolItem, shovelHasOilItem)) {
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
        return handleClose(controller, world, pos, bukkitPlayer);
    }

    // 瓷盆的工具类物品 厨铲 油瓶 木棍 走副手优先
    private boolean isBasinTool(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return false;
        }
        return stack.getType() == Material.STICK
                || isCustomItem(stack, shovelNoOilItem)
                || isCustomItem(stack, shovelHasOilItem)
                || isCustomItem(stack, oilItem);
    }

    private InteractionResult handleEasterEgg(CEWorld world, BlockPos pos, Player bukkitPlayer,
                                              org.bukkit.inventory.EquipmentSlot slot) {
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
                                           org.bukkit.inventory.EquipmentSlot slot) {
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
                                           org.bukkit.inventory.EquipmentSlot slot) {
        boolean dipping = isCustomItem(heldItem, shovelNoOilItem);
        if (dipping ? controller.getOilCount() <= 0 : controller.getOilCount() >= maxOil) {
            return InteractionResult.PASS;
        }
        // 先备好替换后的厨铲 key 失效时不能扣掉油却不换铲
        if (!swapShovel(dipping ? shovelHasOilItem : shovelNoOilItem, bukkitPlayer, player, slot)) {
            return InteractionResult.PASS;
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

    private boolean swapShovel(Key shovelKey, Player bukkitPlayer, BukkitServerPlayer player,
                               org.bukkit.inventory.EquipmentSlot slot) {
        Item newShovel = InventoryUtils.createOrEmpty(shovelKey);
        if (ItemUtils.isEmpty(newShovel)) {
            return false;
        }
        bukkitPlayer.getInventory().setItem(slot, ItemStackUtils.getBukkitStack(newShovel));
        return true;
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
            b.shovelNoOilItem = Key.of(BehaviorConfig.getString(section, b.shovelNoOilItem.asString(), "shovel_no_oil_item", "shovel-no-oil-item"));
            b.shovelHasOilItem = Key.of(BehaviorConfig.getString(section, b.shovelHasOilItem.asString(), "shovel_has_oil_item", "shovel-has-oil-item"));
            return b;
        }
    }
}
