package net.kaleidoscope.cookery.block.behavior;

import net.momirealms.craftengine.bukkit.entity.furniture.element.ItemDisplayFurnitureElementConfig;
import net.momirealms.craftengine.bukkit.entity.furniture.element.ItemDisplayFurnitureElement;
import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.core.entity.furniture.FurnitureDefinition;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureBehaviorFactory;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureBehaviorTemplate;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureController;
import net.momirealms.craftengine.core.entity.furniture.element.FurnitureElement;
import net.momirealms.craftengine.core.entity.furniture.element.FurnitureElementConfig;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.item.ItemMatch;
import net.kaleidoscope.cookery.recipe.AccurateFoodRecipe;
import net.kaleidoscope.cookery.recipe.FlexFoodRecipe;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.util.RecipeUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class RecipeDisplayBehavior extends FurnitureBehaviorTemplate {

    public RecipeDisplayBehavior(FurnitureDefinition furniture) {
        super(furniture);
    }

    public static final FurnitureBehaviorFactory<RecipeDisplayBehavior> FACTORY = new Factory();

    @Override
    public FurnitureController createController(Furniture furniture) {
        return new RecipeDisplayController(furniture);
    }

    // 配方展示控制器 根据承载物品决定是否生成展示用的成品 ItemDisplay 元素
    public static class RecipeDisplayController extends FurnitureController {
        public RecipeDisplayController(Furniture furniture) {
            super(furniture);
        }

        @Override
        public void gatherElements(Consumer<FurnitureElement> consumer) {
            Item sourceItem = furniture.persistentData.item().orElse(null);
            if (sourceItem == null) {
                return;
            }
            if (!ItemMatch.is(sourceItem, ItemKeys.RECIPE_ITEM_HAS_RECIPE)) {
                return;
            }

            Key resultKey = resolveResultKey(sourceItem);
            if (resultKey == null) {
                return;
            }

            consumer.accept(buildDisplayElement(resultKey));
        }

        // 从 sourceItem 的 PDC 读取配方并定位成品
        private Key resolveResultKey(Item sourceItem) {
            ItemStack bukkitStack = (ItemStack) sourceItem.platformItem();
            ItemMeta meta = bukkitStack.getItemMeta();
            if (meta == null) {
                return null;
            }

            String recipeIdStr = meta.getPersistentDataContainer().get(RecipeUtils.RECIPE_ID_KEY, PersistentDataType.STRING);
            String recipeType = meta.getPersistentDataContainer().get(RecipeUtils.RECIPE_TYPE_KEY, PersistentDataType.STRING);
            if (recipeIdStr == null) {
                return null;
            }

            Key recipeId = Key.of(recipeIdStr);
            if ("accurate".equals(recipeType)) {
                AccurateFoodRecipe r = FoodRecipeRegistry.instance().findAccurateById(recipeId);
                if (r == null) {
                    return null;
                }
                return r.primaryResult();
            } else {
                FlexFoodRecipe r = FoodRecipeRegistry.instance().findFlexById(recipeId);
                if (r == null) {
                    return null;
                }
                return r.result();
            }
        }

        // 在家具表面叠加成品 ItemDisplay
        private FurnitureElement buildDisplayElement(Key resultKey) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("item", resultKey.asString());
            map.put("scale", List.of(0.4f, 0.4f, 0.4f));
            map.put("position", List.of(0f, 0.18f, 0.019f));
            map.put("rotation", List.of(0f, 0f, 0f, 1f));
            map.put("display_context", "FIXED");

            ConfigSection section = ConfigSection.ofRoot(map);
            FurnitureElementConfig<ItemDisplayFurnitureElement> config =
                    ItemDisplayFurnitureElementConfig.FACTORY.create(section);
            return config.create(furniture);
        }
    }

    private static class Factory implements FurnitureBehaviorFactory<RecipeDisplayBehavior> {
        @Override
        public RecipeDisplayBehavior create(FurnitureDefinition furniture, ConfigSection section) {
            return new RecipeDisplayBehavior(furniture);
        }
    }
}
