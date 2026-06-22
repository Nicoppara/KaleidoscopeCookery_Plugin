package top.nicoppa.nicoPengRen.recipe.food;

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
import top.nicoppa.nicoPengRen.NicoPengRen;
import top.nicoppa.nicoPengRen.recipe.ApplianceType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 食谱系统管理器
 */
public final class FoodRecipeManager {

    public static final LoadingStage POT_FOOD_RAW        = new LoadingStage("pot food raw");
    public static final LoadingStage STOCK_FOOD_RAW      = new LoadingStage("stock food raw");
    public static final LoadingStage POT_FLEX_FOODS      = new LoadingStage("pot flex foods");
    public static final LoadingStage STOCK_FLEX_FOODS    = new LoadingStage("stock flex foods");
    public static final LoadingStage ACCURATE_FOODS      = new LoadingStage("accurate foods");
    public static final LoadingStage CHOPPING_BOARD_RAWS = new LoadingStage("chopping board raws");

    private FoodRecipeManager() {}

    public static void registerParsers() {
        CraftEngine.instance().packManager().registerConfigSectionParser(new PotFoodRawParser());
        CraftEngine.instance().packManager().registerConfigSectionParser(new StockFoodRawParser());
        CraftEngine.instance().packManager().registerConfigSectionParser(new PotFlexFoodsParser());
        CraftEngine.instance().packManager().registerConfigSectionParser(new StockFlexFoodsParser());
        CraftEngine.instance().packManager().registerConfigSectionParser(new AccurateFoodsParser());
        CraftEngine.instance().packManager().registerConfigSectionParser(new ChoppingBoardRawsParser());
    }

    /** 解析 "minecraft:beef 2" → ItemRequirement(beef, 2)；省略数量默认 1 */
    private static ItemRequirement parseAmount(String raw) {
        String[] parts = raw.trim().split("\\s+", 2);
        int count = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
        return new ItemRequirement(Key.of(parts[0]), count);
    }

    // pot_food_raw:
    //   meat:
    //     - minecraft:beef
    //   vegetable:
    //     - minecraft:carrot
    static final class PotFoodRawParser extends SectionConfigParser {
        private int count;

        @Override public String[]     sectionId()    { return new String[]{"pot_food_raw", "pot-food-raw"}; }
        @Override public LoadingStage loadingStage() { return POT_FOOD_RAW; }
        @Override public List<LoadingStage> dependencies() { return List.of(LoadingStages.ITEM); }
        @Override public int count() { return count; }

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

    // stock_food_raw:
    //   liquid:                                  # 汤底表：item=倒入所需桶，show=锅中液面模型
    //     - { item: minecraft:water_bucket, show: show:stove_water }
    //     - { item: minecraft:lava_bucket,  show: show:lava }
    //     - { item: minecraft:cod_bucket,   show: show:stove_water }
    //   meat:
    //     - minecraft:beef
    static final class StockFoodRawParser extends SectionConfigParser {
        private int count;

        @Override public String[]     sectionId()    { return new String[]{"stock_food_raw", "stock-food-raw"}; }
        @Override public LoadingStage loadingStage() { return STOCK_FOOD_RAW; }
        @Override public List<LoadingStage> dependencies() { return List.of(LoadingStages.ITEM); }
        @Override public int count() { return count; }

        @Override
        public void preProcess() {
            count = 0;
            FoodCategoryRegistry.instance().clear(ApplianceType.STOCKPOT);
            SoupBaseRegistry.instance().clear();
        }

