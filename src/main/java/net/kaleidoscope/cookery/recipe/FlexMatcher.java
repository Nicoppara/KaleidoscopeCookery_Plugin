package net.kaleidoscope.cookery.recipe;

import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.AdventureHelper;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.libraries.adventure.text.format.NamedTextColor;
import net.momirealms.craftengine.libraries.adventure.text.format.TextDecoration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 柔性配方匹配与成品构建 只吃传入的配方快照 不持有存储
public final class FlexMatcher {
    private FlexMatcher() {}

    public record Match(FlexFoodRecipe recipe, DishQuality quality, int portions) {}

    // 必需食材齐全后优先覆盖种类最多的配方 同组再按理想比例取最近邻
    public static Match bestMatch(List<FlexFoodRecipe> recipes, double minScore,
                                  ApplianceType type, List<Key> ingredientIds, Key liquid) {
        if (ingredientIds.isEmpty()) {
            return null;
        }
        // 两个开关组合出四种视图 一次烹饪里锅内食材不变 按组合建一次复用
        View[] views = new View[4];

        FlexFoodRecipe best = null;
        View bestView = null;
        Ideal bestIdeal = null;
        double bestCos = -1;
        int bestSpecificity = -1;
        for (FlexFoodRecipe recipe : recipes) {
            if (recipe.cook() != type || recipe.norm() <= 0) {
                continue;
            }
            if (!recipe.liquids().isEmpty() && (liquid == null || !recipe.liquids().contains(liquid))) {
                continue;
            }
            View view = view(views, ingredientIds, recipe);
            if (view == null) {
                continue;
            }
            Ideal ideal = ideal(recipe, view);
            if (ideal.norm() <= 0 || !hasAllRequired(view, ideal)) {
                continue;
            }
            double cos = cosine(view, ideal);
            int specificity = ideal.weights().size();
            // 同分时按注册顺序取先者 配置顺序就是最终优先级
            if (specificity > bestSpecificity || specificity == bestSpecificity && cos > bestCos) {
                bestSpecificity = specificity;
                bestCos = cos;
                best = recipe;
                bestView = view;
                bestIdeal = ideal;
            }
        }
        if (best == null || bestCos < minScore) {
            return null;
        }

        int portions = portions(bestView, bestIdeal);
        int deviation = requiredDeviation(bestView, bestIdeal, portions) + extraIngredientCount(bestView, bestIdeal);
        return new Match(best, DishQuality.fromDeviation(deviation), portions);
    }

    // 调味品已剔除 等效开启时键已归一成标签
    private record View(Map<Key, Integer> counts, double norm, boolean canonical) {}

    // 与 View 同一套键下的理想配比 等效关闭时就是 perfect 本身
    private record Ideal(Map<Key, Integer> weights, double norm) {}

    private static View view(View[] cache, List<Key> ingredientIds, FlexFoodRecipe recipe) {
        FoodGroups groups = FoodGroups.instance();
        boolean equivalent = recipe.useEquivalentFoods() && groups.hasEquivalents();
        boolean seasoning = recipe.useSeasonings() && groups.hasSeasonings();
        int index = (equivalent ? 1 : 0) | (seasoning ? 2 : 0);
        if (cache[index] != null) {
            return cache[index];
        }
        Map<Key, Integer> counts = new HashMap<>();
        for (Key ingredient : ingredientIds) {
            if (seasoning && groups.isSeasoning(ingredient)) {
                continue;
            }
            counts.merge(equivalent ? groups.canonical(ingredient) : ingredient, 1, Integer::sum);
        }
        double norm = norm(counts.values());
        // 一锅全是调味品 这条视图下没有有效食材 用它的配方一律不成立
        View built = norm <= 0 ? null : new View(counts, norm, equivalent);
        cache[index] = built;
        return built;
    }

    // 同一等效组里的两种必需食材会被归并成一项 权重相加 范数必须跟着重算
    private static Ideal ideal(FlexFoodRecipe recipe, View view) {
        if (!view.canonical()) {
            return new Ideal(recipe.perfect(), recipe.norm());
        }
        FoodGroups groups = FoodGroups.instance();
        Map<Key, Integer> weights = new HashMap<>(recipe.perfect().size());
        for (Map.Entry<Key, Integer> e : recipe.perfect().entrySet()) {
            weights.merge(groups.canonical(e.getKey()), e.getValue(), Integer::sum);
        }
        return new Ideal(weights, norm(weights.values()));
    }

    private static double norm(Iterable<Integer> values) {
        double square = 0;
        for (int value : values) {
            square += (double) value * value;
        }
        return Math.sqrt(square);
    }

    private static boolean hasAllRequired(View view, Ideal ideal) {
        for (Key ingredient : ideal.weights().keySet()) {
            if (!view.counts().containsKey(ingredient)) {
                return false;
            }
        }
        return true;
    }

    private static double cosine(View view, Ideal ideal) {
        double dot = 0;
        for (Map.Entry<Key, Integer> e : ideal.weights().entrySet()) {
            dot += (double) e.getValue() * view.counts().get(e.getKey());
        }
        return dot / (view.norm() * ideal.norm());
    }

    private static int portions(View view, Ideal ideal) {
        int portions = Integer.MAX_VALUE;
        for (Map.Entry<Key, Integer> e : ideal.weights().entrySet()) {
            portions = Math.min(portions, view.counts().get(e.getKey()) / e.getValue());
        }
        return Math.max(1, portions);
    }

    private static int requiredDeviation(View view, Ideal ideal, int portions) {
        int deviation = 0;
        for (Map.Entry<Key, Integer> e : ideal.weights().entrySet()) {
            deviation += Math.abs(view.counts().get(e.getKey()) - e.getValue() * portions);
        }
        return deviation;
    }

    private static int extraIngredientCount(View view, Ideal ideal) {
        int extras = 0;
        for (Map.Entry<Key, Integer> e : view.counts().entrySet()) {
            if (!ideal.weights().containsKey(e.getKey())) {
                extras += e.getValue();
            }
        }
        return extras;
    }

    // 品质写进成品 改名字颜色 挂一行档位 lore 并按倍率缩放食物属性
    public static Item buildDish(Match match) {
        Item item = InventoryUtils.createOrEmpty(match.recipe().result());
        if (ItemUtils.isEmpty(item)) {
            return null;
        }
        DishQuality quality = match.quality();
        Component base = item.hoverNameComponent()
                .orElseGet(() -> Component.translatable(itemTranslationKey(match.recipe().result())));
        Component name = base.colorIfAbsent(NamedTextColor.NAMES.value(quality.color()))
                .decoration(TextDecoration.ITALIC, false);
        item.customNameJson(AdventureHelper.componentToJson(name));

        Component lore = Component.translatable(quality.translationKey())
                .color(NamedTextColor.NAMES.value(quality.color()))
                .decoration(TextDecoration.ITALIC, false);
        item.loreJson(List.of(AdventureHelper.componentToJson(lore)));

        return DishFoodScaler.scale(item, quality.ratio());
    }

    private static String itemTranslationKey(Key key) {
        return "item." + key.namespace() + "." + key.value();
    }
}
