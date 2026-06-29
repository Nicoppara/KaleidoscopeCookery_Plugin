package net.kaleidoscope.cookery.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.AdventureHelper;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.AccurateFoodRecipe;
import net.kaleidoscope.cookery.recipe.FlexFoodRecipe;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.FoodRecipeResult;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.item.ItemNames;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

// 菜谱物品工具 读写菜谱 NBT 标记 写入 lore 并把背包食材一键投入炒锅
public final class RecipeUtils {
    private RecipeUtils() {}

    private static final NamespacedKey HAS_RECIPE_KEY =
            new NamespacedKey(KaleidoscopeCookeryPlugin.instance(), "has_recipe");
    public static final NamespacedKey RECIPE_ID_KEY =
            new NamespacedKey(KaleidoscopeCookeryPlugin.instance(), "recipe_id");
    public static final NamespacedKey RECIPE_TYPE_KEY =
            new NamespacedKey(KaleidoscopeCookeryPlugin.instance(), "recipe_type");
    public static final NamespacedKey RECIPE_INGREDIENTS_KEY =
            new NamespacedKey(KaleidoscopeCookeryPlugin.instance(), "recipe_ingredients");

    public static boolean hasRecipe(ItemStack stack) {
        if (stack == null || stack.getItemMeta() == null) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(HAS_RECIPE_KEY, PersistentDataType.BYTE);
    }

    public static boolean tryAutoFill(Player player, ItemStack recipeStack,
                                      Consumer<Item> addIngredient) {
        ItemMeta meta = recipeStack.getItemMeta();
        if (meta == null) {
            return false;
        }

        // 读持久化的实际食材列表 大乱炖食谱也能精准还原当时丢了哪些材料
        String ingStr = meta.getPersistentDataContainer().get(RECIPE_INGREDIENTS_KEY, PersistentDataType.STRING);
        if (ingStr == null || ingStr.isEmpty()) {
            return false;
        }
        List<Key> needed = Arrays.stream(ingStr.split(","))
                .map(Key::of)
                .toList();

        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        boolean any = false;
        for (Key ingredientKey : needed) {
            ItemStack found = findInInventory(player, ingredientKey);
            if (found != null) {
                if (!creative) {
                    found.setAmount(found.getAmount() - 1);
                }
                Item ceItem = BukkitItemManager.instance().createWrappedItem(ingredientKey, null);
                addIngredient.accept(ceItem);
                any = true;
            }
        }
        return any;
    }

    private static ItemStack findInInventory(Player player, Key key) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            Item wrapped = BukkitItemManager.instance().wrap(item);
            if (wrapped.id().equals(key)) {
                return item;
            }
        }
        return null;
    }

    public static void setRecipeItem(ItemStack stack, Key recipeId, String recipeType, List<Key> actualIngredients, Key liquid) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.getPersistentDataContainer().set(HAS_RECIPE_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(RECIPE_ID_KEY, PersistentDataType.STRING, recipeId.asString());
        meta.getPersistentDataContainer().set(RECIPE_TYPE_KEY, PersistentDataType.STRING, recipeType);
        String ingStr = actualIngredients.stream()
                .map(Key::asString)
                .collect(Collectors.joining(","));
        meta.getPersistentDataContainer().set(RECIPE_INGREDIENTS_KEY, PersistentDataType.STRING, ingStr);

        Key resultKey;
        List<Key> ingredients;
        String applianceName;
        ApplianceType applianceType;
        List<Key> recipeLiquids = List.of();

        if ("accurate".equals(recipeType)) {
            AccurateFoodRecipe r = FoodRecipeRegistry.instance().findAccurateById(recipeId);
            resultKey = r.primaryResult();
            ingredients = actualIngredients;
            applianceName = applianceDisplayName(r.cook());
            applianceType = r.cook();
        } else {
            FlexFoodRecipe r = FoodRecipeRegistry.instance().findFlexById(recipeId);
            resultKey = r.result();
            ingredients = actualIngredients;
            applianceName = applianceDisplayName(r.cook());
            applianceType = r.cook();
            recipeLiquids = r.liquids();
        }

        String resultName;
        String outLine;

        if ("flex".equals(recipeType)) {
            Optional<FoodRecipeResult> res = FoodRecipeRegistry.instance()
                    .cookFlex(applianceType, actualIngredients,
                            recipeLiquids.isEmpty() ? null : recipeLiquids.get(0));

            if (res.isPresent()) {
                FoodRecipeResult fr = res.get();
                Item out = fr.item();
                String img = out.id().value();
                StringBuilder outLineBuilder = new StringBuilder("<white><!i>输出: ");
                for (int i = 0; i < Math.max(1, fr.count()); i++) {
                    outLineBuilder.append("<image:kaleidoscopecookery:").append(img).append(">");
                }
                outLine = outLineBuilder.toString();
                resultName = out.hoverNameComponent()
                        .map(AdventureHelper::componentToMiniMessage)
                        .orElse(getDisplayName(out.id()));
            } else {
                outLine = "<white><!i>输出: <image:kaleidoscopecookery:suspicious_stir_fry>";
                resultName = getDisplayName(ItemKeys.SUSPICIOUS_STIR_FRY);
            }
        } else {
            resultName = getDisplayName(resultKey);
            outLine = "<white><!i>输出: <image:kaleidoscopecookery:" + resultKey.value() + ">";
        }

        StringBuilder ingLine = new StringBuilder("<white><!i>原料: ");
        for (Key ing : ingredients) {
            ingLine.append("<image:kaleidoscopecookery:").append(ing.value()).append(">");
        }

        List<Component> lore = new ArrayList<>();
        lore.add(MiniMessage.miniMessage().deserialize(ingLine.toString()));
        lore.add(MiniMessage.miniMessage().deserialize(""));
        lore.add(MiniMessage.miniMessage().deserialize(outLine));
        if (liquid != null && !liquid.asString().equals("minecraft:water")) {
            lore.add(MiniMessage.miniMessage().deserialize(""));
            lore.add(MiniMessage.miniMessage().deserialize("<white><!i>所需汤底：<image:kaleidoscopecookery:" + liquid.value() + ">"));
        }
        lore.add(MiniMessage.miniMessage().deserialize("<gray><!i>右键炒锅/煮锅记录食材，下次可右键使用一键投入"));

        meta.lore(lore);
        meta.displayName(MiniMessage.miniMessage().deserialize(
                "<white><!i>菜谱：" + resultName + "，用于" + applianceName));
        stack.setItemMeta(meta);
    }

    private static String applianceDisplayName(ApplianceType type) {
        return switch (type) {
            case POT -> "炒锅";
            case STOCKPOT -> "煮锅";
            case SHAWARMA -> "沙威玛烤架";
            default -> type.name();
        };
    }

    private static String getDisplayName(Key key) {
        return ItemNames.displayName(key);
    }
}