package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.AdventureHelper;
import net.kaleidoscope.cookery.item.ItemNames;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.libraries.adventure.text.format.TextDecoration;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runtime registry for Kaleidoscope Cookery recipes loaded from CraftEngine configs.
 *
 * <p>External plugins can query existing recipes or register additional recipe
 * objects during their enable phase.</p>
 */
@SuppressWarnings("unused")
public final class FoodRecipeRegistry {
    private static final String TEXT_FLEX_DISH_NAMED = "item.kaleidoscopecookery.flex_dish.named";
    private static final String TEXT_FLEX_DISH_SEPARATOR = "item.kaleidoscopecookery.flex_dish.separator";
    private static final FoodRecipeRegistry INSTANCE = new FoodRecipeRegistry();
    private final List<FlexFoodRecipe> flexRecipes = new CopyOnWriteArrayList<>();
    private final List<AccurateFoodRecipe> accurateRecipes = new CopyOnWriteArrayList<>();
    private final List<ChoppingBoardRecipe> choppingRecipes = new CopyOnWriteArrayList<>();
    private final List<TeapotRecipe> teapotRecipes = new CopyOnWriteArrayList<>();
    private final Map<Key, TeapotLiquid> teapotLiquids = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile TeapotLiquid defaultLiquid;
    private final Map<Key, TeaCup> teaCups = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Key, TeaCup> teaCupsByItem = new java.util.concurrent.ConcurrentHashMap<>();

    private FoodRecipeRegistry() {
    }

    /**
     * Returns the shared registry.
     *
     * @return the singleton registry
     */
    public static FoodRecipeRegistry instance() {
        return INSTANCE;
    }

    /**
     * Counts all registered cookery recipes.
     *
     * @return total recipe count
     */
    public int totalRecipeCount() {
        return flexRecipeCount() + accurateRecipeCount() + choppingRecipeCount() + teapotRecipeCount();
    }

    /**
     * Counts recipes for one appliance type.
     *
     * @param cook appliance type
     * @return matching recipe count
     */
    public int recipeCount(ApplianceType cook) {
        int count = flexRecipeCount(cook) + accurateRecipeCount(cook);
        if (cook == ApplianceType.CHOPPING_BOARD) {
            count += choppingRecipeCount();
        } else if (cook == ApplianceType.TEAPOT) {
            count += teapotRecipeCount();
        }
        return count;
    }

    /**
     * Counts all flexible pot/stockpot recipes.
     *
     * @return flexible recipe count
     */
    public int flexRecipeCount() {
        return flexRecipes.size();
    }

    /**
     * Counts flexible recipes for one appliance type.
     *
     * @param cook appliance type
     * @return matching flexible recipe count
     */
    public int flexRecipeCount(ApplianceType cook) {
        int count = 0;
        for (FlexFoodRecipe recipe : flexRecipes) {
            if (recipe.cook() == cook) {
                count++;
            }
        }
        return count;
    }

    /**
     * Counts all accurate one-input recipes.
     *
     * @return accurate recipe count
     */
    public int accurateRecipeCount() {
        return accurateRecipes.size();
    }

    /**
     * Counts accurate recipes for one appliance type.
     *
     * @param cook appliance type
     * @return matching accurate recipe count
     */
    public int accurateRecipeCount(ApplianceType cook) {
        int count = 0;
        for (AccurateFoodRecipe recipe : accurateRecipes) {
            if (recipe.cook() == cook) {
                count++;
            }
        }
        return count;
    }

    /**
     * Counts chopping board recipes.
     *
     * @return chopping board recipe count
     */
    public int choppingRecipeCount() {
        return choppingRecipes.size();
    }

    /**
     * Counts teapot recipes.
     *
     * @return teapot recipe count
     */
    public int teapotRecipeCount() {
        return teapotRecipes.size();
    }

    /**
     * Counts registered teapot liquids.
     *
     * @return teapot liquid count
     */
    public int teapotLiquidCount() {
        return teapotLiquids.size();
    }

    /**
     * Counts registered tea cup display definitions.
     *
     * @return tea cup definition count
     */
    public int teaCupCount() {
        return teaCups.size();
    }

    /**
     * Registers a flexible pot or stockpot recipe.
     *
     * @param r recipe to register
     */
    public void registerFlex(FlexFoodRecipe r) {
        flexRecipes.add(r);
    }

