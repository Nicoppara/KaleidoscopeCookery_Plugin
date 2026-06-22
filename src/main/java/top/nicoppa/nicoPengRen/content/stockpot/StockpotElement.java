package top.nicoppa.nicoPengRen.content.stockpot;

import it.unimi.dsi.fastutil.ints.IntList;
import net.momirealms.craftengine.bukkit.entity.data.BaseEntityData;
import net.momirealms.craftengine.bukkit.entity.data.DisplayData;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.util.KeyUtils;
import net.momirealms.craftengine.bukkit.util.RegistryUtils;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.MiscUtils;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.proxy.minecraft.core.registries.BuiltInRegistriesProxy;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundAddEntityPacketProxy;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacketProxy;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundSetEntityDataPacketProxy;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundUpdateAttributesPacketProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.EntityProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.EntityTypeProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.ai.attributes.AttributeInstanceProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.ai.attributes.AttributesProxy;
import net.momirealms.craftengine.proxy.minecraft.world.phys.Vec3Proxy;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import top.nicoppa.nicoPengRen.common.render.ItemDisplayPackets;
import top.nicoppa.nicoPengRen.common.render.ItemDisplaySet;
import top.nicoppa.nicoPengRen.common.render.PacketBundles;
import top.nicoppa.nicoPengRen.common.entity.PufferfishData;
import top.nicoppa.nicoPengRen.recipe.food.SoupBaseRegistry;

import java.util.*;

/**
 * 高汤锅渲染元素 用 item_display 展示食材/液面、增加真实生物渲染
 * 食材实体与收发委托 {@link ItemDisplaySet} 液面/鱼为各 1 个独立实体，单独维护
 * 数据来源 {@link StockpotController}，交互入口见 {@link StockpotBehavior}。
 */
public final class StockpotElement implements BlockEntityElement {
    private final StockpotController controller;
    private final WorldPosition basePos;

    private final ItemDisplaySet display = new ItemDisplaySet(StockpotController.MAX_INGREDIENTS);

    private final int liquidEntityId;
    private final UUID liquidUuid;
    private Object liquidSpawnPacket;

    private final int fishEntityId;
    private final UUID fishUuid;
    private Object fishSpawnPacket;
    private Object fishMetaPacket;

    private static final Key STOVE_FINISHED = Key.of("show:stove_finished");

    private Key currentLiquidModel() {
        if (controller.stage() == StockpotStage.FINISHED) return STOVE_FINISHED;
        return SoupBaseRegistry.instance().showModel(controller.getSoupBaseId());
    }

    // FINISHED 时液面随剩余份数下降，高度比例最低 0.1
    private float liquidYOffset() {
        if (controller.stage() != StockpotStage.FINISHED) return 0f;
        int max = Math.max(1, controller.getFinishedMax());
        float fraction = Math.max(0.1f, (float) controller.getTakeoutCount() / max);
        return -(1f - fraction) * 0.3f;
    }

    // 食材分布情况以及位置
    private static float ingredientX(int hash) { return ((hash % 100) - 50) * 0.003f; }
    private static float ingredientZ(int hash) { return (((hash / 100) % 100) - 50) * 0.003f; }
    private static float ingredientY(int hash) { return 0.23f + (hash % 16) * 0.003f; }

    private static Quaternionf ingredientRot(int hash) {
        return new Quaternionf()
                .rotateX((float) Math.toRadians(85 + hash % 10))
                .rotateY((float) Math.toRadians((hash % 2 == 0 ? -1 : 1) * 20 + hash % 10))
                .rotateZ((float) Math.toRadians(hash % 360));
    }

    private ItemDisplayPackets ingredientPackets(Item item, boolean withPos, float animY) {
        int h = item.minecraftItem().hashCode();
        ItemDisplayPackets p = withPos ? ItemDisplayPackets.at(basePos) : ItemDisplayPackets.builder();
        return p.item(item).scale(0.5f)
                .translation(ingredientX(h), ingredientY(h) + animY, ingredientZ(h))
                .leftRotation(ingredientRot(h))
                .itemTransform((byte) 8);
    }

    private static float ingredientAnimY(int hash, long time) {
        return (float) (Math.sin((hash + time) * 0.0005) * 0.05);
    }

