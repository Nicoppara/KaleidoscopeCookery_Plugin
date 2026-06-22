package top.nicoppa.nicoPengRen.content.enamelbasin;

import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.plugin.user.BukkitServerPlayer;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.EntityBlock;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import net.momirealms.craftengine.proxy.minecraft.world.entity.player.InventoryProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.player.PlayerProxy;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import top.nicoppa.nicoPengRen.common.config.BehaviorConfig;
import top.nicoppa.nicoPengRen.common.item.ItemKeys;
import top.nicoppa.nicoPengRen.common.item.ItemMatch;

/**
 * 搪瓷盆方块行为 处理右键开合、加油/取油、厨铲沾油等交互入口
 * 数据与状态见 {@link EnamelBasinController}
 *
 * 可在 behaviors 配置里覆盖（默认值即下方所示，键名支持下划线/连字符）
 * behaviors:
 *   - type: nicopengren:enamel_basin
 *     max_oil: 16                  # 盆的油容量上限
 *     oil_item: "custom:oil"
 *     shovel_no_oil_item: "custom:kitchen_shovel_no_oil"
 *     shovel_has_oil_item: "custom:kitchen_shovel_has_oil"
 */
public class EnamelBasinBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<EnamelBasinBehavior> FACTORY = new Factory();

    private static final float DEFAULT_VOLUME = 0.8f;
    // TODO 声音固定，若有修改需求，这里可以重写
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
        if (player == null) return InteractionResult.PASS;

        CEWorld world = context.getLevel().storageWorld();
        BlockEntity blockEntity = world.getBlockEntityAtIfLoaded(context.getClickedPos());
        if (blockEntity == null) return InteractionResult.PASS;

        EnamelBasinController controller = blockEntity.controller.get(EnamelBasinController.class, this.controllerId);
        Player bukkitPlayer = player.platformPlayer();
        ItemStack heldItem = bukkitPlayer.getInventory().getItemInMainHand();
        boolean isSneaking = bukkitPlayer.isSneaking();

        // 彩蛋 木棍，潜行 + 无油厨铲
        if (handleEasterEgg(heldItem, isSneaking, world, context.getClickedPos(), bukkitPlayer) != null) {
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        // 盆处于关闭状态时先打开
        if (controller.isClosed()) {
            playSound(world, context.getClickedPos(), OPEN_CLOSE_SOUND, DEFAULT_VOLUME, 0.8f);
            controller.setClosed(false);
            bukkitPlayer.swingMainHand();
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        // 盆已打开 处理加油/取油与关闭
        return handleOpenBasinInteraction(heldItem, isSneaking, controller, world, context.getClickedPos(), bukkitPlayer, player);
    }

    private InteractionResult handleEasterEgg(ItemStack heldItem, boolean isSneaking,
                                              CEWorld world, net.momirealms.craftengine.core.world.BlockPos pos,
                                              Player player) {
        boolean isEasterEgg = heldItem.getType() == Material.STICK ||
                (isCustomItem(heldItem, shovelNoOilItem) && isSneaking);
        if (isEasterEgg) {
            playSound(world, pos, OPEN_CLOSE_SOUND, DEFAULT_VOLUME, 0.8f);
            player.swingMainHand();
            return InteractionResult.SUCCESS;
        }
        return null;
    }

    private InteractionResult handleOpenBasinInteraction(ItemStack heldItem, boolean isSneaking,
                                                         EnamelBasinController controller, CEWorld world,
                                                         net.momirealms.craftengine.core.world.BlockPos pos,
                                                         Player bukkitPlayer, BukkitServerPlayer player) {
        // 空手 + 潜行 逐个取出油
        if (heldItem.getType() == Material.AIR && isSneaking) {
            if (controller.getOilCount() > 0) {
                net.momirealms.craftengine.bukkit.item.BukkitItem oilItem =
                        net.momirealms.craftengine.bukkit.item.BukkitItemManager.instance()
                                .createWrappedItem(this.oilItem, player);
                if (oilItem != null) {
                    controller.removeOil(1);

                    net.momirealms.craftengine.core.item.Item animationItem = oilItem.copyWithCount(1);

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
            }
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        // 空手（不潜行） 关闭盆
        if (heldItem.getType() == Material.AIR) {
            playSound(world, pos, OPEN_CLOSE_SOUND, DEFAULT_VOLUME, 0.4f);
            controller.setClosed(true);
            bukkitPlayer.swingMainHand();
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        // 手持无油厨铲 从盆中沾油，转为有油厨铲
        if (isCustomItem(heldItem, shovelNoOilItem)) {
            if (controller.getOilCount() > 0) {
                controller.removeOil(1);
                net.momirealms.craftengine.bukkit.item.BukkitItem newShovel =
                        net.momirealms.craftengine.bukkit.item.BukkitItemManager.instance()
                                .createWrappedItem(shovelHasOilItem, player);
                if (newShovel != null) {
                    bukkitPlayer.getInventory().setItemInMainHand(ItemStackUtils.getBukkitStack(newShovel));
                }
                playSound(world, pos, OIL_SOUND, DEFAULT_VOLUME, 0.8f);
                bukkitPlayer.swingMainHand();
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        // 手持油 一次性倒入直至装满
        if (isCustomItem(heldItem, oilItem)) {
            int currentOil = controller.getOilCount();
            int canAdd = maxOil - currentOil;
            if (canAdd > 0) {
                int toAdd = Math.min(heldItem.getAmount(), canAdd);
                controller.addOil(toAdd);
                heldItem.setAmount(heldItem.getAmount() - toAdd);
                playSound(world, pos, OIL_SOUND, DEFAULT_VOLUME, 0.8f);
                bukkitPlayer.swingMainHand();
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        // 手持有油厨铲 把油倒入盆中，转为无油厨铲
        if (isCustomItem(heldItem, shovelHasOilItem)) {
            if (controller.getOilCount() < maxOil) {
                controller.addOil(1);
                net.momirealms.craftengine.bukkit.item.BukkitItem newShovel =
                        net.momirealms.craftengine.bukkit.item.BukkitItemManager.instance()
                                .createWrappedItem(shovelNoOilItem, player);
                if (newShovel != null) {
                    bukkitPlayer.getInventory().setItemInMainHand(ItemStackUtils.getBukkitStack(newShovel));
                }
                playSound(world, pos, OIL_SOUND, DEFAULT_VOLUME, 0.8f);
                bukkitPlayer.swingMainHand();
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        // 手持其它物品 关闭盆
        playSound(world, pos, OPEN_CLOSE_SOUND, DEFAULT_VOLUME, 0.4f);
        controller.setClosed(true);
        bukkitPlayer.swingMainHand();
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // TODO 这里写的有点复杂和抽象，直接接入Item的轮子给玩家就好了，如不要动画效果或者出现bug或者性能超过预期这里重写
    private void spawnFakeItemFromBlock(net.momirealms.craftengine.core.entity.player.Player player,
                                        net.momirealms.craftengine.core.item.Item item,
                                        net.momirealms.craftengine.core.world.BlockPos blockPos) {
        net.momirealms.craftengine.core.world.Vec3d blockCenter = Vec3d.atCenterOf(blockPos);
        net.momirealms.craftengine.core.world.Vec3d playerPos = player.position().toVec3d();

        double distanceSq = Vec3d.distanceToSqr(blockCenter, playerPos);
        double distance = Math.sqrt(distanceSq);

        double speed = 0.6;
        double velX = (playerPos.x() - blockCenter.x()) / distance * speed;
        double velY = (playerPos.y() + 0.5 - blockCenter.y()) / distance * speed + 0.1;
        double velZ = (playerPos.z() - blockCenter.z()) / distance * speed;

        int entityId = net.momirealms.craftengine.proxy.minecraft.world.entity.EntityProxy.ENTITY_COUNTER.incrementAndGet();

        Object addEntityPacket = net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundAddEntityPacketProxy.INSTANCE.newInstance(
                entityId,
                java.util.UUID.randomUUID(),
                blockCenter.x(),
                blockCenter.y(),
                blockCenter.z(),
                0, 0,
                net.momirealms.craftengine.proxy.minecraft.world.entity.EntityTypeProxy.ITEM,
                0,
                net.momirealms.craftengine.proxy.minecraft.world.phys.Vec3Proxy.INSTANCE.newInstance(velX, velY, velZ),
                0
        );

        Object itemMetaPacket = net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundSetEntityDataPacketProxy.INSTANCE.newInstance(
                entityId,
                java.util.List.of(net.momirealms.craftengine.bukkit.entity.data.item.ItemEntityData.Item.createEntityData(item.copyWithCount(1).minecraftItem()))
        );

        player.sendPackets(java.util.List.of(addEntityPacket, itemMetaPacket), false);

        player.world().playSound(player.position(),
                net.momirealms.craftengine.core.sound.Sounds.ENTITY_ITEM_PICKUP,
                0.2F,
                ((net.momirealms.craftengine.core.util.random.RandomUtils.generateRandomFloat() -
                        net.momirealms.craftengine.core.util.random.RandomUtils.generateRandomFloat()) * 0.7F + 1.0F) * 2.0F,
                net.momirealms.craftengine.core.sound.SoundSource.PLAYER);

        long delayTicks = (long) Math.ceil(distance / speed / 0.05 * 0.05);

        net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine.instance().scheduler().platform().runLater(
                () -> {
                    player.sendPacket(net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacketProxy.INSTANCE.newInstance(
                            net.momirealms.craftengine.core.util.MiscUtils.init(new it.unimi.dsi.fastutil.ints.IntArrayList(), k -> k.add(entityId))
                    ), false);
                },
                null,
                delayTicks,
                (org.bukkit.entity.Player) player.platformPlayer()
        );
    }

    private boolean isCustomItem(ItemStack item, Key expectedKey) {
        if (item == null || item.getType() == Material.AIR) return false;
        net.momirealms.craftengine.core.item.Item ceItem =
                net.momirealms.craftengine.bukkit.item.BukkitItemManager.instance().wrap(item);
        return ItemMatch.is(ceItem, expectedKey);
    }

    private void playSound(CEWorld world, net.momirealms.craftengine.core.world.BlockPos pos,
                           Sound sound, float volume, float pitch) {
        Key soundKey;
        if (sound == OPEN_CLOSE_SOUND) {
            soundKey = OPEN_CLOSE_SOUND_KEY;
        } else if (sound == OIL_SOUND) {
            soundKey = OIL_SOUND_KEY;
        } else {
            org.bukkit.NamespacedKey key = org.bukkit.Registry.SOUNDS.getKey(sound);
            if (key == null) return;
            soundKey = net.momirealms.craftengine.core.util.Key.of(key.toString());
        }

        world.world().playSound(
                Vec3d.atCenterOf(pos),
                soundKey,
                volume,
                pitch,
                net.momirealms.craftengine.core.sound.SoundSource.BLOCK
        );
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
