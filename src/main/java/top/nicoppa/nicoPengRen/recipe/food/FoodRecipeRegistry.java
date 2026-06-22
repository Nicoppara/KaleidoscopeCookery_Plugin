package top.nicoppa.nicoPengRen.recipe.food;

import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.AdventureHelper;
import top.nicoppa.nicoPengRen.common.item.ItemNames;
import net.momirealms.craftengine.core.util.Key;
import top.nicoppa.nicoPengRen.recipe.ApplianceType;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FoodRecipeRegistry {
    private static final FoodRecipeRegistry INSTANCE = new FoodRecipeRegistry();
    private final List<FlexFoodRecipe> flexRecipes = new CopyOnWriteArrayList<>();
    private final List<AccurateFoodRecipe> accurateRecipes = new CopyOnWriteArrayList<>();
    private final List<ChoppingBoardRecipe> choppingRecipes = new CopyOnWriteArrayList<>();

    private FoodRecipeRegistry() {}
    public static FoodRecipeRegistry instance() { return INSTANCE; }

    public void registerFlex(FlexFoodRecipe r) { flexRecipes.add(r); }
    public void registerAccurate(AccurateFoodRecipe r) { accurateRecipes.add(r); }
    public void registerChopping(ChoppingBoardRecipe r) { choppingRecipes.add(r); }
    public void clearFlex(ApplianceType cook) { flexRecipes.removeIf(r -> r.cook() == cook); }
    public void clearAccurate() { accurateRecipes.clear(); }
    public void clearChopping() { choppingRecipes.clear(); }

    public ChoppingBoardRecipe findChoppingByInput(Key input) {
        for (ChoppingBoardRecipe r : choppingRecipes) if (r.input().equals(input)) return r;
        return null;
    }

    public Item pickChoppingResult(ChoppingBoardRecipe recipe) {
        List<ChoppingResult> results = recipe.results();
        if (results.isEmpty()) return Item.empty();
        int total = 0;
        for (ChoppingResult r : results) if (r.weight() > 0) total += r.weight();
        ChoppingResult chosen = results.get(0);
        if (total > 0) {
            int roll = (int) (Math.random() * total);
            for (ChoppingResult r : results) {
                if (r.weight() <= 0) continue;
                if (roll < r.weight()) { chosen = r; break; }
                roll -= r.weight();
            }
        }
        Item item = BukkitItemManager.instance().createWrappedItem(chosen.key(), null);
        if (item == null) return Item.empty();
        return item.copyWithCount(Math.max(1, chosen.count()));
    }

    public Optional<FoodRecipeResult> findAccurate(ApplianceType type, Key inputItem) {
        for (AccurateFoodRecipe recipe : accurateRecipes) {
            if (recipe.cook() != type || !recipe.input().equals(inputItem)) continue;
            Key chosen = pickWeighted(recipe.results());
            if (chosen == null) continue;
            Item item = BukkitItemManager.instance().createWrappedItem(chosen, null);
            if (item == null) continue;
            if (!recipe.lore().isEmpty()) {
                item.loreJson(recipe.lore().stream()
                        .map(l -> AdventureHelper.componentToJson(
                                AdventureHelper.miniMessage().deserialize("<!i>" + l)))
                        .toList());
            }
            return Optional.of(new FoodRecipeResult(item, 1));
        }
        return Optional.empty();
    }

    /**
     * 烹饪出一道菜：在所有匹配配方里优先选消耗食材最多的，依次以
     * unpreferred 种类数、lore 命中数、最早放入的主料位置打破平局
     * 产出 count 份该成品（已套用名称/lore），多余食材丢弃；无任何匹配返回空
     */
    public Optional<FoodRecipeResult> cookFlex(ApplianceType type, List<Key> ingredientIds) {
        return cookFlex(type, ingredientIds, null);
    }

    /**
     * 同上，但额外按 {@code liquid}(当前汤底的桶 id)过滤：配方声明了 liquids 时，当前汤底须命中其一
     * 炒锅传 null 即可（其配方 liquids 恒为空）。
     */
    public Optional<FoodRecipeResult> cookFlex(ApplianceType type, List<Key> ingredientIds, Key liquid) {
        if (ingredientIds == null || ingredientIds.isEmpty()) return Optional.empty();
        Map<Key, Integer> counts = new HashMap<>();
        for (Key k : ingredientIds) counts.merge(k, 1, Integer::sum);

        FlexFoodRecipe best = null;
        int bestInstances = 0, bestConsumed = -1, bestUnpref = -1, bestLore = -1, bestFirst = Integer.MAX_VALUE;
        for (FlexFoodRecipe r : flexRecipes) {
            if (r.cook() != type) continue;
            if (!r.liquids().isEmpty() && (liquid == null || !r.liquids().contains(liquid))) continue;
            int instances = maxInstances(r, counts);
            if (instances <= 0) continue;
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
        if (best == null) return Optional.empty();
        Item dish = buildDish(best, counts);
        if (dish == null) return Optional.empty();
        return Optional.of(new FoodRecipeResult(dish, bestInstances));
    }

    // 这锅食材最多能做几份该配方（受 require 数量与 raw 类别 min 限制）
    private int maxInstances(FlexFoodRecipe r, Map<Key, Integer> counts) {
        int inst = Integer.MAX_VALUE;
        boolean constrained = false;
        for (ItemRequirement req : r.require()) {
            constrained = true;
            inst = Math.min(inst, counts.getOrDefault(req.item(), 0) / req.count());
        }
        FoodCategoryRegistry cat = FoodCategoryRegistry.instance();
        for (RawRequirement raw : r.raw()) {
            if (raw.min() <= 0) continue;
            constrained = true;
            int c = 0;
            for (var e : counts.entrySet()) if (cat.isInCategory(r.cook(), e.getKey(), raw.category())) c += e.getValue();
            inst = Math.min(inst, c / raw.min());
        }
        return constrained ? inst : 0;
    }

    // 单份所消耗的食材数（raw 类别 min 之和 + 不在任何 raw 类别里的 require 数量）
    private int itemsPerInstance(FlexFoodRecipe r) {
        FoodCategoryRegistry cat = FoodCategoryRegistry.instance();
        int per = 0;
        for (RawRequirement raw : r.raw()) if (raw.min() > 0) per += raw.min();
        for (ItemRequirement req : r.require()) {
            boolean inRaw = false;
            for (RawRequirement raw : r.raw()) {
                if (raw.min() > 0 && cat.isInCategory(r.cook(), req.item(), raw.category())) { inRaw = true; break; }
            }
            if (!inRaw) per += req.count();
        }
        return Math.max(per, 1);
    }

    private int presentCount(List<Key> list, Map<Key, Integer> counts) {
        int n = 0;
        for (Key k : list) if (counts.getOrDefault(k, 0) > 0) n++;
        return n;
    }

    private int matchedLoreCount(FlexFoodRecipe r, Map<Key, Integer> counts) {
        return resolveLoreLines(r, counts).size();
    }

    // 配方标志性食材最早出现的位置：优先看 require，没有 require 才退回 raw 类别成员
    private int firstMainIndex(FlexFoodRecipe r, List<Key> ids) {
        if (!r.require().isEmpty()) {
            for (int i = 0; i < ids.size(); i++)
                for (ItemRequirement req : r.require())
                    if (req.item().equals(ids.get(i))) return i;
            return Integer.MAX_VALUE;
        }
        FoodCategoryRegistry cat = FoodCategoryRegistry.instance();
        for (int i = 0; i < ids.size(); i++)
            for (RawRequirement raw : r.raw())
                if (raw.min() > 0 && cat.isInCategory(r.cook(), ids.get(i), raw.category())) return i;
        return Integer.MAX_VALUE;
    }

    // 套用名称与 lore：unpreferred 存在则覆盖 preferred 底味
    private Item buildDish(FlexFoodRecipe r, Map<Key, Integer> counts) {
        Item item = BukkitItemManager.instance().createWrappedItem(r.result(), null);
        if (item == null) return null;

        boolean unprefPresent = anyPresent(r.unpreferred(), counts);

        List<Key> flavor = unprefPresent ? r.unpreferred() : r.preferred();
        List<String> names = new ArrayList<>();
        for (Key k : flavor) if (counts.getOrDefault(k, 0) > 0) names.add(displayName(k));
        if (!names.isEmpty()) {
            String base = item.hoverNameComponent().map(AdventureHelper::componentToMiniMessage).orElse(r.result().value());
            item.customNameJson(AdventureHelper.componentToJson(
                    AdventureHelper.miniMessage().deserialize("<!i>添加了" + String.join("、", names) + "的" + base)));
        }

        List<String> loreLines = resolveLoreLines(r, counts);
        if (!loreLines.isEmpty()) {
            List<String> loreJson = new ArrayList<>();
            for (String line : loreLines)
                loreJson.add(AdventureHelper.componentToJson(AdventureHelper.miniMessage().deserialize("<!i>" + line)));
            item.loreJson(loreJson);
        }
        return item;
    }

    private boolean anyPresent(List<Key> list, Map<Key, Integer> counts) {
        for (Key k : list) if (counts.getOrDefault(k, 0) > 0) return true;
        return false;
    }

    // when 中所有条件（物品达到数量）都满足
    private boolean whenMet(LoreCondition lc, Map<Key, Integer> counts) {
        if (lc.when().isEmpty()) return false;
        for (ItemRequirement c : lc.when()) if (counts.getOrDefault(c.item(), 0) < c.count()) return false;
        return true;
    }

    // 这条 lore 算 unpreferred 底味：显式标记优先，否则看 when 是否含配方 unpreferred 食材
    private boolean isUnprefRule(LoreCondition lc, FlexFoodRecipe r) {
        if (lc.unpreferred() != null) return lc.unpreferred();
        for (ItemRequirement c : lc.when()) if (r.unpreferred().contains(c.item())) return true;
        return false;
    }

    // a 的条件是否覆盖 b：b 的每个物品要求，a 都以 ≥ 的数量要求着
    private boolean covers(LoreCondition a, LoreCondition b) {
        for (ItemRequirement bc : b.when()) {
            int aReq = 0;
            for (ItemRequirement ac : a.when()) if (ac.item().equals(bc.item())) aReq = Math.max(aReq, ac.count());
            if (aReq < bc.count()) return false;
        }
        return true;
    }

    // 解析最终要显示的 lore 行：满足 when + 与当前底味一致 + 不被更具体的同类条件覆盖
    private List<String> resolveLoreLines(FlexFoodRecipe r, Map<Key, Integer> counts) {
        boolean unprefPresent = anyPresent(r.unpreferred(), counts);
        List<LoreCondition> pool = new ArrayList<>();
        for (LoreCondition lc : r.loreConditions()) {
            if (!whenMet(lc, counts)) continue;
            if (isUnprefRule(lc, r) != unprefPresent) continue;
            pool.add(lc);
        }
        List<String> lines = new ArrayList<>();
        for (LoreCondition lc : pool) {
            boolean dominated = false;
            for (LoreCondition other : pool) {
                if (other == lc) continue;
                if (covers(other, lc) && !covers(lc, other)) { dominated = true; break; }
            }
            if (!dominated) lines.addAll(lc.data());
        }
        return lines;
    }

    // 子集是否满足配方的 require 与 raw 门槛
    private boolean matches(FlexFoodRecipe recipe, ApplianceType type, List<Key> subset) {
        if (recipe.cook() != type) return false;
        for (ItemRequirement req : recipe.require()) {
            int have = 0;
            for (Key k : subset) if (k.equals(req.item())) have++;
            if (have < req.count()) return false;
        }
        FoodCategoryRegistry cat = FoodCategoryRegistry.instance();
        for (RawRequirement raw : recipe.raw()) {
            if (raw.min() <= 0) continue;
            if (cat.countInCategory(type, subset, raw.category()) < raw.min()) return false;
        }
        return true;
    }

    // 记录食谱用：在匹配配方里挑要求最具体的那个（raw 门槛多者优先）
    private FlexFoodRecipe findBestSingleMatch(ApplianceType type, List<Key> subset) {
        List<FlexFoodRecipe> candidates = new ArrayList<>();
        for (FlexFoodRecipe recipe : flexRecipes) {
            if (matches(recipe, type, subset)) candidates.add(recipe);
        }
        if (candidates.isEmpty()) return null;
        candidates.sort((a, b) -> {
            int cmp = Integer.compare(b.nonZeroRawCount(), a.nonZeroRawCount());
            return cmp != 0 ? cmp : Integer.compare(b.totalMinCount(), a.totalMinCount());
        });
        return candidates.get(0);
    }

    public Optional<FlexFoodRecipe> findBestFlexRecipe(ApplianceType type, List<Key> ingredientIds) {
        return Optional.ofNullable(findBestSingleMatch(type, ingredientIds));
    }

    private String displayName(Key key) {
        return ItemNames.displayName(key);
    }

    // 按权重随机选一个成品 Key；权重总和归一化（自适应概率）。全 0 权重则退回首个
    private Key pickWeighted(List<WeightedResult> results) {
        if (results.isEmpty()) return null;
        int total = 0;
        for (WeightedResult r : results) if (r.weight() > 0) total += r.weight();
        if (total <= 0) return results.get(0).key();
        int roll = (int) (Math.random() * total);
        for (WeightedResult r : results) {
            if (r.weight() <= 0) continue;
            if (roll < r.weight()) return r.key();
            roll -= r.weight();
        }
        return results.get(results.size() - 1).key();
    }

    public AccurateFoodRecipe findAccurateById(Key id) {
        for (AccurateFoodRecipe r : accurateRecipes) { if (r.id().equals(id)) return r; }
        return null;
    }

    public FlexFoodRecipe findFlexById(Key id) {
        for (FlexFoodRecipe r : flexRecipes) { if (r.id().equals(id)) return r; }
        return null;
    }
}