    /**
     * Registers an accurate one-input recipe.
     *
     * @param r recipe to register
     */
    public void registerAccurate(AccurateFoodRecipe r) {
        accurateRecipes.add(r);
    }

    /**
     * Registers a chopping board recipe.
     *
     * @param r recipe to register
     */
    public void registerChopping(ChoppingBoardRecipe r) {
        choppingRecipes.add(r);
    }

    /**
     * Clears flexible recipes for one appliance type.
     *
     * @param cook appliance type
     */
    public void clearFlex(ApplianceType cook) {
        flexRecipes.removeIf(r -> r.cook() == cook);
    }

    /**
     * Clears all accurate recipes.
     */
    public void clearAccurate() {
        accurateRecipes.clear();
    }

    /**
     * Clears all chopping board recipes.
     */
    public void clearChopping() {
        choppingRecipes.clear();
    }

    /**
     * Registers a teapot recipe.
     *
     * @param r recipe to register
     */
    public void registerTeapot(TeapotRecipe r) {
        teapotRecipes.add(r);
    }

    /**
     * Clears all teapot recipes.
     */
    public void clearTeapot() {
        teapotRecipes.clear();
    }

    /**
     * Registers a teapot liquid type.
     *
     * @param l liquid definition
     */
    public void registerTeapotLiquid(TeapotLiquid l) {
        teapotLiquids.put(l.fluid(), l);
        if (defaultLiquid == null) {
            defaultLiquid = l;
        }
    }

    /**
     * Clears all teapot liquid types.
     */
    public void clearTeapotLiquid() {
        teapotLiquids.clear();
        defaultLiquid = null;
    }

    /**
     * Gets a teapot liquid by fluid id.
     *
     * @param fluid fluid id
     * @return liquid definition, or {@code null}
     */
    public TeapotLiquid getTeapotLiquid(Key fluid) {
        return teapotLiquids.get(fluid);
    }

    /**
     * Gets a teapot liquid by fluid id.
     *
     * @param fluid fluid id such as {@code minecraft:water}
     * @return liquid definition, or {@code null}
     */
    public TeapotLiquid getTeapotLiquid(String fluid) {
        return getTeapotLiquid(Key.of(fluid));
    }

    /**
     * Checks whether a teapot liquid is registered.
     *
     * @param fluid fluid id
     * @return {@code true} if registered
     */
    public boolean hasTeapotLiquid(Key fluid) {
        return teapotLiquids.containsKey(fluid);
    }

    /**
     * Checks whether a teapot liquid is registered.
     *
     * @param fluid fluid id such as {@code minecraft:water}
     * @return {@code true} if registered
     */
    public boolean hasTeapotLiquid(String fluid) {
        return hasTeapotLiquid(Key.of(fluid));
    }

    // 空壶液体条用首个注册液体的左右空格字形
    /**
     * Returns the first registered teapot liquid.
     *
     * @return default liquid, or {@code null} if none is registered
     */
    public TeapotLiquid defaultTeapotLiquid() {
        return defaultLiquid;
    }

    /**
     * Registers a tea cup display definition.
     *
     * @param c tea cup definition
     */
    public void registerTeaCup(TeaCup c) {
        teaCups.put(c.tea(), c);
        teaCupsByItem.put(c.item(), c);
    }

    /**
     * Clears all tea cup definitions.
     */
    public void clearTeaCup() {
        teaCups.clear();
        teaCupsByItem.clear();
    }

    /**
     * Checks whether a tea cup is registered by tea id.
     *
     * @param tea tea id
     * @return {@code true} if registered
     */
    public boolean hasTeaCup(Key tea) {
        return teaCups.containsKey(tea);
    }

    /**
     * Checks whether a tea cup is registered by tea id.
     *
     * @param tea tea id such as {@code otherplugin:jasmine_tea}
     * @return {@code true} if registered
     */
    public boolean hasTeaCup(String tea) {
        return hasTeaCup(Key.of(tea));
    }

    /**
     * Gets a tea cup definition by tea id.
     *
     * @param tea tea id
     * @return tea cup definition, or {@code null}
     */
    public TeaCup getTeaCup(Key tea) {
        return teaCups.get(tea);
    }

