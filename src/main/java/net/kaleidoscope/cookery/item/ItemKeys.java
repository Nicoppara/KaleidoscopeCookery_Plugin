package net.kaleidoscope.cookery.item;

import net.momirealms.craftengine.core.util.Key;

// 插件用到的物品与展示模型 Key 统一放这里
public final class ItemKeys {
    private ItemKeys() {}

    public static final String NAMESPACE = "kaleidoscopecookery";

    public static final Key KITCHEN_SHOVEL = Key.of("kaleidoscopecookery:kitchen_shovel_no_oil");
    // 沾油态的 item_model CE 按物品 id 生成模型定义 所以删旧物品条目前必须留下同名模型
    public static final Key KITCHEN_SHOVEL_OIL_MODEL = Key.of("kaleidoscopecookery:kitchen_shovel_has_oil");
    // 旧沾油锅铲物品 与上面的模型同名只是历史巧合 1.1.9 删
    public static final Key KITCHEN_SHOVEL_LEGACY_OILED = KITCHEN_SHOVEL_OIL_MODEL;
    public static final Key OIL = Key.of("kaleidoscopecookery:oil");
    public static final Key OIL_POT = Key.of("kaleidoscopecookery:oil_pot");
    public static final Key OIL_POT_EMPTY = Key.of("kaleidoscopecookery:oil_pot_empty");
    public static final Key SHOW_SCARECROW = Key.of("show:scarecrow_body");
    public static final Key SHOW_SCARECROW_HEADLESS = Key.of("show:scarecrow_headless");
    public static final Key SHOW_SCARECROW_LANTERN = Key.of("show:scarecrow_lantern");
    public static final Key SHOW_SCARECROW_SOUL_LANTERN = Key.of("show:scarecrow_soul_lantern");
    public static final Key LANTERN = Key.of("minecraft:lantern");
    public static final Key SOUL_LANTERN = Key.of("minecraft:soul_lantern");

    public static boolean isLantern(Key vanillaId) {
        return LANTERN.equals(vanillaId) || SOUL_LANTERN.equals(vanillaId);
    }

    public static final Key STOCKPOT_LID = Key.of("kaleidoscopecookery:stockpot_lid");

    public static final Key DARK_CUISINE = Key.of("kaleidoscopecookery:dark_cuisine");
    public static final Key SUSPICIOUS_STIR_FRY = Key.of("kaleidoscopecookery:suspicious_stir_fry");
    public static final Key SUSPICIOUS_STEW = Key.of("minecraft:suspicious_stew");

    public static final Key BOWL = Key.of("minecraft:bowl");
    public static final Key CHARCOAL = Key.of("minecraft:charcoal");
    public static final Key BUCKET = Key.of("minecraft:bucket");
    public static final Key LEAD = Key.of("minecraft:lead");
    public static final Key WATER = Key.of("minecraft:water");
    public static final Key LAVA = Key.of("minecraft:lava");
    public static final Key WATER_BUCKET = Key.of("minecraft:water_bucket");
    public static final Key LAVA_BUCKET = Key.of("minecraft:lava_bucket");
    public static final Key FLINT_AND_STEEL = Key.of("minecraft:flint_and_steel");
    public static final Key FIRE_CHARGE = Key.of("minecraft:fire_charge");
    public static final Key WOODEN_SHOVEL = Key.of("minecraft:wooden_shovel");
    public static final Key STONE_SHOVEL = Key.of("minecraft:stone_shovel");
    public static final Key IRON_SHOVEL = Key.of("minecraft:iron_shovel");
    public static final Key GOLDEN_SHOVEL = Key.of("minecraft:golden_shovel");
    public static final Key DIAMOND_SHOVEL = Key.of("minecraft:diamond_shovel");
    public static final Key NETHERITE_SHOVEL = Key.of("minecraft:netherite_shovel");

    public static final Key NEW_MILLSTONE_STICK = Key.of("show:new_millstone_stick");
    public static final Key NEW_MILLSTONE_STICK2 = Key.of("show:new_millstone_stick2");
    public static final Key NEW_MILLSTONE_STONE = Key.of("show:new_millstone_stone");

