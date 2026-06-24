package net.kaleidoscope.cookery.plugin;

import net.momirealms.craftengine.core.block.behavior.BlockBehaviorType;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureBehaviorType;
import net.momirealms.craftengine.core.util.Key;
import net.kaleidoscope.cookery.block.behavior.BarStoolBehavior;
import net.kaleidoscope.cookery.block.behavior.RecipeDisplayBehavior;
import net.kaleidoscope.cookery.block.behavior.StoveBehavior;
import net.kaleidoscope.cookery.block.behavior.SteamerBehavior;
import net.kaleidoscope.cookery.block.behavior.PotBehavior;
import net.kaleidoscope.cookery.block.behavior.StockpotBehavior;
import net.kaleidoscope.cookery.block.behavior.EnamelBasinBehavior;
import net.kaleidoscope.cookery.block.behavior.ShawarmaSpitBehavior;
import net.kaleidoscope.cookery.block.behavior.KitchenwareRacksBehavior;
import net.kaleidoscope.cookery.block.behavior.MillstoneBehavior;
import net.kaleidoscope.cookery.block.behavior.ChoppingBoardBehavior;

// 方块行为注册：注册细节委托给 RegistryUtils
public final class BlockBehaviors {
    public static BlockBehaviorType<PotBehavior> COOKING_POT;
    public static BlockBehaviorType<EnamelBasinBehavior> ENAMEL_BASIN;
    public static BlockBehaviorType<StoveBehavior> STOVE;
    public static BlockBehaviorType<KitchenwareRacksBehavior> KITCHENWARE_RACKS;
    public static BlockBehaviorType<SteamerBehavior> STEAMER;
    public static BlockBehaviorType<StockpotBehavior> STOCKPOT;
    public static BlockBehaviorType<ShawarmaSpitBehavior> SHAWARMA_SPIT;
    public static BlockBehaviorType<ChoppingBoardBehavior> CHOPPING_BOARD;
    public static FurnitureBehaviorType<MillstoneBehavior> MILLSTONE_FURNITURE;
    public static BlockBehaviorType<BarStoolBehavior> BAR_STOOL;
    public static FurnitureBehaviorType<RecipeDisplayBehavior> RECIPE_FURNITURE;

    private BlockBehaviors() {}

    public static void register() {
        if (COOKING_POT == null) {
            COOKING_POT = RegistryUtils.registerBlockBehavior(
                    Key.of("kaleidoscopecookery:cooking_pot"),
                    PotBehavior.FACTORY
            );
        }
        if (ENAMEL_BASIN == null) {
            ENAMEL_BASIN = RegistryUtils.registerBlockBehavior(
                    Key.of("kaleidoscopecookery:cooking_enamel_basin"),
                    EnamelBasinBehavior.FACTORY
            );
        }
        if (STOVE == null) {
            STOVE = RegistryUtils.registerBlockBehavior(
                    Key.of("kaleidoscopecookery:stove"),
                    StoveBehavior.FACTORY
            );
        }
        if (KITCHENWARE_RACKS == null) {
            KITCHENWARE_RACKS = RegistryUtils.registerBlockBehavior(
                    Key.of("kaleidoscopecookery:kitchenware_racks"),
                    KitchenwareRacksBehavior.FACTORY
            );
        }
        if (STEAMER == null) {
            STEAMER = RegistryUtils.registerBlockBehavior(
                    Key.of("kaleidoscopecookery:steamer"),
                    SteamerBehavior.FACTORY
            );
        }
        if (STOCKPOT== null) {
            STOCKPOT = RegistryUtils.registerBlockBehavior(
                    Key.of("kaleidoscopecookery:stockpot"),
                    StockpotBehavior.FACTORY
            );
        }
        if (SHAWARMA_SPIT == null) {
            SHAWARMA_SPIT = RegistryUtils.registerBlockBehavior(
                    Key.of("kaleidoscopecookery:shawarma_spit"),
                    ShawarmaSpitBehavior.FACTORY
            );
        }

        if (CHOPPING_BOARD == null) {
            CHOPPING_BOARD = RegistryUtils.registerBlockBehavior(
                    Key.of("kaleidoscopecookery:chopping_board"),
                    ChoppingBoardBehavior.FACTORY
            );
        }
        if (BAR_STOOL == null) {
            BAR_STOOL = RegistryUtils.registerBlockBehavior(
                    Key.of("kaleidoscopecookery:bar_stool"),
                    BarStoolBehavior.FACTORY
            );
        }
        if (RECIPE_FURNITURE == null) {
            RECIPE_FURNITURE = RegistryUtils.registerFurnitureBehavior(
                    Key.of("kaleidoscopecookery:recipe_furniture"),
                    RecipeDisplayBehavior.FACTORY
            );
        }
        if (MILLSTONE_FURNITURE == null) {
            MILLSTONE_FURNITURE = RegistryUtils.registerFurnitureBehavior(
                    Key.of("kaleidoscopecookery:millstone"),
                    MillstoneBehavior.FACTORY
            );
        }
    }
}