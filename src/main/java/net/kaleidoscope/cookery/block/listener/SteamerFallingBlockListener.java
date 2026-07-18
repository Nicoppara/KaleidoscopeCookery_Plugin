package net.kaleidoscope.cookery.block.listener;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import net.kaleidoscope.cookery.block.behavior.SteamerBehavior;
import net.kaleidoscope.cookery.nms.NmsBridgeProvider;
import net.kaleidoscope.cookery.util.HeatSourceUtils;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.bukkit.world.BukkitWorldManager;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.proxy.bukkit.craftbukkit.entity.CraftEntityProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.BlockGetterProxy;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SteamerFallingBlockListener implements Listener {

    private static final Set<Object> ceCancelledEntities = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityChangeBlockLowest(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fb)) return;
        Object nmsEntity = getNmsHandle(fb);
        if (nmsEntity == null || !SteamerBehavior.pendingData.containsKey(nmsEntity)) return;
        // 蒸笼底层是重力方块 落地变方块时绝不能掉落原版方块物品
        fb.setDropItem(false);
        // 标记为待处理 无论此刻是否已被其它插件取消 都在 HIGHEST 阶段统一裁决 避免落地丢失
        ceCancelledEntities.add(nmsEntity);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fb)) return;
        Object nmsEntity = getNmsHandle(fb);
        if (nmsEntity == null) return;
        // 只处理被我们标记的蒸笼下落实体
        if (!ceCancelledEntities.remove(nmsEntity)) return;
        SteamerBehavior.PendingData data = SteamerBehavior.pendingData.get(nmsEntity);
        if (data == null) return;

        Block block = event.getBlock();
        CEWorld ceWorld = BukkitWorldManager.instance().getWorld(block.getWorld().getUID());
        if (ceWorld == null) return;
        Object level = ceWorld.world().minecraftWorld();
        Object landingPos = LocationUtils.toBlockPos(block.getX(), block.getY(), block.getZ());
        Object belowPos = LocationUtils.toBlockPos(block.getX(), block.getY() - 1, block.getZ());

        if (isLandingSupported(level, belowPos, data.steamerState.owner().value())) {
            // 落点受支撑 强制放行落地 由 CE 的 onLand 还原状态与 NBT
            event.setCancelled(false);
        } else {
            // 落点不受支撑 蒸笼绝不能变成地面方块 立刻取消落地并掉落
            event.setCancelled(true);
            SteamerBehavior.dropPendingSteamer(level, landingPos, nmsEntity);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fb)) return;
        Object nmsEntity = getNmsHandle(fb);
        if (nmsEntity == null) return;
        ceCancelledEntities.remove(nmsEntity);
        // pendingData 仍在 表示既没成功落地 也没被落地事件处理
        // 此时 CE 的 onLand 与 onBrokenAfterFall 都不会触发 且 dropItem 已关闭 必须手动掉落以免凭空消失
        if (!SteamerBehavior.pendingData.containsKey(nmsEntity)) return;
        Location loc = fb.getLocation();
        World bukkitWorld = loc.getWorld();
        CEWorld ceWorld = bukkitWorld == null ? null : BukkitWorldManager.instance().getWorld(bukkitWorld.getUID());
        if (ceWorld == null) {
            SteamerBehavior.pendingData.remove(nmsEntity);
            return;
        }
        Object level = ceWorld.world().minecraftWorld();
        Object blockPos = LocationUtils.toBlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        // 延迟一 tick 再掉落 成功落地时 onLand 会在本 tick 内清掉 pendingData
        // 下一 tick 若仍在 pendingData 才说明确实丢失 避免与正常落地重复掉落
        BukkitCraftEngine.instance().scheduler().platform().runLater(() -> {
            if (SteamerBehavior.pendingData.containsKey(nmsEntity)) {
                SteamerBehavior.dropPendingSteamer(level, blockPos, nmsEntity);
            }
        }, 1L, ceWorld.world(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    private boolean isLandingSupported(Object level, Object belowPos, BlockDefinition steamerDef) {
        if (HeatSourceUtils.isHeatSource(level, belowPos)) return true;
        Object belowState = BlockGetterProxy.INSTANCE.getBlockState(level, belowPos);
        ImmutableBlockState belowCustom = BlockStateUtils.getOptionalCustomBlockState(belowState).orElse(null);
        if (belowCustom == null) return false;
        if (belowCustom.owner().value() == steamerDef) return true;
        return belowCustom.owner().value().id().equals(SteamerBehavior.STOVE_BLOCK_KEY);
    }

    private static Object getNmsHandle(Entity entity) {
        return CraftEntityProxy.INSTANCE.getEntity(entity);
    }
}