    public static final Key RECIPE_ITEM_NO_RECIPE = Key.of("kaleidoscopecookery:recipe_item_no_recipe");
    public static final Key RECIPE_ITEM_HAS_RECIPE = Key.of("kaleidoscopecookery:recipe_item_has_recipe");

    public static final Key TRANSMUTATION_LUNCH_BAG = Key.of("kaleidoscopecookery:transmutation_lunch_bag");
    public static final Key TRANSMUTATION_LUNCH_BAG_EATING = Key.of("kaleidoscopecookery:transmutation_lunch_bag_eating");
    public static final Key COOKED_BEEF = Key.of("minecraft:cooked_beef");
    public static final Key CATERPILLAR = Key.of("kaleidoscopecookery:caterpillar");

    public static final Key DIAMOND_KITCHEN_KNIFE = Key.of("kaleidoscopecookery:diamond_kitchen_knife");
    public static final Key GOLD_KITCHEN_KNIFE = Key.of("kaleidoscopecookery:gold_kitchen_knife");
    public static final Key IRON_KITCHEN_KNIFE = Key.of("kaleidoscopecookery:iron_kitchen_knife");
    public static final Key NETHERITE_KITCHEN_KNIFE = Key.of("kaleidoscopecookery:netherite_kitchen_knife");

    public static final Key STOVE_FINISHED = Key.of("show:stove_finished");

    public static final Key TRASHCAN_BODY = Key.of("show:trashcan_1");
    public static final Key TRASHCAN_LID = Key.of("show:trashcan_2");
    public static final Key TRASHCAN_EYE = Key.of("show:trashcan_3");
    public static final Key TRASHCAN_HELMET = Key.of("kaleidoscopecookery:trashcan_helmet");

    public static final Key TEAPOT_LID = Key.of("show:teapot_1");
    public static final Key TEAPOT_BODY = Key.of("show:teapot_2");

    public static final Key EMPTY_CUP = Key.of("kaleidoscopecookery:empty_cup");
    public static final Key EMPTY_CUP_MODEL = Key.of("show:empty_cup");

    // 食谱菜单图标 全部用原版物品 不依赖资源包 缺资源包的服也能正常显示
    public static final Key MENU_FILLER = Key.of("minecraft:gray_stained_glass_pane");
    public static final Key MENU_INVALID = Key.of("minecraft:barrier");
    public static final Key MENU_BACK = Key.of("minecraft:arrow");
    public static final Key MENU_PREV = Key.of("minecraft:spectral_arrow");
    public static final Key MENU_NEXT = Key.of("minecraft:spectral_arrow");
    public static final Key MENU_CREATE = Key.of("minecraft:writable_book");
    public static final Key MENU_SAVE = Key.of("minecraft:lime_dye");
    public static final Key MENU_DELETE = Key.of("minecraft:red_dye");
    public static final Key MENU_ADD = Key.of("minecraft:emerald");
    public static final Key MENU_COUNT = Key.of("minecraft:comparator");
    public static final Key MENU_MODE = Key.of("minecraft:lever");
    public static final Key MENU_ROTATION = Key.of("minecraft:clock");
    public static final Key MENU_LIQUID = Key.of("minecraft:water_bucket");
    // 盛装容器槽 空手态要和灰玻璃背景区分开 否则看不见
    public static final Key MENU_CARRIER_NONE = Key.of("minecraft:leather");
    public static final Key MENU_POT = Key.of("kaleidoscopecookery:pot");
    public static final Key MENU_STOCKPOT = Key.of("kaleidoscopecookery:stockpot");
    public static final Key MENU_STEAMER = Key.of("kaleidoscopecookery:steamer");
    public static final Key MENU_SHAWARMA = Key.of("kaleidoscopecookery:shawarma_spit");
    public static final Key MENU_MILLSTONE = Key.of("kaleidoscopecookery:new_millstone");
    public static final Key MENU_CHOPPING_BOARD = Key.of("kaleidoscopecookery:chopping_board");
    public static final Key MENU_TEAPOT = Key.of("kaleidoscopecookery:teapot");
}
