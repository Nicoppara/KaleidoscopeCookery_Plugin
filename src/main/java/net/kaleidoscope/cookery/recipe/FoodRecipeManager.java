package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.ConfigValue;
import net.momirealms.craftengine.core.plugin.config.IdSectionConfigParser;
import net.momirealms.craftengine.core.plugin.config.SectionConfigParser;
import net.momirealms.craftengine.core.plugin.config.lifecycle.LoadingStage;
import net.momirealms.craftengine.core.plugin.config.lifecycle.LoadingStages;
import net.momirealms.craftengine.core.util.Key;
import org.jetbrains.annotations.NotNull;
import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.kaleidoscope.cookery.util.ConsoleMessages;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// 食谱系统管理器 注册各类配方的配置解析器
public final class FoodRecipeManager {

    public static final LoadingStage POT_FOOD_RAW = new LoadingStage("pot food raw");
    public static final LoadingStage STOCK_FOOD_RAW = new LoadingStage("stock food raw");
    public static final LoadingStage POT_FLEX_FOODS = new LoadingStage("pot flex foods");
    public static final LoadingStage STOCK_FLEX_FOODS = new LoadingStage("stock flex foods");
    public static final LoadingStage ACCURATE_FOODS = new LoadingStage("accurate foods");
    public static final LoadingStage CHOPPING_BOARD_RAWS = new LoadingStage("chopping board raws");
    public static final LoadingStage TEAPOT_LIQUID = new LoadingStage("teapot liquid");
    public static final LoadingStage TEA_CUP = new LoadingStage("tea cup");
    public static final LoadingStage TEAPOT_RESULT = new LoadingStage("teapot result");

    private FoodRecipeManager() {}

    public static void registerParsers() {
        CraftEngine.instance().packManager().registerConfigSectionParser(new PotFoodRawParser());
        CraftEngine.instance().packManager().registerConfigSectionParser(new StockFoodRawParser());
        CraftEngine.instance().packManager().registerConfigSectionParser(new PotFlexFoodsParser());
        CraftEngine.instance().packManager().registerConfigSectionParser(new StockFlexFoodsParser());
        CraftEngine.instance().packManager().registerConfigSectionParser(new AccurateFoodsParser());
        CraftEngine.instance().packManager().registerConfigSectionParser(new ChoppingBoardRawsParser());
        CraftEngine.instance().packManager().registerConfigSectionParser(new TeapotLiquidParser());
        CraftEngine.instance().packManager().registerConfigSectionParser(new TeaCupParser());
        CraftEngine.instance().packManager().registerConfigSectionParser(new TeapotResultParser());
    }

    // 解析 minecraft:beef 2 得到 beef 数量 2 省略数量默认 1
    private static ItemRequirement parseAmount(String raw) {
        String[] parts = raw.trim().split("\\s+", 2);
        int count = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
        return new ItemRequirement(Key.of(parts[0]), count);
    }

    static final class PotFoodRawParser extends SectionConfigParser {
        private int count;

        @Override
        public String[] sectionId() {
            return new String[]{"pot_food_raw", "pot-food-raw"};
        }

        @Override
        public LoadingStage loadingStage() {
            return POT_FOOD_RAW;
        }

        @Override
        public List<LoadingStage> dependencies() {
            return List.of(LoadingStages.ITEM);
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public void preProcess() {
            count = 0;
            FoodCategoryRegistry.instance().clear(ApplianceType.POT);
        }

        @Override
        protected void parseSection(Pack pack, Path path, ConfigSection section) {
            for (String category : section.keySet()) {
                for (String itemStr : section.getStringList(category)) {
                    FoodCategoryRegistry.instance().register(ApplianceType.POT, category, Key.of(itemStr));
                    count++;
                }
            }
        }
    }

    static final class StockFoodRawParser extends SectionConfigParser {
        private int count;

        @Override
        public String[] sectionId() {
            return new String[]{"stock_food_raw", "stock-food-raw"};
        }

        @Override
        public LoadingStage loadingStage() {
            return STOCK_FOOD_RAW;
        }

