package net.kaleidoscope.cookery.ui;

import java.util.ArrayList;
import java.util.List;
import net.kaleidoscope.cookery.api.ui.MenuButton;
import net.kaleidoscope.cookery.api.ui.MenuScreen;
import net.kaleidoscope.cookery.api.ui.RecipeMenuHooks;
import net.kaleidoscope.cookery.api.ui.RecipeMenuStyle;
import net.kaleidoscope.cookery.recipe.AccurateFoodRecipe;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.ChoppingBoardRecipe;
import net.kaleidoscope.cookery.recipe.ChoppingResult;
import net.kaleidoscope.cookery.recipe.FlexFoodRecipe;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.TeapotRecipe;
import net.kaleidoscope.cookery.recipe.WeightedResult;
import net.kaleidoscope.cookery.recipe.edit.AccurateRecipeDraft;
import net.kaleidoscope.cookery.recipe.edit.ChoppingRecipeDraft;
import net.kaleidoscope.cookery.recipe.edit.FlexRecipeDraft;
import net.kaleidoscope.cookery.recipe.edit.RecipeSourceIndex;
import net.kaleidoscope.cookery.recipe.edit.TeapotRecipeDraft;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.gui.GuiLayout;
import net.momirealms.craftengine.core.plugin.gui.Ingredient;
import net.momirealms.craftengine.core.plugin.gui.ItemWithAction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.libraries.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public final class RecipeListMenu {
   private RecipeListMenu() {
   }

   public static void open(Player bukkitPlayer, ApplianceType cook, boolean editable) {
      if (!RecipeMenuHooks.instance().dispatchList(bukkitPlayer, cook, editable)) {
         net.momirealms.craftengine.core.entity.player.Player viewer = RecipeMenus.adapt(bukkitPlayer);
         if (viewer != null) {
            List<AccurateFoodRecipe> accurate = editable
               ? FoodRecipeRegistry.instance().menuAccurateRecipes(cook)
               : FoodRecipeRegistry.instance().accurateRecipes(cook);
            List<FlexFoodRecipe> flex = editable ? FoodRecipeRegistry.instance().menuFlexRecipes(cook) : FoodRecipeRegistry.instance().flexRecipes(cook);
            List<ChoppingBoardRecipe> chopping = cook == ApplianceType.CHOPPING_BOARD
               ? (editable ? FoodRecipeRegistry.instance().menuChoppingRecipes() : FoodRecipeRegistry.instance().choppingRecipes())
               : List.of();
            List<TeapotRecipe> teapot = cook == ApplianceType.TEAPOT
               ? (editable ? FoodRecipeRegistry.instance().menuTeapotRecipes() : FoodRecipeRegistry.instance().teapotRecipes())
               : List.of();
            int total = accurate.size() + flex.size() + chopping.size() + teapot.size();
            GuiLayout layout = new GuiLayout(new String[]{"#########", "#XXXXXXX#", "#XXXXXXX#", "#XXXXXXX#", "#XXXXXXX#", "R#<###>#N"});
            layout.addIngredient('#', Ingredient.simple(MenuIcons.filler(viewer)));
            layout.addIngredient('X', Ingredient.paged());
            layout.addIngredient('<', Ingredient.simple(MenuIcons.previousPage(viewer)));
            layout.addIngredient('>', Ingredient.simple(MenuIcons.nextPage(viewer)));
            layout.addIngredient('R', Ingredient.simple(MenuIcons.back(viewer, () -> RecipeMenus.openHome(bukkitPlayer, editable))));
            layout.addIngredient(
               'N',
               Ingredient.simple(
                  editable
                     ? MenuIcons.button(
                        MenuIcons.icon(MenuButton.CREATE, viewer, MenuIcons.text("新建食谱", NamedTextColor.GREEN)),
                        () -> RecipeMenus.startCreate(bukkitPlayer, cook)
                     )
                     : MenuIcons.filler(viewer)
               )
            );
            LazyPagedGui gui = new LazyPagedGui(
               layout,
               RecipeMenus.inventoryGuard(),
               total,
               (from, count) -> loadPage(bukkitPlayer, viewer, accurate, flex, chopping, teapot, from, count, editable)
            );
            gui.title(
                  RecipeMenuStyle.instance()
                     .title(editable ? MenuScreen.LIST_EDIT : MenuScreen.LIST_BROWSE, "appliance", MenuIcons.displayName(cook), "count", String.valueOf(total))
               )
               .refresh()
               .open(viewer);
         }
      }
   }

   private static List<ItemWithAction> loadPage(
      Player bukkitPlayer,
      net.momirealms.craftengine.core.entity.player.Player viewer,
      List<AccurateFoodRecipe> accurate,
      List<FlexFoodRecipe> flex,
      List<ChoppingBoardRecipe> chopping,
      List<TeapotRecipe> teapot,
      int from,
      int count,
      boolean editable
   ) {
      List<ItemWithAction> page = new ArrayList<>(count);

      for (int i = from; i < from + count; i++) {
         int idx = i;
         if (idx < accurate.size()) {
            page.add(accurateEntry(bukkitPlayer, viewer, accurate.get(idx), editable));
         } else {
            idx -= accurate.size();
            if (idx < flex.size()) {
               page.add(flexEntry(bukkitPlayer, viewer, flex.get(idx), editable));
            } else {
               idx -= flex.size();
               if (idx < chopping.size()) {
                  page.add(choppingEntry(bukkitPlayer, viewer, chopping.get(idx), editable));
               } else {
                  page.add(teapotEntry(bukkitPlayer, viewer, teapot.get(idx - chopping.size()), editable));
               }
            }
         }
      }

      return page;
   }

   private static ItemWithAction choppingEntry(
      Player bukkitPlayer, net.momirealms.craftengine.core.entity.player.Player viewer, ChoppingBoardRecipe recipe, boolean editable
   ) {
      List<Component> lore = new ArrayList<>();
      lore.add(MenuIcons.text("砧板食谱", NamedTextColor.LIGHT_PURPLE));
      if (editable) {
         lore.add(MenuIcons.gray("id " + recipe.id().asString()));
         addDuplicateLore(lore, recipe);
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
      Item item = MenuIcons.icon(icon, viewer, MenuIcons.itemName(icon).colorIfAbsent(NamedTextColor.GOLD), lore);
      return new ItemWithAction(item, (element, click) -> {
         click.cancel();
         if (editable) {
            ChoppingEditMenu.open(bukkitPlayer, ChoppingRecipeDraft.editing(recipe));
         } else {
            RecipeDetailMenu.openChopping(bukkitPlayer, recipe, () -> open(bukkitPlayer, ApplianceType.CHOPPING_BOARD, false));
         }
      });
   }

   private static ItemWithAction teapotEntry(
      Player bukkitPlayer, net.momirealms.craftengine.core.entity.player.Player viewer, TeapotRecipe recipe, boolean editable
   ) {
      List<Component> lore = new ArrayList<>();
      lore.add(MenuIcons.text("茶壶食谱", NamedTextColor.AQUA));
      if (editable) {
         lore.add(MenuIcons.gray("id " + recipe.id().asString()));
         addDuplicateLore(lore, recipe);
      }

      lore.add(MenuIcons.grayWith("液体 ", recipe.fluid(), ""));
      lore.add(MenuIcons.grayWith("原料 ", recipe.input(), " x" + recipe.ingredientCount()));
      lore.add(MenuIcons.grayWith("成品 ", recipe.result(), " x" + recipe.resultCount()));
      lore.add(MenuIcons.gray("熬煮 " + recipe.time() + " tick"));
      lore.add(MenuIcons.text(editable ? "左键编辑" : "左键查看详情", NamedTextColor.YELLOW));
      Item item = MenuIcons.icon(recipe.result(), viewer, MenuIcons.itemName(recipe.result()).colorIfAbsent(NamedTextColor.GOLD), lore);
      return new ItemWithAction(item, (element, click) -> {
         click.cancel();
         if (editable) {
            TeapotEditMenu.open(bukkitPlayer, TeapotRecipeDraft.editing(recipe));
         } else {
            RecipeDetailMenu.openTeapot(bukkitPlayer, recipe, () -> open(bukkitPlayer, ApplianceType.TEAPOT, false));
         }
      });
   }

   private static ItemWithAction accurateEntry(
      Player bukkitPlayer, net.momirealms.craftengine.core.entity.player.Player viewer, AccurateFoodRecipe recipe, boolean editable
   ) {
      List<Component> lore = new ArrayList<>();
      lore.add(MenuIcons.text("精准食谱", NamedTextColor.LIGHT_PURPLE));
      if (editable) {
         lore.add(MenuIcons.gray("id " + recipe.id().asString()));
         addDuplicateLore(lore, recipe);
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
      Item icon = MenuIcons.icon(recipe.primaryResult(), viewer, MenuIcons.itemName(recipe.primaryResult()).colorIfAbsent(NamedTextColor.GOLD), lore);
      return new ItemWithAction(icon, (element, click) -> {
         click.cancel();
         if (editable) {
            AccurateEditMenu.open(bukkitPlayer, AccurateRecipeDraft.editing(recipe));
         } else {
            RecipeDetailMenu.openAccurate(bukkitPlayer, recipe, () -> open(bukkitPlayer, recipe.cook(), false));
         }
      });
   }

   private static ItemWithAction flexEntry(
      Player bukkitPlayer, net.momirealms.craftengine.core.entity.player.Player viewer, FlexFoodRecipe recipe, boolean editable
   ) {
      List<Component> lore = new ArrayList<>();
      lore.add(MenuIcons.text("模糊食谱", NamedTextColor.AQUA));
      if (editable) {
         lore.add(MenuIcons.gray("id " + recipe.id().asString()));
         addDuplicateLore(lore, recipe);
      }

      lore.add(MenuIcons.gray("理想配比"));
      recipe.perfect().forEach((key, weight) -> lore.add(MenuIcons.grayWith("  ", key, " x" + weight)));
      if (!recipe.liquids().isEmpty()) {
         lore.add(MenuIcons.gray("限定汤底 " + recipe.liquids().size() + " 种"));
      }

      lore.add(MenuIcons.text(editable ? "左键编辑" : "左键查看详情", NamedTextColor.YELLOW));
      Item icon = MenuIcons.icon(recipe.result(), viewer, MenuIcons.itemName(recipe.result()).colorIfAbsent(NamedTextColor.GOLD), lore);
      return new ItemWithAction(icon, (element, click) -> {
         click.cancel();
         if (editable) {
            FlexEditMenu.open(bukkitPlayer, FlexRecipeDraft.editing(recipe));
         } else {
            RecipeDetailMenu.openFlex(bukkitPlayer, recipe, () -> open(bukkitPlayer, recipe.cook(), false));
         }
      });
   }

   private static void addDuplicateLore(List<Component> lore, Object recipe) {
      RecipeSourceIndex index = RecipeSourceIndex.instance();
      if (index.isDuplicate(recipe)) {
         lore.add(MenuIcons.text("重复 ID：运行时未加载", NamedTextColor.RED));
      }
   }
}
