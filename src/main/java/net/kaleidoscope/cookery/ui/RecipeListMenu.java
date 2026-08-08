package net.kaleidoscope.cookery.ui;
import net.kaleidoscope.cookery.api.ui.RecipeMenuHooks;

import net.kaleidoscope.cookery.api.ui.MenuButton;
import net.kaleidoscope.cookery.api.ui.MenuScreen;
import net.kaleidoscope.cookery.api.ui.RecipeMenuStyle;

import net.kaleidoscope.cookery.recipe.AccurateFoodRecipe;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.FlexFoodRecipe;
import net.kaleidoscope.cookery.recipe.ChoppingBoardRecipe;
import net.kaleidoscope.cookery.recipe.ChoppingResult;
import net.kaleidoscope.cookery.recipe.TeapotRecipe;
import net.kaleidoscope.cookery.recipe.edit.ChoppingRecipeDraft;
import net.kaleidoscope.cookery.recipe.edit.TeapotRecipeDraft;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.WeightedResult;
import net.kaleidoscope.cookery.recipe.edit.AccurateRecipeDraft;
import net.kaleidoscope.cookery.recipe.edit.FlexRecipeDraft;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.gui.GuiLayout;
import net.momirealms.craftengine.core.plugin.gui.Ingredient;
import net.momirealms.craftengine.core.plugin.gui.ItemWithAction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.libraries.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;

// 某个厨具下的全部配方列表 分页只建当前页的图标 翻页时才建下一页
public final class RecipeListMenu {
    private RecipeListMenu() {
    }

    public static void open(org.bukkit.entity.Player bukkitPlayer, ApplianceType cook, boolean editable) {
        if (RecipeMenuHooks.instance().dispatchList(bukkitPlayer, cook, editable)) {
            return;
        }
        Player viewer = RecipeMenus.adapt(bukkitPlayer);
        if (viewer == null) {
            return;
        }
        // 配方记录本身是轻量 record 快照整表不贵 真正贵的是每条建一个图标物品
        List<AccurateFoodRecipe> accurate = FoodRecipeRegistry.instance().accurateRecipes(cook);
        List<FlexFoodRecipe> flex = FoodRecipeRegistry.instance().flexRecipes(cook);
        // 砧板与茶壶各有自己的配方表 不走 accurate/flex 那两条
        List<ChoppingBoardRecipe> chopping = cook == ApplianceType.CHOPPING_BOARD
                ? FoodRecipeRegistry.instance().choppingRecipes() : List.of();
        List<TeapotRecipe> teapot = cook == ApplianceType.TEAPOT
                ? FoodRecipeRegistry.instance().teapotRecipes() : List.of();
        int total = accurate.size() + flex.size() + chopping.size() + teapot.size();

        GuiLayout layout = new GuiLayout(
                "#########",
                "#XXXXXXX#",
                "#XXXXXXX#",
                "#XXXXXXX#",
                "#XXXXXXX#",
                "R#<###>#N");
        layout.addIngredient('#', Ingredient.simple(MenuIcons.filler(viewer)));
        layout.addIngredient('X', Ingredient.paged());
        layout.addIngredient('<', Ingredient.simple(MenuIcons.previousPage(viewer)));
        layout.addIngredient('>', Ingredient.simple(MenuIcons.nextPage(viewer)));
        layout.addIngredient('R', Ingredient.simple(
                MenuIcons.back(viewer, () -> RecipeMenus.openHome(bukkitPlayer, editable))));
        layout.addIngredient('N', Ingredient.simple(editable
                ? MenuIcons.button(MenuIcons.icon(MenuButton.CREATE, viewer,
                        MenuIcons.text("新建食谱", NamedTextColor.GREEN)),
                () -> RecipeMenus.startCreate(bukkitPlayer, cook))
                : MenuIcons.filler(viewer)));

        LazyPagedGui gui = new LazyPagedGui(layout, RecipeMenus.inventoryGuard(), total,
                (from, count) -> loadPage(bukkitPlayer, viewer, accurate, flex, chopping, teapot, from, count, editable));
        gui.title(RecipeMenuStyle.instance().title(
                        editable ? MenuScreen.LIST_EDIT : MenuScreen.LIST_BROWSE,
                        "appliance", MenuIcons.displayName(cook), "count", String.valueOf(total)))
                .refresh()
                .open(viewer);
    }