        @Override
        public List<LoadingStage> dependencies() {
            return List.of(LoadingStages.ITEM);
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public void preProcess() {
            count = 0;
            FoodCategoryRegistry.instance().clear(ApplianceType.STOCKPOT);
            SoupBaseRegistry.instance().clear();
        }

        @Override
        protected void parseSection(Pack pack, Path path, ConfigSection section) {
            for (String category : section.keySet()) {
                if (category.equals("liquid")) {
                    continue;
                }
                for (String itemStr : section.getStringList(category)) {
                    FoodCategoryRegistry.instance().register(ApplianceType.STOCKPOT, category, Key.of(itemStr));
                    count++;
                }
            }
            section.getSectionList("liquid", s -> {
                SoupBaseRegistry.instance().register(
                        s.getNonNullIdentifier("item"), s.getNonNullIdentifier("show"));
                count++;
                return s;
            });
        }
    }

    // 解析并注册一条模糊配方 pot 与 stock 共用 require 超过 raw min 视为非法 告警跳过 返回是否注册成功
    private static boolean parseFlexRecipe(Key id, ConfigSection section, ApplianceType cook, List<Key> liquids) {
        Key result = section.getNonNullIdentifier("result");

        // require 格式 minecraft:beef 2 得到 beef 数量 2 省略数量默认 1
        List<ItemRequirement> require = section.getStringList("require").stream()
                .map(FoodRecipeManager::parseAmount).toList();

        // raw 格式 meat 1 得到类别 meat 最少 1
        List<RawRequirement> raw = section.getStringList("raw").stream()
                .map(s -> {
                    String[] parts = s.trim().split("\\s+", 2);
                    int min = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                    return new RawRequirement(parts[0], min);
                })
                .toList();

        List<Key> preferred = section.getList("preferred", ConfigValue::getAsIdentifier);
        List<Key> unpreferred = section.getList("unpreferred", ConfigValue::getAsIdentifier);

        // lore 条件 扁平写法 require 加 data 或块式写法 when 列表 加 unpreferred 加 data 列表
        List<LoreCondition> loreConditions = section.getSectionList("lore", s -> {
            List<ItemRequirement> when = new ArrayList<>();
            Boolean unpref = null;
            List<String> data;
            if (s.keySet().contains("when")) {
                when = s.getSectionList("when", w -> parseAmount(w.getNonNullString("require")));
                if (s.keySet().contains("unpreferred")) {
                    unpref = s.getBoolean("unpreferred", false);
                }
                data = s.getStringList("data");
            } else {
                when.add(parseAmount(s.getNonNullString("require")));
                data = List.of(s.getNonNullString("data"));
            }
            return new LoreCondition(when, unpref, data);
        });

        // 校验 require 的数量不能超过它所属 raw 类别的 min
        FoodCategoryRegistry cat = FoodCategoryRegistry.instance();
        for (ItemRequirement req : require) {
            for (RawRequirement r : raw) {
                if (r.min() > 0 && cat.isInCategory(cook, req.item(), r.category()) && r.min() < req.count()) {
                    KaleidoscopeCookeryPlugin.instance().getLogger().warning(
                            ConsoleMessages.t("food.flex.require_exceeds_min",
                                    id.asString(), req.item().asString(), req.count(), r.category(), r.min()));
                    return false;
                }
            }
        }

        FoodRecipeRegistry.instance().registerFlex(
                new FlexFoodRecipe(id, result, cook, require, raw,
                        preferred, unpreferred, loreConditions, liquids));
        return true;
    }

    static final class PotFlexFoodsParser extends IdSectionConfigParser {
        private int count;

        @Override
        public String[] sectionId() {
            return new String[]{"pot_flex_foods", "pot-flex-foods"};
        }

        @Override
        public LoadingStage loadingStage() {
            return POT_FLEX_FOODS;
        }

        @Override
        public List<LoadingStage> dependencies() {
            return List.of(POT_FOOD_RAW);
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public void preProcess() {
            count = 0;
            FoodRecipeRegistry.instance().clearFlex(ApplianceType.POT);
        }

        @Override
        protected void parseSection(@NotNull Pack pack, @NotNull Path path,
                                    @NotNull Key id, @NotNull ConfigSection section) {
            if (parseFlexRecipe(id, section, ApplianceType.POT, List.of())) {
                count++;
            }
        }
    }

    static final class StockFlexFoodsParser extends IdSectionConfigParser {
        private int count;

        @Override
        public String[] sectionId() {
            return new String[]{"stock_flex_foods", "stock-flex-foods"};
        }

