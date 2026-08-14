package net.kaleidoscope.cookery.util;

import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.WorldPosition;

import java.util.List;

// 掉落工具 把物品自然掉落到方块实体所在格子的中心
public final class DropUtils {
    private DropUtils() {}

    // 方块实体被移除时掉落 放置被领地插件回滚而触发的那次移除绝不能掉 见 PlacementGuard
    public static void dropOnRemove(BlockEntity blockEntity, Item item) {
        if (PlacementGuard.isPlacing(blockEntity)) {
            return;
        }
        dropAtCenter(blockEntity, item);
    }

    public static void dropAtCenter(BlockEntity blockEntity, Item item) {
        if (ItemUtils.isEmpty(item)) {
            return;
        }
        blockEntity.world.world().dropItemNaturally(Vec3d.atCenterOf(blockEntity.pos), item);
    }

    // 吃完退还的容器掉在玩家脚下 别直接塞背包
    public static void dropAtPlayer(Player player, Item item) {
        player.world().dropItemNaturally(player.position(), item);
    }

    // 战利品一次性掉在同一点 空物品跳过
    public static void dropAll(World world, WorldPosition position, List<Item> items) {
        for (Item item : items) {
            if (!ItemUtils.isEmpty(item)) {
                world.dropItemNaturally(position, item);
            }
        }
    }
}