    // 全局序号先落在精准段 再落在模糊段 翻页只会命中其中一段的一小片
    private static List<ItemWithAction> loadPage(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                                 List<AccurateFoodRecipe> accurate, List<FlexFoodRecipe> flex,
                                                 List<ChoppingBoardRecipe> chopping, List<TeapotRecipe> teapot,
                                                 int from, int count, boolean editable) {
        List<ItemWithAction> page = new ArrayList<>(count);
        for (int i = from; i < from + count; i++) {
            int idx = i;
            if (idx < accurate.size()) {
                page.add(accurateEntry(bukkitPlayer, viewer, accurate.get(idx), editable));
                continue;
            }
            idx -= accurate.size();
            if (idx < flex.size()) {
                page.add(flexEntry(bukkitPlayer, viewer, flex.get(idx), editable));
                continue;
            }
            idx -= flex.size();
            if (idx < chopping.size()) {
                page.add(choppingEntry(bukkitPlayer, viewer, chopping.get(idx), editable));
                continue;
            }
            page.add(teapotEntry(bukkitPlayer, viewer, teapot.get(idx - chopping.size()), editable));
        }
        return page;
    }

    private static ItemWithAction choppingEntry(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                                ChoppingBoardRecipe recipe, boolean editable) {
        List<Component> lore = new ArrayList<>();
        lore.add(MenuIcons.text("砧板食谱", NamedTextColor.LIGHT_PURPLE));
        if (editable) {
            lore.add(MenuIcons.gray("id " + recipe.id().asString()));
        }
        lore.add(MenuIcons.grayWith("原料 ", recipe.input(), ""));
        lore.add(MenuIcons.gray("需要切 " + recipe.stage() + " 刀"));
        if (recipe.values().isEmpty()) {
            lore.add(MenuIcons.gray("不换模型 直接展示原料"));
        }
        for (ChoppingResult r : recipe.results()) {
            lore.add(MenuIcons.grayWith("成品 ", r.key(), " x" + r.count() + " 权重 " + r.weight()));
        }
        lore.add(MenuIcons.text(editable ? "左键编辑" : "左键查看详情", NamedTextColor.YELLOW));

        Key icon = recipe.results().isEmpty() ? recipe.input() : recipe.results().get(0).key();
        Item item = MenuIcons.icon(icon, viewer,
                MenuIcons.itemName(icon).colorIfAbsent(NamedTextColor.GOLD), lore);
        return new ItemWithAction(item, (element, click) -> {
            click.cancel();
            if (editable) {
                ChoppingEditMenu.open(bukkitPlayer, ChoppingRecipeDraft.editing(recipe));
            } else {
                RecipeDetailMenu.openChopping(bukkitPlayer, recipe,
                        () -> open(bukkitPlayer, ApplianceType.CHOPPING_BOARD, false));
            }
        });
    }

