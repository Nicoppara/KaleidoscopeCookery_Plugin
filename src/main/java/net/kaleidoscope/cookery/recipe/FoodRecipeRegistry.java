package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.AdventureHelper;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

// 配方运行期注册表 数据来自 CraftEngine 配置加载
// 外部插件追加注册须在自身 enable 阶段完成 之后配置重载会清空并重新填充
@SuppressWarnings("unused")
public final class FoodRecipeRegistry {
    private record AccurateKey(ApplianceType cook, Key input) {}

    // 必需食材齐全后允许任意杂料 默认不再用相似度阈值拒绝成菜
    private static final double DEFAULT_MIN_FLEX_SCORE = 0.0;

    private static final FoodRecipeRegistry INSTANCE = new FoodRecipeRegistry();
    private final List<FlexFoodRecipe> flexRecipes = new CopyOnWriteArrayList<>();
    private final List<FlexFoodRecipe> menuFlexRecipes = new CopyOnWriteArrayList<>();
    // 最高分低于这个值就不出菜 调用方据此产出迷之炒菜 乱炖自然降级不需要特判
    private volatile double minFlexScore = DEFAULT_MIN_FLEX_SCORE;
    private final List<AccurateFoodRecipe> accurateRecipes = new CopyOnWriteArrayList<>();
    private final List<AccurateFoodRecipe> menuAccurateRecipes = new CopyOnWriteArrayList<>();
    // 器具加输入唯一确定一条精确配方 注册期建索引 免得热路径全表扫
    private final Map<AccurateKey, AccurateFoodRecipe> accurateIndex = new ConcurrentHashMap<>();
    private final List<ChoppingBoardRecipe> choppingRecipes = new CopyOnWriteArrayList<>();
    private final List<ChoppingBoardRecipe> menuChoppingRecipes = new CopyOnWriteArrayList<>();
    private final List<TeapotRecipe> teapotRecipes = new CopyOnWriteArrayList<>();
    private final List<TeapotRecipe> menuTeapotRecipes = new CopyOnWriteArrayList<>();
    private final Map<Key, TeapotLiquid> teapotLiquids = new ConcurrentHashMap<>();
    private volatile TeapotLiquid defaultLiquid;
    private final Map<Key, TeaCup> teaCups = new ConcurrentHashMap<>();
    private final Map<Key, TeaCup> teaCupsByItem = new ConcurrentHashMap<>();

    private FoodRecipeRegistry() {
    }

    public static FoodRecipeRegistry instance() {
        return INSTANCE;
    }

    public int totalRecipeCount() {
        return flexRecipeCount() + accurateRecipeCount() + choppingRecipeCount() + teapotRecipeCount();
    }

    public int recipeCount(ApplianceType cook) {
        int count = flexRecipeCount(cook) + accurateRecipeCount(cook);
        if (cook == ApplianceType.CHOPPING_BOARD) {
            count += choppingRecipeCount();
        } else if (cook == ApplianceType.TEAPOT) {
            count += teapotRecipeCount();
        }
        return count;
    }

    public int flexRecipeCount() {
        return flexRecipes.size();
    }

    public int flexRecipeCount(ApplianceType cook) {
        int count = 0;
        for (FlexFoodRecipe recipe : flexRecipes) {
            if (recipe.cook() == cook) {
                count++;
            }
        }
        return count;
    }

    public int accurateRecipeCount() {
        return accurateRecipes.size();
    }

    public int accurateRecipeCount(ApplianceType cook) {
        int count = 0;
        for (AccurateFoodRecipe recipe : accurateRecipes) {
            if (recipe.cook() == cook) {
                count++;
            }
        }
        return count;
    }

    public int choppingRecipeCount() {
        return choppingRecipes.size();
    }

    public int teapotRecipeCount() {
        return teapotRecipes.size();
    }

    public int teapotLiquidCount() {
        return teapotLiquids.size();
    }

    public int teaCupCount() {
        return teaCups.size();
    }

