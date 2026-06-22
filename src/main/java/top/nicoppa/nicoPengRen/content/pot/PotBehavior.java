package top.nicoppa.nicoPengRen.content.pot;

import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.DirectionUtils;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.EntityBlock;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.context.BlockPlaceContext;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.proxy.minecraft.world.level.BlockGetterProxy;
import org.bukkit.inventory.ItemStack;
import top.nicoppa.nicoPengRen.common.block.HeatSourceUtils;
import top.nicoppa.nicoPengRen.common.block.SupportStateUtils;
import top.nicoppa.nicoPengRen.common.config.BehaviorConfig;
import top.nicoppa.nicoPengRen.common.item.InventoryUtils;
import top.nicoppa.nicoPengRen.common.item.ItemKeys;
import top.nicoppa.nicoPengRen.common.item.ItemMatch;
import top.nicoppa.nicoPengRen.recipe.ApplianceType;
import top.nicoppa.nicoPengRen.recipe.food.FlexFoodRecipe;
import top.nicoppa.nicoPengRen.recipe.food.FoodCategoryRegistry;
import top.nicoppa.nicoPengRen.recipe.food.FoodRecipeRegistry;
import top.nicoppa.nicoPengRen.util.RecipeUtils;

import java.util.List;

/**
 * 炒锅方块行为：倒油/翻炒/投料/盛出/记录食谱 数据见 {@link PotController}，渲染见 {@link PotElement}
 *
 * 可在方块 behaviors 配置里覆盖以下项（默认值即下方所示，键名支持下划线/连字符两种写法）
 * behaviors:
 *   - type: nicopengren:cooking_pot
 *     stir_fry_count: 6            # 出锅所需翻炒次数
 *     cook_done_time: 200          # 出锅后多少 tick 进入烧焦阶段（-1 = 永不烧焦）
 *     burnt_to_charcoal_time: 400  # 烧焦后多少 tick 变成木炭
 *     oil_item: "custom:oil"
 *     shovel_no_oil_item: "custom:kitchen_shovel_no_oil"
 *     shovel_has_oil_item: "custom:kitchen_shovel_has_oil"
 *     recipe_item_no_recipe: "custom:recipe_item_no_recipe"
 *     recipe_item_has_recipe: "custom:recipe_item_has_recipe"
 *     bowl_item: "minecraft:bowl"
 *     msg_need_bowl: "你需要碗来盛装！"
 *     msg_has_oil: "锅里已经有油了"
 *     msg_pot_occupied: "锅里还有东西！"
 *     msg_need_heat: "需要先放火上"
 *     msg_need_oil_first: "请先倒油！"
 *     msg_not_enough_ingredients: "食材不足！"
 *     msg_burnt_no_recipe: "糊了，没法记食谱！"
 *     msg_not_done_yet: "菜还没出锅呢！"
 *     msg_mixed_no_recipe: "大乱炖没法记食谱！"
 *     msg_recipe_saved: "食谱记录成功！"
 *     msg_start_cooking: "开始烹饪了，记得准备碗来盛菜"
 *     msg_dish_ready: "出锅！"
 *     msg_all_burnt: "糟糕，全糊了！"
 *     msg_not_ingredient: "无法加入：不是食材"
 */
