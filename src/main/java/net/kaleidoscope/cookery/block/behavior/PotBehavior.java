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
import net.momirealms.craftengine.core.entity.EquipmentSlot;
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
import net.kaleidoscope.cookery.util.Localization;
import net.kaleidoscope.cookery.util.MessageKeys;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.item.ItemMatch;
import net.kaleidoscope.cookery.item.ItemNames;
import net.kaleidoscope.cookery.item.KitchenShovel;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.FlexFoodRecipe;
import net.kaleidoscope.cookery.recipe.ApplianceFoodRegistry;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.util.RecipeUtils;
import net.kaleidoscope.cookery.util.EventUtils;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.api.PotCookConditions;
import net.kaleidoscope.cookery.api.event.PotExtractDishEvent;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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
    // 每次翻炒按此概率掉一点锅铲耐久 0 即不磨损
    public double stirFryDamageChance = 0.25;
    public int stirFryDamage = 1;
    public Key oilItem = ItemKeys.OIL;

    public Key shovelItem = ItemKeys.KITCHEN_SHOVEL;
    public Key shovelOilModel = ItemKeys.KITCHEN_SHOVEL_OIL_MODEL;
    // 油壶 一壶多次 耐久即剩余油量 倒空换成空壶
    public Key oilPotItem = ItemKeys.OIL_POT;
    public Key oilPotEmptyItem = ItemKeys.OIL_POT_EMPTY;
    public Key recipeItemNoRecipe = ItemKeys.RECIPE_ITEM_NO_RECIPE;
    public Key recipeItemHasRecipe = ItemKeys.RECIPE_ITEM_HAS_RECIPE;

    public Key bowlItem = ItemKeys.BOWL;

    // 没配出菜的产物与它的盛出容器 容器写 minecraft:air 表示空手就能盛
    public Key failedResultItem = ItemKeys.SUSPICIOUS_STIR_FRY;
    public Key failedResultCarrier = ItemKeys.BOWL;
    // 出锅后过了盛出窗口烧糊的产物
    public Key burntResultItem = ItemKeys.DARK_CUISINE;
    public Key burntResultCarrier = ItemKeys.BOWL;

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
        if (KitchenShovel.is(toolItem, shovelItem)) {
            toolResult = KitchenShovel.hasOil(toolItem, shovelOilModel)
                    ? handleAddOilWithShovel(context, controller, player, toolHand, toolItem, hasHeatSource)
                    : handleStirFry(context, controller, player, toolHand, toolItem, hasHeatSource);
        } else if (ItemMatch.is(toolItem, oilPotItem)) {
            toolResult = handleAddOilWithPot(context, controller, player, toolHand, toolItem, hasHeatSource);
        } else if (ItemMatch.is(toolItem, oilItem)) {
            toolResult = handleAddOil(context, controller, player, toolHand, toolItem, hasHeatSource);
        } else if (ItemMatch.is(toolItem, recipeItemNoRecipe) || ItemMatch.is(toolItem, recipeItemHasRecipe)) {
            toolResult = handleRecipe(context, controller, player, toolHand, toolItem, hasHeatSource);
        }
        if (toolResult != InteractionResult.PASS) {
            return toolResult;
        }

        // 取放食材只认主手
        Item mainItem = player.getItemInHand(InteractionHand.MAIN_HAND);
        // 盛装容器由配方决定 carrier 为空表示空手就能盛 所以这条要排在空手取食材前面
        boolean cooked = controller.stage() == PotStage.DONE || controller.stage() == PotStage.BURNT;
        if (cooked) {
            Key carrier = controller.resultCarrier();
            boolean holdingCarrier = carrier == null ? mainItem.isEmpty() : mainItem.vanillaId().equals(carrier);
            if (holdingCarrier) {
                return handleExtractDish(context, controller, player, InteractionHand.MAIN_HAND);
            }
        }
        if (mainItem.isEmpty()) {
            return handleTakeIngredient(controller, player, InteractionHand.MAIN_HAND);
        }
        if (ApplianceFoodRegistry.instance().isAllowed(ApplianceType.POT, mainItem.id())) {
            return handleAddIngredient(context, controller, player, InteractionHand.MAIN_HAND, mainItem, hasHeatSource);
        }
        return InteractionResult.PASS;
    }

    // 锅的工具类物品 锅铲 油瓶 食谱本 这些走副手优先
    private boolean isPotTool(Item item) {
        return KitchenShovel.is(item, shovelItem)
                || ItemMatch.is(item, oilPotItem)
                || ItemMatch.is(item, oilItem)
                || ItemMatch.is(item, recipeItemNoRecipe)
                || ItemMatch.is(item, recipeItemHasRecipe);
    }

    // 用碗盛出成品 有多少碗盛多少份
    private InteractionResult handleExtractDish(UseOnContext context, PotController controller, Player player, InteractionHand hand) {
        // 先预览再发事件 事件取消时不能已经扣掉份数
        Item preview = controller.peekResult();
        // 锅里没成品 这次右键什么都没做 交回原版
        if (preview.isEmpty()) return InteractionResult.PASS;

        BlockPos pos = context.getClickedPos();
        Location dishLoc = new Location((World) context.getLevel().platformWorld(), pos.x(), pos.y(), pos.z());
        ItemStack dishStack = ItemStackUtils.getBukkitStack(preview.minecraftItem());
        PotExtractDishEvent event = new PotExtractDishEvent((org.bukkit.entity.Player) player.platformPlayer(), dishLoc, dishStack);
        if (EventUtils.fireAndCheckCancel(event)) {
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        Item dish = BukkitItemManager.instance().wrap(event.dish());

        Key carrier = controller.resultCarrier();
        int available = controller.resultCount();
        int taken = 0;
        // carrier 为空的菜空手就能拿 不扣任何东西 有容器的每份扣一个
        while (taken < available && (carrier == null || InventoryUtils.consumeItem(player, carrier, 1))) {
            InventoryUtils.giveOrHold(player, hand, dish.copyWithCount(1));
            taken++;
        }

        if (taken > 0) {
            controller.consumeResult(taken);
            player.swingHand(hand);
        } else {
            player.sendActionBar(carrier == null
                    ? Localization.component(MessageKeys.POT_USE_HAND)
                    : Localization.componentWithReplacement(
                            MessageKeys.POT_NEED_BOWL, "%s", ItemNames.displayName(carrier)));
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
    private InteractionResult handleAddOilWithShovel(UseOnContext context, PotController controller, Player player,
                                                      InteractionHand hand, Item shovel, boolean hasHeatSource) {
        if (controller.hasOil()) {
            player.sendActionBar(Localization.component(MessageKeys.POT_HAS_OIL));
        } else if (controller.stage() == PotStage.DONE || controller.stage() == PotStage.BURNT) {
            player.sendActionBar(Localization.component(MessageKeys.POT_OCCUPIED));
        } else if (!hasHeatSource) {
            player.sendActionBar(Localization.component(MessageKeys.POT_NEED_HEAT));
        } else {
            controller.setHasOil(true);
            if (!KitchenShovel.isLegacy(shovel)) {
                KitchenShovel.setHasOil(shovel, false, shovelItem, shovelOilModel);
            }
            context.getLevel().playSound(Vec3d.atCenterOf(context.getClickedPos()), SOUND_ADD_OIL, DEFAULT_VOLUME, 1.0f, SoundSource.BLOCK);
            player.swingHand(hand);
        }
        if (KitchenShovel.isLegacy(shovel)) {
            KitchenShovel.migrateLegacy(player, hand, shovel, shovelItem, shovelOilModel, false);
        }
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 用油壶倒油 扣一点耐久 耐久耗尽换成空壶 别让它碎掉
    private InteractionResult handleAddOilWithPot(UseOnContext context, PotController controller, Player player, InteractionHand hand, Item itemInHand, boolean hasHeatSource) {
        if (controller.hasOil()) {
            player.sendActionBar(Localization.component(MessageKeys.POT_HAS_OIL));
        } else if (controller.stage() == PotStage.DONE || controller.stage() == PotStage.BURNT) {
            player.sendActionBar(Localization.component(MessageKeys.POT_OCCUPIED));
        } else if (!hasHeatSource) {
            player.sendActionBar(Localization.component(MessageKeys.POT_NEED_HEAT));
        } else {
            controller.setHasOil(true);
            if (!player.canInstabuild()) {
                // 先判断这是不是最后一次 是就直接换空壶
                // 交给 hurtAndBreak 的话物品会先摔碎再补发 会多一声破碎音效和一帧空手
                int max = itemInHand.maxDamage();
                int damage = itemInHand.damage().orElse(0);
                if (max > 0 && damage + 1 >= max) {
                    player.setItemInHand(hand, InventoryUtils.createOrEmpty(oilPotEmptyItem));
                } else {
                    itemInHand.damage(damage + 1);
                    player.setItemInHand(hand, itemInHand);
                }
            }
            context.getLevel().playSound(Vec3d.atCenterOf(context.getClickedPos()), SOUND_ADD_OIL, DEFAULT_VOLUME, 1.0f, SoundSource.BLOCK);
            player.swingHand(hand);
        }
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 用油瓶倒油
    private InteractionResult handleAddOil(UseOnContext context, PotController controller, Player player, InteractionHand hand, Item itemInHand, boolean hasHeatSource) {
        if (controller.hasOil()) {
            player.sendActionBar(Localization.component(MessageKeys.POT_HAS_OIL));
        } else if (controller.stage() == PotStage.DONE || controller.stage() == PotStage.BURNT) {
            player.sendActionBar(Localization.component(MessageKeys.POT_OCCUPIED));
        } else if (!hasHeatSource) {
            player.sendActionBar(Localization.component(MessageKeys.POT_NEED_HEAT));
        } else {
            controller.setHasOil(true);
            InventoryUtils.shrinkHeld(player, itemInHand, 1);
            context.getLevel().playSound(Vec3d.atCenterOf(context.getClickedPos()), SOUND_ADD_OIL, DEFAULT_VOLUME, 1.0f, SoundSource.BLOCK);
            player.swingHand(hand);
        }
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 翻炒 锅里翻炒不了(空/已完成/动画中)就不挥手 返回 PASS 让调用方继续走主手逻辑
    // 起炒条件没满足时已经提示过玩家 这里吞掉这次右键但不挥手 别让人以为炒了一下
    private InteractionResult handleStirFry(UseOnContext context, PotController controller, Player player,
                                            InteractionHand hand, Item shovel, boolean hasHeatSource) {
        PotController.StirResult result = controller.stirFry(hasHeatSource, player);
        if (result == PotController.StirResult.IDLE) {
            return InteractionResult.PASS;
        }
        if (result == PotController.StirResult.DENIED) {
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        if (stirFryDamage > 0 && stirFryDamageChance > 0 && !player.canInstabuild()
                && ThreadLocalRandom.current().nextDouble() < stirFryDamageChance) {
            shovel.hurtAndBreak(stirFryDamage, player,
                    hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
        context.getLevel().playSound(Vec3d.atCenterOf(context.getClickedPos()), SOUND_STIR_FRY, DEFAULT_VOLUME, 1.0f, SoundSource.BLOCK);
        player.swingHand(hand);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 食谱书 自动投料或记录食谱
    private InteractionResult handleRecipe(UseOnContext context, PotController controller, Player player, InteractionHand hand, Item itemInHand, boolean hasHeatSource) {
        ItemStack bukkitStack = ItemStackUtils.getBukkitStack(itemInHand.minecraftItem());
        if (RecipeUtils.hasRecipe(bukkitStack)) {
            if (controller.stage() == PotStage.IDLE && controller.ingredients().isEmpty()) {
                PotCookConditions.Verdict verdict = controller.cookVerdict(hasHeatSource, player);
                if (!verdict.allowed()) {
                    if (verdict.message() != null) {
                        player.sendActionBar(Localization.component(verdict.message()));
                    }
                } else if (!RecipeUtils.tryAutoFill((org.bukkit.entity.Player) player.platformPlayer(), bukkitStack,
                        item -> controller.addIngredient(item, hasHeatSource, player))) {
                    player.sendActionBar(Localization.component(MessageKeys.POT_NOT_ENOUGH_INGREDIENTS));
                } else {
                    player.swingHand(hand);
                    context.getLevel().playSound(Vec3d.atCenterOf(context.getClickedPos()), SOUND_ADD_INGREDIENT, DEFAULT_VOLUME, 0.5f, SoundSource.BLOCK);
                }
            }
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        if (controller.stage() == PotStage.BURNT) {
            player.sendActionBar(Localization.component(MessageKeys.POT_BURNT_NO_RECIPE));
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        if (controller.stage() != PotStage.DONE) {
            player.sendActionBar(Localization.component(MessageKeys.POT_NOT_DONE_YET));
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        List<Key> ingredientIds = controller.ingredients().stream().map(Item::id).toList();
        FlexFoodRecipe matchedRecipe = FoodRecipeRegistry.instance().findBestFlexRecipe(ApplianceType.POT, ingredientIds).orElse(null);
        if (matchedRecipe == null) {
            player.sendActionBar(Localization.component(MessageKeys.POT_MIXED_NO_RECIPE));
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        Item recipeItem = InventoryUtils.createOrEmpty(recipeItemHasRecipe);
        ItemStack recorded = ItemStackUtils.getBukkitStack(recipeItem.minecraftItem());
        RecipeUtils.setRecipeItem(recorded, matchedRecipe.id(), "flex", ingredientIds, null);
        InventoryUtils.shrinkHeld(player, itemInHand, 1);
        InventoryUtils.giveOrHold(player, hand, BukkitItemManager.instance().wrap(recorded));
        player.swingHand(hand);
        player.sendActionBar(Localization.component(MessageKeys.POT_RECIPE_SAVED));
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    // 投入单个食材
    private InteractionResult handleAddIngredient(UseOnContext context, PotController controller, Player player, InteractionHand hand, Item itemInHand, boolean hasHeatSource) {
        int preCount = controller.ingredients().size();
        controller.addIngredient(itemInHand.copyWithCount(1), hasHeatSource, player);
        if (preCount < controller.ingredients().size()) {
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
            b.stirFryDamageChance = BehaviorConfig.getDouble(section, b.stirFryDamageChance, "stir_fry_damage_chance", "stir-fry-damage-chance");
            b.stirFryDamage = BehaviorConfig.getInt(section, b.stirFryDamage, "stir_fry_damage", "stir-fry-damage");

            b.failedResultItem = BehaviorConfig.getKey(section, b.failedResultItem, "failed_result_item", "failed-result-item");
            b.failedResultCarrier = BehaviorConfig.getCarrier(section, b.failedResultCarrier, "failed_result_carrier", "failed-result-carrier");
            b.burntResultItem = BehaviorConfig.getKey(section, b.burntResultItem, "burnt_result_item", "burnt-result-item");
            b.burntResultCarrier = BehaviorConfig.getCarrier(section, b.burntResultCarrier, "burnt_result_carrier", "burnt-result-carrier");

            b.oilItem = Key.of(BehaviorConfig.getString(section, b.oilItem.asString(), "oil_item", "oil-item"));
            b.shovelItem = Key.of(BehaviorConfig.getString(section, b.shovelItem.asString(), "shovel_item", "shovel-item", "shovel_no_oil_item", "shovel-no-oil-item"));
            b.shovelOilModel = Key.of(BehaviorConfig.getString(section, b.shovelOilModel.asString(), "shovel_oil_model", "shovel-oil-model", "shovel_has_oil_item", "shovel-has-oil-item"));
            b.oilPotItem = Key.of(BehaviorConfig.getString(section, b.oilPotItem.asString(), "oil_pot_item", "oil-pot-item"));
            b.oilPotEmptyItem = Key.of(BehaviorConfig.getString(section, b.oilPotEmptyItem.asString(), "oil_pot_empty_item", "oil-pot-empty-item"));
            b.recipeItemNoRecipe = Key.of(BehaviorConfig.getString(section, b.recipeItemNoRecipe.asString(), "recipe_item_no_recipe", "recipe-item-no-recipe"));
            b.recipeItemHasRecipe = Key.of(BehaviorConfig.getString(section, b.recipeItemHasRecipe.asString(), "recipe_item_has_recipe", "recipe-item-has-recipe"));
            b.bowlItem = Key.of(BehaviorConfig.getString(section, b.bowlItem.asString(), "bowl_item", "bowl-item"));

            return b;
        }
    }
}
