package net.kaleidoscope.cookery.block.behavior;
import net.kaleidoscope.cookery.block.entity.EnamelBasinController;
import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.kaleidoscope.cookery.util.BehaviorConfig;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.item.ItemMatch;
import net.momirealms.antigrieflib.Flag;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.entity.data.item.ItemEntityData;
import net.momirealms.craftengine.bukkit.item.BukkitItem;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
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
import net.momirealms.craftengine.core.sound.Sounds;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.random.RandomUtils;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundAddEntityPacketProxy;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacketProxy;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundSetEntityDataPacketProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.EntityProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.EntityTypeProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.player.InventoryProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.player.PlayerProxy;
import net.momirealms.craftengine.proxy.minecraft.world.phys.Vec3Proxy;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public class EnamelBasinBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<EnamelBasinBehavior> FACTORY = new Factory();

    private static final float DEFAULT_VOLUME = 0.8f;
    private static final Sound OPEN_CLOSE_SOUND = Sound.BLOCK_LANTERN_BREAK;
    private static final Sound OIL_SOUND = Sound.BLOCK_HONEY_BLOCK_BREAK;

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

        // 领地权限校验
        Location agLoc = new Location((World) context.getLevel().platformWorld(), pos.x(), pos.y(), pos.z());
        if (!KaleidoscopeCookeryPlugin.antiGrief().test(bukkitPlayer, Flag.INTERACT, agLoc)) {
            return InteractionResult.PASS;
        }

        EnamelBasinController controller = blockEntity.controller.get(EnamelBasinController.class, this.controllerId);
        ItemStack heldItem = bukkitPlayer.getInventory().getItemInMainHand();
        boolean isSneaking = bukkitPlayer.isSneaking();

        // 彩蛋：木棍，或潜行 + 无油厨铲
        if (heldItem.getType() == Material.STICK || (isCustomItem(heldItem, shovelNoOilItem) && isSneaking)) {
            return handleEasterEgg(world, pos, bukkitPlayer);
        }

        // 盆关闭时先打开
        if (controller.isClosed()) {
            return handleToggleOpen(controller, world, pos, bukkitPlayer);
        }

        // 空手 + 潜行：取出油
        if (heldItem.getType() == Material.AIR && isSneaking) {
            return handleTakeOil(controller, world, pos, bukkitPlayer, player);
        }

        // 空手：关闭盆
        if (heldItem.getType() == Material.AIR) {
            return handleClose(controller, world, pos, bukkitPlayer);
        }

        // 手持厨铲：沾油 / 倒油
        if (isCustomItem(heldItem, shovelNoOilItem) || isCustomItem(heldItem, shovelHasOilItem)) {
            return handleShovel(heldItem, controller, world, pos, bukkitPlayer, player);
        }

        // 手持油：倒入盆中
        if (isCustomItem(heldItem, oilItem)) {
            return handleAddOil(heldItem, controller, world, pos, bukkitPlayer);
        }

        // 手持其它物品：关闭盆
        return handleClose(controller, world, pos, bukkitPlayer);
    }

    // 彩蛋：仅播放开合音效
    private InteractionResult handleEasterEgg(CEWorld world, BlockPos pos, Player bukkitPlayer) {
        playSound(world, pos, OPEN_CLOSE_SOUND, DEFAULT_VOLUME, 0.8f);
        bukkitPlayer.swingMainHand();
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 打开盆
    private InteractionResult handleToggleOpen(EnamelBasinController controller, CEWorld world, BlockPos pos, Player bukkitPlayer) {
        playSound(world, pos, OPEN_CLOSE_SOUND, DEFAULT_VOLUME, 0.8f);
        controller.setClosed(false);
        bukkitPlayer.swingMainHand();
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 关闭盆
    private InteractionResult handleClose(EnamelBasinController controller, CEWorld world, BlockPos pos, Player bukkitPlayer) {
        playSound(world, pos, OPEN_CLOSE_SOUND, DEFAULT_VOLUME, 0.4f);
        controller.setClosed(true);
        bukkitPlayer.swingMainHand();
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 逐个取出油，带飞向玩家动画
    private InteractionResult handleTakeOil(EnamelBasinController controller, CEWorld world, BlockPos pos,
                                            Player bukkitPlayer, BukkitServerPlayer player) {
        if (controller.getOilCount() <= 0) {
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        BukkitItem oilItem = BukkitItemManager.instance().createWrappedItem(this.oilItem, player);
        if (oilItem == null) {
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        controller.removeOil(1);

        Item animationItem = oilItem.copyWithCount(1);

        Object serverPlayer = player.serverPlayer();
        Object inventory = PlayerProxy.INSTANCE.getInventory(serverPlayer);
        boolean added = InventoryProxy.INSTANCE.add(inventory, oilItem.minecraftItem());

        if (added) {
            spawnFakeItemFromBlock(player, animationItem, pos);
        } else {
            WorldPosition dropPos = new WorldPosition(world.world(),
                    pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5);
            world.world().dropItemNaturally(dropPos, oilItem);
        }

        playSound(world, pos, OIL_SOUND, DEFAULT_VOLUME, 1.2f);
        bukkitPlayer.swingMainHand();
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 手持油：一次性倒入直至装满
    private InteractionResult handleAddOil(ItemStack heldItem, EnamelBasinController controller,
                                           CEWorld world, BlockPos pos, Player bukkitPlayer) {
        int canAdd = maxOil - controller.getOilCount();
        if (canAdd > 0) {
            int toAdd = Math.min(heldItem.getAmount(), canAdd);
            controller.addOil(toAdd);
            heldItem.setAmount(heldItem.getAmount() - toAdd);
            playSound(world, pos, OIL_SOUND, DEFAULT_VOLUME, 0.8f);
            bukkitPlayer.swingMainHand();
        }
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 厨铲：无油从盆中沾油，有油把油倒入盆中
    private InteractionResult handleShovel(ItemStack heldItem, EnamelBasinController controller,
                                           CEWorld world, BlockPos pos, Player bukkitPlayer, BukkitServerPlayer player) {
        if (isCustomItem(heldItem, shovelNoOilItem)) {
            if (controller.getOilCount() > 0) {
                controller.removeOil(1);
                swapShovel(shovelHasOilItem, bukkitPlayer, player);
                playSound(world, pos, OIL_SOUND, DEFAULT_VOLUME, 0.8f);
                bukkitPlayer.swingMainHand();
            }
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        if (controller.getOilCount() < maxOil) {
            controller.addOil(1);
            swapShovel(shovelNoOilItem, bukkitPlayer, player);
            playSound(world, pos, OIL_SOUND, DEFAULT_VOLUME, 0.8f);
            bukkitPlayer.swingMainHand();
        }
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 把主手厨铲替换为指定状态
    private void swapShovel(Key shovelKey, Player bukkitPlayer, BukkitServerPlayer player) {
        BukkitItem newShovel = BukkitItemManager.instance().createWrappedItem(shovelKey, player);
        if (newShovel != null) {
            bukkitPlayer.getInventory().setItemInMainHand(ItemStackUtils.getBukkitStack(newShovel));
        }
    }

    // 取油的飞向玩家动画
    private void spawnFakeItemFromBlock(net.momirealms.craftengine.core.entity.player.Player player,
                                        Item item, BlockPos blockPos) {
        Vec3d blockCenter = Vec3d.atCenterOf(blockPos);
        Vec3d playerPos = player.position().toVec3d();

        double distance = Math.sqrt(Vec3d.distanceToSqr(blockCenter, playerPos));

        double speed = 0.6;
        double velX = (playerPos.x() - blockCenter.x()) / distance * speed;
        double velY = (playerPos.y() + 0.5 - blockCenter.y()) / distance * speed + 0.1;
        double velZ = (playerPos.z() - blockCenter.z()) / distance * speed;

        int entityId = EntityProxy.ENTITY_COUNTER.incrementAndGet();

        Object addEntityPacket = ClientboundAddEntityPacketProxy.INSTANCE.newInstance(
                entityId, UUID.randomUUID(),
                blockCenter.x(), blockCenter.y(), blockCenter.z(),
                0, 0, EntityTypeProxy.ITEM, 0,
                Vec3Proxy.INSTANCE.newInstance(velX, velY, velZ), 0
        );

        Object itemMetaPacket = ClientboundSetEntityDataPacketProxy.INSTANCE.newInstance(
                entityId,
                List.of(ItemEntityData.Item.createEntityData(item.copyWithCount(1).minecraftItem()))
        );

        player.sendPackets(List.of(addEntityPacket, itemMetaPacket), false);

        player.world().playSound(player.position(),
                Sounds.ENTITY_ITEM_PICKUP,
                0.2F,
                ((RandomUtils.generateRandomFloat() - RandomUtils.generateRandomFloat()) * 0.7F + 1.0F) * 2.0F,
                SoundSource.PLAYER);

        long delayTicks = (long) Math.ceil(distance / speed / 0.05 * 0.05);

        IntArrayList removeIds = new IntArrayList();
        removeIds.add(entityId);
        BukkitCraftEngine.instance().scheduler().platform().runLater(
                () -> player.sendPacket(ClientboundRemoveEntitiesPacketProxy.INSTANCE.newInstance(removeIds), false),
                null,
                delayTicks,
                (Player) player.platformPlayer()
        );
    }

    private boolean isCustomItem(ItemStack item, Key expectedKey) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        Item ceItem = BukkitItemManager.instance().wrap(item);
        return ItemMatch.is(ceItem, expectedKey);
    }

    private void playSound(CEWorld world, BlockPos pos, Sound sound, float volume, float pitch) {
        Key soundKey;
        if (sound == OPEN_CLOSE_SOUND) {
            soundKey = OPEN_CLOSE_SOUND_KEY;
        } else if (sound == OIL_SOUND) {
            soundKey = OIL_SOUND_KEY;
        } else {
            NamespacedKey key = Registry.SOUNDS.getKey(sound);
            if (key == null) {
                return;
            }
            soundKey = Key.of(key.toString());
        }

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
            b.oilItem = Key.of(BehaviorConfig.getString(section, b.oilItem.toString(), "oil_item", "oil-item"));
            b.shovelNoOilItem = Key.of(BehaviorConfig.getString(section, b.shovelNoOilItem.toString(), "shovel_no_oil_item", "shovel-no-oil-item"));
            b.shovelHasOilItem = Key.of(BehaviorConfig.getString(section, b.shovelHasOilItem.toString(), "shovel_has_oil_item", "shovel-has-oil-item"));
            return b;
        }
    }
}