    public void minFlexScore(double value) {
        this.minFlexScore = value;
    }

    // 方向相同即余弦恒等 两道菜会永远打平 注册前查重
    public FlexFoodRecipe findSameDirection(FlexFoodRecipe candidate) {
        return findSameDirection(candidate, null);
    }

    public FlexFoodRecipe findSameDirection(FlexFoodRecipe candidate, FlexFoodRecipe excluded) {
        for (FlexFoodRecipe r : flexRecipes) {
            if (r == excluded) {
                continue;
            }
            if (r.cook() != candidate.cook() || r.perfect().size() != candidate.perfect().size()) {
                continue;
            }
            // 汤底不重叠的两条配方在匹配时就被过滤开了 理想配比再像也不会打平
            // 水底饺子和岩浆底生煎馒头就是同一个向量 但永远碰不到一起
            if (!liquidsOverlap(r.liquids(), candidate.liquids())) {
                continue;
            }
            Double scale = null;
            boolean same = true;
            for (Map.Entry<Key, Integer> e : candidate.perfect().entrySet()) {
                Integer other = r.perfect().get(e.getKey());
                if (other == null) {
                    same = false;
                    break;
                }
                double ratio = (double) other / e.getValue();
                if (scale == null) {
                    scale = ratio;
                } else if (Math.abs(scale - ratio) > 1e-6) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return r;
            }
        }
        return null;
    }