        @Override
        public LoadingStage loadingStage() {
            return STOCK_FLEX_FOODS;
        }

        @Override
        public List<LoadingStage> dependencies() {
            return List.of(STOCK_FOOD_RAW);
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public void preProcess() {
            count = 0;
            FoodRecipeRegistry.instance().clearFlex(ApplianceType.STOCKPOT);
        }

        @Override
        protected void parseSection(@NotNull Pack pack, @NotNull Path path,
                                    @NotNull Key id, @NotNull ConfigSection section) {
            List<Key> liquids = section.getStringList("liquid").stream().map(Key::of).toList();
            if (parseFlexRecipe(id, section, ApplianceType.STOCKPOT, liquids)) {
                count++;
            }
        }
    }

    static final class AccurateFoodsParser extends IdSectionConfigParser {
        private int count;

        @Override
        public String[] sectionId() {
            return new String[]{"accurate_foods", "accurate-foods"};
        }

        @Override
        public LoadingStage loadingStage() {
            return ACCURATE_FOODS;
        }

        @Override
        public List<LoadingStage> dependencies() {
            return List.of(LoadingStages.ITEM);
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public void preProcess() {
            count = 0;
            FoodRecipeRegistry.instance().clearAccurate();
            ApplianceFoodRegistry.instance().clear(ApplianceType.STEAMER);
            ApplianceFoodRegistry.instance().clear(ApplianceType.SHAWARMA);
            ApplianceFoodRegistry.instance().clear(ApplianceType.MILLSTONE);
        }

        @Override
        protected void parseSection(@NotNull Pack pack, @NotNull Path path,
                                    @NotNull Key id, @NotNull ConfigSection section) {
            Key input = section.getNonNullIdentifier("require");

            // result 列表写法 每项 物品 权重 扁平标量则单成品满概率 1 比 1
            List<WeightedResult> results = new ArrayList<>();
            Object rawResult = section.get("result");
            if (rawResult instanceof List<?> list) {
                for (Object o : list) {
                    String[] parts = String.valueOf(o).trim().split("\\s+", 2);
                    int weight = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 100;
                    results.add(new WeightedResult(Key.of(parts[0]), weight));
                }
            } else {
                results.add(new WeightedResult(section.getNonNullIdentifier("result"), 100));
            }

            ApplianceType cook = ApplianceType.valueOf(
                    section.getNonNullString("cook").toUpperCase());

            // rotations 产出所需圈数 仅石磨可用 写在其它机型上报错跳过 0 表示用 behavior 默认
            int rotations = 0;
            if (section.get("rotations") != null) {
                if (cook != ApplianceType.MILLSTONE) {
                    KaleidoscopeCookeryPlugin.instance().getLogger().warning(
                            ConsoleMessages.t("food.accurate.rotations_millstone_only", id.asString()));
                    return;
                }
                rotations = section.getInt("rotations", 0);
            }
            List<String> lore = section.getStringList("lore");

            FoodRecipeRegistry.instance().registerAccurate(
                    new AccurateFoodRecipe(id, input, results, cook, rotations, lore));
            // require 自动放入白名单
            ApplianceFoodRegistry.instance().register(cook, input);
            count++;
        }
    }

    // teapot_liquid 每个液体 id 下配 bar_left bar_right bar_empty 与满格字形(bar_water/bar_lava/bar_xxx) 字形为完整 image id
    static final class TeapotLiquidParser extends SectionConfigParser {
        private int count;

        @Override
        public String[] sectionId() {
            return new String[]{"teapot_liquid", "teapot-liquid"};
        }

        @Override
        public LoadingStage loadingStage() {
            return TEAPOT_LIQUID;
        }

