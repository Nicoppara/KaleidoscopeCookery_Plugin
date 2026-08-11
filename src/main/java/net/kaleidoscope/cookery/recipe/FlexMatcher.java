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

    // 一次匹配的结果 配方 品质 份数
    public record Match(FlexFoodRecipe recipe, DishQuality quality, int portions) {}

    // 必需食材齐全后优先覆盖种类最多的配方 同组再按理想比例取最近邻
    public static Match bestMatch(List<FlexFoodRecipe> recipes, double minScore,
                                  ApplianceType type, List<Key> ingredientIds, Key liquid) {
        if (ingredientIds == null || ingredientIds.isEmpty()) {
            return null;
        }
        Map<Key, Integer> counts = new HashMap<>();
        for (Key ingredient : ingredientIds) {
            counts.merge(ingredient, 1, Integer::sum);
        }
        double actualNorm = actualNorm(counts);
        if (actualNorm <= 0) {
            return null;
        }

        FlexFoodRecipe best = null;
        double bestCos = -1;
        int bestSpecificity = -1;
        for (FlexFoodRecipe recipe : recipes) {
            if (!eligible(recipe, type, liquid, counts)) {
                continue;
            }
            double cos = cosine(recipe, counts, actualNorm);
            int specificity = recipe.perfect().size();
            // 同分时按注册顺序取先者 配置顺序就是最终优先级
            if (specificity > bestSpecificity || specificity == bestSpecificity && cos > bestCos) {
                bestSpecificity = specificity;
                bestCos = cos;
                best = recipe;
            }
        }
        if (best == null || bestCos < minScore) {
            return null;
        }

        int portions = portions(best, counts);
        int deviation = requiredDeviation(best, counts, portions) + extraIngredientCount(best, counts);
        return new Match(best, DishQuality.fromDeviation(deviation), portions);
    }

    private static double actualNorm(Map<Key, Integer> counts) {
        double square = 0;
        for (int count : counts.values()) {
            square += (double) count * count;
        }
        return Math.sqrt(square);
    }

    private static boolean eligible(FlexFoodRecipe recipe, ApplianceType type, Key liquid,
                                    Map<Key, Integer> counts) {
        if (recipe.cook() != type || recipe.norm() <= 0) {
            return false;
        }
        if (!recipe.liquids().isEmpty() && (liquid == null || !recipe.liquids().contains(liquid))) {
            return false;
        }
        for (Key ingredient : recipe.perfect().keySet()) {
            if (!counts.containsKey(ingredient)) {
                return false;
            }
        }
        return true;
    }

    private static double cosine(FlexFoodRecipe recipe, Map<Key, Integer> counts, double actualNorm) {
        double dot = 0;
        for (Map.Entry<Key, Integer> e : recipe.perfect().entrySet()) {
            dot += (double) e.getValue() * counts.get(e.getKey());
        }
        return dot / (actualNorm * recipe.norm());
    }

    private static int portions(FlexFoodRecipe recipe, Map<Key, Integer> counts) {
        int portions = Integer.MAX_VALUE;
        for (Map.Entry<Key, Integer> e : recipe.perfect().entrySet()) {
            portions = Math.min(portions, counts.get(e.getKey()) / e.getValue());
        }
        return Math.max(1, portions);
    }

    private static int requiredDeviation(FlexFoodRecipe recipe, Map<Key, Integer> counts, int portions) {
        int deviation = 0;
        for (Map.Entry<Key, Integer> e : recipe.perfect().entrySet()) {
            deviation += Math.abs(counts.get(e.getKey()) - e.getValue() * portions);
        }
        return deviation;
    }

    private static int extraIngredientCount(FlexFoodRecipe recipe, Map<Key, Integer> counts) {
        int extras = 0;
        for (Map.Entry<Key, Integer> e : counts.entrySet()) {
            if (!recipe.perfect().containsKey(e.getKey())) {
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
