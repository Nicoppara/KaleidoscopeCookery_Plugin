package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.IdSectionConfigParser;
import net.momirealms.craftengine.core.plugin.config.SectionConfigParser;
import net.momirealms.craftengine.core.plugin.config.lifecycle.LoadingStage;
import net.momirealms.craftengine.core.plugin.config.lifecycle.LoadingStages;
import net.momirealms.craftengine.core.util.Key;
import org.jetbrains.annotations.NotNull;
import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.kaleidoscope.cookery.recipe.edit.RecipeSourceIndex;
import net.kaleidoscope.cookery.recipe.edit.RecipeFileStore;
import net.kaleidoscope.cookery.util.ConsoleMessages;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

// 食谱系统管理器 注册各类配方的配置解析器
public final class FoodRecipeManager {

    // 空手盛出的哨兵值 配置里写 minecraft:air 等同于不写 carrier
    private static final Key AIR = Key.of("minecraft:air");

    static final String[] USE_EQUIVALENT_FOODS = {"use_equivalent_foods", "use-equivalent-foods"};
    static final String[] USE_SEASONINGS = {"use_seasonings", "use-seasonings"};

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

    // 下面两个基类只是把段名 stage 依赖 计数这套样板收口
    // CE 的 SectionConfigParser 与 IdSectionConfigParser 是两条继承线 只能各写一份
    private abstract static class CookerySectionParser extends SectionConfigParser {
        private final LoadingStage stage;
        private final List<LoadingStage> dependencies;
        private final String[] sectionIds;
        private int count;

        CookerySectionParser(LoadingStage stage, List<LoadingStage> dependencies, String... sectionIds) {
            this.stage = stage;
            this.dependencies = dependencies;
            this.sectionIds = sectionIds;
        }

        @Override
        public Key type() {
            return Key.of("kaleidoscopecookery:" + this.sectionIds[0]);
        }

        // CE 只在注册与注销时遍历读取 不留引用也不改内容 直接返字段不必拷贝
        @Override
        public String[] sectionId() {
            return sectionIds;
        }

        @Override
        public LoadingStage loadingStage() {
            return stage;
        }

        @Override
        public List<LoadingStage> dependencies() {
            return dependencies;
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public void preProcess() {
            count = 0;
            reset();
        }

        @Override
        protected final void parseSection(Pack pack, Path path, ConfigSection section) {
            count += parseAndCount(pack, path, section);
        }

        // 每轮解析前清空本 parser 负责的注册表
        protected abstract void reset();

        // 返回本段登记了几条
        protected abstract int parseAndCount(Pack pack, Path path, ConfigSection section);
    }

    private abstract static class CookeryIdParser extends IdSectionConfigParser {
        private record ClaimedTarget(Path file, RecipeFileStore.SourceTarget target) {
            private ClaimedTarget {
                file = file.toAbsolutePath().normalize();
            }
        }

        private final LoadingStage stage;
        private final List<LoadingStage> dependencies;
        private final String[] sectionIds;
        private final RecipeSourceIndex.Kind kind;
        private final Map<Key, List<Path>> occurrences = new LinkedHashMap<>();
        private final Set<ClaimedTarget> claimedTargets = new LinkedHashSet<>();
        private boolean loadActive;
        private int count;

        CookeryIdParser(LoadingStage stage, List<LoadingStage> dependencies,
                        RecipeSourceIndex.Kind kind, String... sectionIds) {
            this.stage = stage;
            this.dependencies = dependencies;
            this.kind = kind;
            this.sectionIds = sectionIds;
        }

        @Override
        public Key type() {
            return Key.of("kaleidoscopecookery:" + this.sectionIds[0]);
        }

        // CE 只在注册与注销时遍历读取 不留引用也不改内容 直接返字段不必拷贝
        @Override
        public String[] sectionId() {
            return sectionIds;
        }

        @Override
        public LoadingStage loadingStage() {
            return stage;
        }

