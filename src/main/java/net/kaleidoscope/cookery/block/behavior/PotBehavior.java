package net.kaleidoscope.cookery.block.behavior;
import net.kaleidoscope.cookery.block.entity.PotStage;
import net.kaleidoscope.cookery.block.entity.PotController;

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
import net.momirealms.craftengine.core.world.BlockPos;
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
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import net.kaleidoscope.cookery.util.HeatSourceUtils;
import net.kaleidoscope.cookery.util.SupportStateUtils;
import net.kaleidoscope.cookery.util.BehaviorConfig;
import net.kaleidoscope.cookery.util.Hands;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.item.ItemMatch;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.FlexFoodRecipe;
import net.kaleidoscope.cookery.recipe.FoodCategoryRegistry;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.util.RecipeUtils;
import net.kaleidoscope.cookery.util.EventUtils;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.api.event.PotExtractDishEvent;

import java.util.List;

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

    public int animChunkRadius = TrackedPlayers.DEFAULT_ANIM_CHUNK_RADIUS;
    public int stirFryCount = 6;
    public int cookDoneTime = 200;
    public int burntToCharcoalTime = 400;
    public Key oilItem = ItemKeys.OIL;

    public Key shovelNoOilItem = ItemKeys.KITCHEN_SHOVEL_NO_OIL;
    public Key shovelHasOilItem = ItemKeys.KITCHEN_SHOVEL_HAS_OIL;
    public Key recipeItemNoRecipe = ItemKeys.RECIPE_ITEM_NO_RECIPE;
    public Key recipeItemHasRecipe = ItemKeys.RECIPE_ITEM_HAS_RECIPE;

    public Key bowlItem = ItemKeys.BOWL;
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

        // 无交互权限放行原版处理
        BlockPos clickedPos = context.getClickedPos();
        if (!InteractGuard.canInteract(player, context.getLevel(), clickedPos)) {
            return InteractionResult.PASS;
        }

        // 只处理主手那次调用 避免主副手各触发一次
        if (context.getHand() == InteractionHand.OFF_HAND) {
            return InteractionResult.PASS;
        }
        boolean hasHeatSource = HeatSourceUtils.isHeatSourceBelow(context);

        // 工具操作副手优先
        InteractionHand toolHand = Hands.toolHand(player, this::isPotTool);
        Item toolItem = player.getItemInHand(toolHand);
        InteractionResult toolResult = InteractionResult.PASS;
        if (ItemMatch.is(toolItem, shovelHasOilItem)) {
            toolResult = handleAddOilWithShovel(context, controller, player, toolHand, hasHeatSource);
        } else if (ItemMatch.is(toolItem, oilItem)) {
            toolResult = handleAddOil(context, controller, player, toolHand, toolItem, hasHeatSource);
        } else if (ItemMatch.is(toolItem, shovelNoOilItem)) {
            toolResult = handleStirFry(context, controller, player, toolHand, hasHeatSource);
        } else if (ItemMatch.is(toolItem, recipeItemNoRecipe) || ItemMatch.is(toolItem, recipeItemHasRecipe)) {
            toolResult = handleRecipe(context, controller, player, toolHand, toolItem, hasHeatSource);
        }
        if (toolResult != InteractionResult.PASS) {
            return toolResult;
        }

        // 取放食材只认主手
        Item mainItem = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (mainItem.vanillaId().equals(bowlItem) && (controller.stage() == PotStage.DONE || controller.stage() == PotStage.BURNT)) {
            return handleExtractDish(context, controller, player, InteractionHand.MAIN_HAND);
        }
        if (mainItem.isEmpty()) {
            return handleTakeIngredient(controller, player, InteractionHand.MAIN_HAND);
        }
        if (FoodCategoryRegistry.instance().isRegistered(ApplianceType.POT, mainItem.id())) {
            return handleAddIngredient(context, controller, player, InteractionHand.MAIN_HAND, mainItem, hasHeatSource);
        }
        return InteractionResult.PASS;
    }

    // 锅的工具类物品 锅铲 油瓶 食谱本 这些走副手优先
    private boolean isPotTool(Item item) {
        return ItemMatch.is(item, shovelHasOilItem)
                || ItemMatch.is(item, oilItem)
                || ItemMatch.is(item, shovelNoOilItem)
                || ItemMatch.is(item, recipeItemNoRecipe)
                || ItemMatch.is(item, recipeItemHasRecipe);
    }

    // 用碗盛出成品
    private InteractionResult handleExtractDish(UseOnContext context, PotController controller, Player player, InteractionHand hand) {
        List<Item> results = controller.getResults();
        if (results.isEmpty()) return InteractionResult.SUCCESS_AND_CANCEL;

        BlockPos pos = context.getClickedPos();
        Location dishLoc = new Location((World) context.getLevel().platformWorld(), pos.x(), pos.y(), pos.z());
        ItemStack dishStack = ItemStackUtils.getBukkitStack(results.get(0).copyWithCount(1).minecraftItem());
        PotExtractDishEvent event = new PotExtractDishEvent((org.bukkit.entity.Player) player.platformPlayer(), dishLoc, dishStack);
        if (EventUtils.fireAndCheckCancel(event)) {
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        Item dish = BukkitItemManager.instance().wrap(event.dish());

        int taken = 0;
        for (int i = 0; i < results.size(); i++) {
            Item res = results.get(i);
            while (res.count() > 0) {
                if (InventoryUtils.consumeItem(player, bowlItem, 1)) {
                    InventoryUtils.giveOrHold(player, hand, dish.copyWithCount(1));
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

    // 空手取出顶层食材
    private InteractionResult handleTakeIngredient(PotController controller, Player player, InteractionHand hand) {
        Item extracted = controller.extractItem(player);
        if (extracted == null || extracted.isEmpty()) return InteractionResult.PASS;
        InventoryUtils.giveOrHold(player, hand, extracted);
        player.swingHand(hand);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 用带油锅铲倒油
    private InteractionResult handleAddOilWithShovel(UseOnContext context, PotController controller, Player player, InteractionHand hand, boolean hasHeatSource) {
        if (controller.hasOil()) {
            player.sendActionBar(Component.text(msgHasOil));
        } else if (controller.stage() == PotStage.DONE || controller.stage() == PotStage.BURNT) {
            player.sendActionBar(Component.text(msgPotOccupied));
        } else if (!hasHeatSource) {
            player.sendActionBar(Component.text(msgNeedHeat));
        } else {
            controller.setHasOil(true);
            player.setItemInHand(hand, InventoryUtils.createOrEmpty(shovelNoOilItem));
            context.getLevel().playSound(Vec3d.atCenterOf(context.getClickedPos()), SOUND_ADD_OIL, DEFAULT_VOLUME, 1.0f, SoundSource.BLOCK);
            player.swingHand(hand);
        }
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 用油瓶倒油
    private InteractionResult handleAddOil(UseOnContext context, PotController controller, Player player, InteractionHand hand, Item itemInHand, boolean hasHeatSource) {
        if (controller.hasOil()) {
            player.sendActionBar(Component.text(msgHasOil));
        } else if (controller.stage() == PotStage.DONE || controller.stage() == PotStage.BURNT) {
            player.sendActionBar(Component.text(msgPotOccupied));
        } else if (!hasHeatSource) {
            player.sendActionBar(Component.text(msgNeedHeat));
        } else {
            controller.setHasOil(true);
            InventoryUtils.shrinkHeld(player, itemInHand, 1);
            context.getLevel().playSound(Vec3d.atCenterOf(context.getClickedPos()), SOUND_ADD_OIL, DEFAULT_VOLUME, 1.0f, SoundSource.BLOCK);
            player.swingHand(hand);
        }
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 翻炒 锅里翻炒不了(空/已完成/动画中)就不挥手 返回 PASS 让调用方继续走主手逻辑
    private InteractionResult handleStirFry(UseOnContext context, PotController controller, Player player, InteractionHand hand, boolean hasHeatSource) {
        if (!controller.stirFry(hasHeatSource, player)) {
            return InteractionResult.PASS;
        }
        if (hasHeatSource && controller.hasOil()) {
            context.getLevel().playSound(Vec3d.atCenterOf(context.getClickedPos()), SOUND_STIR_FRY, DEFAULT_VOLUME, 1.0f, SoundSource.BLOCK);
        }
        player.swingHand(hand);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 食谱书 自动投料或记录食谱
    private InteractionResult handleRecipe(UseOnContext context, PotController controller, Player player, InteractionHand hand, Item itemInHand, boolean hasHeatSource) {
        ItemStack bukkitStack = ItemStackUtils.getBukkitStack(itemInHand.minecraftItem());
        if (RecipeUtils.hasRecipe(bukkitStack)) {
            if (controller.stage() == PotStage.IDLE && controller.getIngredients().isEmpty()) {
                if (!controller.hasOil()) {
                    player.sendActionBar(Component.text(msgNeedOilFirst));
                } else if (!RecipeUtils.tryAutoFill((org.bukkit.entity.Player) player.platformPlayer(), bukkitStack, item -> controller.addIngredient(item, hasHeatSource, player))) {
                    player.sendActionBar(Component.text(msgNotEnoughIngredients));
                } else {
                    player.swingHand(hand);
                    context.getLevel().playSound(Vec3d.atCenterOf(context.getClickedPos()), SOUND_ADD_INGREDIENT, DEFAULT_VOLUME, 0.5f, SoundSource.BLOCK);
                }
            }
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        if (controller.stage() == PotStage.BURNT) {
            player.sendActionBar(Component.text(msgBurntNoRecipe));
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        if (controller.stage() != PotStage.DONE) {
            player.sendActionBar(Component.text(msgNotDoneYet));
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        List<Key> ingredientIds = controller.getIngredients().stream().map(Item::id).toList();
        FlexFoodRecipe matchedRecipe = FoodRecipeRegistry.instance().findBestFlexRecipe(ApplianceType.POT, ingredientIds).orElse(null);
        if (matchedRecipe == null) {
            player.sendActionBar(Component.text(msgMixedNoRecipe));
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        Item recipeItem = InventoryUtils.createOrEmpty(recipeItemHasRecipe);
        ItemStack recorded = ItemStackUtils.getBukkitStack(recipeItem.minecraftItem());
        RecipeUtils.setRecipeItem(recorded, matchedRecipe.id(), "flex", ingredientIds, null);
        InventoryUtils.shrinkHeld(player, itemInHand, 1);
        InventoryUtils.giveOrHold(player, hand, BukkitItemManager.instance().wrap(recorded));
        player.swingHand(hand);
        player.sendActionBar(Component.text(msgRecipeSaved));
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 投入单个食材
    private InteractionResult handleAddIngredient(UseOnContext context, PotController controller, Player player, InteractionHand hand, Item itemInHand, boolean hasHeatSource) {
        int preCount = controller.getIngredients().size();
        controller.addIngredient(itemInHand.copyWithCount(1), hasHeatSource, player);
        if (preCount < controller.getIngredients().size()) {
            InventoryUtils.shrinkHeld(player, itemInHand, 1);
            context.getLevel().playSound(Vec3d.atCenterOf(context.getClickedPos()), SOUND_ADD_INGREDIENT, DEFAULT_VOLUME, 0.5f, SoundSource.BLOCK);
            player.swingHand(hand);
        }
        return InteractionResult.SUCCESS_AND_CANCEL;
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

    public Property<Boolean> getHasBaseProperty() {
        return hasBaseProperty;
    }

    public Property<Boolean> getHasOilProperty() {
        return hasOilProperty;
    }

    public Property<Direction> getFacingProperty() {
        return facingProperty;
    }

    private static class Factory implements BlockBehaviorFactory<PotBehavior> {
        @Override
        public PotBehavior create(BlockDefinition block, ConfigSection section) {
            PotBehavior b = new PotBehavior(block);
            String path = section.path();
            b.hasBaseProperty = BlockBehaviorFactory.getProperty(path, block, "has_base", Boolean.class);
            b.hasOilProperty = BlockBehaviorFactory.getProperty(path, block, "has_oil", Boolean.class);
            b.facingProperty = BlockBehaviorFactory.getProperty(path, block, "facing", Direction.class);

            b.animChunkRadius = BehaviorConfig.getInt(section, b.animChunkRadius, "animation_view_distance", "animation-view-distance");
            b.stirFryCount = BehaviorConfig.getInt(section, b.stirFryCount, "stir_fry_count", "stir-fry-count");
            b.cookDoneTime = BehaviorConfig.getInt(section, b.cookDoneTime, "cook_done_time", "cook-done-time");
            b.burntToCharcoalTime = BehaviorConfig.getInt(section, b.burntToCharcoalTime, "burnt_to_charcoal_time", "burnt-to-charcoal-time");

            b.oilItem = Key.of(BehaviorConfig.getString(section, b.oilItem.asString(), "oil_item", "oil-item"));
            b.shovelNoOilItem = Key.of(BehaviorConfig.getString(section, b.shovelNoOilItem.asString(), "shovel_no_oil_item", "shovel-no-oil-item"));
            b.shovelHasOilItem = Key.of(BehaviorConfig.getString(section, b.shovelHasOilItem.asString(), "shovel_has_oil_item", "shovel-has-oil-item"));
            b.recipeItemNoRecipe = Key.of(BehaviorConfig.getString(section, b.recipeItemNoRecipe.asString(), "recipe_item_no_recipe", "recipe-item-no-recipe"));
            b.recipeItemHasRecipe = Key.of(BehaviorConfig.getString(section, b.recipeItemHasRecipe.asString(), "recipe_item_has_recipe", "recipe-item-has-recipe"));
            b.bowlItem = Key.of(BehaviorConfig.getString(section, b.bowlItem.asString(), "bowl_item", "bowl-item"));

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