    public StockpotElement(StockpotController controller, WorldPosition position) {
        this.controller = controller;
        this.basePos = position;

        liquidEntityId = EntityProxy.ENTITY_COUNTER.incrementAndGet();
        liquidUuid = UUID.randomUUID();
        fishEntityId = EntityProxy.ENTITY_COUNTER.incrementAndGet();
        fishUuid = UUID.randomUUID();

        refreshPackets();
    }

    public void refreshPackets() {
        List<Item> ingredients = controller.getIngredients();

        for (int i = 0; i < StockpotController.MAX_INGREDIENTS; i++) {
            if (i < ingredients.size()) {
                ItemDisplayPackets packets = ingredientPackets(ingredients.get(i), true, 0f);
                display.setPackets(i, packets.spawn(display.id(i), display.uuid(i)), packets.meta(display.id(i)));
            } else {
                display.clear(i);
            }
        }

        refreshLiquidAndFishPackets();
    }

    public void updateAnimation(Player player) {
        if (controller.hasLid()) return;

        List<Item> ingredients = controller.getIngredients();
        long time = System.currentTimeMillis();

        for (int i = 0; i < ingredients.size(); i++) {
            if (display.spawn(i) != null) {
                int itemHash = ingredients.get(i).minecraftItem().hashCode();
                float animY = (float) (Math.sin((itemHash + time) * 0.0005) * 0.05);

                List<Object> dataValues = new ArrayList<>();

                if (VersionHelper.isOrAbove1_20_2) {
                    DisplayData.ItemDisplayData.TransformationInterpolationDelay.addEntityData(0, dataValues);
                    DisplayData.ItemDisplayData.TransformationInterpolationDuration.addEntityData(3, dataValues);
                } else {
                    DisplayData.ItemDisplayData.InterpolationDuration.addEntityData(3, dataValues);
                }

                DisplayData.ItemDisplayData.Translation.addEntityData(
                        new Vector3f(ingredientX(itemHash), ingredientY(itemHash) + animY, ingredientZ(itemHash)), dataValues);

                Object metaPacket = ClientboundSetEntityDataPacketProxy.INSTANCE.newInstance(
                        display.id(i), dataValues);
                player.sendPacket(metaPacket, false);
            }
        }
    }

    private void refreshLiquidAndFishPackets() {
        String id = controller.getSoupBaseId().toString();

        boolean hasSoupBase = currentLiquidModel() != null;

        if (hasSoupBase) {
            this.liquidSpawnPacket = ClientboundAddEntityPacketProxy.INSTANCE.newInstance(
                    liquidEntityId, liquidUuid,
                    basePos.x - 0.2f, basePos.y + 0.8f, basePos.z - 0.2f,
                    0, 0, EntityTypeProxy.ITEM_DISPLAY, 0, Vec3Proxy.ZERO, 0
            );
        } else {
            this.liquidSpawnPacket = null;
        }

        Object fishType = controller.stage() == StockpotStage.FINISHED ? null : getFishEntityType(id);
        if (fishType != null) {
            this.fishSpawnPacket = ClientboundAddEntityPacketProxy.INSTANCE.newInstance(
                    fishEntityId, fishUuid,
                    basePos.x + 0.03, basePos.y + 0.1, basePos.z + 0.03,
                    0, -45, fishType, 0, Vec3Proxy.ZERO, -45
            );
            List<Object> fishData = new ArrayList<>();
            BaseEntityData.SharedFlags.addEntityData((byte) 0x04, fishData); // NoAI
            BaseEntityData.NoGravity.addEntityData(true, fishData);
            BaseEntityData.Silent.addEntityData(true, fishData);

            Object pufferfishType = getEntityTypeByKey("minecraft:pufferfish");
            if (fishType.equals(pufferfishType)) {
                PufferfishData.PuffState.addEntityData(1, fishData);
            }

            this.fishMetaPacket = ClientboundSetEntityDataPacketProxy.INSTANCE.newInstance(fishEntityId, fishData);
        } else {
            this.fishSpawnPacket = null;
            this.fishMetaPacket = null;
        }
    }