    /**
     * Gets a tea cup definition by tea id.
     *
     * @param tea tea id such as {@code otherplugin:jasmine_tea}
     * @return tea cup definition, or {@code null}
     */
    public TeaCup getTeaCup(String tea) {
        return getTeaCup(Key.of(tea));
    }

    // 按手持物品 id 找茶杯 用于直接放置茶到杯垫
    /**
     * Gets a tea cup definition by the held item id that places the tea.
     *
     * @param itemId item id
     * @return tea cup definition, or {@code null}
     */
    public TeaCup getTeaCupByItem(Key itemId) {
        return teaCupsByItem.get(itemId);
    }

    /**
     * Gets a tea cup definition by the held item id that places the tea.
     *
     * @param itemId item id such as {@code otherplugin:jasmine_cup}
     * @return tea cup definition, or {@code null}
     */
    public TeaCup getTeaCupByItem(String itemId) {
        return getTeaCupByItem(Key.of(itemId));
    }

    // 茶杯成品随机取一个展示模型 无则返回 null
    /**
     * Picks a random display model for a tea cup.
     *
     * @param tea tea id
     * @return model item id, or {@code null} if no model is registered
     */
    public Key pickTeaModel(Key tea) {
        TeaCup c = teaCups.get(tea);
        if (c == null || c.displayModels().isEmpty()) {
            return null;
        }
        List<Key> models = c.displayModels();
        return models.get(ThreadLocalRandom.current().nextInt(models.size()));
    }

    /**
     * Picks a random display model for a tea cup.
     *
     * @param tea tea id such as {@code otherplugin:jasmine_tea}
     * @return model item id, or {@code null} if no model is registered
     */
    public Key pickTeaModel(String tea) {
        return pickTeaModel(Key.of(tea));
    }

