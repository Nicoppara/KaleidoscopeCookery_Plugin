package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.FruitBasketBehavior;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.entity.cat.FruitBasketCatGoal;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.item.DataComponentTypes;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.core.world.WorldPosition;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.ListTag;
import net.momirealms.craftengine.libraries.nbt.Tag;
import net.momirealms.craftengine.proxy.minecraft.world.item.ItemStackProxy;
import net.momirealms.craftengine.proxy.minecraft.world.item.component.ItemContainerContentsProxy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

// 果篮方块实体 8 槽存物 放/取/破坏
public final class FruitBasketController extends BlockEntityController {
    private static final int SLOTS = 8;
    private static final String DATA_KEY = "kaleidoscopecookery:fruit_basket";

    private final FruitBasketBehavior behavior;
    private final Item[] items = new Item[SLOTS];
    private final Item[] lastItems = new Item[SLOTS];
    private final FruitBasketElement element;
    private WorldPosition[] positions;
    private boolean positionsInitialized;
    private boolean creativeBreak;

    public void markCreativeBreak() {
        this.creativeBreak = true;
    }

    public FruitBasketController(BlockEntity blockEntity, FruitBasketBehavior behavior) {
        super(blockEntity);
        this.behavior = behavior;
        Arrays.fill(this.items, Item.empty());
        Arrays.fill(this.lastItems, Item.empty());
        this.element = new FruitBasketElement(this);
    }

    @Override
    public boolean hasElement() {
        return true;
    }

    @Override
    public void gatherElements(Consumer<BlockEntityElement> consumer) {
        consumer.accept(this.element);
    }

    Item[] slotItems() {
        return this.items;
    }

    Item[] slotLastItems() {
        return this.lastItems;
    }

    public void ensurePositionsInitialized() {
        if (positionsInitialized || super.blockEntity.world == null) {
            return;
        }
        Direction facing = behavior.getFacingProperty() != null
                ? super.blockEntity.blockState.get(behavior.getFacingProperty()) : Direction.SOUTH;
        int rotation = facing.data2d() * 90;
        Quaternionf facingRot = new Quaternionf().rotateY((float) Math.toRadians(-rotation));

        WorldPosition[] p = new WorldPosition[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            int row = i / 4;
            int col = i % 4;
            float localX = -0.4f + 0.15f * (col + 1);
            float localZ = -0.15f + 0.32f * row + (i % 2 == 0 ? -0.01f : 0.01f);
            Vector3f v = facingRot.transform(new Vector3f(localX, 0.3f, localZ));
            p[i] = new WorldPosition(super.blockEntity.world.world,
                    (float) (super.blockEntity.pos.x + 0.5 + v.x),
                    (float) (super.blockEntity.pos.y + v.y),
                    (float) (super.blockEntity.pos.z + 0.5 + v.z));
        }

        Quaternionf leftRot = new Quaternionf()
                .rotateY((float) Math.toRadians(-rotation - 90))
                .rotateX((float) Math.toRadians(30));

        this.positions = p;
        this.element.configure(p, leftRot);
        for (int i = 0; i < SLOTS; i++) {
            this.element.refreshItem(i, items[i]);
        }
        this.positionsInitialized = true;
    }

    // 放入 先合并到相同物品 再填空槽
    public int putOn(Item held) {
        if (held.isEmpty()) {
            return 0;
        }
        int remaining = held.count();
        int placed = 0;
        for (int i = 0; i < SLOTS && remaining > 0; i++) {
            if (!items[i].isEmpty() && items[i].isSimilar(held)) {
                int room = items[i].maxStackSize() - items[i].count();
                if (room > 0) {
                    int move = Math.min(room, remaining);
                    items[i] = items[i].copyWithCount(items[i].count() + move);
                    remaining -= move;
                    placed += move;
                }
            }
        }
        for (int i = 0; i < SLOTS && remaining > 0; i++) {
            if (items[i].isEmpty()) {
                int move = Math.min(held.maxStackSize(), remaining);
                items[i] = held.copyWithCount(move);
                remaining -= move;
                placed += move;
            }
        }
        if (placed > 0) {
            refresh();
        }
        return placed;
    }

    // 取出最后放入的 后放先出
    public Item takeOut() {
        for (int i = SLOTS - 1; i >= 0; i--) {
            if (!items[i].isEmpty()) {
                Item taken = items[i];
                items[i] = Item.empty();
                refresh();
                return taken;
            }
        }
        return Item.empty();
    }

