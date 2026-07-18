package net.kaleidoscope.cookery.item.behavior;

import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.item.LunchBagContents;
import net.kaleidoscope.cookery.item.LunchBagEating;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.behavior.ItemBehavior;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.bukkit.Sound;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

// 嬗变饭袋 右键换成进食态由原版播放进食动画 潜行右键把背包里的熟牛肉装进去
public class TransmutationLunchBagBehavior extends ItemBehavior {
    public static final ItemBehaviorFactory<TransmutationLunchBagBehavior> FACTORY = new Factory();

    @Override
    public InteractionResult use(World world, @Nullable Player player, InteractionHand hand) {
        if (player == null) {
            return InteractionResult.PASS;
        }
        Item bag = player.getItemInHand(hand);
        if (!LunchBagContents.isLunchBag(bag)) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            return fill(player, bag);
        }
        if (LunchBagContents.isEmpty(bag)) {
            return InteractionResult.PASS;
        }
        LunchBagEating.begin(player, hand, bag);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 指向方块时 CE 不会调 use 只调 useOnBlock 行为一致
    // 方块本身可交互(如箱子)且玩家没潜行时 CE 在这之前就 return 了 不会误吃
    @Override
    public InteractionResult useOnBlock(UseOnContext context) {
        return use(context.getLevel(), context.getPlayer(), context.getHand());
    }

    private InteractionResult fill(Player player, Item bag) {
        int owned = InventoryUtils.countItem(player, ItemKeys.COOKED_BEEF);
        if (owned <= 0) {
            return InteractionResult.PASS;
        }
        Item beef = InventoryUtils.createOrEmpty(ItemKeys.COOKED_BEEF);
        if (ItemUtils.isEmpty(beef)) {
            return InteractionResult.PASS;
        }
        int added = LunchBagContents.add(bag, beef.copyWithCount(owned));
        if (added <= 0) {
            return InteractionResult.PASS;
        }
        InventoryUtils.consumeItem(player, ItemKeys.COOKED_BEEF, added);
        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) player.platformPlayer();
        bukkitPlayer.playSound(bukkitPlayer.getLocation(), Sound.ITEM_BUNDLE_INSERT, 1.0f, 1.0f);
        player.swingHand(InteractionHand.MAIN_HAND);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    private static class Factory implements ItemBehaviorFactory<TransmutationLunchBagBehavior> {
        @Override
        public TransmutationLunchBagBehavior create(Pack pack, Path path, Key key, ConfigSection section) {
            return new TransmutationLunchBagBehavior();
        }
    }
}
