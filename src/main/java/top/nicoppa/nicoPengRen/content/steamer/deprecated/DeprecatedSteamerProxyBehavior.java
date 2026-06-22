package top.nicoppa.nicoPengRen.content.steamer.deprecated;

// TODO 已删除 可参考

// package top.nicoppa.nicoPengRen.content.steamer;
//
// import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
// import net.momirealms.craftengine.bukkit.block.behavior.BukkitFallableBlock;
// import net.momirealms.craftengine.bukkit.util.LocationUtils;
// import net.momirealms.craftengine.bukkit.world.BukkitWorldManager;
// import net.momirealms.craftengine.core.block.BlockDefinition;
// import net.momirealms.craftengine.core.block.ImmutableBlockState;
// import net.momirealms.craftengine.core.block.UpdateFlags;
// import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
// import net.momirealms.craftengine.core.block.entity.BlockEntity;
// import net.momirealms.craftengine.core.block.property.type.SlabType;
// import net.momirealms.craftengine.core.plugin.CraftEngine;
// import net.momirealms.craftengine.core.plugin.config.Config;
// import net.momirealms.craftengine.core.plugin.config.ConfigSection;
// import net.momirealms.craftengine.core.world.BlockPos;
// import net.momirealms.craftengine.core.world.CEWorld;
// import net.momirealms.craftengine.proxy.minecraft.core.Vec3iProxy;
// import net.momirealms.craftengine.proxy.minecraft.world.level.LevelProxy;
// import net.momirealms.craftengine.proxy.minecraft.world.level.LevelWriterProxy;
//
// import java.nio.file.Path;
//
// 蒸笼掉落代理方块行为 蒸笼下落时临时替身，落地还原成真蒸笼并回灌数据，摔碎则掉落物品与蒸笼。
// 配合 SteamerBehavior#pendingData 取回掉落快照，数据回灌见 SteamerController。
// public final class DeprecatedSteamerProxyBehavior extends BukkitBlockBehavior implements BukkitFallableBlock {
//     public static final BlockBehaviorFactory<DeprecatedSteamerProxyBehavior> FACTORY = new Factory();
//
//     private DeprecatedSteamerProxyBehavior(BlockDefinition blockDefinition) {
//         super(blockDefinition);
//     }
//
//     @Override
//     public void onLand(Object thisBlock, Object[] args) {
//         Object level = args[0];
//         Object blockPos = args[1];
//         Object fallingBlock = args[4];
//         SteamerBehavior.PendingData data = SteamerBehavior.pendingData.remove(fallingBlock);
//         if (data == null) return;
//
//         ImmutableBlockState steamerState = data.steamerState;
//
//         LevelWriterProxy.INSTANCE.setBlock(level, blockPos, steamerState.customBlockState().minecraftState(), 2);
//
//         BlockPos landPos = LocationUtils.fromBlockPos(blockPos);
//         org.bukkit.World bukkitWorld = LevelProxy.INSTANCE.getWorld(level);
//         CEWorld ceWorld = BukkitWorldManager.instance().getWorld(bukkitWorld.getUID());
//         BlockEntity blockEntity = ceWorld.getBlockEntityAtIfLoaded(landPos);
//         if (blockEntity != null) {
//             SteamerController controller = blockEntity.controller.get(SteamerController.class, data.controllerId);
//             if (controller != null) {
//                 controller.loadCustomData(data.tag);
//                 controller.refreshElementState();
//             }
//         }
//     }
//
//     @Override
//     public void onBrokenAfterFall(Object thisBlock, Object[] args) {
//         Object level = args[0];
//         Object blockPos = args[1];
//         Object fallingBlock = args[2];
//         SteamerBehavior.PendingData removed = SteamerBehavior.pendingData.remove(fallingBlock);
//
//         if (removed != null) {
//             try {
//                 org.bukkit.World bukkitWorld = net.momirealms.craftengine.proxy.minecraft.world.level.LevelProxy.INSTANCE.getWorld(level);
//                 net.momirealms.craftengine.core.world.CEWorld ceWorld = net.momirealms.craftengine.bukkit.world.BukkitWorldManager.instance().getWorld(bukkitWorld.getUID());
//                 net.momirealms.craftengine.core.world.BlockPos pos = net.momirealms.craftengine.bukkit.util.LocationUtils.fromBlockPos(blockPos);
//                 net.momirealms.craftengine.core.world.Vec3d dropPos = net.momirealms.craftengine.core.world.Vec3d.atCenterOf(pos);
//
//                 net.momirealms.craftengine.libraries.nbt.CompoundTag data = removed.tag.getCompound("steamer_data");
//                 if (data != null) {
//                     net.momirealms.craftengine.libraries.nbt.ListTag itemsTag = data.getList("items");
//                     if (itemsTag != null) {
//                         int dataVersion = data.getInt("data_version", Config.itemDataFixerUpperFallbackVersion());
//                         for (net.momirealms.craftengine.libraries.nbt.Tag itemTag : itemsTag) {
//                             Object nmsItem = net.momirealms.craftengine.bukkit.util.ItemStackUtils.parseMinecraftItem(itemTag, dataVersion);
//                             if (nmsItem != null) {
//                                 net.momirealms.craftengine.core.item.Item item = net.momirealms.craftengine.bukkit.util.ItemStackUtils.wrap(nmsItem);
//                                 ceWorld.world().dropItemNaturally(dropPos, item);
//                             }
//                         }
//                     }
//                 }
//
//                 net.momirealms.craftengine.core.block.property.type.SlabType type = net.momirealms.craftengine.core.block.property.type.SlabType.BOTTOM;
//                 net.momirealms.craftengine.core.block.behavior.BlockBehavior behavior = removed.steamerState.behavior();
//                 if (behavior instanceof SteamerBehavior) {
//                     type = removed.steamerState.get(((SteamerBehavior) behavior).getTypeProperty());
//                 }
//                 int amountToDrop = (removed.type == SlabType.DOUBLE) ? 2 : 1;
//                 net.momirealms.craftengine.core.util.Key steamerKey = removed.steamerState.owner().value().id();
//                 net.momirealms.craftengine.core.item.Item steamerItem = net.momirealms.craftengine.bukkit.item.BukkitItemManager.instance().createWrappedItem(steamerKey, null);
//                 if (steamerItem != null) {
//                     steamerItem = steamerItem.copyWithCount(amountToDrop);
//                     ceWorld.world().dropItemNaturally(dropPos, steamerItem);
//                 }
//             } catch (Exception e) {
//                 CraftEngine.instance().logger().error("Failed to handle broken falling steamer", e);
//             }
//         }
//     }
//
//     private static class Factory implements BlockBehaviorFactory<DeprecatedSteamerProxyBehavior> {
//         @Override
//         public DeprecatedSteamerProxyBehavior create(BlockDefinition block, ConfigSection section) {
//             return new DeprecatedSteamerProxyBehavior(block);
//         }
//     }
// }
//