    // 液体类型与原料共同匹配茶壶配方
    /**
     * Finds a teapot recipe by liquid and input item.
     *
     * @param fluid liquid id
     * @param input input item id
     * @return matching recipe, or {@code null}
     */
    public TeapotRecipe findTeapot(Key fluid, Key input) {
        for (TeapotRecipe r : teapotRecipes) {
            if (r.fluid().equals(fluid) && r.input().equals(input)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Finds a teapot recipe by liquid and input item.
     *
     * @param fluid liquid id such as {@code minecraft:water}
     * @param input input item id
     * @return matching recipe, or {@code null}
     */
    public TeapotRecipe findTeapot(String fluid, String input) {
        return findTeapot(Key.of(fluid), Key.of(input));
    }

    /**
     * Finds a chopping board recipe by input item.
     *
     * @param input input item id
     * @return matching recipe, or {@code null}
     */
    public ChoppingBoardRecipe findChoppingByInput(Key input) {
        for (ChoppingBoardRecipe r : choppingRecipes) {
            if (r.input().equals(input)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Finds a chopping board recipe by input item.
     *
     * @param input input item id such as {@code minecraft:carrot}
     * @return matching recipe, or {@code null}
     */
    public ChoppingBoardRecipe findChoppingByInput(String input) {
        return findChoppingByInput(Key.of(input));
    }

    // 按配方模式产出成品 切完调用 返回需要掉落的物品列表 空列表表示无产出
    /**
     * Rolls the output items for a completed chopping board recipe.
     *
     * @param recipe chopping recipe
     * @return item stacks to drop or give; empty when no result was rolled
     */
    public List<Item> rollChoppingResults(ChoppingBoardRecipe recipe) {
        List<ChoppingResult> results = recipe.results();
        if (results.isEmpty()) {
            return List.of();
        }
        List<Item> out = new ArrayList<>();
        switch (recipe.mode()) {
            case SINGLE -> addChoppingItem(out, pickWeightedChopping(results));
            case SINGLE_EXTRA -> {
                addChoppingItem(out, pickWeightedChopping(results));
                for (ChoppingResult extra : recipe.extras()) {
                    if (rollChance(extra.weight())) {
                        addChoppingItem(out, extra);
                    }
                }
            }
            case MULTI_RANDOM -> {
                for (ChoppingResult r : results) {
                    if (rollChance(r.weight())) {
                        addChoppingItem(out, r);
                    }
                }
                if (out.isEmpty()) {
                    addChoppingItem(out, pickWeightedChopping(results));
                }
            }
        }
        return out;
    }

    // 权重当作百分比 独立判定一次是否命中 权重大于等于 100 必中
    private boolean rollChance(int weight) {
        if (weight <= 0) {
            return false;
        }
        return weight >= 100 || ThreadLocalRandom.current().nextInt(100) < weight;
    }

    // 按相对权重随机选一个产物 全 0 权重退回首个
    private ChoppingResult pickWeightedChopping(List<ChoppingResult> results) {
        int total = 0;
        for (ChoppingResult r : results) {
            if (r.weight() > 0) {
                total += r.weight();
            }
        }
        if (total <= 0) {
            return results.getFirst();
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (ChoppingResult r : results) {
            if (r.weight() <= 0) {
                continue;
            }
            if (roll < r.weight()) {
                return r;
            }
            roll -= r.weight();
        }
        return results.getLast();
    }

    private void addChoppingItem(List<Item> out, ChoppingResult result) {
        Item item = BukkitItemManager.instance().createWrappedItem(result.key(), null);
        if (item != null) {
            out.add(item.copyWithCount(Math.max(1, result.count())));
        }
    }

    /**
     * Finds and rolls an accurate one-input recipe result.
     *
     * @param type appliance type
     * @param inputItem input item id
     * @return rolled result if a matching recipe exists
     */
    public Optional<FoodRecipeResult> findAccurate(ApplianceType type, Key inputItem) {
        for (AccurateFoodRecipe recipe : accurateRecipes) {
            if (recipe.cook() != type || !recipe.input().equals(inputItem)) {
                continue;
            }
            Key chosen = pickWeighted(recipe.results());
            if (chosen == null) {
                continue;
            }
            Item item = BukkitItemManager.instance().createWrappedItem(chosen, null);
            if (item == null) {
                continue;
            }
            if (!recipe.lore().isEmpty()) {
                item.loreJson(recipe.lore().stream()
                        .map(l -> AdventureHelper.componentToJson(
                                AdventureHelper.miniMessage().deserialize("<!i>" + l)))
                        .toList());
            }
            return Optional.of(new FoodRecipeResult(item, recipe.resultCount()));
        }
        return Optional.empty();
    }

    /**
     * Finds and rolls an accurate one-input recipe result.
     *
     * @param type appliance type
     * @param inputItem input item id such as {@code minecraft:wheat}
     * @return rolled result if a matching recipe exists
     */
    public Optional<FoodRecipeResult> findAccurate(ApplianceType type, String inputItem) {
        return findAccurate(type, Key.of(inputItem));
    }

    // 石磨研磨该输入所需圈数 配方未指定圈数或无对应配方时用传入的默认值
    /**
     * Finds the required millstone rotations for an input item.
     *
     * @param inputItem input item id
     * @param defaultRotations value returned when no recipe-specific value exists
     * @return required rotations
     */
    public int findGrindRotations(Key inputItem, int defaultRotations) {
        for (AccurateFoodRecipe recipe : accurateRecipes) {
            if (recipe.cook() == ApplianceType.MILLSTONE && recipe.input().equals(inputItem)) {
                return recipe.rotations() > 0 ? recipe.rotations() : defaultRotations;
            }
        }
        return defaultRotations;
    }

    /**
     * Finds the required millstone rotations for an input item.
     *
     * @param inputItem input item id such as {@code minecraft:wheat}
     * @param defaultRotations value returned when no recipe-specific value exists
     * @return required rotations
     */
    public int findGrindRotations(String inputItem, int defaultRotations) {
        return findGrindRotations(Key.of(inputItem), defaultRotations);
    }

    // 烹饪一道菜 匹配配方里优先选消耗食材最多的 再依次以 unpreferred 种类数 lore 命中数
    // 最早主料位置打破平局 产出 count 份成品 已套用名称与 lore 多余食材丢弃 无匹配返回空
    /**
     * Cooks a flexible recipe without a soup base filter.
     *
     * @param type appliance type
     * @param ingredientIds ingredient item ids
     * @return cooked result if a matching recipe exists
     */
    public Optional<FoodRecipeResult> cookFlex(ApplianceType type, List<Key> ingredientIds) {
        return cookFlex(type, ingredientIds, null);
    }

    // 同上 但按当前汤底桶 id 过滤 配方声明 liquids 时当前汤底须命中其一 炒锅传 null 即可
    /**
     * Cooks a flexible recipe, optionally filtered by current soup base.
     *
     * @param type appliance type
     * @param ingredientIds ingredient item ids
     * @param liquid current soup base id, or {@code null}
     * @return cooked result if a matching recipe exists
     */
    public Optional<FoodRecipeResult> cookFlex(ApplianceType type, List<Key> ingredientIds, Key liquid) {
        if (ingredientIds == null || ingredientIds.isEmpty()) {
            return Optional.empty();
        }
        Map<Key, Integer> counts = new HashMap<>();
        for (Key k : ingredientIds) {
            counts.merge(k, 1, Integer::sum);
        }

        FlexFoodRecipe best = null;
        int bestInstances = 0, bestConsumed = -1, bestUnpref = -1, bestLore = -1, bestFirst = Integer.MAX_VALUE;
        for (FlexFoodRecipe r : flexRecipes) {
            if (r.cook() != type) {
                continue;
            }
            if (!r.liquids().isEmpty() && (liquid == null || !r.liquids().contains(liquid))) {
                continue;
            }
            int instances = maxInstances(r, counts);
            if (instances <= 0) {
                continue;
            }
            int consumed = instances * itemsPerInstance(r);
            int unpref = presentCount(r.unpreferred(), counts);
            int lore = matchedLoreCount(r, counts);
            int first = firstMainIndex(r, ingredientIds);

            boolean win = consumed > bestConsumed
                    || (consumed == bestConsumed && unpref > bestUnpref)
                    || (consumed == bestConsumed && unpref == bestUnpref && lore > bestLore)
                    || (consumed == bestConsumed && unpref == bestUnpref && lore == bestLore && first < bestFirst);
            if (win) {
                best = r; bestInstances = instances; bestConsumed = consumed;
                bestUnpref = unpref; bestLore = lore; bestFirst = first;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        Item dish = buildDish(best, counts);
        if (dish == null) {
            return Optional.empty();
        }
        return Optional.of(new FoodRecipeResult(dish, bestInstances));
    }

    // 这锅食材最多能做几份该配方
    private int maxInstances(FlexFoodRecipe r, Map<Key, Integer> counts) {
        int inst = Integer.MAX_VALUE;
        boolean constrained = false;
        for (ItemRequirement req : r.require()) {
            constrained = true;
            inst = Math.min(inst, counts.getOrDefault(req.item(), 0) / req.count());
        }
        FoodCategoryRegistry cat = FoodCategoryRegistry.instance();
        for (RawRequirement raw : r.raw()) {
            if (raw.min() <= 0) {
                continue;
            }
            constrained = true;
            int c = 0;
            for (var e : counts.entrySet()) {
                if (cat.isInCategory(r.cook(), e.getKey(), raw.category())) {
                    c += e.getValue();
                }
            }
            inst = Math.min(inst, c / raw.min());
        }
        return constrained ? inst : 0;
    }

    // 单份所消耗的食材数
    private int itemsPerInstance(FlexFoodRecipe r) {
        FoodCategoryRegistry cat = FoodCategoryRegistry.instance();
        int per = 0;
        for (RawRequirement raw : r.raw()) {
            if (raw.min() > 0) {
                per += raw.min();
            }
        }
        for (ItemRequirement req : r.require()) {
            boolean inRaw = false;
            for (RawRequirement raw : r.raw()) {
                if (raw.min() > 0 && cat.isInCategory(r.cook(), req.item(), raw.category())) {
                    inRaw = true;
                    break;
                }
            }
            if (!inRaw) {
                per += req.count();
            }
        }
        return Math.max(per, 1);
    }

    private int presentCount(List<Key> list, Map<Key, Integer> counts) {
        int n = 0;
        for (Key k : list) {
            if (counts.getOrDefault(k, 0) > 0) {
                n++;
            }
        }
        return n;
    }

    private int matchedLoreCount(FlexFoodRecipe r, Map<Key, Integer> counts) {
        return resolveLoreLines(r, counts).size();
    }

    // 配方标志性食材最早出现的位置 优先看 require 没有 require 才退回 raw 类别成员
    private int firstMainIndex(FlexFoodRecipe r, List<Key> ids) {
        if (!r.require().isEmpty()) {
            for (int i = 0; i < ids.size(); i++) {
                for (ItemRequirement req : r.require()) {
                    if (req.item().equals(ids.get(i))) {
                        return i;
                    }
                }
            }
            return Integer.MAX_VALUE;
        }
        FoodCategoryRegistry cat = FoodCategoryRegistry.instance();
        for (int i = 0; i < ids.size(); i++) {
            for (RawRequirement raw : r.raw()) {
                if (raw.min() > 0 && cat.isInCategory(r.cook(), ids.get(i), raw.category())) {
                    return i;
                }
            }
        }
        return Integer.MAX_VALUE;
    }

    // 套用名称与 lore unpreferred 存在则覆盖 preferred 底味
    private Item buildDish(FlexFoodRecipe r, Map<Key, Integer> counts) {
        Item item = BukkitItemManager.instance().createWrappedItem(r.result(), null);
        if (item == null) {
            return null;
        }

        boolean unprefPresent = anyPresent(r.unpreferred(), counts);

        List<Key> flavor = unprefPresent ? r.unpreferred() : r.preferred();
        List<Component> names = new ArrayList<>();
        for (Key k : flavor) {
            if (counts.getOrDefault(k, 0) > 0) {
                names.add(displayNameComponent(k));
            }
        }
        if (!names.isEmpty()) {
            Component base = item.hoverNameComponent()
                    .orElseGet(() -> Component.translatable(itemTranslationKey(r.result())));
            Component name = Component.translatable(TEXT_FLEX_DISH_NAMED, joinNames(names), base)
                    .decoration(TextDecoration.ITALIC, false);
            item.customNameJson(AdventureHelper.componentToJson(name));
        }

        List<String> loreLines = resolveLoreLines(r, counts);
        if (!loreLines.isEmpty()) {
            List<String> loreJson = new ArrayList<>();
            for (String line : loreLines) {
                loreJson.add(AdventureHelper.componentToJson(AdventureHelper.miniMessage().deserialize("<!i>" + line)));
            }
            item.loreJson(loreJson);
        }
        return item;
    }

    private boolean anyPresent(List<Key> list, Map<Key, Integer> counts) {
        for (Key k : list) {
            if (counts.getOrDefault(k, 0) > 0) {
                return true;
            }
        }
        return false;
    }

    // when 中所有条件都满足
    private boolean whenMet(LoreCondition lc, Map<Key, Integer> counts) {
        if (lc.when().isEmpty()) {
            return false;
        }
        for (ItemRequirement c : lc.when()) {
            if (counts.getOrDefault(c.item(), 0) < c.count()) {
                return false;
            }
        }
        return true;
    }

    // 这条 lore 算 unpreferred 底味 显式标记优先 否则看 when 是否含配方 unpreferred 食材
    private boolean isUnprefRule(LoreCondition lc, FlexFoodRecipe r) {
        if (lc.unpreferred() != null) {
            return lc.unpreferred();
        }
        for (ItemRequirement c : lc.when()) {
            if (r.unpreferred().contains(c.item())) {
                return true;
            }
        }
        return false;
    }

    // a 的条件是否覆盖 b b 的每个物品要求 a 都以 ≥ 的数量要求着
    private boolean covers(LoreCondition a, LoreCondition b) {
        for (ItemRequirement bc : b.when()) {
            int aReq = 0;
            for (ItemRequirement ac : a.when()) {
                if (ac.item().equals(bc.item())) {
                    aReq = Math.max(aReq, ac.count());
                }
            }
            if (aReq < bc.count()) {
                return false;
            }
        }
        return true;
    }

    // 解析最终要显示的 lore 行 满足 when + 与当前底味一致 + 不被更具体的同类条件覆盖
    private List<String> resolveLoreLines(FlexFoodRecipe r, Map<Key, Integer> counts) {
        boolean unprefPresent = anyPresent(r.unpreferred(), counts);
        List<LoreCondition> pool = new ArrayList<>();
        for (LoreCondition lc : r.loreConditions()) {
            if (!whenMet(lc, counts)) {
                continue;
            }
            if (isUnprefRule(lc, r) != unprefPresent) {
                continue;
            }
            pool.add(lc);
        }
        List<String> lines = new ArrayList<>();
        for (LoreCondition lc : pool) {
            boolean dominated = false;
            for (LoreCondition other : pool) {
                if (other == lc) {
                    continue;
                }
                if (covers(other, lc) && !covers(lc, other)) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) {
                lines.addAll(lc.data());
            }
        }
        return lines;
    }

    // 子集是否满足配方的 require 与 raw 门槛
    private boolean matches(FlexFoodRecipe recipe, ApplianceType type, List<Key> subset) {
        if (recipe.cook() != type) {
            return false;
        }
        for (ItemRequirement req : recipe.require()) {
            int have = 0;
            for (Key k : subset) {
                if (k.equals(req.item())) {
                    have++;
                }
            }
            if (have < req.count()) {
                return false;
            }
        }
        FoodCategoryRegistry cat = FoodCategoryRegistry.instance();
        for (RawRequirement raw : recipe.raw()) {
            if (raw.min() <= 0) {
                continue;
            }
            if (cat.countInCategory(type, subset, raw.category()) < raw.min()) {
                return false;
            }
        }
        return true;
    }

    // 记录食谱用 在匹配配方里挑要求最具体的那个
    private FlexFoodRecipe findBestSingleMatch(ApplianceType type, List<Key> subset) {
        List<FlexFoodRecipe> candidates = new ArrayList<>();
        for (FlexFoodRecipe recipe : flexRecipes) {
            if (matches(recipe, type, subset)) {
                candidates.add(recipe);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort((a, b) -> {
            int cmp = Integer.compare(b.nonZeroRawCount(), a.nonZeroRawCount());
            return cmp != 0 ? cmp : Integer.compare(b.totalMinCount(), a.totalMinCount());
        });
        return candidates.getFirst();
    }

    /**
     * Finds the best flexible recipe for a set of ingredients.
     *
     * @param type appliance type
     * @param ingredientIds ingredient item ids
     * @return best matching recipe, if any
     */
    public Optional<FlexFoodRecipe> findBestFlexRecipe(ApplianceType type, List<Key> ingredientIds) {
        return Optional.ofNullable(findBestSingleMatch(type, ingredientIds));
    }

    private String displayName(Key key) {
        return ItemNames.displayName(key);
    }

    private Component displayNameComponent(Key key) {
        String displayName = displayName(key);
        if (!displayName.equals(key.value())) {
            return AdventureHelper.miniMessage().deserialize(displayName);
        }
        return Component.translatable(itemTranslationKey(key));
    }

    private Component joinNames(List<Component> names) {
        Component result = Component.empty();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                result = result.append(Component.translatable(TEXT_FLEX_DISH_SEPARATOR));
            }
            result = result.append(names.get(i));
        }
        return result;
    }

    private String itemTranslationKey(Key key) {
        return "item." + key.namespace() + "." + key.value();
    }

    // 按权重随机选一个成品 Key 权重总和归一化 全 0 权重则退回首个
    private Key pickWeighted(List<WeightedResult> results) {
        if (results.isEmpty()) {
            return null;
        }
        int total = 0;
        for (WeightedResult r : results) {
            if (r.weight() > 0) {
                total += r.weight();
            }
        }
        if (total <= 0) {
            return results.getFirst().key();
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (WeightedResult r : results) {
            if (r.weight() <= 0) {
                continue;
            }
            if (roll < r.weight()) {
                return r.key();
            }
            roll -= r.weight();
        }
        return results.getLast().key();
    }

    /**
     * Finds an accurate recipe by recipe id.
     *
     * @param id recipe id
     * @return matching recipe, or {@code null}
     */
    public AccurateFoodRecipe findAccurateById(Key id) {
        for (AccurateFoodRecipe r : accurateRecipes) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Finds an accurate recipe by recipe id.
     *
     * @param id recipe id such as {@code otherplugin:recipe_name}
     * @return matching recipe, or {@code null}
     */
    public AccurateFoodRecipe findAccurateById(String id) {
        return findAccurateById(Key.of(id));
    }

    /**
     * Finds a flexible recipe by recipe id.
     *
     * @param id recipe id
     * @return matching recipe, or {@code null}
     */
    public FlexFoodRecipe findFlexById(Key id) {
        for (FlexFoodRecipe r : flexRecipes) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Finds a flexible recipe by recipe id.
     *
     * @param id recipe id such as {@code otherplugin:recipe_name}
     * @return matching recipe, or {@code null}
     */
    public FlexFoodRecipe findFlexById(String id) {
        return findFlexById(Key.of(id));
    }
}
