package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.ChairBehavior;
import net.kaleidoscope.cookery.item.CarpetColors;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureController;
import net.momirealms.craftengine.core.entity.furniture.element.FurnitureElement;
import net.momirealms.craftengine.core.entity.furniture.hitbox.FurnitureHitBox;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.context.InteractEntityContext;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

// 椅子铺地毯 右键放 空手潜行取 换色先弹旧的
// 颜色只存一个 Key 不占方块状态
public final class ChairController extends FurnitureController {
    private static final String DATA_KEY = "kaleidoscopecookery:chair";
    private static final String K_CARPET = "carpet";

    private final ChairBehavior behavior;
    private final ChairElement element;
    private volatile Key carpet;

    public ChairController(Furniture furniture, ChairBehavior behavior) {
        super(furniture);
        this.behavior = behavior;
        this.element = new ChairElement(this, behavior);
    }

    // 当前坐垫的展示模型 没铺返回 null
    @Nullable
    public Key carpetModel() {
        Key current = this.carpet;
        return current == null ? null : CarpetColors.chairModel(current);
    }

    @Override
    public InteractionResult useOnFurniture(FurnitureHitBox hitBox, InteractEntityContext context) {
        Player player = context.getPlayer();
        if (context.getHand() != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!InteractGuard.canInteract(player, furniture().position())) {
            return InteractionResult.PASS;
        }
        // 空手不潜行必须放行 CE 只在非潜行时才去找座位 拦下来玩家就坐不了了
        Item inHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (ItemUtils.isEmpty(inHand)) {
            return player.isSecondaryUseActive() ? takeCarpet(player) : InteractionResult.PASS;
        }
        Key id = inHand.id();
        if (!CarpetColors.isCarpet(id)) {
            return InteractionResult.PASS;
        }
        return putCarpet(player, inHand, id);
    }

    private InteractionResult putCarpet(Player player, Item inHand, Key id) {
        Key previous = this.carpet;
        if (id.equals(previous)) {
            return InteractionResult.PASS;
        }
        this.carpet = id;
        InventoryUtils.shrinkHeld(player, inHand, 1);
        if (previous != null) {
            InventoryUtils.giveOrHold(player, InteractionHand.MAIN_HAND, InventoryUtils.createOrEmpty(previous));
        }
        refresh();
        player.swingHand(InteractionHand.MAIN_HAND);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    private InteractionResult takeCarpet(Player player) {
        Key previous = this.carpet;
        if (previous == null) {
            return InteractionResult.PASS;
        }
        this.carpet = null;
        InventoryUtils.giveOrHold(player, InteractionHand.MAIN_HAND, InventoryUtils.createOrEmpty(previous));
        refresh();
        player.swingHand(InteractionHand.MAIN_HAND);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // trackedBy 是列表快照 getTrackedBy 返回的是活的集合 别的 region 线程正在改它
    private void refresh() {
        this.element.rebuild();
        furniture().trackedBy().forEach(this.element::update);
        furniture().setUnsaved();
    }

    @Override
    public void gatherElements(Consumer<FurnitureElement> consumer) {
        consumer.accept(this.element);
    }

    @Override
    public void onLoad() {
        this.element.rebuild();
    }

    @Override
    public void onPlace(Player player) {
        this.element.rebuild();
    }

    @Override
    public void preRemove(Player player) {
        Key previous = this.carpet;
        if (previous == null) {
            return;
        }
        this.carpet = null;
        Item drop = InventoryUtils.createOrEmpty(previous);
        if (!ItemUtils.isEmpty(drop)) {
            furniture().position().world().dropItemNaturally(furniture().position(), drop);
        }
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        Key current = this.carpet;
        if (current == null) {
            return;
        }
        CompoundTag data = new CompoundTag();
        data.putString(K_CARPET, current.asString());
        tag.put(DATA_KEY, data);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        CompoundTag data = tag.getCompound(DATA_KEY);
        if (data == null) {
            return;
        }
        String id = data.getString(K_CARPET);
        this.carpet = id == null || id.isEmpty() ? null : Key.of(id);
        this.element.rebuild();
    }
}
