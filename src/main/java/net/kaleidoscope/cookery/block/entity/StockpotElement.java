package net.kaleidoscope.cookery.block.entity;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.momirealms.craftengine.bukkit.entity.data.BaseEntityData;
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
import net.momirealms.craftengine.bukkit.util.EntityUtils;
import net.momirealms.craftengine.proxy.minecraft.world.entity.EntityTypesProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.ai.attributes.AttributeInstanceProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.ai.attributes.AttributesProxy;
import net.momirealms.craftengine.proxy.minecraft.world.phys.Vec3Proxy;
import org.joml.Quaternionf;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplayPackets;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplaySet;
import net.kaleidoscope.cookery.block.entity.render.PacketBundles;
import net.kaleidoscope.cookery.entity.data.PufferfishData;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.recipe.SoupBaseRegistry;

import java.util.*;

public final class StockpotElement implements BlockEntityElement {
    private static final byte ITEM_TRANSFORM_HEAD = 5;
    private static final int LIQUID_INTERPOLATION = 5;

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

    private Key currentLiquidModel() {
        if (controller.stage() == StockpotStage.FINISHED) return ItemKeys.STOVE_FINISHED;
        return SoupBaseRegistry.instance().showModel(controller.soupBaseId());
    }

    // FINISHED 时液面随剩余份数下降 高度比例最低 0.1
    private float liquidYOffset() {
        if (controller.stage() != StockpotStage.FINISHED) return 0f;
        int max = Math.max(1, controller.finishedMax());
        float fraction = Math.max(0.1f, (float) controller.takeoutCount() / max);
        return -(1f - fraction) * 0.3f;
    }

    private static float ingredientX(int hash) {
        return ((hash % 100) - 50) * 0.003f;
    }

    private static float ingredientZ(int hash) {
        return (((hash / 100) % 100) - 50) * 0.003f;
    }

    private static float ingredientY(int hash) {
        return 0.23f + (hash % 16) * 0.003f;
    }

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

        liquidEntityId = EntityUtils.ENTITY_COUNTER.incrementAndGet();
        liquidUuid = UUID.randomUUID();
        fishEntityId = EntityUtils.ENTITY_COUNTER.incrementAndGet();
        fishUuid = UUID.randomUUID();

