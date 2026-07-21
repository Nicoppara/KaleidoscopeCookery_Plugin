package net.kaleidoscope.cookery.block.entity;
import net.kaleidoscope.cookery.block.behavior.ChoppingBoardBehavior;

import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.block.entity.tick.BlockEntityTicker;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.Tag;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.ApplianceFoodRegistry;
import net.kaleidoscope.cookery.recipe.ChoppingBoardRecipe;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ChoppingBoardController extends BlockEntityController {
    private static final String DATA_KEY = "kaleidoscopecookery:chopping_board";
    private static final String K_BLOCK_ENTITY_TAG = "BlockEntityTag";
    private static final String K_DATA_VERSION = "data_version";
    private static final String K_STAGE = "stage";
    private static final String K_ITEM = "item";

    private final ChoppingBoardBehavior behavior;
    private final ChoppingBoardElement element;
    private Item placedItem = Item.empty();
    // 当前切割阶段
    private int currentStage = 0;

    public ChoppingBoardController(BlockEntity blockEntity, ChoppingBoardBehavior behavior) {
        super(blockEntity);
        this.behavior = behavior;
        // 展示实体置于方块中心上方
        this.element = new ChoppingBoardElement(this, new WorldPosition(
                null, (float) super.blockEntity.pos.x() + 0.5f,
                (float) super.blockEntity.pos.y() + 0.625f,
                (float) super.blockEntity.pos.z() + 0.5f
        ));
    }

    public float facingYawRadians() {
        if (behavior.getFacingProperty() == null) {
            return 0f;
        }
        Direction f = super.blockEntity.blockState.get(behavior.getFacingProperty());
        int data2D = switch (f) {
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> 0;
        };
        float yaw = (float) Math.toRadians(data2D * 90);
        if (f == Direction.NORTH || f == Direction.SOUTH) {
            yaw += (float) Math.PI;
        }
        return yaw;
    }

    @Override
    public <C extends BlockEntityController> BlockEntityTicker<C> createBlockEntityTicker(
            CEWorld world, ImmutableBlockState blockState) {
        return null;
    }

    public void refreshDynamicElement(BiConsumer<ChoppingBoardElement, Player> consumer) {
        TrackedPlayers.forEach(super.blockEntity, trackedPlayer -> consumer.accept(this.element, trackedPlayer));
    }

    public void refreshElementState() {
        this.element.prepareUpdate();
        refreshDynamicElement(ChoppingBoardElement::update);
    }

    public boolean isEmpty() {
        return currentStage == 0 || placedItem.isEmpty();
    }

    // 该物品是否允许放上砧板
    public boolean canChop(Item food) {
        return ApplianceFoodRegistry.instance().isAllowed(ApplianceType.CHOPPING_BOARD, food.id());
    }

    public Item placedItem() {
        return placedItem;
    }

    public int currentStage() {
        return currentStage;
    }

    // 当前阶段的展示模型路径 无料时返回 null
    public String currentStageModel() {
        if (isEmpty()) {
            return null;
        }
        ChoppingBoardRecipe recipe = recipe();
        if (recipe == null) {
            return null;
        }
        int idx = Math.min(currentStage, recipe.stage()) - 1;
        if (idx < 0 || idx >= recipe.values().size()) {
            return null;
        }
        return recipe.values().get(idx);
    }

    private ChoppingBoardRecipe recipe() {
        if (placedItem.isEmpty()) {
            return null;
        }
        return FoodRecipeRegistry.instance().findChoppingByInput(placedItem.id());
    }

    public boolean place(Item food) {
        if (!isEmpty() || !canChop(food)) {
            return false;
        }
        this.placedItem = food.copyWithCount(1);
        this.currentStage = 1;
        refreshElementState();
        super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
        return true;
    }

    public CutResult cut() {
        if (isEmpty()) {
            return CutResult.NOTHING;
        }
        ChoppingBoardRecipe recipe = recipe();
        if (recipe == null) {
            return CutResult.NOTHING;
        }

        if (currentStage < recipe.stage()) {
            currentStage++;
            refreshElementState();
            super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
            return CutResult.ADVANCED;
        }

        for (Item result : FoodRecipeRegistry.instance().rollChoppingResults(recipe)) {
            if (!result.isEmpty()) {
                DropUtils.dropAtCenter(super.blockEntity, result);
            }
        }
        clearBoard();
        return CutResult.FINISHED;
    }

    public Item takeBack() {
        if (isEmpty()) {
            return Item.empty();
        }
        Item ret = placedItem;
        clearBoard();
        return ret;
    }

    private void clearBoard() {
        this.placedItem = Item.empty();
        this.currentStage = 0;
        refreshElementState();
        super.blockEntity.world.blockEntityChanged(super.blockEntity.pos);
    }

    // 切割结果
    public enum CutResult { NOTHING, ADVANCED, FINISHED }

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
        if (!placedItem.isEmpty()) {
            DropUtils.dropAtCenter(super.blockEntity, placedItem);
        }
        super.onRemove();
    }

    @Override
    public void loadCustomDataFromItem(Item item) {
        Object nmsItem = item.minecraftItem();
        Tag tag = ItemStackUtils.saveMinecraftItemStackAsTag(nmsItem);
        if (tag instanceof CompoundTag compoundTag && compoundTag.containsKey(K_BLOCK_ENTITY_TAG)) {
            loadCustomData(compoundTag.getCompound(K_BLOCK_ENTITY_TAG));
        }
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        if (isEmpty()) {
            return;
        }
        CompoundTag data = new CompoundTag();
        data.putInt(K_DATA_VERSION, VersionHelper.WORLD_VERSION);
        data.putInt(K_STAGE, currentStage);
        if (!placedItem.isEmpty()) {
            data.put(K_ITEM, ItemStackUtils.saveMinecraftItemStackAsTag(placedItem.minecraftItem()));
        }
        tag.put(DATA_KEY, data);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        CompoundTag data = tag.getCompound(DATA_KEY);
        if (data == null) {
            return;
        }
        this.currentStage = data.getInt(K_STAGE, 0);
        this.placedItem = Item.empty();
        Tag itemTag = data.get(K_ITEM);
        if (itemTag != null) {
            int dataVersion = data.getInt(K_DATA_VERSION, Config.itemDataFixerUpperFallbackVersion());
            Object nmsItem = ItemStackUtils.parseMinecraftItem(itemTag, dataVersion);
            if (nmsItem != null) {
                this.placedItem = ItemStackUtils.wrap(nmsItem);
            }
        }
        if (this.placedItem.isEmpty()) {
            this.currentStage = 0;
        }
        this.element.refreshPackets();
    }
}