// TODO 原代码参考片段
// ---------------------------------------------------------------------------
// 原 SteamerBehavior.java 中用到掉落 / 代理方块的代码片段
// ---------------------------------------------------------------------------
//
// // public final class SteamerBehavior extends BukkitBlockBehavior implements EntityBlock, BukkitFallableBlock {
//
// // public static final Map<Object, PendingData> pendingData = new ConcurrentHashMap<>();
//
// // 掉落数据快照：蒸笼变 FallingBlock 时暂存的 NBT、原方块状态、controllerId 与层类型，落地后由代理方块取回还原
// // public static class PendingData {
// //     public final CompoundTag tag;
// //     public final ImmutableBlockState steamerState;
// //     public final int controllerId;
// //     public final SlabType type;
// //
// //     public PendingData(CompoundTag tag, ImmutableBlockState steamerState, int controllerId, SlabType type) {
// //         this.tag = tag;
// //         this.steamerState = steamerState;
// //         this.controllerId = controllerId;
// //         this.type = type;
// //     }
// // }
//
// // FallingBlock 实体移除时清理暂存，避免内存泄漏
// // @EventHandler(priority = EventPriority.MONITOR)
// // public void onEntityRemove(EntityRemoveFromWorldEvent event) {
// //     if (event.getEntity() instanceof FallingBlock) {
// //         SteamerBehavior.pendingData.remove(event.getEntity());
// //     }
// // }
//
// // 失去支撑时把蒸笼变成 FallingBlock
// // @Override
// // public void neighborChanged(Object thisBlock, Object[] args) {
// //     LevelAccessorProxy.INSTANCE.scheduleTick$0(args[1], args[2], thisBlock, 2);
// // }
// //
// // @Override
// // public void tick(Object thisBlock, Object[] args) {
// //     Object blockState = args[0], level = args[1], blockPos = args[2];
// //     Optional<ImmutableBlockState> optionalCustomState = BlockStateUtils.getOptionalCustomBlockState(blockState);
// //     if (optionalCustomState.isEmpty()) return;
// //     ImmutableBlockState customState = optionalCustomState.get();
// //     Object belowPos = LocationUtils.toBlockPos(Vec3iProxy.INSTANCE.getX(blockPos), Vec3iProxy.INSTANCE.getY(blockPos) - 1, Vec3iProxy.INSTANCE.getZ(blockPos));
// //     Object belowState = BlockGetterProxy.INSTANCE.getBlockState(level, belowPos);
// //
// //     if (!FallingBlockProxy.INSTANCE.isFree(belowState)) return;
// //     if (HeatSourceUtils.isHeatSource(level, belowPos)) return;
// //     ImmutableBlockState belowCustom = BlockStateUtils.getOptionalCustomBlockState(belowState).orElse(null);
// //     if (belowCustom != null && belowCustom.owner().value() == super.blockDefinition) return;
// //
// //     BlockPos pos = LocationUtils.fromBlockPos(blockPos);
// //     Object abovePos = LocationUtils.toBlockPos(pos.x(), pos.y() + 1, pos.z());
// //     ImmutableBlockState aboveCustom = BlockStateUtils.getOptionalCustomBlockState(BlockGetterProxy.INSTANCE.getBlockState(level, abovePos)).orElse(null);
// //     boolean isTopLayer = aboveCustom == null || aboveCustom.owner().value() != super.blockDefinition;
// //
// //     CompoundTag tag = new CompoundTag();
// //     BlockEntity blockEntity = BukkitWorldManager.instance().getWorld(LevelProxy.INSTANCE.getWorld(level).getUID()).getBlockEntityAtIfLoaded(pos);
// //     if (blockEntity != null) {
// //         SteamerController controller = blockEntity.controller.get(SteamerController.class, this.controllerId);
// //         if (controller != null) {
// //             controller.saveCustomData(tag);
// //             Arrays.fill(controller.getItems(), Item.empty());
// //         }
// //     }
// //
// //     Object proxyNmsState = getProxyNmsState(customState, isTopLayer);
// //     Object fallingBlockEntity = FastNMS.INSTANCE.createInjectedFallingBlockEntity(level, blockPos, proxyNmsState != null ? proxyNmsState : blockState);
// //     pendingData.put(fallingBlockEntity, new PendingData(tag, customState, this.controllerId, customState.get(this.typeProperty)));
// // }
// //
// // 构造代理方块的 NMS 方块状态（落地后由 SteamerProxyBehavior 还原成真蒸笼）
// // private Object getProxyNmsState(ImmutableBlockState steamerState, boolean isTopLayer) {
// //     BlockDefinition proxyDef = CraftEngineBlocks.byId(Key.of("custom:steamer_proxy"));
// //     if (proxyDef == null) return null;
// //     try {
// //         CompoundTag tag = new CompoundTag();
// //         tag.putString("facing", steamerState.get(this.facingProperty).name().toLowerCase());
// //         tag.putString("type", steamerState.get(this.typeProperty).name().toLowerCase());
// //         tag.putBoolean("has_lid", isTopLayer);
// //         return proxyDef.getBlockState(tag).customBlockState().minecraftState();
// //     } catch (Exception e) {
// //         return null;
// //     }
// // }
// //
// // @Override
// // public void onLand(Object thisBlock, Object[] args) {}
// //
// // LevelAccessorProxy.INSTANCE.scheduleTick$0(level, blockPos, thisBlock, 2);