        @Override
        public List<LoadingStage> dependencies() {
            return dependencies;
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public void preProcess() {
            RecipeSourceIndex.instance().beginLoad(kind);
            loadActive = true;
            try {
                count = 0;
                occurrences.clear();
                claimedTargets.clear();
                reset();
            } catch (RuntimeException | Error error) {
                finishLoad();
                throw error;
            }
        }

        @Override
        public void loadAll() {
            try {
                super.loadAll();
            } catch (RuntimeException | Error error) {
                finishLoad();
                throw error;
            }
        }

        @Override
        public void postProcess() {
            finishLoad();
        }

        private void finishLoad() {
            if (loadActive) {
                loadActive = false;
                RecipeSourceIndex.instance().endLoad(kind);
            }
        }

        @Override
        protected boolean isDuplicate(Key id, Path filePath, String currentNode) {
            List<Path> files = occurrences.computeIfAbsent(id, ignored -> new ArrayList<>());
            files.add(filePath.toAbsolutePath().normalize());
            if (files.size() > 1) {
                KaleidoscopeCookeryPlugin.instance().getLogger().severe(
                        "食谱 ID 重复，所有重复定义均不加载: " + id.asString());
            }
            return false;
        }

        protected final boolean duplicated(Key id) {
            List<Path> files = occurrences.get(id);
            return files != null && files.size() > 1;
        }

        protected final RecipeSourceIndex.Kind kind() {
            return kind;
        }

        @Override
        protected final void parseSection(@NotNull Pack pack, @NotNull Path path,
                                          @NotNull Key id, @NotNull ConfigSection section) {
            List<RecipeFileStore.SourceTarget> targets = new ArrayList<>(
                    RecipeFileStore.resolveTargets(kind, id, path, section.path(), section.values()));
            for (RecipeFileStore.SourceTarget deleted
                    : RecipeSourceIndex.instance().deletedTargets(kind, id, path)) {
                if (deleted.generatedNode().equals(section.path()) && !targets.contains(deleted)) {
                    targets.add(deleted);
                }
            }
            RecipeFileStore.SourceTarget target = null;
            for (RecipeFileStore.SourceTarget candidate : targets) {
                if (claimedTargets.add(new ClaimedTarget(path, candidate))) {
                    target = candidate;
                    break;
                }
            }
            if (target == null) {
                target = RecipeFileStore.SourceTarget.unresolved(section.path());
                claimedTargets.add(new ClaimedTarget(path, target));
            }
            if (RecipeSourceIndex.instance().isDeleted(kind, id, path, target)) {
                return;
            }
            count += parseAndCount(pack, path, id, section, target);
        }

        protected abstract void reset();

        // 返回 1 表示该配方登记成功 校验不过返 0
        protected abstract int parseAndCount(Pack pack, Path path, Key id, ConfigSection section,
                                             RecipeFileStore.SourceTarget target);
    }

    static final class PotFoodRawParser extends CookerySectionParser {
        PotFoodRawParser() {
            super(POT_FOOD_RAW, List.of(LoadingStages.ITEM), "pot_food_raw", "pot-food-raw");
        }

        @Override
        protected void reset() {
            ApplianceFoodRegistry.instance().clear(ApplianceType.POT);
        }

        @Override
        protected int parseAndCount(Pack pack, Path path, ConfigSection section) {
            return registerRaw(section, ApplianceType.POT, null);
        }
    }

    static final class StockFoodRawParser extends CookerySectionParser {
        StockFoodRawParser() {
            super(STOCK_FOOD_RAW, List.of(LoadingStages.ITEM), "stock_food_raw", "stock-food-raw");
        }

        @Override
        protected void reset() {
            ApplianceFoodRegistry.instance().clear(ApplianceType.STOCKPOT);
            SoupBaseRegistry.instance().clear();
        }

        @Override
        protected int parseAndCount(Pack pack, Path path, ConfigSection section) {
            int raws = registerRaw(section, ApplianceType.STOCKPOT, "liquid");
            // getSectionList 的返回值用不上 这里借它的元素数当汤底计数
            return raws + section.getSectionList("liquid", s -> {
                // show 可以不写 不写就画成水 只有岩浆这类才必须自己指定
                String show = s.getString(new String[]{"show"}, (String) null);
                SoupBaseRegistry.instance().register(
                        s.getNonNullIdentifier("item"),
                        show == null || show.isBlank()
                                ? SoupBaseRegistry.DEFAULT_SHOW : Key.of(show));
                return s;
            }).size();
        }
    }

