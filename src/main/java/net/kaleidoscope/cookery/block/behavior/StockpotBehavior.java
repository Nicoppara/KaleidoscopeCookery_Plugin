package net.kaleidoscope.cookery.block.behavior;
import net.kaleidoscope.cookery.block.entity.StockpotController;
import net.kaleidoscope.cookery.block.entity.StockpotStage;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;

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
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.BlockPlaceContext;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.proxy.minecraft.core.DirectionProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.BlockGetterProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.SupportTypeProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.state.BlockBehaviourProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelWriterProxy;
import org.bukkit.Location;
import org.bukkit.entity.Player;
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
import net.kaleidoscope.cookery.recipe.SoupBaseRegistry;
import net.kaleidoscope.cookery.util.RecipeUtils;
import net.kaleidoscope.cookery.api.event.StockpotExtractDishEvent;
import net.kaleidoscope.cookery.util.EventUtils;

import java.util.List;

public final class StockpotBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<StockpotBehavior> FACTORY = new Factory();
    private int controllerId;
    private Property<Boolean> hasLidProperty;
    private Property<Boolean> hasBaseProperty;
    private Property<Boolean> hasChainsProperty;
    private Property<Direction> facingProperty;

    public int animChunkRadius = TrackedPlayers.DEFAULT_ANIM_CHUNK_RADIUS;
    public int particleInterval = 20;
    public int particleCount = 3;
    public int cookingTime = 400;
    public Key lidItem = ItemKeys.STOCKPOT_LID;
    public Key bowlItem = ItemKeys.BOWL;
    public Key recipeItemNoRecipe = ItemKeys.RECIPE_ITEM_NO_RECIPE;
    public Key recipeItemHasRecipe = ItemKeys.RECIPE_ITEM_HAS_RECIPE;
    public String msgStartStewing = "开始炖煮了，记得准备碗来盛菜";
    public String msgNotEnoughIngredients = "背包中没有足够的食材！";
    public String msgNoRecipe = "当前食材没有对应的配方！";
    public String msgRecipeSaved = "已成功记录这道菜的食谱！";
    public String msgUseBowl = "请手持碗右键盛出";

    public StockpotBehavior(BlockDefinition blockDefinition) {
        super(blockDefinition);
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        net.momirealms.craftengine.core.entity.player.Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        CEWorld world = context.getLevel().storageWorld();
        BlockEntity blockEntity = world.getBlockEntityAtIfLoaded(context.getClickedPos());
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }

        StockpotController controller = blockEntity.controller.get(StockpotController.class, this.controllerId);
        if (controller == null) {
            return InteractionResult.PASS;
        }

        World level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!InteractGuard.canInteract(player, level, pos)) {
            return InteractionResult.PASS;
        }

        // 只处理主手那次调用 避免主副手各触发一次
        if (context.getHand() == InteractionHand.OFF_HAND) {
            return InteractionResult.PASS;
        }
        InteractionHand mainHand = InteractionHand.MAIN_HAND;
        Item mainItem = player.getItemInHand(mainHand);

        // 锅盖与食谱本 工具副手优先
        InteractionHand toolHand = Hands.toolHand(player, this::isStockpotTool);
        Item toolItem = player.getItemInHand(toolHand);

        // 盖上锅盖 工具
        InteractionResult result = handleAddLid(context, state, controller, player, toolHand, toolItem);
        if (result != InteractionResult.PASS) {
            return result;
        }

        // 取下锅盖 空手 只认主手
        result = handleRemoveLid(context, state, controller, player, mainHand, mainItem);
        if (result != InteractionResult.PASS) {
            return result;
        }

        // 无锅盖时处理汤底/食材/食谱
        if (!state.get(hasLidProperty)) {
            // 汤底 只认主手
            result = handleSoupBase(context, controller, player, mainHand, mainItem);
            if (result != InteractionResult.PASS) {
                return result;
            }

            // 食谱本 工具
            result = handleRecipe(controller, player, toolHand, toolItem);
            if (result != InteractionResult.PASS) {
                return result;
            }

            // 食材 只认主手
            result = handleIngredient(controller, player, mainHand, mainItem);
            if (result != InteractionResult.PASS) {
                return result;
            }
        }

        // 盛出成品 只认主手
        result = handleExtractDish(state, controller, player, mainHand, mainItem, pos, world);
        if (result != InteractionResult.PASS) {
            return result;
        }

        return InteractionResult.PASS;
    }

    // 高汤锅的工具类物品 锅盖 食谱本 走副手优先
    private boolean isStockpotTool(Item item) {
        return ItemMatch.is(item, lidItem)
                || ItemMatch.is(item, recipeItemNoRecipe)
                || ItemMatch.is(item, recipeItemHasRecipe);
    }

    // 盖上锅盖
    private InteractionResult handleAddLid(UseOnContext context, ImmutableBlockState state, StockpotController controller,
                                           net.momirealms.craftengine.core.entity.player.Player player,
                                           InteractionHand hand, Item itemInHand) {
        if (ItemMatch.is(itemInHand, lidItem) && !state.get(hasLidProperty)) {
            if (controller.addLid(itemInHand)) {
                InventoryUtils.shrinkHeld(player, itemInHand, 1);
                updateLidState(context, state, true);
                player.swingHand(hand);
                if (controller.stage() == StockpotStage.PUT_INGREDIENT && !controller.getIngredients().isEmpty()) {
                    player.sendActionBar(Component.text(msgStartStewing));
                }
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
        }
        return InteractionResult.PASS;
    }

    // 取下锅盖
    private InteractionResult handleRemoveLid(UseOnContext context, ImmutableBlockState state, StockpotController controller,
                                              net.momirealms.craftengine.core.entity.player.Player player,
                                              InteractionHand hand, Item itemInHand) {
        if (itemInHand.isEmpty() && state.get(hasLidProperty)) {
            Item lid = controller.removeLid();
            if (lid != null) {
                InventoryUtils.giveOrHold(player, hand, lid);
                updateLidState(context, state, false);
                player.swingHand(hand);
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
        }
        return InteractionResult.PASS;
    }

    // 放入/舀出汤底
    private InteractionResult handleSoupBase(UseOnContext context, StockpotController controller,
                                             net.momirealms.craftengine.core.entity.player.Player player,
                                             InteractionHand hand, Item itemInHand) {
        // 放入汤底
        if (SoupBaseRegistry.instance().isSoupBase(itemInHand.id())) {
            if (controller.addSoupBase(itemInHand.id(), HeatSourceUtils.isHeatSourceBelow(context))) {
                InventoryUtils.shrinkHeld(player, itemInHand, 1);
                InventoryUtils.giveOrHold(player, hand, InventoryUtils.createOrEmpty(ItemKeys.BUCKET));
                player.swingHand(hand);
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
        }

        // 舀出汤底
        if (ItemMatch.is(itemInHand, ItemKeys.BUCKET)) {
            if (controller.stage() == StockpotStage.PUT_INGREDIENT && controller.getIngredients().isEmpty()) {
                Item extractedSoup = controller.extractSoupBase();
                if (extractedSoup != null) {
                    InventoryUtils.shrinkHeld(player, itemInHand, 1);
                    InventoryUtils.giveOrHold(player, hand, extractedSoup);
                    player.swingHand(hand);
                    return InteractionResult.SUCCESS_AND_CANCEL;
                }
            }
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        return InteractionResult.PASS;
    }

    // 食谱 一键投料 / 记录食谱
    private InteractionResult handleRecipe(StockpotController controller,
                                           net.momirealms.craftengine.core.entity.player.Player player,
                                           InteractionHand hand, Item itemInHand) {
        if (!ItemMatch.is(itemInHand, recipeItemNoRecipe) && !ItemMatch.is(itemInHand, recipeItemHasRecipe)) {
            return InteractionResult.PASS;
        }

        ItemStack bukkitStack = ItemStackUtils.getBukkitStack(itemInHand.minecraftItem());

        if (RecipeUtils.hasRecipe(bukkitStack)) {
            // 一键投料
            if (controller.stage() == StockpotStage.PUT_INGREDIENT && controller.getIngredients().isEmpty()) {
                boolean filled = RecipeUtils.tryAutoFill(
                        (Player) player.platformPlayer(),
                        bukkitStack,
                        controller::addIngredient
                );
                if (filled) {
                    player.swingHand(hand);
                } else {
                    player.sendActionBar(Component.text(msgNotEnoughIngredients));
                }
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            return InteractionResult.PASS;
        }

        // 记录食谱
        List<Key> ingredientIds = null;
        if (!controller.getIngredients().isEmpty()
                && controller.stage() == StockpotStage.PUT_INGREDIENT) {
            ingredientIds = controller.getIngredients().stream().map(Item::id).toList();
        } else if (controller.stage() == StockpotStage.FINISHED
                && !controller.getLastCookedIngredients().isEmpty()) {
            ingredientIds = controller.getLastCookedIngredients();
        }
        if (ingredientIds != null) {
            FlexFoodRecipe matchedRecipe = FoodRecipeRegistry.instance()
                    .findBestFlexRecipe(ApplianceType.STOCKPOT, ingredientIds)
                    .orElse(null);
            if (matchedRecipe == null) {
                player.sendActionBar(Component.text(msgNoRecipe));
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            Item hasRecipeItem = InventoryUtils.createOrEmpty(recipeItemHasRecipe);
            ItemStack recorded = ItemStackUtils.getBukkitStack(hasRecipeItem.minecraftItem());
            RecipeUtils.setRecipeItem(recorded, matchedRecipe.id(), "flex", ingredientIds, controller.getSoupBaseId());
            InventoryUtils.shrinkHeld(player, itemInHand, 1);
            Item ceRecorded = BukkitItemManager.instance().wrap(recorded);
            InventoryUtils.giveOrHold(player, hand, ceRecorded);
            player.swingHand(hand);
            player.sendActionBar(Component.text(msgRecipeSaved));
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 加入/取出食材
    private InteractionResult handleIngredient(StockpotController controller,
                                               net.momirealms.craftengine.core.entity.player.Player player,
                                               InteractionHand hand, Item itemInHand) {
        // 加入食材
        if (!itemInHand.isEmpty()
                && FoodCategoryRegistry.instance().isRegistered(ApplianceType.STOCKPOT, itemInHand.id())
                && controller.getIngredients().size() < StockpotController.MAX_INGREDIENTS) {
            if (controller.addIngredient(itemInHand.copyWithCount(1))) {
                InventoryUtils.shrinkHeld(player, itemInHand, 1);
                player.swingHand(hand);
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
        }

        // 取出食材
        if (itemInHand.isEmpty()) {
            Item extracted = controller.extractIngredient(player);
            if (!extracted.isEmpty()) {
                InventoryUtils.giveOrHold(player, hand, extracted);
                player.swingHand(hand);
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
        }

        return InteractionResult.PASS;
    }

    // 盛出成品
    private InteractionResult handleExtractDish(ImmutableBlockState state, StockpotController controller,
                                                net.momirealms.craftengine.core.entity.player.Player player,
                                                InteractionHand hand, Item itemInHand,
                                                BlockPos pos, CEWorld world) {
        if (controller.stage() == StockpotStage.FINISHED && !state.get(hasLidProperty)) {
            if (ItemMatch.is(itemInHand, bowlItem)) {
                Item result = controller.takeOutResult();
                if (!result.isEmpty()) {
                    // 触发 StockpotExtractDishEvent 可改写/取消
                    ItemStack dish = ItemStackUtils.getBukkitStack(result.minecraftItem());
                    Location loc = new Location((org.bukkit.World) world.world().platformWorld(), pos.x, pos.y, pos.z);
                    StockpotExtractDishEvent event = new StockpotExtractDishEvent(
                            (Player) player.platformPlayer(), loc, dish);
                    if (EventUtils.fireAndCheckCancel(event)) {
                        return InteractionResult.SUCCESS_AND_CANCEL;
                    }
                    Item finalResult = BukkitItemManager.instance().wrap(event.dish());
                    InventoryUtils.shrinkHeld(player, itemInHand, 1);
                    InventoryUtils.giveOrHold(player, hand, finalResult);
                    player.swingHand(hand);
                    return InteractionResult.SUCCESS_AND_CANCEL;
                }
            } else {
                player.sendActionBar(Component.text(msgUseBowl));
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
        }
        return InteractionResult.PASS;
    }

    private void updateLidState(UseOnContext context, ImmutableBlockState state, boolean hasLid) {
        ImmutableBlockState newState = state.with(hasLidProperty, hasLid);
        LevelWriterProxy.INSTANCE.setBlock(
                context.getLevel().minecraftWorld(),
                LocationUtils.toBlockPos(context.getClickedPos()),
                newState.customBlockState().minecraftState(),
                3
        );
    }

    @Override
    public Object updateShape(Object thisBlock, Object[] args) {
        Object blockState = args[0];
        Object level = args[updateShape$level];
        Object blockPos = args[updateShape$blockPos];
        Object direction = args[updateShape$direction];
        Object neighborState = args[updateShape$neighborState];

        ImmutableBlockState customState = BlockStateUtils.getOptionalCustomBlockState(blockState).orElse(null);
        if (customState == null || customState.isEmpty()) return blockState;

        Direction nmsDirection = DirectionUtils.fromNMSDirection(direction);

        if (nmsDirection == Direction.DOWN) {
            Object belowPos = LocationUtils.below(blockPos);
            Object belowState = BlockGetterProxy.INSTANCE.getBlockState(level, belowPos);
            boolean hasChains = customState.get(this.hasChainsProperty);
            boolean shouldHaveBase = !SupportStateUtils.isSturdyUp(level, belowPos, belowState) && !hasChains;
            if (customState.get(this.hasBaseProperty) != shouldHaveBase) {
                return customState.with(this.hasBaseProperty, shouldHaveBase)
                        .customBlockState().minecraftState();
            }
        }

        if (nmsDirection == Direction.UP) {
            Object abovePos = LocationUtils.above(blockPos);
            Object aboveState = BlockGetterProxy.INSTANCE.getBlockState(level, abovePos);
            boolean shouldHaveChains = canHangChains(level, abovePos, aboveState);
            Object belowPos = LocationUtils.below(blockPos);
            Object belowState = BlockGetterProxy.INSTANCE.getBlockState(level, belowPos);
            boolean shouldHaveBase = !SupportStateUtils.isSturdyUp(level, belowPos, belowState) && !shouldHaveChains;
            if (customState.get(this.hasChainsProperty) != shouldHaveChains
                    || customState.get(this.hasBaseProperty) != shouldHaveBase) {
                return customState
                        .with(this.hasChainsProperty, shouldHaveChains)
                        .with(this.hasBaseProperty, shouldHaveBase)
                        .customBlockState().minecraftState();
            }
        }

        return blockState;
    }

    @Override
    public ImmutableBlockState updateStateForPlacement(BlockPlaceContext context, ImmutableBlockState state) {
        Object level = context.getLevel().minecraftWorld();
        Object clickedPos = LocationUtils.toBlockPos(context.getClickedPos());

        ImmutableBlockState newState = state;

        Object abovePos = LocationUtils.above(clickedPos);
        Object aboveState = BlockGetterProxy.INSTANCE.getBlockState(level, abovePos);
        boolean shouldHaveChains = canHangChains(level, abovePos, aboveState);

        Object belowPos = LocationUtils.below(clickedPos);
        Object belowState = BlockGetterProxy.INSTANCE.getBlockState(level, belowPos);
        boolean shouldHaveBase = !SupportStateUtils.isSturdyUp(level, belowPos, belowState) && !shouldHaveChains;

        newState = newState
                .with(this.hasChainsProperty, shouldHaveChains)
                .with(this.hasBaseProperty, shouldHaveBase);

        return newState;
    }

    private boolean canHangChains(Object level, Object abovePos, Object aboveState) {
        try {
            return BlockBehaviourProxy.BlockStateBaseProxy.INSTANCE.isFaceSturdy(
                    aboveState, level, abovePos, DirectionProxy.DOWN, SupportTypeProxy.CENTER);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new StockpotController(blockEntity, this);
    }

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    public Property<Boolean> getHasLidProperty() {
        return hasLidProperty;
    }

    public Property<Boolean> getHasBaseProperty() {
        return hasBaseProperty;
    }

    public Property<Boolean> getHasChainsProperty() {
        return hasChainsProperty;
    }

    public Property<Direction> getFacingProperty() {
        return facingProperty;
    }

    private static class Factory implements BlockBehaviorFactory<StockpotBehavior> {
        @Override
        public StockpotBehavior create(BlockDefinition block, ConfigSection section) {
            StockpotBehavior behavior = new StockpotBehavior(block);
            behavior.hasLidProperty = BlockBehaviorFactory.getProperty(section.path(), block, "has_lid", Boolean.class);
            behavior.hasBaseProperty = BlockBehaviorFactory.getProperty(section.path(), block, "has_base", Boolean.class);
            behavior.hasChainsProperty = BlockBehaviorFactory.getProperty(section.path(), block, "has_chains", Boolean.class);
            behavior.facingProperty = BlockBehaviorFactory.getProperty(section.path(), block, "facing", Direction.class);

            behavior.animChunkRadius = BehaviorConfig.getInt(section, behavior.animChunkRadius, "animation_view_distance", "animation-view-distance");
            behavior.particleInterval = BehaviorConfig.getInt(section, behavior.particleInterval, "particle_interval", "particle-interval");
            behavior.particleCount = BehaviorConfig.getInt(section, behavior.particleCount, "particle_count", "particle-count");
            behavior.cookingTime = BehaviorConfig.getInt(section, behavior.cookingTime, "cooking_time", "cooking-time");
            behavior.lidItem = Key.of(BehaviorConfig.getString(section, behavior.lidItem.asString(), "lid_item", "lid-item"));
            behavior.bowlItem = Key.of(BehaviorConfig.getString(section, behavior.bowlItem.asString(), "bowl_item", "bowl-item"));
            behavior.recipeItemNoRecipe = Key.of(BehaviorConfig.getString(section, behavior.recipeItemNoRecipe.asString(), "recipe_item_no_recipe", "recipe-item-no-recipe"));
            behavior.recipeItemHasRecipe = Key.of(BehaviorConfig.getString(section, behavior.recipeItemHasRecipe.asString(), "recipe_item_has_recipe", "recipe-item-has-recipe"));
            behavior.msgStartStewing = BehaviorConfig.getString(section, behavior.msgStartStewing, "msg_start_stewing", "msg-start-stewing");
            behavior.msgNotEnoughIngredients = BehaviorConfig.getString(section, behavior.msgNotEnoughIngredients, "msg_not_enough_ingredients", "msg-not-enough-ingredients");
            behavior.msgNoRecipe = BehaviorConfig.getString(section, behavior.msgNoRecipe, "msg_no_recipe", "msg-no-recipe");
            behavior.msgRecipeSaved = BehaviorConfig.getString(section, behavior.msgRecipeSaved, "msg_recipe_saved", "msg-recipe-saved");
            behavior.msgUseBowl = BehaviorConfig.getString(section, behavior.msgUseBowl, "msg_use_bowl", "msg-use-bowl");
            return behavior;
        }
    }
}