    private Object createLiquidMetaPacket(Player player) {
        Key model = currentLiquidModel();
        if (model == null) return null;
        Item item = BukkitItemManager.instance().createWrappedItem(model, player);
        if (item == null) return null;
        item = BukkitItemManager.instance().s2c(item, player).orElse(item);

        List<Object> dataValues = new ArrayList<>();
        if (VersionHelper.isOrAbove1_20_2) {
            DisplayData.ItemDisplayData.TransformationInterpolationDelay.addEntityData(0, dataValues);
            DisplayData.ItemDisplayData.TransformationInterpolationDuration.addEntityData(5, dataValues);
        }
        DisplayData.ItemDisplayData.ItemStack.addEntityData(item.minecraftItem(), dataValues);
        DisplayData.ItemDisplayData.Scale.addEntityData(new Vector3f(1f, 1f, 1f), dataValues);
        DisplayData.ItemDisplayData.Translation.addEntityData(new Vector3f(0f, liquidYOffset(), 0f), dataValues);
        DisplayData.ItemDisplayData.ItemTransform.addEntityData((byte) 5, dataValues); // FIXED
        return ClientboundSetEntityDataPacketProxy.INSTANCE.newInstance(liquidEntityId, dataValues);
    }

    // 根据桶物品 ID 返回对应的 NMS EntityType
    private static Object getFishEntityType(String bucketId) {
        return switch (bucketId) {
            case "minecraft:cod_bucket"          -> getEntityTypeByKey("minecraft:cod");
            case "minecraft:salmon_bucket"       -> getEntityTypeByKey("minecraft:salmon");
            case "minecraft:tropical_fish_bucket"-> getEntityTypeByKey("minecraft:tropical_fish");
            case "minecraft:pufferfish_bucket"   -> getEntityTypeByKey("minecraft:pufferfish");
            case "minecraft:axolotl_bucket"      -> getEntityTypeByKey("minecraft:axolotl");
            case "minecraft:tadpole_bucket"      -> getEntityTypeByKey("minecraft:frog");
            default -> null;
        };
    }

    private static Object getEntityTypeByKey(String key) {
        try {
            return RegistryUtils.getRegistryValue(
                    BuiltInRegistriesProxy.ENTITY_TYPE,
                    KeyUtils.toIdentifier(Key.of(key))
            );
        } catch (Exception e) {
            return null;
        }
    }

    private void sendFishPackets(Player player, List<Object> packets) {
        if (fishSpawnPacket == null || fishMetaPacket == null) return;
        packets.add(fishSpawnPacket);
        packets.add(fishMetaPacket);
        if (VersionHelper.isOrAbove1_20_5) {
            Object attributeIns = AttributeInstanceProxy.INSTANCE.newInstance$0(
                    AttributesProxy.SCALE, $ -> {});
            AttributeInstanceProxy.INSTANCE.setBaseValue(attributeIns, 0.5f);
            packets.add(ClientboundUpdateAttributesPacketProxy.INSTANCE.newInstance$0(
                    fishEntityId, Collections.singletonList(attributeIns)));
        }
    }

    @Override
    public void show(Player player) {
        if (controller.hasLid()) return;
        forceShow(player);
    }

    public void forceShow(Player player) {
        List<Item> ingredients = controller.getIngredients();
        long time = System.currentTimeMillis();

        for (int i = 0; i < StockpotController.MAX_INGREDIENTS; i++) {
            if (display.spawn(i) != null && i < ingredients.size()) {
                int itemHash = ingredients.get(i).minecraftItem().hashCode();
                Object metaPacket = ingredientPackets(ingredients.get(i), false, ingredientAnimY(itemHash, time)).meta(display.id(i));
                player.sendPackets(List.of(display.spawn(i), metaPacket), false);
            }
        }

        if (liquidSpawnPacket != null) {
            Object liquidMeta = createLiquidMetaPacket(player);
            if (liquidMeta != null) {
                player.sendPackets(List.of(liquidSpawnPacket, liquidMeta), false);
            }
        }

        if (fishSpawnPacket != null) {
            List<Object> packets = new ArrayList<>();
            sendFishPackets(player, packets);
            if (!packets.isEmpty()) player.sendPackets(packets, false);
        }
    }

    @Override
    public void hide(Player player) {
        IntList ids = MiscUtils.init(new it.unimi.dsi.fastutil.ints.IntArrayList(), a -> {
            for (int i = 0; i < display.size(); i++) a.add(display.id(i));
            a.add(liquidEntityId);
            a.add(fishEntityId);
        });
        player.sendPacket(ClientboundRemoveEntitiesPacketProxy.INSTANCE.newInstance(ids), false);
    }