    private void refresh() {
        ensurePositionsInitialized();
        for (int i = 0; i < SLOTS; i++) {
            if (items[i].isSimilar(lastItems[i])) {
                continue;
            }
            element.refreshItem(i, items[i]);
            int slot = i;
            if (lastItems[i].isEmpty()) {
                TrackedPlayers.forEach(super.blockEntity, p -> element.showSlot(p, slot));
            } else if (items[i].isEmpty()) {
                TrackedPlayers.forEach(super.blockEntity, p -> element.removeSlot(p, slot));
            } else {
                TrackedPlayers.forEach(super.blockEntity, p -> element.metaSlot(p, slot));
            }
        }
        if (super.blockEntity.world != null) {
            super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
        }
        System.arraycopy(items, 0, lastItems, 0, SLOTS);
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        CompoundTag data = new CompoundTag();
        data.putInt("data_version", VersionHelper.WORLD_VERSION);
        ListTag list = new ListTag();
        for (Item item : items) {
            if (!item.isEmpty()) {
                list.add(ItemStackUtils.saveMinecraftItemStackAsTag(item.minecraftItem()));
            }
        }
        data.put("items", list);
        tag.put(DATA_KEY, data);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        Arrays.fill(items, Item.empty());
        CompoundTag data = tag.getCompound(DATA_KEY);
        if (data != null) {
            int dataVersion = data.getInt("data_version", Config.itemDataFixerUpperFallbackVersion());
            ListTag list = data.getList("items");
            if (list != null) {
                int i = 0;
                for (Tag t : list) {
                    if (i >= SLOTS) {
                        break;
                    }
                    Object nms = ItemStackUtils.parseMinecraftItem(t, dataVersion);
                    if (nms != null) {
                        items[i++] = ItemStackUtils.wrap(nms);
                    }
                }
            }
        }
        for (int i = 0; i < SLOTS; i++) {
            element.refreshItem(i, items[i]);
        }
        System.arraycopy(items, 0, lastItems, 0, SLOTS);
    }

    @Override
    public void loadCustomDataFromItem(Item item) {
        Arrays.fill(items, Item.empty());
        Tag container = item.getComponentAsSparrowTag(DataComponentTypes.CONTAINER);
        if (container instanceof ListTag list) {
            int version = Config.itemDataFixerUpperFallbackVersion();
            for (Tag entry : list) {
                if (!(entry instanceof CompoundTag c)) {
                    continue;
                }
                int slot = c.getInt("slot", -1);
                Tag itemTag = c.get("item");
                if (slot < 0 || slot >= SLOTS || itemTag == null) {
                    continue;
                }
                Object nms = ItemStackUtils.parseMinecraftItem(itemTag, version);
                if (nms != null) {
                    items[slot] = ItemStackUtils.wrap(nms);
                }
            }
        }
        ensurePositionsInitialized();
        for (int i = 0; i < SLOTS; i++) {
            element.refreshItem(i, items[i]);
            if (!items[i].isEmpty()) {
                int slot = i;
                TrackedPlayers.forEach(super.blockEntity, p -> element.showSlot(p, slot));
            }
        }
        System.arraycopy(items, 0, lastItems, 0, SLOTS);
    }

    @Override
    public void onRemove() {
        if (super.blockEntity.world != null) {
            FruitBasketCatGoal.releaseClaim(super.blockEntity.world.world().uuid(),
                    super.blockEntity.pos.x, super.blockEntity.pos.y, super.blockEntity.pos.z);
        }
        if (creativeBreak) {
            for (Item item : items) {
                if (!item.isEmpty()) {
                    DropUtils.dropAtCenter(super.blockEntity, item);
                }
            }
        } else {
            Key key = super.blockEntity.blockState.owner().value().id();
            Item basket = InventoryUtils.createOrEmpty(key);
            if (!ItemUtils.isEmpty(basket)) {
                List<Object> nmsItems = new ArrayList<>(SLOTS);
                for (Item item : items) {
                    nmsItems.add(item.isEmpty() ? ItemStackProxy.EMPTY : item.minecraftItem());
                }
                basket.setExactComponent(DataComponentTypes.CONTAINER, ItemContainerContentsProxy.INSTANCE.fromItems(nmsItems));
                DropUtils.dropAtCenter(super.blockEntity, basket);
            }
        }
        Arrays.fill(items, Item.empty());
    }
}
