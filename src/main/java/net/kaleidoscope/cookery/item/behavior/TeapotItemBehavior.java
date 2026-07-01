package net.kaleidoscope.cookery.item.behavior;

import net.kaleidoscope.cookery.block.entity.TeacupCoasterController;
import net.kaleidoscope.cookery.block.entity.TeapotBar;
import net.kaleidoscope.cookery.block.entity.TeapotController;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.momirealms.craftengine.bukkit.block.BukkitBlockManager;
import net.momirealms.craftengine.bukkit.item.behavior.BlockItemBehavior;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.pack.PendingConfigSection;
import net.momirealms.craftengine.core.plugin.config.ConfigConstants;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.ConfigValue;
import net.momirealms.craftengine.core.util.AdventureHelper;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.Tag;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

// 茶壶物品 视线指向水/岩浆源时右键装满 否则照常放置
public class TeapotItemBehavior extends BlockItemBehavior {
    public static final ItemBehaviorFactory<TeapotItemBehavior> FACTORY = new Factory();
    private static final double REACH = 5.0;

    public TeapotItemBehavior(Key blockId) {
        super(blockId);
    }

    @Override
    public InteractionResult use(World world, @Nullable Player player, InteractionHand hand) {
        if (player != null && tryFill(player, hand)) {
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        return super.use(world, player, hand);
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null) {
            InteractionHand hand = context.getHand();
            if (tryFill(player, hand) || tryPour(context, player, hand)) {
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
        }
        return super.useOnBlock(context);
    }

    // 手持完成态茶壶右键杯垫 倒入首个空杯 倒一份扣一份 倒空恢复空壶
    private boolean tryPour(UseOnContext context, Player player, InteractionHand hand) {
        Item held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            return false;
        }
        Tag tag = held.getSparrowTag(TeapotBar.ITEM_DATA_KEY);
        if (!(tag instanceof CompoundTag data) || data.getInt("status", 0) != TeapotController.FINISHED) {
            return false;
        }
        Tag resultTag = data.get("result");
        if (resultTag == null) {
            return false;
        }
        int version = Config.itemDataFixerUpperFallbackVersion();
        Object nms = ItemStackUtils.parseMinecraftItem(resultTag, version);
        Item tea = nms == null ? Item.empty() : ItemStackUtils.wrap(nms);
        if (tea.isEmpty()) {
            return false;
        }
        BlockEntity blockEntity = context.getLevel().storageWorld().getBlockEntityAtIfLoaded(context.getClickedPos());
        if (blockEntity == null) {
            return false;
        }
        TeacupCoasterController coaster = blockEntity.controller.get(TeacupCoasterController.class, 0);
        if (coaster == null || !coaster.pourInto(tea.id())) {
            return false;
        }
        // 创造不消耗手中茶壶 其余每倒一杯扣一份(共8份) 液体条一格格清空 倒空恢复空壶
        if (!player.canInstabuild()) {
            String fluidStr = data.getString("fluid");
            Key fluidKey = (fluidStr == null || fluidStr.isEmpty()) ? null : Key.of(fluidStr);
            int remaining = data.getInt("servings", TeapotBar.CELLS) - 1;
            String barStr;
            if (remaining <= 0) {
                held.setSparrowTag(new CompoundTag(), TeapotBar.ITEM_DATA_KEY);
                barStr = TeapotBar.build(null);
            } else {
                data.putInt("servings", remaining);
                held.setSparrowTag(data, TeapotBar.ITEM_DATA_KEY);
                barStr = TeapotBar.build(fluidKey, remaining);
            }
            held.loreJson(List.of(AdventureHelper.componentToJson(
                    AdventureHelper.miniMessage().deserialize("<!i>" + barStr))));
            player.setItemInHand(hand, held);
            player.sendActionBar(AdventureHelper.miniMessage().deserialize(barStr));
        }
        player.swingHand(hand);
        return true;
    }

    // 流体射线优先命中水/岩浆源就装满 比放置点近时即视为对着水
    private boolean tryFill(Player player, InteractionHand hand) {
        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) player.platformPlayer();
        RayTraceResult result = bukkitPlayer.rayTraceBlocks(REACH, FluidCollisionMode.SOURCE_ONLY);
        if (result == null || result.getHitBlock() == null) {
            return false;
        }
        Material type = result.getHitBlock().getType();
        Key fluid;
        if (type == Material.WATER) {
            fluid = ItemKeys.WATER;
        } else if (type == Material.LAVA) {
            fluid = ItemKeys.LAVA;
        } else {
            return false;
        }
        Item held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            return false;
        }
        // 茶壶已有液体或已存茶(pdc)时不可再装
        Tag existing = held.getSparrowTag(TeapotBar.ITEM_DATA_KEY);
        if (existing instanceof CompoundTag tag) {
            String had = tag.getString("fluid");
            if ((had != null && !had.isEmpty()) || tag.containsKey("result")) {
                return false;
            }
        }
        CompoundTag data = new CompoundTag();
        data.putString("fluid", fluid.asString());
        held.setSparrowTag(data, TeapotBar.ITEM_DATA_KEY);
        held.loreJson(List.of(AdventureHelper.componentToJson(
                AdventureHelper.miniMessage().deserialize("<!i>" + TeapotBar.build(fluid)))));
        player.setItemInHand(hand, held);
        player.sendActionBar(AdventureHelper.miniMessage().deserialize(TeapotBar.build(fluid)));
        // 取液复用桶音效 岩浆用岩浆桶 其余用水桶
        Sound sound = type == Material.LAVA
                ? Sound.ITEM_BUCKET_FILL_LAVA : Sound.ITEM_BUCKET_FILL;
        bukkitPlayer.playSound(bukkitPlayer.getLocation(), sound, 1.0f, 1.0f);
        player.swingHand(hand);
        return true;
    }

    private static class Factory implements ItemBehaviorFactory<TeapotItemBehavior> {
        @Override
        public TeapotItemBehavior create(Pack pack, Path path, Key key, ConfigSection section) {
            ConfigValue blockValue = section.getNonNullValue("block", ConfigConstants.ARGUMENT_SECTION);
            if (blockValue.is(Map.class)) {
                BukkitBlockManager.instance().blockParser().addPendingConfigSection(
                        new PendingConfigSection(pack, path, key, blockValue.getAsSection()));
                return new TeapotItemBehavior(key);
            }
            return new TeapotItemBehavior(blockValue.getAsIdentifier());
        }
    }
}
