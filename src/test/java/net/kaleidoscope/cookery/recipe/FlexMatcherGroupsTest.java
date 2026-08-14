package net.kaleidoscope.cookery.recipe;

import net.kaleidoscope.cookery.api.ItemTags;
import net.momirealms.craftengine.core.util.Key;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

// 等效食物表与调味品表对模糊匹配的影响
class FlexMatcherGroupsTest {

    private static final Key RED_MEAT = Key.of("kaleidoscopecookery:test_red_meat");
    private static final Key SEASONING = Key.of("kaleidoscopecookery:test_seasoning");
    private static final Key BEEF = Key.of("minecraft:beef");
    private static final Key PORK = Key.of("minecraft:porkchop");
    private static final Key POTATO = Key.of("minecraft:potato");
    private static final Key SUGAR = Key.of("minecraft:sugar");
    private static final Key CARROT = Key.of("minecraft:carrot");

    @BeforeEach
    void setUp() {
        ItemTags.instance().register(RED_MEAT, List.of(BEEF.asString(), PORK.asString()));
        ItemTags.instance().register(SEASONING, List.of(SUGAR.asString()));
        FoodGroups.instance().equivalentTags(List.of(RED_MEAT));
        FoodGroups.instance().seasoningTags(List.of(SEASONING));
    }

    @AfterEach
    void tearDown() {
        FoodGroups.instance().equivalentTags(List.of());
        FoodGroups.instance().seasoningTags(List.of());
        ItemTags.instance().remove(RED_MEAT);
        ItemTags.instance().remove(SEASONING);
    }

    private static FlexFoodRecipe beefDish(boolean equivalent, boolean seasoning) {
        return FlexFoodRecipe.of(Key.of("test:beef_dish"), Key.of("test:result"), ApplianceType.POT,
                Map.of(BEEF, 1, POTATO, 1), List.of(), null, equivalent, seasoning);
    }

    private static FlexMatcher.Match match(FlexFoodRecipe recipe, List<Key> ingredients) {
        return FlexMatcher.bestMatch(List.of(recipe), 0.0, ApplianceType.POT, ingredients, null);
    }

    @Test
    void equivalentIngredientSatisfiesRequirement() {
        FlexMatcher.Match match = match(beefDish(true, true), List.of(PORK, POTATO));
        assertNotNull(match);
        assertEquals(1, match.portions());
        assertEquals(DishQuality.fromDeviation(0), match.quality());
    }

    @Test
    void equivalentOffKeepsStrictMatching() {
        assertNull(match(beefDish(false, true), List.of(PORK, POTATO)));
    }

    @Test
    void mixedEquivalentMembersCountTogether() {
        // 牛肉加猪肉归一成同一项 等于两份红肉 配上两份土豆正好两份菜
        FlexMatcher.Match match = match(beefDish(true, true), List.of(BEEF, PORK, POTATO, POTATO));
        assertNotNull(match);
        assertEquals(2, match.portions());
        assertEquals(DishQuality.fromDeviation(0), match.quality());
    }

    @Test
    void seasoningDoesNotHurtQuality() {
        FlexMatcher.Match clean = match(beefDish(true, true), List.of(BEEF, POTATO));
        FlexMatcher.Match seasoned = match(beefDish(true, true), List.of(BEEF, POTATO, SUGAR));
        assertNotNull(seasoned);
        assertEquals(clean.quality(), seasoned.quality());
        assertEquals(clean.portions(), seasoned.portions());
    }

    @Test
    void seasoningOffCountsAsExtraIngredient() {
        FlexMatcher.Match seasoned = match(beefDish(true, false), List.of(BEEF, POTATO, SUGAR));
        assertNotNull(seasoned);
        assertEquals(DishQuality.fromDeviation(1), seasoned.quality());
    }

    @Test
    void nonSeasoningExtraStillHurtsQuality() {
        FlexMatcher.Match dirty = match(beefDish(true, true), List.of(BEEF, POTATO, CARROT));
        assertNotNull(dirty);
        assertEquals(DishQuality.fromDeviation(1), dirty.quality());
    }

    @Test
    void allSeasoningsPotMatchesNothing() {
        assertNull(match(beefDish(true, true), List.of(SUGAR, SUGAR)));
    }

    // 白名单从 perfect 反推 等效替身与调味品都反推不到 必须靠 isAllowed 兜底放行
    @Test
    void equivalentAndSeasoningPassWhitelist() {
        ApplianceFoodRegistry registry = ApplianceFoodRegistry.instance();
        registry.clear(ApplianceType.POT);
        registry.register(ApplianceType.POT, BEEF);

        assertTrue(registry.isAllowed(ApplianceType.POT, BEEF));
        assertTrue(registry.isAllowed(ApplianceType.POT, PORK));
        assertTrue(registry.isAllowed(ApplianceType.POT, SUGAR));
        assertFalse(registry.isAllowed(ApplianceType.POT, CARROT));
        // 精确配方的厨具不吃这两张表
        assertFalse(registry.isAllowed(ApplianceType.STEAMER, PORK));

        registry.clear(ApplianceType.POT);
    }

    @Test
    void requiredIngredientsInSameGroupMergeWeights() {
        // 牛肉猪肉各要一份 归一后是红肉要两份 两块红肉就该正好命中
        FlexFoodRecipe recipe = FlexFoodRecipe.of(Key.of("test:double_meat"), Key.of("test:result"),
                ApplianceType.POT, Map.of(BEEF, 1, PORK, 1), List.of(), null, true, true);
        FlexMatcher.Match match = match(recipe, List.of(BEEF, PORK));
        assertNotNull(match);
        assertEquals(1, match.portions());
        assertEquals(DishQuality.fromDeviation(0), match.quality());
    }
}
