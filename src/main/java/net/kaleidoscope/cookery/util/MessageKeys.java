package net.kaleidoscope.cookery.util;

// 玩家可见文案的翻译键 文案本体在 configuration/lang/*.yml
public final class MessageKeys {
    private MessageKeys() {}

    public static final String POT_NEED_BOWL = "kaleidoscopecookery.message.pot.need_bowl";
    // carrier 为空的菜空手就能拿 这时提示需要什么容器没有意义
    public static final String POT_USE_HAND = "kaleidoscopecookery.message.pot.use_hand";
    public static final String POT_HAS_OIL = "kaleidoscopecookery.message.pot.has_oil";
    public static final String POT_OCCUPIED = "kaleidoscopecookery.message.pot.occupied";
    public static final String POT_NEED_HEAT = "kaleidoscopecookery.message.pot.need_heat";
    public static final String POT_NEED_OIL_FIRST = "kaleidoscopecookery.message.pot.need_oil_first";
    public static final String POT_NOT_ENOUGH_INGREDIENTS = "kaleidoscopecookery.message.pot.not_enough_ingredients";
    public static final String POT_BURNT_NO_RECIPE = "kaleidoscopecookery.message.pot.burnt_no_recipe";
    public static final String POT_NOT_DONE_YET = "kaleidoscopecookery.message.pot.not_done_yet";
    public static final String POT_MIXED_NO_RECIPE = "kaleidoscopecookery.message.pot.mixed_no_recipe";
    public static final String POT_RECIPE_SAVED = "kaleidoscopecookery.message.pot.recipe_saved";
    public static final String POT_START_COOKING = "kaleidoscopecookery.message.pot.start_cooking";
    public static final String POT_DISH_READY = "kaleidoscopecookery.message.pot.dish_ready";
    // 空手就能盛的菜用这条 不提容器
    public static final String POT_DISH_READY_HAND = "kaleidoscopecookery.message.pot.dish_ready_hand";
    public static final String POT_ALL_BURNT = "kaleidoscopecookery.message.pot.all_burnt";
    public static final String POT_NOT_INGREDIENT = "kaleidoscopecookery.message.pot.not_ingredient";

    public static final String STOCKPOT_START_STEWING = "kaleidoscopecookery.message.stockpot.start_stewing";
    public static final String STOCKPOT_NOT_ENOUGH_INGREDIENTS = "kaleidoscopecookery.message.stockpot.not_enough_ingredients";
    public static final String STOCKPOT_NO_RECIPE = "kaleidoscopecookery.message.stockpot.no_recipe";
    public static final String STOCKPOT_RECIPE_SAVED = "kaleidoscopecookery.message.stockpot.recipe_saved";
    public static final String STOCKPOT_USE_BOWL = "kaleidoscopecookery.message.stockpot.use_bowl";
    // 空手就能盛的汤用这条 不提容器
    public static final String STOCKPOT_USE_HAND = "kaleidoscopecookery.message.stockpot.use_hand";

    public static final String STEAMER_MAX_LAYERS = "kaleidoscopecookery.message.steamer.max_layers";
    public static final String STEAMER_FULL = "kaleidoscopecookery.message.steamer.full";
    public static final String STEAMER_NEED_STOVE = "kaleidoscopecookery.message.steamer.need_stove";

    public static final String TEAPOT_PUT = "kaleidoscopecookery.message.teapot.put";
    public static final String TEAPOT_PROCESSING = "kaleidoscopecookery.message.teapot.processing";
    public static final String TEAPOT_FINISHED = "kaleidoscopecookery.message.teapot.finished";

    public static final String MILLSTONE_STOP_ANIMAL_HINT = "kaleidoscopecookery.message.millstone.stop_animal_hint";

    public static final String TRASHCAN_CLEARED = "kaleidoscopecookery.message.trashcan.cleared";
}