    // 下锅白名单 只关心并集里有哪些 id 分组键名当注释用 平铺或分组都收
    // skip 是该段里不属于白名单的子键 高汤锅的 liquid 走汤底表
    private static int registerRaw(ConfigSection section, ApplianceType cook, String skip) {
        int count = 0;
        for (String key : section.keySet()) {
            if (key.equals(skip)) {
                continue;
            }
            for (String itemStr : section.getStringList(key)) {
                ApplianceFoodRegistry.instance().register(cook, Key.of(itemStr));
                count++;
            }
        }
        return count;
    }

    private static boolean parseFlexRecipe(Key id, Path path, ConfigSection section,
                                           ApplianceType cook, List<Key> liquids,
                                           RecipeSourceIndex.Kind kind, boolean duplicate,
                                           RecipeFileStore.SourceTarget target) {
        Key result = section.getNonNullIdentifier("result");

        Map<Key, Integer> perfect = new LinkedHashMap<>();
        ConfigSection perfectSection = section.getSection("perfect");
        if (perfectSection != null) {
            for (String itemStr : perfectSection.keySet()) {
                int weight = perfectSection.getInt(itemStr, 1);
                if (weight > 0) {
                    perfect.put(Key.of(itemStr), weight);
                }
            }
        } else {
            // 兼容写法 perfect 也可以写成 minecraft:beef 2 这样的字符串列表
            for (String raw : section.getStringList("perfect")) {
                ItemRequirement req = parseAmount(raw);
                perfect.put(req.item(), req.count());
            }
        }

        if (perfect.isEmpty()) {
            KaleidoscopeCookeryPlugin.instance().getLogger().warning(
                    ConsoleMessages.t("food.flex.empty_perfect", id.asString()));
            return false;
        }

        // carrier 省略表示空手取 写 minecraft:bowl 就是要碗 花盆之类同理
        // 模板没法条件性省略键 所以用 minecraft:air 表示空手盛出
        String carrierId = section.getString("carrier", (String) null);
        Key carrier = carrierId == null || carrierId.isEmpty() || AIR.asString().equals(carrierId)
                ? null : Key.of(carrierId);
        // 两张组表默认对所有菜生效 只有明确写 false 的菜才退回按具体物品严格匹配
        boolean useEquivalent = section.getBoolean(USE_EQUIVALENT_FOODS, true);
        boolean useSeasonings = section.getBoolean(USE_SEASONINGS, true);
        FlexFoodRecipe recipe = FlexFoodRecipe.of(id, result, cook, perfect, liquids, carrier,
                useEquivalent, useSeasonings);
        FoodRecipeRegistry.instance().registerMenuFlex(recipe);
        RecipeSourceIndex.instance().put(kind, id, path, target, recipe, duplicate);
        if (duplicate) {
            return false;
        }
        // 余弦是尺度无关的 方向相同的两道菜会永远打平 加载期就报出来
        FlexFoodRecipe clash = FoodRecipeRegistry.instance().findSameDirection(recipe);
        if (clash != null) {
            KaleidoscopeCookeryPlugin.instance().getLogger().warning(
                    ConsoleMessages.t("food.flex.duplicate_perfect", id.asString(), clash.id().asString()));
            return false;
        }
        FoodRecipeRegistry.instance().registerFlex(recipe);
        // 能配出菜的料就该能下锅 白名单直接从 perfect 反推
        for (Key ingredient : perfect.keySet()) {
            ApplianceFoodRegistry.instance().register(cook, ingredient);
        }
        return true;
    }

    // 清空某器具的模糊配方前先摘掉它们的来源登记 避免重载后残留指向旧文件
    static final class PotFlexFoodsParser extends CookeryIdParser {
        PotFlexFoodsParser() {
            super(POT_FLEX_FOODS, List.of(POT_FOOD_RAW), RecipeSourceIndex.Kind.POT_FLEX,
                    "pot_flex_foods", "pot-flex-foods");
        }

        @Override
        protected void reset() {
            RecipeSourceIndex.instance().clearKind(kind());
            FoodRecipeRegistry.instance().clearFlex(ApplianceType.POT);
        }

        @Override
        protected int parseAndCount(Pack pack, Path path, Key id, ConfigSection section,
                                    RecipeFileStore.SourceTarget target) {
            return parseFlexRecipe(id, path, section, ApplianceType.POT, List.of(),
                    kind(), duplicated(id), target) ? 1 : 0;
        }
    }

