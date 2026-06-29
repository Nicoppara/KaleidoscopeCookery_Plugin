package net.kaleidoscope.cookery.item.behavior;

import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.behavior.ItemBehavior;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.World;
import net.kaleidoscope.cookery.item.ItemKeys;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

// TODO 已弃用 纯 CE 即可实现 保留作为物品行为编写参考
public class DoughPullingBehavior extends ItemBehavior {

    public static final ItemBehaviorFactory<DoughPullingBehavior> FACTORY = new Factory();
    private static final String PULL_TAG = "pull_progress";
    private static final int MAX_PULL = 4;

    @Override
    public InteractionResult use(World world, @Nullable Player player, InteractionHand hand) {
        if (player == null) {
            return InteractionResult.PASS;
        }

        Item item = player.getItemInHand(hand);
        int currentProgress = getPullProgress(item);

        if (currentProgress >= MAX_PULL) {
            convertToNoodles(player, hand);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        setPullProgress(item, currentProgress + 1);
        player.swingHand(hand);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    private int getPullProgress(Item item) {
        Integer progress = (Integer) item.getTagAsJava(PULL_TAG);
        return progress != null ? progress : 0;
    }

    private void setPullProgress(Item item, int progress) {
        item.setJavaTag(progress, PULL_TAG);
        item.customModelData(progress);
    }

    private void convertToNoodles(Player player, InteractionHand hand) {
        Item noodles = CraftEngine.instance().itemManager()
                .createWrappedItem(ItemKeys.RAW_NOODLES, player);
        player.setItemInHand(hand, noodles);
    }

    private static class Factory implements ItemBehaviorFactory<DoughPullingBehavior> {
        @Override
        public DoughPullingBehavior create(Pack pack, Path path, Key key, ConfigSection section) {
            return new DoughPullingBehavior();
        }
    }
}