        refreshPackets();
    }

    public void refreshPackets() {
        List<Item> ingredients = controller.ingredients();

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

    // interpDuration 与控制器的发送间隔一致 客户端两次发包之间正好插值衔接 浮动才连续
    // 包内容不含玩家相关数据 整段建一次 bundle 给所有收件人复用 别按玩家重复构建
    public Object buildAnimationBundle(int interpDuration) {
        if (controller.hasLid()) return null;

        List<Item> ingredients = controller.ingredients();
        long time = System.currentTimeMillis();
        List<Object> packets = new ArrayList<>();

        for (int i = 0; i < ingredients.size(); i++) {
            if (display.spawn(i) != null) {
                int itemHash = ingredients.get(i).minecraftItem().hashCode();
                float animY = ingredientAnimY(itemHash, time);
                packets.add(ItemDisplayPackets.builder()
                        .translation(ingredientX(itemHash), ingredientY(itemHash) + animY, ingredientZ(itemHash))
                        .interpolation(interpDuration, 0)
                        .meta(display.id(i)));
            }
        }
        return packets.isEmpty() ? null : PacketBundles.of(packets);
    }

    private void refreshLiquidAndFishPackets() {
        String id = controller.soupBaseId().asString();

        boolean hasSoupBase = currentLiquidModel() != null;

        if (hasSoupBase) {
            this.liquidSpawnPacket = ClientboundAddEntityPacketProxy.INSTANCE.newInstance(
                    liquidEntityId, liquidUuid,
                    basePos.x - 0.2f, basePos.y + 0.8f, basePos.z - 0.2f,
                    0, 0, EntityTypesProxy.ITEM_DISPLAY, 0, Vec3Proxy.ZERO, 0
            );
        } else {
            this.liquidSpawnPacket = null;
        }

        Object fishType = controller.stage() == StockpotStage.FINISHED ? null : FISH_TYPE_BY_BUCKET.get(id);
        if (fishType != null) {
            this.fishSpawnPacket = ClientboundAddEntityPacketProxy.INSTANCE.newInstance(
                    fishEntityId, fishUuid,
                    basePos.x + 0.03, basePos.y + 0.1, basePos.z + 0.03,
                    0, -45, fishType, 0, Vec3Proxy.ZERO, -45
            );
            List<Object> fishData = new ArrayList<>();
            BaseEntityData.SharedFlags.addEntityData((byte) 0x04, fishData);
            BaseEntityData.NoGravity.addEntityData(true, fishData);
            BaseEntityData.Silent.addEntityData(true, fishData);

            if (fishType.equals(PUFFERFISH_TYPE)) {
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
        Item item = InventoryUtils.createOrEmpty(model, player);
        if (item == null) return null;
        item = BukkitItemManager.instance().s2c(item, player).orElse(item);

        // interpolation 5 是盛出时液面下降的一次性平滑 不是连续动画 不受动画视距 gate
        return ItemDisplayPackets.builder()
                .interpolation(LIQUID_INTERPOLATION, 0)
                .item(item)
                .scale(1f)
                .translation(0f, liquidYOffset(), 0f)
                .itemTransform(ITEM_TRANSFORM_HEAD)
                .meta(liquidEntityId);
    }

    // 注册表查找恒定 启动时算一次 别每次刷新都查
    private static final Map<String, Object> FISH_TYPE_BY_BUCKET = buildFishTypes();
    private static final Object PUFFERFISH_TYPE = getEntityTypeByKey("minecraft:pufferfish");

    private static Map<String, Object> buildFishTypes() {
        Map<String, Object> types = new HashMap<>();
        types.put("minecraft:cod_bucket", getEntityTypeByKey("minecraft:cod"));
        types.put("minecraft:salmon_bucket", getEntityTypeByKey("minecraft:salmon"));
        types.put("minecraft:tropical_fish_bucket", getEntityTypeByKey("minecraft:tropical_fish"));
        types.put("minecraft:pufferfish_bucket", getEntityTypeByKey("minecraft:pufferfish"));
        types.put("minecraft:axolotl_bucket", getEntityTypeByKey("minecraft:axolotl"));
        types.put("minecraft:tadpole_bucket", getEntityTypeByKey("minecraft:frog"));
        types.values().removeIf(java.util.Objects::isNull);
        return Map.copyOf(types);
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
        List<Item> ingredients = controller.ingredients();
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
        IntList ids = MiscUtils.init(new IntArrayList(), a -> {
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
        StockpotStage lastStage = tracker.stage();
        int lastIngredientCount = tracker.ingredientCount();
        List<Item> ingredients = controller.ingredients();
        boolean soupBaseChanged = !controller.soupBaseId().equals(tracker.soupBaseId());

        if (lastStage == stage && lastIngredientCount == ingredients.size() && !soupBaseChanged) {
            return;
        }

        List<Object> packets = new ArrayList<>();

        if (lastIngredientCount > ingredients.size()) {
            IntList idsToRemove = new IntArrayList();
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
            IntList toRemove = MiscUtils.init(new IntArrayList(), a -> {
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

        List<Item> ingredients = controller.ingredients();
        if (index >= ingredients.size()) return;

        int itemHash = ingredients.get(index).minecraftItem().hashCode();
        long time = System.currentTimeMillis();
        Object metaPacket = ingredientPackets(ingredients.get(index), false, ingredientAnimY(itemHash, time)).meta(display.id(index));
        player.sendPackets(List.of(display.spawn(index), metaPacket), false);
    }

    public void hideIndex(Player player, int index) {
        if (index >= 0 && index < StockpotController.MAX_INGREDIENTS) {
            IntList ids = MiscUtils.init(new IntArrayList(),
                    a -> a.add(display.id(index)));
            player.sendPacket(ClientboundRemoveEntitiesPacketProxy.INSTANCE.newInstance(ids), false);
        }
    }

    public void clearAll(Player player) {
        hide(player);
    }

    public void onFinished(Player player) {
        IntList ids = MiscUtils.init(new IntArrayList(), a -> {
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