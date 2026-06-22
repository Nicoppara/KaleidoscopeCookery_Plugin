package top.nicoppa.nicoPengRen.common.item;

import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.world.Vec3d;

/**
 * 掉落工具 把物品自然掉落到方块实体所在格子的中心
 */
public final class DropUtils {
    private DropUtils() {}

    public static void dropAtCenter(BlockEntity blockEntity, Item item) {
        if (item == null || item.isEmpty()) return;
        blockEntity.world.world().dropItemNaturally(Vec3d.atCenterOf(blockEntity.pos), item);
    }
}