public final class PotBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<PotBehavior> FACTORY = new Factory();
    private static final float DEFAULT_VOLUME = 1.0f;
    private static final Key SOUND_ADD_OIL = Key.of("minecraft:block.lava.extinguish");
    private static final Key SOUND_STIR_FRY = Key.of("minecraft:block.lava.extinguish");
    private static final Key SOUND_ADD_INGREDIENT = Key.of("minecraft:block.lantern.place");

    private int controllerId;
    private Property<Boolean> hasBaseProperty;
    private Property<Boolean> hasOilProperty;
    private Property<Direction> facingProperty;
    public String customDataKey = "nicopengren:cooking_pot";

    public int stirFryCount = 6;
    public int cookDoneTime = 200;
    public int burntToCharcoalTime = 400;
    public Key oilItem = ItemKeys.OIL;

    public Key shovelNoOilItem = ItemKeys.KITCHEN_SHOVEL_NO_OIL;
    public Key shovelHasOilItem = ItemKeys.KITCHEN_SHOVEL_HAS_OIL;
    public Key recipeItemNoRecipe = ItemKeys.RECIPE_ITEM_NO_RECIPE;
    public Key recipeItemHasRecipe = ItemKeys.RECIPE_ITEM_HAS_RECIPE;

    public Key bowlItem = Key.of("minecraft:bowl");
    public String msgNeedBowl = "你需要碗来盛装！";
    public String msgHasOil = "锅里已经有油了";
    public String msgPotOccupied = "锅里还有东西！";
    public String msgNeedHeat = "需要先放火上";
    public String msgNeedOilFirst = "请先倒油！";
    public String msgNotEnoughIngredients = "食材不足！";
    public String msgBurntNoRecipe = "糊了，没法记食谱！";
    public String msgNotDoneYet = "菜还没出锅呢！";
    public String msgMixedNoRecipe = "大乱炖没法记食谱！";
    public String msgRecipeSaved = "食谱记录成功！";
    public String msgStartCooking = "开始烹饪了，记得准备碗来盛菜";
    public String msgDishReady = "出锅！";
    public String msgAllBurnt = "糟糕，全糊了！";
    public String msgNotIngredient = "无法加入：不是食材";

    public PotBehavior(BlockDefinition blockDefinition) {
        super(blockDefinition);
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        CEWorld world = context.getLevel().storageWorld();
        BlockEntity blockEntity = world.getBlockEntityAtIfLoaded(context.getClickedPos());
        if (blockEntity == null) return InteractionResult.PASS;
        PotController controller = blockEntity.controller.get(PotController.class, this.controllerId);
        if (controller == null) return InteractionResult.PASS;

        InteractionHand hand = context.getHand();
        Item itemInHand = player.getItemInHand(hand);
        boolean hasHeatSource = HeatSourceUtils.isHeatSourceBelow(context);
        Vec3d centerPos = Vec3d.atCenterOf(context.getClickedPos());

        if (itemInHand.vanillaId().equals(bowlItem) && (controller.stage() == CookingStage.DONE || controller.stage() == CookingStage.BURNT)) {
            List<Item> results = controller.getResults();
            if (results.isEmpty()) return InteractionResult.SUCCESS_AND_CANCEL;

            int taken = 0;
            for (int i = 0; i < results.size(); i++) {
                Item res = results.get(i);
                while (res.count() > 0) {
                    if (InventoryUtils.consumeItem(player, bowlItem, 1)) {
                        InventoryUtils.giveOrHold(player, hand, res.copyWithCount(1));
                        res.shrink(1);
                        taken++;
                    } else {
                        break;
                    }
                }
                if (res.isEmpty()) {
                    results.remove(i);
                    i--;
                }
            }

            if (taken > 0) {
                controller.syncIngredientsToResults();
                player.swingHand(hand);
            } else {
                player.sendActionBar(Component.text(msgNeedBowl));
            }
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        if (itemInHand.isEmpty()) {
            Item extracted = controller.extractItem(player);
            if (extracted == null || extracted.isEmpty()) return InteractionResult.PASS;
            InventoryUtils.giveOrHold(player, hand, extracted);
            player.swingHand(hand);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        if (ItemMatch.is(itemInHand, shovelHasOilItem)) {
            if (controller.hasOil()) {
                player.sendActionBar(Component.text(msgHasOil));
            } else if (controller.stage() == CookingStage.DONE || controller.stage() == CookingStage.BURNT) {
                player.sendActionBar(Component.text(msgPotOccupied));
            } else if (!hasHeatSource) {
                player.sendActionBar(Component.text(msgNeedHeat));
            } else {
                controller.setHasOil(true);
                player.setItemInHand(hand, BukkitItemManager.instance().createWrappedItem(shovelNoOilItem, null));
                context.getLevel().playSound(centerPos, SOUND_ADD_OIL, DEFAULT_VOLUME, 1.0f, SoundSource.BLOCK);
                player.swingHand(hand);
            }
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        if (ItemMatch.is(itemInHand, oilItem)) {
            if (controller.hasOil()) {
                player.sendActionBar(Component.text(msgHasOil));
            } else if (controller.stage() == CookingStage.DONE || controller.stage() == CookingStage.BURNT) {
                player.sendActionBar(Component.text(msgPotOccupied));
            } else if (!hasHeatSource) {
                player.sendActionBar(Component.text(msgNeedHeat));
            } else {
                controller.setHasOil(true);
                itemInHand.shrink(1);
                context.getLevel().playSound(centerPos, SOUND_ADD_OIL, DEFAULT_VOLUME, 1.0f, SoundSource.BLOCK);
                player.swingHand(hand);
            }
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        if (ItemMatch.is(itemInHand, shovelNoOilItem)) {
            if (controller.stirFry(hasHeatSource, player) && hasHeatSource && controller.hasOil()) {
                context.getLevel().playSound(centerPos, SOUND_STIR_FRY, DEFAULT_VOLUME, 1.0f, SoundSource.BLOCK);
            }
            player.swingHand(hand);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        if (ItemMatch.is(itemInHand, recipeItemNoRecipe) || ItemMatch.is(itemInHand, recipeItemHasRecipe)) {
            ItemStack bukkitStack = ItemStackUtils.getBukkitStack(itemInHand.minecraftItem());
            if (RecipeUtils.hasRecipe(bukkitStack)) {
                if (controller.stage() == CookingStage.IDLE && controller.getIngredients().isEmpty()) {
                    if (!controller.hasOil()) {
                        player.sendActionBar(Component.text(msgNeedOilFirst));
                    } else if (!RecipeUtils.tryAutoFill((org.bukkit.entity.Player) player.platformPlayer(), bukkitStack, item -> controller.addIngredient(item, hasHeatSource, player))) {
                        player.sendActionBar(Component.text(msgNotEnoughIngredients));
                    } else {
                        player.swingHand(hand);
                        context.getLevel().playSound(centerPos, SOUND_ADD_INGREDIENT, DEFAULT_VOLUME, 0.5f, SoundSource.BLOCK);
                    }
                }
                return InteractionResult.SUCCESS_AND_CANCEL;
            }

            if (controller.stage() == CookingStage.BURNT) {
                player.sendActionBar(Component.text(msgBurntNoRecipe));
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            if (controller.stage() != CookingStage.DONE) {
                player.sendActionBar(Component.text(msgNotDoneYet));
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            List<Key> ingredientIds = controller.getIngredients().stream().map(Item::id).toList();
            FlexFoodRecipe matchedRecipe = FoodRecipeRegistry.instance().findBestFlexRecipe(ApplianceType.POT, ingredientIds).orElse(null);
            if (matchedRecipe == null) {
                player.sendActionBar(Component.text(msgMixedNoRecipe));
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            Item recipeItem = BukkitItemManager.instance().createWrappedItem(recipeItemHasRecipe, null);
            ItemStack recorded = ItemStackUtils.getBukkitStack(recipeItem.minecraftItem());
            RecipeUtils.setRecipeItem(recorded, matchedRecipe.id(), "flex", ingredientIds, null);
            itemInHand.shrink(1);
            InventoryUtils.giveOrHold(player, hand, BukkitItemManager.instance().wrap(recorded));
            player.swingHand(hand);
            player.sendActionBar(Component.text(msgRecipeSaved));
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        if (!itemInHand.isEmpty() && FoodCategoryRegistry.instance().isRegistered(ApplianceType.POT, itemInHand.id())) {
            int preCount = controller.getIngredients().size();
            controller.addIngredient(itemInHand.copyWithCount(1), hasHeatSource, player);
            if (preCount < controller.getIngredients().size()) {
                itemInHand.shrink(1);
                context.getLevel().playSound(centerPos, SOUND_ADD_INGREDIENT, DEFAULT_VOLUME, 0.5f, SoundSource.BLOCK);
                player.swingHand(hand);
            }
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        return InteractionResult.PASS;
    }

    @Override
    public Object updateShape(Object thisBlock, Object[] args) {
        Object blockState = args[0];
        Object level = args[updateShape$level];
        Object blockPos = args[updateShape$blockPos];

        ImmutableBlockState customState = BlockStateUtils.getOptionalCustomBlockState(blockState).orElse(null);
        if (customState == null || customState.isEmpty()) return blockState;

        if (DirectionUtils.fromNMSDirection(args[updateShape$direction]) == Direction.DOWN && this.hasBaseProperty != null) {
            Object belowPos = LocationUtils.below(blockPos);
            boolean shouldHaveBase = !SupportStateUtils.isSturdyUp(level, belowPos, BlockGetterProxy.INSTANCE.getBlockState(level, belowPos));
            if (customState.get(this.hasBaseProperty) != shouldHaveBase) {
                ImmutableBlockState next = customState.with(this.hasBaseProperty, shouldHaveBase);
                if (this.facingProperty != null) next = next.with(this.facingProperty, customState.get(this.facingProperty));
                return next.customBlockState().minecraftState();
            }
        }
        return blockState;
    }

    @Override
    public ImmutableBlockState updateStateForPlacement(BlockPlaceContext context, ImmutableBlockState state) {
        if (this.hasBaseProperty == null) return state;
        Object level = context.getLevel().minecraftWorld();
        Object belowPos = LocationUtils.below(LocationUtils.toBlockPos(context.getClickedPos()));
        boolean shouldHaveBase = !SupportStateUtils.isSturdyUp(level, belowPos, BlockGetterProxy.INSTANCE.getBlockState(level, belowPos));
        Direction facing = this.facingProperty != null ? state.get(this.facingProperty) : Direction.NORTH;
        return state.with(this.hasBaseProperty, shouldHaveBase).with(this.facingProperty, facing);
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new PotController(blockEntity, this);
    }

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    public Property<Boolean> getHasBaseProperty() { return hasBaseProperty; }
    public Property<Boolean> getHasOilProperty() { return hasOilProperty; }
    public Property<Direction> getFacingProperty() { return facingProperty; }

    private static class Factory implements BlockBehaviorFactory<PotBehavior> {
        @Override
        public PotBehavior create(BlockDefinition block, ConfigSection section) {
            PotBehavior b = new PotBehavior(block);
            String path = section.path();
            b.hasBaseProperty = BlockBehaviorFactory.getProperty(path, block, "has_base", Boolean.class);
            b.hasOilProperty = BlockBehaviorFactory.getProperty(path, block, "has_oil", Boolean.class);
            b.facingProperty = BlockBehaviorFactory.getProperty(path, block, "facing", Direction.class);
            b.customDataKey = section.getString("data_key", "nicopengren:cooking_pot");

            b.stirFryCount = BehaviorConfig.getInt(section, b.stirFryCount, "stir_fry_count", "stir-fry-count");
            b.cookDoneTime = BehaviorConfig.getInt(section, b.cookDoneTime, "cook_done_time", "cook-done-time");
            b.burntToCharcoalTime = BehaviorConfig.getInt(section, b.burntToCharcoalTime, "burnt_to_charcoal_time", "burnt-to-charcoal-time");

            b.oilItem = Key.of(BehaviorConfig.getString(section, b.oilItem.toString(), "oil_item", "oil-item"));
            b.shovelNoOilItem = Key.of(BehaviorConfig.getString(section, b.shovelNoOilItem.toString(), "shovel_no_oil_item", "shovel-no-oil-item"));
            b.shovelHasOilItem = Key.of(BehaviorConfig.getString(section, b.shovelHasOilItem.toString(), "shovel_has_oil_item", "shovel-has-oil-item"));
            b.recipeItemNoRecipe = Key.of(BehaviorConfig.getString(section, b.recipeItemNoRecipe.toString(), "recipe_item_no_recipe", "recipe-item-no-recipe"));
            b.recipeItemHasRecipe = Key.of(BehaviorConfig.getString(section, b.recipeItemHasRecipe.toString(), "recipe_item_has_recipe", "recipe-item-has-recipe"));
            b.bowlItem = Key.of(BehaviorConfig.getString(section, b.bowlItem.toString(), "bowl_item", "bowl-item"));

            b.msgNeedBowl = BehaviorConfig.getString(section, b.msgNeedBowl, "msg_need_bowl", "msg-need-bowl");
            b.msgHasOil = BehaviorConfig.getString(section, b.msgHasOil, "msg_has_oil", "msg-has-oil");
            b.msgPotOccupied = BehaviorConfig.getString(section, b.msgPotOccupied, "msg_pot_occupied", "msg-pot-occupied");
            b.msgNeedHeat = BehaviorConfig.getString(section, b.msgNeedHeat, "msg_need_heat", "msg-need-heat");
            b.msgNeedOilFirst = BehaviorConfig.getString(section, b.msgNeedOilFirst, "msg_need_oil_first", "msg-need-oil-first");
            b.msgNotEnoughIngredients = BehaviorConfig.getString(section, b.msgNotEnoughIngredients, "msg_not_enough_ingredients", "msg-not-enough-ingredients");
            b.msgBurntNoRecipe = BehaviorConfig.getString(section, b.msgBurntNoRecipe, "msg_burnt_no_recipe", "msg-burnt-no-recipe");
            b.msgNotDoneYet = BehaviorConfig.getString(section, b.msgNotDoneYet, "msg_not_done_yet", "msg-not-done-yet");
            b.msgMixedNoRecipe = BehaviorConfig.getString(section, b.msgMixedNoRecipe, "msg_mixed_no_recipe", "msg-mixed-no-recipe");
            b.msgRecipeSaved = BehaviorConfig.getString(section, b.msgRecipeSaved, "msg_recipe_saved", "msg-recipe-saved");
            b.msgStartCooking = BehaviorConfig.getString(section, b.msgStartCooking, "msg_start_cooking", "msg-start-cooking");
            b.msgDishReady = BehaviorConfig.getString(section, b.msgDishReady, "msg_dish_ready", "msg-dish-ready");
            b.msgAllBurnt = BehaviorConfig.getString(section, b.msgAllBurnt, "msg_all_burnt", "msg-all-burnt");
            b.msgNotIngredient = BehaviorConfig.getString(section, b.msgNotIngredient, "msg_not_ingredient", "msg-not-ingredient");
            return b;
        }
    }
}