        @Override
        public List<LoadingStage> dependencies() {
            return List.of(LoadingStages.ITEM);
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public void preProcess() {
            count = 0;
            FoodRecipeRegistry.instance().clearTeapotLiquid();
        }

        @Override
        protected void parseSection(Pack pack, Path path, ConfigSection section) {
            for (String fluidStr : section.keySet()) {
                ConfigSection sub = section.getSection(fluidStr);
                if (sub == null) {
                    continue;
                }
                String name = sub.getString(new String[]{"display_name", "display-name"}, fluidStr);
                String left = sub.getString(new String[]{"bar_left", "bar-left"}, "");
                String right = sub.getString(new String[]{"bar_right", "bar-right"}, "");
                String empty = sub.getString(new String[]{"bar_empty", "bar-empty"}, "");
                String full = findFullGlyph(sub);
                FoodRecipeRegistry.instance().registerTeapotLiquid(
                        new TeapotLiquid(Key.of(fluidStr), name, left, right, empty, full));
                count++;
            }
        }

        // 满格字形键名随液体而变(bar_water/bar_lava/bar_xxx) 取除左右空格外的首个 bar_ 键值
        private static String findFullGlyph(ConfigSection sub) {
            for (String key : sub.keySet()) {
                String norm = key.replace('-', '_');
                if (norm.equals("bar_left") || norm.equals("bar_right") || norm.equals("bar_empty")) {
                    continue;
                }
                if (norm.startsWith("bar_")) {
                    String value = sub.getString(new String[]{key}, "");
                    if (value != null && !value.isEmpty()) {
                        return value;
                    }
                }
            }
            return "";
        }
    }

    // tea_cup 每个茶(成品)id 下配 display_model 扁平或列表 成形时随机取一个 模型需在 items 定义
    static final class TeaCupParser extends SectionConfigParser {
        private int count;

        @Override
        public String[] sectionId() {
            return new String[]{"tea_cup", "tea-cup"};
        }

        @Override
        public LoadingStage loadingStage() {
            return TEA_CUP;
        }

        @Override
        public List<LoadingStage> dependencies() {
            return List.of(LoadingStages.ITEM);
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public void preProcess() {
            count = 0;
            FoodRecipeRegistry.instance().clearTeaCup();
        }

        @Override
        protected void parseSection(Pack pack, Path path, ConfigSection section) {
            for (String teaStr : section.keySet()) {
                ConfigSection sub = section.getSection(teaStr);
                if (sub == null) {
                    continue;
                }
                Key tea = Key.of(teaStr);
                // item 缺省取成品自身 表示手持该物品右键即可放到杯垫
                Key item = Key.of(sub.getString(new String[]{"item"}, teaStr));
                Object raw = sub.get("display_model");
                if (raw == null) {
                    raw = sub.get("display-model");
                }
                List<Key> models = new ArrayList<>();
                if (raw instanceof List<?> list) {
                    for (Object o : list) {
                        models.add(Key.of(String.valueOf(o)));
                    }
                } else if (raw != null) {
                    models.add(Key.of(String.valueOf(raw)));
                }
                if (models.isEmpty()) {
                    continue;
                }
                FoodRecipeRegistry.instance().registerTeaCup(new TeaCup(tea, item, models));
                count++;
            }
        }
    }

    static final class TeapotResultParser extends IdSectionConfigParser {
        private int count;

        @Override
        public String[] sectionId() {
            return new String[]{"teapot_result", "teapot-result"};
        }

        @Override
        public LoadingStage loadingStage() {
            return TEAPOT_RESULT;
        }

        @Override
        public List<LoadingStage> dependencies() {
            return List.of(LoadingStages.ITEM, TEAPOT_LIQUID, TEA_CUP);
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public void preProcess() {
            count = 0;
            FoodRecipeRegistry.instance().clearTeapot();
            ApplianceFoodRegistry.instance().clear(ApplianceType.TEAPOT);
        }

        // fluid 液体类型(如 minecraft:water) require 原料 数量(消耗) result 产物 数量 time 处理 tick
        @Override
        protected void parseSection(@NotNull Pack pack, @NotNull Path path,
                                    @NotNull Key id, @NotNull ConfigSection section) {
            Key fluid = section.getNonNullIdentifier("fluid");
            if (!FoodRecipeRegistry.instance().hasTeapotLiquid(fluid)) {
                KaleidoscopeCookeryPlugin.instance().getLogger().warning(
                        ConsoleMessages.t("food.teapot.unregistered_liquid", id.asString(), fluid.asString()));
                return;
            }
            ItemRequirement ingredient = parseAmount(section.getNonNullString("require"));
            ItemRequirement result = parseAmount(section.getNonNullString("result"));
            // 成品必须在 tea_cup 定义模型 否则跳过
            if (!FoodRecipeRegistry.instance().hasTeaCup(result.item())) {
                KaleidoscopeCookeryPlugin.instance().getLogger().warning(
                        ConsoleMessages.t("food.teapot.missing_tea_cup", id.asString(), result.item().asString()));
                return;
            }
            int time = section.getInt("time", 200);

            FoodRecipeRegistry.instance().registerTeapot(new TeapotRecipe(
                    id, fluid, ingredient.item(), ingredient.count(), result.item(), result.count(), time));
            ApplianceFoodRegistry.instance().register(ApplianceType.TEAPOT, ingredient.item());
            count++;
        }
    }