    static final class StockFlexFoodsParser extends CookeryIdParser {
        StockFlexFoodsParser() {
            super(STOCK_FLEX_FOODS, List.of(STOCK_FOOD_RAW), RecipeSourceIndex.Kind.STOCK_FLEX,
                    "stock_flex_foods", "stock-flex-foods");
        }

        @Override
        protected void reset() {
            RecipeSourceIndex.instance().clearKind(kind());
            FoodRecipeRegistry.instance().clearFlex(ApplianceType.STOCKPOT);
        }

        @Override
        protected int parseAndCount(Pack pack, Path path, Key id, ConfigSection section,
                                    RecipeFileStore.SourceTarget target) {
            List<Key> liquids = section.getStringList("liquid").stream().map(Key::of).toList();
            return parseFlexRecipe(id, path, section, ApplianceType.STOCKPOT, liquids,
                    kind(), duplicated(id), target) ? 1 : 0;
        }
    }

    static final class AccurateFoodsParser extends CookeryIdParser {
        AccurateFoodsParser() {
            super(ACCURATE_FOODS, List.of(LoadingStages.ITEM), RecipeSourceIndex.Kind.ACCURATE,
                    "accurate_foods", "accurate-foods");
        }

        @Override
        protected void reset() {
            RecipeSourceIndex.instance().clearKind(kind());
            FoodRecipeRegistry.instance().clearAccurate();
            ApplianceFoodRegistry.instance().clear(ApplianceType.STEAMER);
            ApplianceFoodRegistry.instance().clear(ApplianceType.SHAWARMA);
            ApplianceFoodRegistry.instance().clear(ApplianceType.MILLSTONE);
        }

        @Override
        protected int parseAndCount(Pack pack, Path path, Key id, ConfigSection section,
                                    RecipeFileStore.SourceTarget target) {
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
            int resultCount = Math.max(1,
                    section.getInt(new String[]{"result_count", "result-count"}, 1));

            // rotations 产出所需圈数 仅石磨可用 写在其它机型上报错跳过 0 表示用 behavior 默认
            int rotations = 0;
            if (section.get("rotations") != null) {
                if (cook != ApplianceType.MILLSTONE) {
                    KaleidoscopeCookeryPlugin.instance().getLogger().warning(
                            ConsoleMessages.t("food.accurate.rotations_millstone_only", id.asString()));
                    return 0;
                }
                rotations = section.getInt("rotations", 0);
            }
            // 单次产出份数 不配或配非法值都归一到 1
            List<String> lore = section.getStringList("lore");

            AccurateFoodRecipe recipe = new AccurateFoodRecipe(
                    id, input, results, cook, rotations, resultCount, lore);
            boolean duplicate = duplicated(id);
            FoodRecipeRegistry.instance().registerMenuAccurate(recipe);
            RecipeSourceIndex.instance().put(kind(), id, path, target, recipe, duplicate);
            if (duplicate) {
                return 0;
            }
            FoodRecipeRegistry.instance().registerAccurate(recipe);
            // require 自动放入白名单
            ApplianceFoodRegistry.instance().register(cook, input);
            return 1;
        }
    }

    static final class TeapotLiquidParser extends CookerySectionParser {
        TeapotLiquidParser() {
            super(TEAPOT_LIQUID, List.of(LoadingStages.ITEM), "teapot_liquid", "teapot-liquid");
        }

        @Override
        protected void reset() {
            FoodRecipeRegistry.instance().clearTeapotLiquid();
        }