    private static ItemWithAction teapotEntry(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                              TeapotRecipe recipe, boolean editable) {
        List<Component> lore = new ArrayList<>();
        lore.add(MenuIcons.text("茶壶食谱", NamedTextColor.AQUA));
        if (editable) {
            lore.add(MenuIcons.gray("id " + recipe.id().asString()));
        }
        lore.add(MenuIcons.grayWith("液体 ", recipe.fluid(), ""));
        lore.add(MenuIcons.grayWith("原料 ", recipe.input(), " x" + recipe.ingredientCount()));
        lore.add(MenuIcons.grayWith("成品 ", recipe.result(), " x" + recipe.resultCount()));
        lore.add(MenuIcons.gray("熬煮 " + recipe.time() + " tick"));
        lore.add(MenuIcons.text(editable ? "左键编辑" : "左键查看详情", NamedTextColor.YELLOW));

        Item item = MenuIcons.icon(recipe.result(), viewer,
                MenuIcons.itemName(recipe.result()).colorIfAbsent(NamedTextColor.GOLD), lore);
        return new ItemWithAction(item, (element, click) -> {
            click.cancel();
            if (editable) {
                TeapotEditMenu.open(bukkitPlayer, TeapotRecipeDraft.editing(recipe));
            } else {
                RecipeDetailMenu.openTeapot(bukkitPlayer, recipe,
                        () -> open(bukkitPlayer, ApplianceType.TEAPOT, false));
            }
        });
    }

    private static ItemWithAction accurateEntry(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                                AccurateFoodRecipe recipe, boolean editable) {
        List<Component> lore = new ArrayList<>();
        lore.add(MenuIcons.text("精准食谱", NamedTextColor.LIGHT_PURPLE));
        if (editable) {
            lore.add(MenuIcons.gray("id " + recipe.id().asString()));
        }
        lore.add(MenuIcons.grayWith("原料 ", recipe.input(), ""));
        for (WeightedResult result : recipe.results()) {
            lore.add(MenuIcons.grayWith("成品 ", result.key(), " 权重 " + result.weight()));
        }
        if (recipe.resultCount() > 1) {
            lore.add(MenuIcons.gray("每次产出 " + recipe.resultCount() + " 份"));
        }
        if (recipe.cook() == ApplianceType.MILLSTONE && recipe.rotations() > 0) {
            lore.add(MenuIcons.gray("研磨圈数 " + recipe.rotations()));
        }
        lore.add(MenuIcons.text(editable ? "左键编辑" : "左键查看详情", NamedTextColor.YELLOW));

        Item icon = MenuIcons.icon(recipe.primaryResult(), viewer,
                MenuIcons.itemName(recipe.primaryResult()).colorIfAbsent(NamedTextColor.GOLD), lore);
        return new ItemWithAction(icon, (element, click) -> {
            click.cancel();
            if (editable) {
                AccurateEditMenu.open(bukkitPlayer, AccurateRecipeDraft.editing(recipe));
            } else {
                RecipeDetailMenu.openAccurate(bukkitPlayer, recipe,
                        () -> open(bukkitPlayer, recipe.cook(), false));
            }
        });
    }

    private static ItemWithAction flexEntry(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                            FlexFoodRecipe recipe, boolean editable) {
        List<Component> lore = new ArrayList<>();
        lore.add(MenuIcons.text("模糊食谱", NamedTextColor.AQUA));
        if (editable) {
            lore.add(MenuIcons.gray("id " + recipe.id().asString()));
        }
        lore.add(MenuIcons.gray("理想配比"));
        recipe.perfect().forEach((key, weight) -> lore.add(MenuIcons.grayWith("  ", key, " x" + weight)));
        if (!recipe.liquids().isEmpty()) {
            lore.add(MenuIcons.gray("限定汤底 " + recipe.liquids().size() + " 种"));
        }
        lore.add(MenuIcons.text(editable ? "左键编辑" : "左键查看详情", NamedTextColor.YELLOW));

        Item icon = MenuIcons.icon(recipe.result(), viewer,
                MenuIcons.itemName(recipe.result()).colorIfAbsent(NamedTextColor.GOLD), lore);
        return new ItemWithAction(icon, (element, click) -> {
            click.cancel();
            if (editable) {
                FlexEditMenu.open(bukkitPlayer, FlexRecipeDraft.editing(recipe));
            } else {
                RecipeDetailMenu.openFlex(bukkitPlayer, recipe,
                        () -> open(bukkitPlayer, recipe.cook(), false));
            }
        });
    }

}