    static final class ChoppingBoardRawsParser extends IdSectionConfigParser {
        private int count;

        @Override
        public String[] sectionId() {
            return new String[]{"chopping_board_raws", "chopping-board-raws"};
        }

        @Override
        public LoadingStage loadingStage() {
            return CHOPPING_BOARD_RAWS;
        }

        @Override
        public List<LoadingStage> dependencies() {
            return List.of(LoadingStages.ITEM);
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public void preProcess() {
            count = 0;
            FoodRecipeRegistry.instance().clearChopping();
            ApplianceFoodRegistry.instance().clear(ApplianceType.CHOPPING_BOARD);
        }

        @Override
        protected void parseSection(@NotNull Pack pack, @NotNull Path path,
                                    @NotNull Key id, @NotNull ConfigSection section) {
            Key input = section.getNonNullIdentifier("require");
            int stage = section.getInt("stage", 1);

            // values 为模型 id 前缀 按 stage 派生各阶段模型 prefix/0 到 prefix/ stage 减 1
            String prefix = section.getNonNullString("values");
            List<String> values = new ArrayList<>(stage);
            for (int i = 0; i < stage; i++) {
                values.add(prefix + "/" + i);
            }

            // 校验实际模型数量与 stage 是否一致 仅后台提示 不阻断注册
            int modelCount = 0;
            for (int i = 0; i < stage + 16; i++) {
                if (CraftEngine.instance().itemManager().getItemDefinition(Key.of(prefix + "/" + i)).isPresent()) {
                    modelCount++;
                } else {
                    break;
                }
            }
            if (modelCount != stage) {
                KaleidoscopeCookeryPlugin.instance().getLogger().warning(
                        ConsoleMessages.t("food.chopping.model_stage_mismatch",
                                id.asString(), modelCount, stage, prefix, stage - 1));
            }

            // result single 与 single_extra 填单个产物 物品 数量 multi_random 可多个 物品 数量 权重 权重当百分比
            ChoppingMode mode = ChoppingMode.fromConfig(section.getString("mode"));
            List<ChoppingResult> results = parseChoppingResults(section.get("result"));
            List<ChoppingResult> extras = parseChoppingResults(section.get("extra"));

            // single 与 single_extra 的 result 只能是单个产物 配多个则报错跳过该配方
            if ((mode == ChoppingMode.SINGLE || mode == ChoppingMode.SINGLE_EXTRA) && results.size() > 1) {
                KaleidoscopeCookeryPlugin.instance().getLogger().warning(
                        ConsoleMessages.t("food.chopping.single_result_too_many",
                                id.asString(), mode, results.size()));
                return;
            }

            FoodRecipeRegistry.instance().registerChopping(
                    new ChoppingBoardRecipe(id, input, stage, values, mode, results, extras));
            ApplianceFoodRegistry.instance().register(ApplianceType.CHOPPING_BOARD, input);
            count++;
        }

        // 标量或列表都解析成产物列表
        private static List<ChoppingResult> parseChoppingResults(Object rawResult) {
            List<ChoppingResult> results = new ArrayList<>();
            if (rawResult instanceof List<?> list) {
                for (Object o : list) {
                    results.add(parseChoppingResult(String.valueOf(o)));
                }
            } else if (rawResult != null) {
                results.add(parseChoppingResult(String.valueOf(rawResult)));
            }
            return results;
        }

        // 解析 物品 数量 权重 缺数量默认 1 缺权重默认 100
        private static ChoppingResult parseChoppingResult(String raw) {
            String[] parts = raw.trim().split("\\s+");
            Key key = Key.of(parts[0]);
            int cnt = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
            int weight = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 100;
            return new ChoppingResult(key, cnt, weight);
        }
    }
}
