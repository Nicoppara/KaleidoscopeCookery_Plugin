package net.kaleidoscope.cookery.plugin;

import net.momirealms.craftengine.core.block.behavior.BlockBehaviorType;
import net.momirealms.craftengine.core.util.Key;
import net.kaleidoscope.cookery.block.behavior.HangingStringBehavior;
import net.kaleidoscope.cookery.block.behavior.StoveBehavior;
import net.kaleidoscope.cookery.block.behavior.SteamerBehavior;
import net.kaleidoscope.cookery.block.behavior.PotBehavior;
import net.kaleidoscope.cookery.block.behavior.StockpotBehavior;
import net.kaleidoscope.cookery.block.behavior.EnamelBasinBehavior;
import net.kaleidoscope.cookery.block.behavior.OilPotBehavior;
import net.kaleidoscope.cookery.block.behavior.ShawarmaSpitBehavior;
import net.kaleidoscope.cookery.block.behavior.KitchenwareRacksBehavior;
import net.kaleidoscope.cookery.block.behavior.ChoppingBoardBehavior;
import net.kaleidoscope.cookery.block.behavior.StackedExtraDropBehavior;
import net.kaleidoscope.cookery.block.behavior.FruitBasketBehavior;
import net.kaleidoscope.cookery.block.behavior.TeapotBehavior;
import net.kaleidoscope.cookery.block.behavior.TeacupCoasterBehavior;
import net.kaleidoscope.cookery.block.behavior.RiceCropBehavior;
import net.kaleidoscope.cookery.block.behavior.CropHarvestBehavior;
import net.kaleidoscope.cookery.block.behavior.TableBehavior;

// 方块行为注册
public final class BlockBehaviors {
    public static BlockBehaviorType<PotBehavior> COOKING_POT;
    public static BlockBehaviorType<EnamelBasinBehavior> ENAMEL_BASIN;
    public static BlockBehaviorType<OilPotBehavior> OIL_POT;
    public static BlockBehaviorType<StoveBehavior> STOVE;
    public static BlockBehaviorType<KitchenwareRacksBehavior> KITCHENWARE_RACKS;
    public static BlockBehaviorType<SteamerBehavior> STEAMER;
    public static BlockBehaviorType<StockpotBehavior> STOCKPOT;
    public static BlockBehaviorType<ShawarmaSpitBehavior> SHAWARMA_SPIT;
    public static BlockBehaviorType<ChoppingBoardBehavior> CHOPPING_BOARD;
    public static BlockBehaviorType<StackedExtraDropBehavior> STACKED_EXTRA_DROP;
    public static BlockBehaviorType<HangingStringBehavior> HANGING_STRING;
    public static BlockBehaviorType<FruitBasketBehavior> FRUIT_BASKET;
    public static BlockBehaviorType<TeapotBehavior> TEAPOT;
    public static BlockBehaviorType<TeacupCoasterBehavior> TEACUP_COASTER;
    public static BlockBehaviorType<RiceCropBehavior> RICE_CROP;
    public static BlockBehaviorType<CropHarvestBehavior> CROP_HARVEST;
    public static BlockBehaviorType<TableBehavior> TABLE;

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
        if (OIL_POT == null) {
            OIL_POT = RegistryUtils.registerBlockBehavior(
                    Key.of("kaleidoscopecookery:oil_pot"),
                    OilPotBehavior.FACTORY
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
        if (STOCKPOT == null) {
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
        if (STACKED_EXTRA_DROP == null) {
            STACKED_EXTRA_DROP = RegistryUtils.registerBlockBehavior(
                    Key.of("kaleidoscopecookery:stacked_extra_drop"),
                    StackedExtraDropBehavior.FACTORY
            );
        }
        if (HANGING_STRING == null) {
            HANGING_STRING = RegistryUtils.registerBlockBehavior(
                    Key.of("kaleidoscopecookery:hanging_string"),
                    HangingStringBehavior.FACTORY
            );
        }
        if (FRUIT_BASKET == null) {
            FRUIT_BASKET = RegistryUtils.registerBlockBehavior(
                    Key.of("kaleidoscopecookery:fruit_basket"),
                    FruitBasketBehavior.FACTORY
            );
        }
        if (TEAPOT == null) {
            TEAPOT = RegistryUtils.registerBlockBehavior(
                    Key.of("kaleidoscopecookery:teapot"),
                    TeapotBehavior.FACTORY
            );
        }
        if (TEACUP_COASTER == null) {
            TEACUP_COASTER = RegistryUtils.registerBlockBehavior(
                    Key.of("kaleidoscopecookery:teacup_coaster"),
                    TeacupCoasterBehavior.FACTORY
            );
        }
        if (RICE_CROP == null) {
            RICE_CROP = RegistryUtils.registerBlockBehavior(
                    Key.of("kaleidoscopecookery:rice_crop"),
                    RiceCropBehavior.FACTORY
            );
        }
        if (CROP_HARVEST == null) {
            CROP_HARVEST = RegistryUtils.registerBlockBehavior(
                    Key.of("kaleidoscopecookery:crop_harvest"),
                    CropHarvestBehavior.FACTORY
            );
        }
        if (TABLE == null) {
            TABLE = RegistryUtils.registerBlockBehavior(
                    Key.of("kaleidoscopecookery:table"),
                    TableBehavior.FACTORY
            );
        }
    }
}