    // 任一方不限汤底就一定会相遇 否则要有交集才算相遇
    private static boolean liquidsOverlap(List<Key> a, List<Key> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return true;
        }
        for (Key k : a) {
            if (b.contains(k)) {
                return true;
            }
        }
        return false;
    }

    public void registerFlex(FlexFoodRecipe r) {
        flexRecipes.add(r);
        DishCarriers.rebuild(flexRecipes);
    }

    public void registerMenuFlex(FlexFoodRecipe r) {
        menuFlexRecipes.add(r);
    }

    public void registerAccurate(AccurateFoodRecipe r) {
        accurateRecipes.add(r);
        // 精确配方按 器具 加 输入 唯一确定 注册期建好索引 别在热路径上全表扫
        accurateIndex.putIfAbsent(new AccurateKey(r.cook(), r.input()), r);
    }

    public void registerMenuAccurate(AccurateFoodRecipe r) {
        menuAccurateRecipes.add(r);
    }

    public List<AccurateFoodRecipe> menuAccurateRecipes(ApplianceType cook) {
        List<AccurateFoodRecipe> out = new ArrayList<>();
        for (AccurateFoodRecipe r : menuAccurateRecipes) {
            if (r.cook() == cook) {
                out.add(r);
            }
        }
        return out;
    }

    public List<FlexFoodRecipe> menuFlexRecipes(ApplianceType cook) {
        List<FlexFoodRecipe> out = new ArrayList<>();
        for (FlexFoodRecipe r : menuFlexRecipes) {
            if (r.cook() == cook) {
                out.add(r);
            }
        }
        return out;
    }

    // 按器具取该器具下的全部精确配方 快照 供编辑与浏览 UI 分页
    public List<AccurateFoodRecipe> accurateRecipes(ApplianceType cook) {
        List<AccurateFoodRecipe> out = new ArrayList<>();
        for (AccurateFoodRecipe r : accurateRecipes) {
            if (r.cook() == cook) {
                out.add(r);
            }
        }
        return out;
    }

    public List<FlexFoodRecipe> flexRecipes(ApplianceType cook) {
        List<FlexFoodRecipe> out = new ArrayList<>();
        for (FlexFoodRecipe r : flexRecipes) {
            if (r.cook() == cook) {
                out.add(r);
            }
        }
        return out;
    }

    // UI 编辑走这两个 删除后整表重建索引 registerAccurate 的 putIfAbsent 只认首个
    public boolean removeAccurate(Key id) {
        if (!accurateRecipes.removeIf(r -> r.id().equals(id))) {
            return false;
        }
        rebuildAccurateIndex();
        return true;
    }

    public boolean removeFlex(ApplianceType cook, Key id) {
        boolean removed = flexRecipes.removeIf(r -> r.cook() == cook && r.id().equals(id));
        if (removed) {
            DishCarriers.rebuild(flexRecipes);
        }
        return removed;
    }

    public boolean removeChopping(Key id) {
        return choppingRecipes.removeIf(r -> r.id().equals(id));
    }

    public boolean removeTeapot(Key id) {
        return teapotRecipes.removeIf(r -> r.id().equals(id));
    }

    public List<ChoppingBoardRecipe> choppingRecipes() {
        return List.copyOf(choppingRecipes);
    }

    public List<ChoppingBoardRecipe> menuChoppingRecipes() {
        return List.copyOf(menuChoppingRecipes);
    }

    public List<TeapotRecipe> teapotRecipes() {
        return List.copyOf(teapotRecipes);
    }

    public List<TeapotRecipe> menuTeapotRecipes() {
        return List.copyOf(menuTeapotRecipes);
    }

    public void removeMenuAccurate(AccurateFoodRecipe recipe) {
        menuAccurateRecipes.removeIf(value -> value == recipe);
    }

    public void removeMenuFlex(FlexFoodRecipe recipe) {
        menuFlexRecipes.removeIf(value -> value == recipe);
    }

    public void removeMenuChopping(ChoppingBoardRecipe recipe) {
        menuChoppingRecipes.removeIf(value -> value == recipe);
    }

    public void removeMenuTeapot(TeapotRecipe recipe) {
        menuTeapotRecipes.removeIf(value -> value == recipe);
    }

    public ChoppingBoardRecipe findChoppingById(Key id) {
        for (ChoppingBoardRecipe r : choppingRecipes) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    public TeapotRecipe findTeapotById(Key id) {
        for (TeapotRecipe r : teapotRecipes) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    private void rebuildAccurateIndex() {
        accurateIndex.clear();
        for (AccurateFoodRecipe r : accurateRecipes) {
            accurateIndex.putIfAbsent(new AccurateKey(r.cook(), r.input()), r);
        }
    }

    public void registerChopping(ChoppingBoardRecipe r) {
        choppingRecipes.add(r);
    }

    public void registerMenuChopping(ChoppingBoardRecipe r) {
        menuChoppingRecipes.add(r);
    }

    public void clearFlex(ApplianceType cook) {
        flexRecipes.removeIf(r -> r.cook() == cook);
        menuFlexRecipes.removeIf(r -> r.cook() == cook);
        DishCarriers.rebuild(flexRecipes);
    }

    public void clearAccurate() {
        accurateRecipes.clear();
        menuAccurateRecipes.clear();
        accurateIndex.clear();
    }

    public void clearChopping() {
        choppingRecipes.clear();
        menuChoppingRecipes.clear();
    }

    public void registerTeapot(TeapotRecipe r) {
        teapotRecipes.add(r);
    }

    public void registerMenuTeapot(TeapotRecipe r) {
        menuTeapotRecipes.add(r);
    }

    public void clearTeapot() {
        teapotRecipes.clear();
        menuTeapotRecipes.clear();
    }

    public void registerTeapotLiquid(TeapotLiquid l) {
        teapotLiquids.put(l.fluid(), l);
        if (defaultLiquid == null) {
            defaultLiquid = l;
        }
    }

    public void clearTeapotLiquid() {
        teapotLiquids.clear();
        defaultLiquid = null;
    }

    public TeapotLiquid getTeapotLiquid(Key fluid) {
        return teapotLiquids.get(fluid);
    }

    public TeapotLiquid getTeapotLiquid(String fluid) {
        return getTeapotLiquid(Key.of(fluid));
    }

    // 已登记的液体 按 id 排序 编辑器列按钮用 顺序不定的话每次开菜单都在跳
    public List<Key> teapotLiquidKeys() {
        List<Key> out = new ArrayList<>(teapotLiquids.keySet());
        out.sort(Comparator.comparing(Key::asString));
        return List.copyOf(out);
    }

    public boolean hasTeapotLiquid(Key fluid) {
        return teapotLiquids.containsKey(fluid);
    }

    public boolean hasTeapotLiquid(String fluid) {
        return hasTeapotLiquid(Key.of(fluid));
    }

    // 空壶液体条用首个注册液体的左右空格字形
    public TeapotLiquid defaultTeapotLiquid() {
        return defaultLiquid;
    }

    public void registerTeaCup(TeaCup c) {
        teaCups.put(c.tea(), c);
        teaCupsByItem.put(c.item(), c);
    }

    public void clearTeaCup() {
        teaCups.clear();
        teaCupsByItem.clear();
    }

    public boolean hasTeaCup(Key tea) {
        return teaCups.containsKey(tea);
    }

    public boolean hasTeaCup(String tea) {
        return hasTeaCup(Key.of(tea));
    }

    public TeaCup getTeaCup(Key tea) {
        return teaCups.get(tea);
    }

    public TeaCup getTeaCup(String tea) {
        return getTeaCup(Key.of(tea));
    }

    // 按手持物品 id 找茶杯 用于直接放置茶到杯垫
    public TeaCup getTeaCupByItem(Key itemId) {
        return teaCupsByItem.get(itemId);
    }

    public TeaCup getTeaCupByItem(String itemId) {
        return getTeaCupByItem(Key.of(itemId));
    }

    // 茶杯成品随机取一个展示模型 无则返回 null
    public Key pickTeaModel(Key tea) {
        TeaCup c = teaCups.get(tea);
        if (c == null || c.displayModels().isEmpty()) {
            return null;
        }
        List<Key> models = c.displayModels();
        return models.get(ThreadLocalRandom.current().nextInt(models.size()));
    }

    public Key pickTeaModel(String tea) {
        return pickTeaModel(Key.of(tea));
    }

    // 液体类型与原料共同匹配茶壶配方
    public TeapotRecipe findTeapot(Key fluid, Key input) {
        for (TeapotRecipe r : teapotRecipes) {
            if (r.fluid().equals(fluid) && r.input().equals(input)) {
                return r;
            }
        }
        return null;
    }

    public TeapotRecipe findTeapot(String fluid, String input) {
        return findTeapot(Key.of(fluid), Key.of(input));
    }

    public ChoppingBoardRecipe findChoppingByInput(Key input) {
        for (ChoppingBoardRecipe r : choppingRecipes) {
            if (r.input().equals(input)) {
                return r;
            }
        }
        return null;
    }

    public ChoppingBoardRecipe findChoppingByInput(String input) {
        return findChoppingByInput(Key.of(input));
    }

    // 按配方模式产出成品 切完调用 返回需要掉落的物品列表 空列表表示无产出
    public List<Item> rollChoppingResults(ChoppingBoardRecipe recipe) {
        List<ChoppingResult> results = recipe.results();
        if (results.isEmpty()) {
            return List.of();
        }
        List<Item> out = new ArrayList<>();
        switch (recipe.mode()) {
            case SINGLE -> addChoppingItem(out, WeightedPicker.pick(results, ChoppingResult::weight));
            case SINGLE_EXTRA -> {
                addChoppingItem(out, WeightedPicker.pick(results, ChoppingResult::weight));
                for (ChoppingResult extra : recipe.extras()) {
                    if (WeightedPicker.roll(extra.weight())) {
                        addChoppingItem(out, extra);
                    }
                }
            }
            case MULTI_RANDOM -> {
                for (ChoppingResult r : results) {
                    if (WeightedPicker.roll(r.weight())) {
                        addChoppingItem(out, r);
                    }
                }
                if (out.isEmpty()) {
                    addChoppingItem(out, WeightedPicker.pick(results, ChoppingResult::weight));
                }
            }
        }
        return out;
    }

    private void addChoppingItem(List<Item> out, ChoppingResult result) {
        Item item = InventoryUtils.createOrEmpty(result.key());
        if (!ItemUtils.isEmpty(item)) {
            out.add(item.copyWithCount(Math.max(1, result.count())));
        }
    }

    public Optional<FoodRecipeResult> findAccurate(ApplianceType type, Key inputItem) {
        AccurateFoodRecipe recipe = accurateIndex.get(new AccurateKey(type, inputItem));
        if (recipe == null) {
            return Optional.empty();
        }
        WeightedResult chosen = WeightedPicker.pick(recipe.results(), WeightedResult::weight);
        if (chosen == null) {
            return Optional.empty();
        }
        Item item = InventoryUtils.createOrEmpty(chosen.key());
        if (ItemUtils.isEmpty(item)) {
            return Optional.empty();
        }
        if (!recipe.lore().isEmpty()) {
            item.loreComponent(recipe.lore().stream()
                    .map(l -> AdventureHelper.miniMessage().deserialize("<!i>" + l))
                    .toList());
        }
        return Optional.of(new FoodRecipeResult(item, recipe.resultCount(), null));
    }

    public Optional<FoodRecipeResult> findAccurate(ApplianceType type, String inputItem) {
        return findAccurate(type, Key.of(inputItem));
    }

    // 石磨研磨该输入所需圈数 配方未指定圈数或无对应配方时用传入的默认值
    public int findGrindRotations(Key inputItem, int defaultRotations) {
        AccurateFoodRecipe recipe = accurateIndex.get(new AccurateKey(ApplianceType.MILLSTONE, inputItem));
        if (recipe == null || recipe.rotations() <= 0) {
            return defaultRotations;
        }
        return recipe.rotations();
    }

    public int findGrindRotations(String inputItem, int defaultRotations) {
        return findGrindRotations(Key.of(inputItem), defaultRotations);
    }

    // 烹饪一道菜 匹配配方里优先选消耗食材最多的 再依次以 unpreferred 种类数 lore 命中数
    // 最早主料位置打破平局 产出 count 份成品 已套用名称与 lore 多余食材丢弃 无匹配返回空
    public Optional<FoodRecipeResult> cookFlex(ApplianceType type, List<Key> ingredientIds) {
        return cookFlex(type, ingredientIds, null);
    }

    // 同上 但按当前汤底桶 id 过滤 配方声明 liquids 时当前汤底须命中其一 炒锅传 null 即可
    public Optional<FoodRecipeResult> cookFlex(ApplianceType type, List<Key> ingredientIds, Key liquid) {
        FlexMatcher.Match match = FlexMatcher.bestMatch(flexRecipes, minFlexScore, type, ingredientIds, liquid);
        if (match == null) {
            return Optional.empty();
        }
        Item dish = FlexMatcher.buildDish(match);
        if (dish == null) {
            return Optional.empty();
        }
        return Optional.of(new FoodRecipeResult(dish, match.portions(), match.recipe().carrier()));
    }

    public Optional<FlexFoodRecipe> findBestFlexRecipe(ApplianceType type, List<Key> ingredientIds) {
        return findBestFlexRecipe(type, ingredientIds, null);
    }

    // 高汤锅的配方几乎都声明了 liquids 不带汤底查会被整条过滤掉 永远匹配不上
    public Optional<FlexFoodRecipe> findBestFlexRecipe(ApplianceType type, List<Key> ingredientIds, Key liquid) {
        FlexMatcher.Match match = FlexMatcher.bestMatch(flexRecipes, minFlexScore, type, ingredientIds, liquid);
        return Optional.ofNullable(match == null ? null : match.recipe());
    }

    public AccurateFoodRecipe findAccurateById(Key id) {
        for (AccurateFoodRecipe r : accurateRecipes) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    public AccurateFoodRecipe findAccurateById(String id) {
        return findAccurateById(Key.of(id));
    }

    public FlexFoodRecipe findFlexById(Key id) {
        for (FlexFoodRecipe r : flexRecipes) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    public FlexFoodRecipe findFlexById(String id) {
        return findFlexById(Key.of(id));
    }
}