        @Override
        protected int parseAndCount(Pack pack, Path path, ConfigSection section) {
            int count = 0;
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
            return count;
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
    static final class TeaCupParser extends CookerySectionParser {
        TeaCupParser() {
            super(TEA_CUP, List.of(LoadingStages.ITEM), "tea_cup", "tea-cup");
        }

        @Override
        protected void reset() {
            FoodRecipeRegistry.instance().clearTeaCup();
        }

        @Override
        protected int parseAndCount(Pack pack, Path path, ConfigSection section) {
            int count = 0;
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
            return count;
        }
    }

    static final class TeapotResultParser extends CookeryIdParser {
        TeapotResultParser() {
            super(TEAPOT_RESULT, List.of(LoadingStages.ITEM, TEAPOT_LIQUID, TEA_CUP),
                    RecipeSourceIndex.Kind.TEAPOT,
                    "teapot_result", "teapot-result");
        }

        @Override
        protected void reset() {
            RecipeSourceIndex.instance().clearKind(kind());
            FoodRecipeRegistry.instance().clearTeapot();
            ApplianceFoodRegistry.instance().clear(ApplianceType.TEAPOT);
        }

        // fluid 液体类型(如 minecraft:water) require 原料 数量(消耗) result 产物 数量 time 处理 tick
        @Override
        protected int parseAndCount(Pack pack, Path path, Key id, ConfigSection section,
                                    RecipeFileStore.SourceTarget target) {
            Key fluid = section.getNonNullIdentifier("fluid");
            if (!FoodRecipeRegistry.instance().hasTeapotLiquid(fluid)) {
                KaleidoscopeCookeryPlugin.instance().getLogger().warning(
                        ConsoleMessages.t("food.teapot.unregistered_liquid", id.asString(), fluid.asString()));
                return 0;
            }
            ItemRequirement ingredient = parseAmount(section.getNonNullString("require"));
            ItemRequirement result = parseAmount(section.getNonNullString("result"));
            // 成品必须在 tea_cup 定义模型 否则跳过
            if (!FoodRecipeRegistry.instance().hasTeaCup(result.item())) {
                KaleidoscopeCookeryPlugin.instance().getLogger().warning(
                        ConsoleMessages.t("food.teapot.missing_tea_cup", id.asString(), result.item().asString()));
                return 0;
            }
            int time = section.getInt("time", 200);

            TeapotRecipe recipe = new TeapotRecipe(
                    id, fluid, ingredient.item(), ingredient.count(), result.item(), result.count(), time);
            boolean duplicate = duplicated(id);
            FoodRecipeRegistry.instance().registerMenuTeapot(recipe);
            RecipeSourceIndex.instance().put(kind(), id, path, target, recipe, duplicate);
            if (duplicate) {
                return 0;
            }
            FoodRecipeRegistry.instance().registerTeapot(recipe);
            ApplianceFoodRegistry.instance().register(ApplianceType.TEAPOT, ingredient.item());
            return 1;
        }
    }

    static final class ChoppingBoardRawsParser extends CookeryIdParser {
        ChoppingBoardRawsParser() {
            super(CHOPPING_BOARD_RAWS, List.of(LoadingStages.ITEM),
                    RecipeSourceIndex.Kind.CHOPPING,
                    "chopping_board_raws", "chopping-board-raws");
        }

        @Override
        protected void reset() {
            RecipeSourceIndex.instance().clearKind(kind());
            FoodRecipeRegistry.instance().clearChopping();
            ApplianceFoodRegistry.instance().clear(ApplianceType.CHOPPING_BOARD);
        }

        @Override
        protected int parseAndCount(Pack pack, Path path, Key id, ConfigSection section,
                                    RecipeFileStore.SourceTarget target) {
            Key input = section.getNonNullIdentifier("require");
            int stage = section.getInt("stage", 1);

            // values 为模型 id 前缀 按 stage 派生各阶段模型 prefix/0 到 prefix/ stage 减 1
            // 省略则不做分阶段模型 砧板上直接展示放上去的东西本身 切的次数与产出照常
            String prefix = section.getString("values", (String) null);
            List<String> values = new ArrayList<>(prefix == null ? 0 : stage);
            if (prefix != null && !prefix.isEmpty()) {
                for (int i = 0; i < stage; i++) {
                    values.add(prefix + "/" + i);
                }
            }

            // 校验实际模型数量与 stage 是否一致 仅后台提示 不阻断注册
            int modelCount = values.isEmpty() ? stage : 0;
            for (int i = 0; !values.isEmpty() && i < stage + 16; i++) {
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
                return 0;
            }

            ChoppingBoardRecipe recipe = new ChoppingBoardRecipe(
                    id, input, stage, values, mode, results, extras);
            boolean duplicate = duplicated(id);
            FoodRecipeRegistry.instance().registerMenuChopping(recipe);
            RecipeSourceIndex.instance().put(kind(), id, path, target, recipe, duplicate);
            if (duplicate) {
                return 0;
            }
            FoodRecipeRegistry.instance().registerChopping(recipe);
            ApplianceFoodRegistry.instance().register(ApplianceType.CHOPPING_BOARD, input);
            return 1;
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
