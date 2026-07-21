package net.kaleidoscope.cookery.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.AdventureHelper;
import net.momirealms.craftengine.core.util.ItemUtils;
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
import java.util.function.Predicate;
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
    private static final String TEXT_INGREDIENTS = "tooltip.kaleidoscopecookery.recipe_item.ingredient";
    private static final String TEXT_OUTPUT = "tooltip.kaleidoscopecookery.recipe_item.output";
    private static final String TEXT_REQUIRED_LIQUID = "tooltip.kaleidoscopecookery.recipe_item.required_liquid";
    private static final String TEXT_USE = "tooltip.kaleidoscopecookery.recipe_item.use";
    private static final String TEXT_TITLE = "item.kaleidoscopecookery.recipe_item.title";

    public static boolean hasRecipe(ItemStack stack) {
        if (stack == null || stack.getItemMeta() == null) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(HAS_RECIPE_KEY, PersistentDataType.BYTE);
    }

    // 炒锅与高汤锅的食材上限都是 8 食谱记录再长也没意义 上限同时防伪造 PDC 撑爆循环
    private static final int MAX_AUTO_FILL_INGREDIENTS = 8;

    public static boolean tryAutoFill(Player player, ItemStack recipeStack,
                                      Predicate<Item> addIngredient) {
        ItemMeta meta = recipeStack.getItemMeta();
        if (meta == null) {
            return false;
        }

        // 读持久化的实际食材列表 大乱炖食谱也能精准还原当时丢了哪些材料
        String ingStr = meta.getPersistentDataContainer().get(RECIPE_INGREDIENTS_KEY, PersistentDataType.STRING);
        if (ingStr == null || ingStr.isEmpty()) {
            return false;
        }
        // PDC 可被玩家伪造 必须限长并容忍非法段 否则一次右键能触发几十万次背包扫描
        List<Key> needed = new ArrayList<>();
        for (String segment : ingStr.split(",")) {
            if (needed.size() >= MAX_AUTO_FILL_INGREDIENTS) {
                break;
            }
            if (segment.isBlank()) {
                continue;
            }
            try {
                needed.add(Key.of(segment));
            } catch (Exception ignored) {
                // 伪造的段直接跳过 别让整次交互抛异常
            }
        }

        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        boolean any = false;
        for (Key ingredientKey : needed) {
            ItemStack found = findInInventory(player, ingredientKey);
            if (found == null) {
                continue;
            }
            // 先建物品再扣材料 食谱记录的 id 因资源包改动失效时不能把材料吞掉
            Item ceItem = InventoryUtils.createOrEmpty(ingredientKey);
            if (ItemUtils.isEmpty(ceItem)) {
                continue;
            }
            // 厨具可能拒收(满了/状态不对/不是该厨具的食材) 收下了才扣材料
            if (!addIngredient.test(ceItem)) {
                continue;
            }
            if (!creative) {
                found.setAmount(found.getAmount() - 1);
            }
            any = true;
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
        Component applianceName;
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

        Component resultName;
        Component outLine;

        if ("flex".equals(recipeType)) {
            Optional<FoodRecipeResult> res = FoodRecipeRegistry.instance()
                    .cookFlex(applianceType, actualIngredients,
                            recipeLiquids.isEmpty() ? null : recipeLiquids.get(0));

            if (res.isPresent()) {
                FoodRecipeResult fr = res.get();
                Item out = fr.item();
                outLine = label(TEXT_OUTPUT).append(Component.space()).append(images(out.id(), Math.max(1, fr.count())));
                resultName = displayName(out);
            } else {
                outLine = label(TEXT_OUTPUT).append(Component.space()).append(image(ItemKeys.SUSPICIOUS_STIR_FRY));
                resultName = displayName(ItemKeys.SUSPICIOUS_STIR_FRY);
            }
        } else {
            resultName = displayName(resultKey);
            outLine = label(TEXT_OUTPUT).append(Component.space()).append(image(resultKey));
        }

        List<Component> lore = new ArrayList<>();
        lore.add(label(TEXT_INGREDIENTS).append(Component.space()).append(images(ingredients)));
        lore.add(Component.empty());
        lore.add(outLine);
        if (liquid != null && !liquid.asString().equals("minecraft:water")) {
            lore.add(Component.empty());
            lore.add(label(TEXT_REQUIRED_LIQUID).append(Component.space()).append(image(liquid)));
        }
        lore.add(Component.translatable(TEXT_USE).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        meta.displayName(Component.translatable(TEXT_TITLE, NamedTextColor.WHITE, resultName, applianceName)
                .decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
    }

    private static Component applianceDisplayName(ApplianceType type) {
        return switch (type) {
            case POT -> Component.translatable("item.kaleidoscopecookery.pot");
            case STOCKPOT -> Component.translatable("item.kaleidoscopecookery.stockpot");
            case SHAWARMA -> Component.translatable("item.kaleidoscopecookery.shawarma_spit");
            default -> Component.text(type.name());
        };
    }

    private static Component label(String key) {
        return Component.translatable(key).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);
    }

    private static Component images(List<Key> keys) {
        Component component = Component.empty();
        for (Key key : keys) {
            component = component.append(image(key));
        }
        return component;
    }

    private static Component images(Key key, int count) {
        Component component = Component.empty();
        for (int i = 0; i < count; i++) {
            component = component.append(image(key));
        }
        return component;
    }

    private static Component image(Key key) {
        return MiniMessage.miniMessage().deserialize("<image:kaleidoscopecookery:" + key.value() + ">");
    }

    private static Component displayName(Item item) {
        return item.hoverNameComponent()
                .map(AdventureHelper::componentToMiniMessage)
                .map(MiniMessage.miniMessage()::deserialize)
                .orElseGet(() -> displayName(item.id()));
    }

    private static Component displayName(Key key) {
        String displayName = ItemNames.displayName(key);
        if (!displayName.equals(key.value())) {
            return MiniMessage.miniMessage().deserialize(displayName);
        }
        return Component.translatable("item." + key.namespace() + "." + key.value());
    }
}