    @Override
    public void update(Player player) {
        if (controller.hasLid()) {
            hide(player);
            return;
        }

        StockpotController.RenderTracker tracker = controller.renderTracker();
        StockpotStage stage = controller.stage();
        StockpotStage lastStage = tracker.stage;
        int lastIngredientCount = tracker.ingredientCount;
        List<Item> ingredients = controller.getIngredients();
        boolean soupBaseChanged = !controller.getSoupBaseId().equals(tracker.soupBaseId);

        if (lastStage == stage && lastIngredientCount == ingredients.size() && !soupBaseChanged) {
            return;
        }

        List<Object> packets = new ArrayList<>();

        if (lastIngredientCount > ingredients.size()) {
            IntList idsToRemove = new it.unimi.dsi.fastutil.ints.IntArrayList();
            for (int i = ingredients.size(); i < lastIngredientCount; i++) {
                idsToRemove.add(display.id(i));
            }
            packets.add(ClientboundRemoveEntitiesPacketProxy.INSTANCE.newInstance(idsToRemove));
        } else if (lastIngredientCount < ingredients.size()) {
            long time = System.currentTimeMillis();
            for (int i = lastIngredientCount; i < ingredients.size(); i++) {
                if (display.spawn(i) != null) {
                    int itemHash = ingredients.get(i).minecraftItem().hashCode();
                    Object metaPacket = ingredientPackets(ingredients.get(i), false, ingredientAnimY(itemHash, time)).meta(display.id(i));
                    packets.add(display.spawn(i));
                    packets.add(metaPacket);
                }
            }
        }

        if (soupBaseChanged) {
            IntList toRemove = MiscUtils.init(new it.unimi.dsi.fastutil.ints.IntArrayList(), a -> {
                a.add(liquidEntityId);
                a.add(fishEntityId);
            });
            packets.add(ClientboundRemoveEntitiesPacketProxy.INSTANCE.newInstance(toRemove));

            refreshLiquidAndFishPackets();

            if (liquidSpawnPacket != null) {
                Object liquidMeta = createLiquidMetaPacket(player);
                if (liquidMeta != null) {
                    packets.add(liquidSpawnPacket);
                    packets.add(liquidMeta);
                }
            }
            sendFishPackets(player, packets);
        }

        PacketBundles.send(player, packets);
    }

    public void showIndex(Player player, int index) {
        if (controller.hasLid()) return;
        if (index >= StockpotController.MAX_INGREDIENTS || display.spawn(index) == null) return;

        List<Item> ingredients = controller.getIngredients();
        if (index >= ingredients.size()) return;

        int itemHash = ingredients.get(index).minecraftItem().hashCode();
        long time = System.currentTimeMillis();
        Object metaPacket = ingredientPackets(ingredients.get(index), false, ingredientAnimY(itemHash, time)).meta(display.id(index));
        player.sendPackets(List.of(display.spawn(index), metaPacket), false);
    }

    public void hideIndex(Player player, int index) {
        if (index >= 0 && index < StockpotController.MAX_INGREDIENTS) {
            IntList ids = MiscUtils.init(new it.unimi.dsi.fastutil.ints.IntArrayList(),
                    a -> a.add(display.id(index)));
            player.sendPacket(ClientboundRemoveEntitiesPacketProxy.INSTANCE.newInstance(ids), false);
        }
    }

    public void clearAll(Player player) {
        hide(player);
    }

    public void onFinished(Player player) {
        IntList ids = MiscUtils.init(new it.unimi.dsi.fastutil.ints.IntArrayList(), a -> {
            for (int i = 0; i < display.size(); i++) a.add(display.id(i));
            a.add(fishEntityId);
        });
        player.sendPacket(ClientboundRemoveEntitiesPacketProxy.INSTANCE.newInstance(ids), false);
        Object meta = createLiquidMetaPacket(player);
        if (meta != null) player.sendPacket(meta, false);
    }

    public void refreshLiquidLevel(Player player) {
        Object meta = createLiquidMetaPacket(player);
        if (meta != null) player.sendPacket(meta, false);
    }
}