        @Override
        protected void parseSection(Pack pack, Path path, ConfigSection section) {
            for (String category : section.keySet()) {
                if (category.equals("liquid")) continue;
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

    // pot_flex_foods:
    //   custom:cooked_beef:
    //     result: minecraft:cooked_beef
    //     require: [ minecraft:beef ]
    //     raw: [ "meat 1", "vegetable 0" ]
    //     preferred: [ minecraft:potato ]
    //     unpreferred: [ minecraft:rotten_flesh ]
    //     lore: [ { require: minecraft:potato, data: "..." } ]
    // stock_flex_foods:
    //   custom:fish_soup:
    //     result: minecraft:cooked_cod
    //     liquid: [ minecraft:water_bucket, minecraft:cod_bucket ]   # 当前汤底命中其一才匹配
    //     require: [ minecraft:cod ]

    /**
     * 解析并注册一条模糊配方（pot/stock 共用）；非法(require 超过 raw min)则告警跳过
     * @return 是否成功注册（用于解析器计数）
     */
    private static boolean parseFlexRecipe(Key id, ConfigSection section, ApplianceType cook, List<Key> liquids) {
        Key result = section.getNonNullIdentifier("result");

        // require 格式："minecraft:beef 2" → ItemRequirement(beef, 2)，省略数量默认 1
        List<ItemRequirement> require = section.getStringList("require").stream()
                .map(FoodRecipeManager::parseAmount).toList();

        // raw 格式："meat 1" → RawRequirement("meat", 1)
        List<RawRequirement> raw = section.getStringList("raw").stream()
                .map(s -> {
                    String[] parts = s.trim().split("\\s+", 2);
                    int min = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                    return new RawRequirement(parts[0], min);
                })
                .toList();

        List<Key> preferred   = section.getList("preferred",   ConfigValue::getAsIdentifier);
        List<Key> unpreferred = section.getList("unpreferred",  ConfigValue::getAsIdentifier);

        // lore 条件：扁平 { require, data } 或 块式 { when:[...], unpreferred, data:[...] }
        List<LoreCondition> loreConditions = section.getSectionList("lore", s -> {
            List<ItemRequirement> when = new ArrayList<>();
            Boolean unpref = null;
            List<String> data;
            if (s.keySet().contains("when")) {
                when = s.getSectionList("when", w -> parseAmount(w.getNonNullString("require")));
                if (s.keySet().contains("unpreferred")) unpref = s.getBoolean("unpreferred", false);
                data = s.getStringList("data");
            } else {
                when.add(parseAmount(s.getNonNullString("require")));
                data = List.of(s.getNonNullString("data"));
            }
            return new LoreCondition(when, unpref, data);
        });

        // 校验：require 的数量不能超过它所属 raw 类别的 min
        FoodCategoryRegistry cat = FoodCategoryRegistry.instance();
        for (ItemRequirement req : require) {
            for (RawRequirement r : raw) {
                if (r.min() > 0 && cat.isInCategory(cook, req.item(), r.category()) && r.min() < req.count()) {
                    NicoPengRen.getPlugin(NicoPengRen.class).getLogger().warning(
                            "[food] 配方 " + id.asString() + " 非法：require " + req.item().asString()
                                    + " " + req.count() + " 超过类别 '" + r.category() + "' 的 min " + r.min()
                                    + "，已跳过该配方");
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

        @Override public String[]     sectionId()    { return new String[]{"pot_flex_foods", "pot-flex-foods"}; }
        @Override public LoadingStage loadingStage() { return POT_FLEX_FOODS; }
        @Override public List<LoadingStage> dependencies() { return List.of(POT_FOOD_RAW); }
        @Override public int count() { return count; }

        @Override
        public void preProcess() {
            count = 0;
            FoodRecipeRegistry.instance().clearFlex(ApplianceType.POT);
        }

        @Override
        protected void parseSection(@NotNull Pack pack, @NotNull Path path,
                                    @NotNull Key id, @NotNull ConfigSection section) {
            if (parseFlexRecipe(id, section, ApplianceType.POT, List.of())) count++;
        }
    }

    static final class StockFlexFoodsParser extends IdSectionConfigParser {
        private int count;

        @Override public String[]     sectionId()    { return new String[]{"stock_flex_foods", "stock-flex-foods"}; }
        @Override public LoadingStage loadingStage() { return STOCK_FLEX_FOODS; }
        @Override public List<LoadingStage> dependencies() { return List.of(STOCK_FOOD_RAW); }
        @Override public int count() { return count; }

        @Override
        public void preProcess() {
            count = 0;
            FoodRecipeRegistry.instance().clearFlex(ApplianceType.STOCKPOT);
        }

        @Override
        protected void parseSection(@NotNull Pack pack, @NotNull Path path,
                                    @NotNull Key id, @NotNull ConfigSection section) {
            List<Key> liquids = section.getStringList("liquid").stream().map(Key::of).toList();
            if (parseFlexRecipe(id, section, ApplianceType.STOCKPOT, liquids)) count++;
        }
    }

    // accurate_foods:
    //   custom:shawarma_cooked_beef:   # 配方名(任意)
    //     require: custom:beef          # 原料(输入)
    //     result: custom:cooked_beef    # 成品(输出)
    //     cook: shawarma
    //     lore:
    //       - "沙威玛烤架烤出来的肉，有股木香"
    static final class AccurateFoodsParser extends IdSectionConfigParser {
        private int count;

        @Override public String[]     sectionId()    { return new String[]{"accurate_foods", "accurate-foods"}; }
        @Override public LoadingStage loadingStage() { return ACCURATE_FOODS; }
        @Override public List<LoadingStage> dependencies() { return List.of(LoadingStages.ITEM); }
        @Override public int count() { return count; }

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

            // result：列表写法 → 每项 "物品 权重"（缺权重默认 100）；扁平标量 → 单成品 100% 1:1
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
            List<String> lore = section.getStringList("lore");

            FoodRecipeRegistry.instance().registerAccurate(
                    new AccurateFoodRecipe(id, input, results, cook, lore));
            // require 自动放入白名单
            ApplianceFoodRegistry.instance().register(cook, input);
            count++;
        }
    }

    // chopping_board_raws:
    //   custom:cod:                  # 配方名(任意)
    //     require: minecraft:cod     # 原料(输入)，仅 require 中存在的物品可放上砧板
    //     stage: 5                   # 阶段数；放下=阶段1(模型 /0)，每切一刀+1，切满产出
    //     values: cb:block/.../cod   # 模型 id 前缀；按 stage 自动派生 0 ~ stage-1
    //     result:                    # "物品 数量 权重"，可重复；缺数量默认 1，缺权重默认 100
    //       - minecraft:cooked_cod 1 100
    //       - minecraft:cooked_salmon 1 50

    static final class ChoppingBoardRawsParser extends IdSectionConfigParser {
        private int count;

        @Override public String[]     sectionId()    { return new String[]{"chopping_board_raws", "chopping-board-raws"}; }
        @Override public LoadingStage loadingStage() { return CHOPPING_BOARD_RAWS; }
        @Override public List<LoadingStage> dependencies() { return List.of(LoadingStages.ITEM); }
        @Override public int count() { return count; }

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

            // values 为模型 id 前缀，按 stage 派生各阶段模型：<prefix>/0 ~ <prefix>/(stage-1)
            String prefix = section.getNonNullString("values");
            List<String> values = new ArrayList<>(stage);
            for (int i = 0; i < stage; i++) values.add(prefix + "/" + i);

            // 校验实际模型(CE 物品)数量与 stage 是否一致：仅后台提示，不阻断注册
            int modelCount = 0;
            for (int i = 0; i < stage + 16; i++) {
                if (CraftEngine.instance().itemManager().getItemDefinition(Key.of(prefix + "/" + i)).isPresent()) modelCount++;
                else break;
            }
            if (modelCount != stage) {
                NicoPengRen.getPlugin(NicoPengRen.class).getLogger().warning(
                        "[food] 砧板配方 " + id.asString() + " 模型数(" + modelCount + ") 与 stage(" + stage
                                + ") 不一致，请检查 " + prefix + "/0 ~ /" + (stage - 1));
            }

            // result："物品 数量 权重"，可重复；缺数量默认 1，缺权重默认 100
            List<ChoppingResult> results = new ArrayList<>();
            Object rawResult = section.get("result");
            if (rawResult instanceof List<?> list) {
                for (Object o : list) results.add(parseChoppingResult(String.valueOf(o)));
            } else if (rawResult != null) {
                results.add(parseChoppingResult(String.valueOf(rawResult)));
            }

            FoodRecipeRegistry.instance().registerChopping(
                    new ChoppingBoardRecipe(id, input, stage, values, results));
            ApplianceFoodRegistry.instance().register(ApplianceType.CHOPPING_BOARD, input);
            count++;
        }

        /** 解析 "物品 [数量] [权重]"：缺数量默认 1，缺权重默认 100 */
        private static ChoppingResult parseChoppingResult(String raw) {
            String[] parts = raw.trim().split("\\s+");
            Key key = Key.of(parts[0]);
            int cnt    = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
            int weight = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 100;
            return new ChoppingResult(key, cnt, weight);
        }
    }
}