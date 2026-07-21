package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.TeacupCoasterBehavior;
import net.kaleidoscope.cookery.block.entity.render.Particles;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.TeaCup;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.world.Vec3d;
import org.bukkit.Particle;

import java.util.concurrent.ThreadLocalRandom;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.block.entity.tick.BlockEntityTicker;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.ListTag;
import net.momirealms.craftengine.libraries.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class TeacupCoasterController extends BlockEntityController {
    public static final int MAX_CUPS = 4;
    private static final String DATA_KEY = "kaleidoscopecookery:teacup_coaster";
    private static final String K_CUPS = "cups";
    private static final String K_ITEM = "item";
    private static final String K_MODEL = "model";
    private static final Key POUR_SOUND = Key.of("minecraft:block.brewing_stand.brew");

    private final TeacupCoasterBehavior behavior;
    private final TeacupCoasterElement element;

    private final List<Item> cupItems = new ArrayList<>(MAX_CUPS);
    private final List<Key> cupModels = new ArrayList<>(MAX_CUPS);
    private int shownCount;

    public TeacupCoasterController(BlockEntity blockEntity, TeacupCoasterBehavior behavior) {
        super(blockEntity);
        this.behavior = behavior;
        this.element = new TeacupCoasterElement(this);
    }

    public BlockPos getPos() {
        return blockEntity.pos;
    }

    public CEWorld getWorld() {
        return blockEntity.world;
    }

    public Direction facing() {
        return blockEntity.blockState.get(behavior.getFacingProperty());
    }

    public float facingYaw() {
        return switch (facing()) {
            case SOUTH -> 180f;
            case EAST -> 90f;
            case WEST -> 270f;
            default -> 0f;
        };
    }

    public int cupCount() {
        return cupItems.size();
    }

    public Key cupModel(int index) {
        return cupModels.get(index);
    }

    public double cupYOffset() {
        return behavior.cupYOffset;
    }

    public float cupScale() {
        return behavior.cupScale;
    }

    // 放杯 仅空杯或已在 tea_cup 注册的茶可放 满 4 个则拒绝 创造不消耗
    public boolean placeCup(Player player, InteractionHand hand, Item held) {
        if (cupItems.size() >= MAX_CUPS) {
            return false;
        }
        Key id = held.id();
        Key model;
        if (id.equals(behavior.emptyCupItem)) {
            model = behavior.emptyCupModel;
        } else {
            TeaCup tc = FoodRecipeRegistry.instance().getTeaCupByItem(id);
            if (tc == null) {
                return false;
            }
            model = FoodRecipeRegistry.instance().pickTeaModel(tc.tea());
        }
        if (model == null) {
            return false;
        }
        cupItems.add(held.copyWithCount(1));
        cupModels.add(model);
        InventoryUtils.shrinkHeld(player, held, 1);
        markChanged();
        refreshCups();
        return true;
    }

    // 茶壶倒茶 找首个空杯就地换成茶模型(先放先倒) 无空杯返回 false
    public boolean pourInto(Key teaKey) {
        TeaCup tc = FoodRecipeRegistry.instance().getTeaCup(teaKey);
        if (tc == null) {
            return false;
        }
        Key model = FoodRecipeRegistry.instance().pickTeaModel(teaKey);
        Item teaItem = InventoryUtils.createOrEmpty(tc.item());
        if (model == null || teaItem.isEmpty()) {
            return false;
        }
        for (int i = 0; i < cupItems.size(); i++) {
            if (!cupItems.get(i).id().equals(behavior.emptyCupItem)) {
                continue;
            }
            cupItems.set(i, teaItem);
            cupModels.set(i, model);
            markChanged();
            element.rebuildPackets();
            int idx = i;
            TrackedPlayers.forEach(blockEntity, p -> p.sendPacket(element.cupMeta(idx), false));
            pourEffects();
            return true;
        }
        return false;
    }

    private void pourEffects() {
        float pitch = 0.9f + ThreadLocalRandom.current().nextFloat() * 0.2f;
        blockEntity.world.world().playSound(Vec3d.atCenterOf(blockEntity.pos), POUR_SOUND, 0.6f, pitch, SoundSource.BLOCK);
        Particles.emit(blockEntity.world, Particle.CRIT,
                blockEntity.pos.x() + 0.5, blockEntity.pos.y() + 0.3, blockEntity.pos.z() + 0.5,
                4, 0.1, 0.05, 0.1, 0.0, null);
    }

    public boolean takeCup(Player player, InteractionHand hand) {
        if (cupItems.isEmpty()) {
            return false;
        }
        int last = cupItems.size() - 1;
        Item removed = cupItems.remove(last);
        cupModels.remove(last);
        InventoryUtils.give(player, removed);
        markChanged();
        refreshCups();
        return true;
    }

    private void markChanged() {
        blockEntity.world.blockEntityChanged(blockEntity.pos);
    }

    // 数量变更后重建并按差异广播 仅放/取时调用 此刻世界已就绪
    private void refreshCups() {
        int oldCount = shownCount;
        int newCount = cupItems.size();
        element.rebuildPackets();
        TrackedPlayers.forEach(blockEntity, p -> element.broadcastChange(p, oldCount, newCount));
        shownCount = newCount;
    }

    @Override
    public <C extends BlockEntityController> BlockEntityTicker<C> createBlockEntityTicker(CEWorld world, ImmutableBlockState blockState) {
        return null;
    }

    @Override
    public boolean hasElement() {
        return true;
    }

    @Override
    public void gatherElements(Consumer<BlockEntityElement> consumer) {
        consumer.accept(element);
    }

    @Override
    public void onRemove() {
        for (Item item : cupItems) {
            if (!item.isEmpty()) {
                DropUtils.dropAtCenter(blockEntity, item);
            }
        }
        cupItems.clear();
        cupModels.clear();
        super.onRemove();
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        CompoundTag data = new CompoundTag();
        ListTag list = new ListTag();
        for (int i = 0; i < cupItems.size(); i++) {
            CompoundTag c = new CompoundTag();
            c.put(K_ITEM, ItemStackUtils.saveMinecraftItemStackAsTag(cupItems.get(i).minecraftItem()));
            c.putString(K_MODEL, cupModels.get(i).asString());
            list.add(c);
        }
        data.put(K_CUPS, list);
        tag.put(DATA_KEY, data);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        CompoundTag data = tag.getCompound(DATA_KEY);
        if (data == null) {
            return;
        }
        cupItems.clear();
        cupModels.clear();
        if (data.containsKey(K_CUPS)) {
            int version = Config.itemDataFixerUpperFallbackVersion();
            for (Tag t : data.getList(K_CUPS)) {
                if (!(t instanceof CompoundTag c)) {
                    continue;
                }
                Object nms = ItemStackUtils.parseMinecraftItem(c.get(K_ITEM), version);
                Item item = nms == null ? Item.empty() : ItemStackUtils.wrap(nms);
                String model = c.getString(K_MODEL);
                if (!item.isEmpty() && model != null && !model.isEmpty() && cupItems.size() < MAX_CUPS) {
                    cupItems.add(item);
                    cupModels.add(Key.of(model));
                }
            }
        }
        shownCount = cupItems.size();
    }
}
