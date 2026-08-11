package net.kaleidoscope.cookery.ui;

import java.util.ArrayList;
import java.util.List;
import net.kaleidoscope.cookery.api.ui.MenuButton;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.TeapotLiquid;
import net.kaleidoscope.cookery.recipe.TeapotRecipe;
import net.kaleidoscope.cookery.recipe.edit.RecipeEditService;
import net.kaleidoscope.cookery.recipe.edit.TeapotRecipeDraft;
import net.kaleidoscope.cookery.ui.input.DialogChoicePrompt;
import net.kaleidoscope.cookery.ui.input.MenuInput;
import net.kaleidoscope.cookery.util.Localization;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.gui.BasicGuiImpl;
import net.momirealms.craftengine.core.plugin.gui.Gui;
import net.momirealms.craftengine.core.plugin.gui.GuiElement;
import net.momirealms.craftengine.core.plugin.gui.GuiLayout;
import net.momirealms.craftengine.core.plugin.gui.Ingredient;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.libraries.adventure.text.format.NamedTextColor;
import net.momirealms.craftengine.libraries.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class TeapotEditMenu {
   private TeapotEditMenu() {
   }

   public static void open(Player bukkitPlayer, TeapotRecipeDraft draft) {
      net.momirealms.craftengine.core.entity.player.Player viewer = RecipeMenus.adapt(bukkitPlayer);
      if (viewer != null) {
         GuiLayout layout = new GuiLayout(new String[]{"#########", "#F#I#R#T#", "#####C###", "B###S###D"});
         layout.addIngredient('#', Ingredient.simple(MenuIcons.filler(viewer)));
         layout.addIngredient('F', fluidSlot(bukkitPlayer, viewer, draft));
         layout.addIngredient('I', inputSlot(bukkitPlayer, viewer, draft));
         layout.addIngredient('R', resultSlot(bukkitPlayer, viewer, draft));
         layout.addIngredient('T', idSlot(bukkitPlayer, viewer, draft));
         layout.addIngredient('C', timeSlot(bukkitPlayer, viewer, draft));
         layout.addIngredient('B', MenuIcons.back(viewer, () -> RecipeListMenu.open(bukkitPlayer, ApplianceType.TEAPOT, true)));
         layout.addIngredient('S', saveSlot(bukkitPlayer, viewer, draft));
         layout.addIngredient('D', deleteSlot(bukkitPlayer, viewer, draft));
         Gui gui = BasicGuiImpl.builder().layout(layout).inventoryClickConsumer(RecipeMenus.inventoryGuard()).build();
         gui.title(MenuIcons.text(draft.isNew() ? "新建茶壶食谱" : "编辑茶壶食谱", NamedTextColor.DARK_GRAY))
            .refresh()
            .open(viewer);
      }
   }

   private static GuiElement fluidSlot(Player bukkitPlayer, net.momirealms.craftengine.core.entity.player.Player viewer, TeapotRecipeDraft draft) {
      Key fluid = draft.fluid();
      List<Component> lore = new ArrayList<>();
      lore.add(fluid == null ? MenuIcons.gray("未设置") : MenuIcons.gray("").append(fluidName(fluid)));
      lore.add(MenuIcons.text("左键从已登记的液体里选", NamedTextColor.YELLOW));
      Item icon = MenuIcons.icon(bucketOf(fluid), viewer, MenuIcons.text("液体", NamedTextColor.AQUA), lore);
      return MenuIcons.button(
         icon,
         () -> DialogChoicePrompt.open(
            bukkitPlayer,
            "选择液体",
            "只能用 teapot_liquid 里登记过的 自定义可填别的",
            liquidChoices(),
            value -> {
               draft.fluid(Key.of(value));
               open(bukkitPlayer, draft);
            },
            () -> MenuInput.requestText(bukkitPlayer, "液体 id", "id", draft.fluid() == null ? "minecraft:" : draft.fluid().asString(), raw -> {
               Key key = RecipeMenus.parseKey(raw);
               if (key == null) {
                  RecipeMenus.message(bukkitPlayer, "液体 id 格式不正确");
               } else {
                  draft.fluid(key);
               }

               open(bukkitPlayer, draft);
            }, () -> open(bukkitPlayer, draft)),
            () -> open(bukkitPlayer, draft)
         )
      );
   }

   private static Key bucketOf(Key fluid) {
      if (fluid == null) {
         return MenuIcons.iconKey(MenuButton.LIQUID);
      }

      Key bucket = Key.of(fluid.namespace(), fluid.value() + "_bucket");
      return Material.matchMaterial(bucket.asString()) == null ? MenuIcons.iconKey(MenuButton.LIQUID) : bucket;
   }

   private static Component fluidName(Key fluid) {
      TeapotLiquid liquid = FoodRecipeRegistry.instance().getTeapotLiquid(fluid);
      return liquid != null && liquid.displayName() != null && !liquid.displayName().isEmpty()
         ? Localization.component(liquid.displayName()).decoration(TextDecoration.ITALIC, false)
         : MenuIcons.text(fluid.value(), NamedTextColor.WHITE);
   }

   private static List<DialogChoicePrompt.Choice> liquidChoices() {
      List<DialogChoicePrompt.Choice> out = new ArrayList<>();

      for (Key fluid : FoodRecipeRegistry.instance().teapotLiquidKeys()) {
         TeapotLiquid liquid = FoodRecipeRegistry.instance().getTeapotLiquid(fluid);
         String name = liquid == null ? null : liquid.displayName();
         out.add(
            name != null && Localization.isTranslationKey(name)
               ? DialogChoicePrompt.Choice.translated(name, fluid.asString())
               : new DialogChoicePrompt.Choice(name != null && !name.isEmpty() ? name : fluid.value(), fluid.asString())
         );
      }

      if (out.isEmpty()) {
         out.add(new DialogChoicePrompt.Choice("水", "minecraft:water"));
         out.add(new DialogChoicePrompt.Choice("岩浆", "minecraft:lava"));
      }

      return out;
   }

   private static GuiElement inputSlot(Player bukkitPlayer, net.momirealms.craftengine.core.entity.player.Player viewer, TeapotRecipeDraft draft) {
      Item icon = MenuIcons.icon(
         draft.input(),
         viewer,
         MenuIcons.text("原料 x" + draft.ingredientCount(), NamedTextColor.GOLD),
         MenuIcons.loreNamed(draft.input(), "左键换物品", "右键改消耗数量")
      );
      return GuiElement.constant(icon, (element, click) -> {
         click.cancel();
         if (!"RIGHT".equals(click.type()) && !"SHIFT_RIGHT".equals(click.type())) {
            AccurateEditMenu.pickItem(bukkitPlayer, click, "设置原料", draft.input(), draft::input, () -> open(bukkitPlayer, draft));
         } else {
            MenuInput.requestInt(bukkitPlayer, "消耗数量", "值", draft.ingredientCount(), 1, 64, value -> {
               draft.ingredientCount(value);
               open(bukkitPlayer, draft);
            }, () -> open(bukkitPlayer, draft));
         }
      });
   }

   private static GuiElement resultSlot(Player bukkitPlayer, net.momirealms.craftengine.core.entity.player.Player viewer, TeapotRecipeDraft draft) {
      Item icon = MenuIcons.icon(
         draft.result(),
         viewer,
         MenuIcons.text("成品 x" + draft.resultCount(), NamedTextColor.GOLD),
         MenuIcons.loreNamed(
            draft.result(),
            "必须在 tea_cup 里定义过模型",
            "左键换物品",
            "右键改产出数量"
         )
      );
      return GuiElement.constant(icon, (element, click) -> {
         click.cancel();
         if (!"RIGHT".equals(click.type()) && !"SHIFT_RIGHT".equals(click.type())) {
            AccurateEditMenu.pickItem(bukkitPlayer, click, "设置成品", draft.result(), draft::result, () -> open(bukkitPlayer, draft));
         } else {
            MenuInput.requestInt(bukkitPlayer, "产出数量", "值", draft.resultCount(), 1, 64, value -> {
               draft.resultCount(value);
               open(bukkitPlayer, draft);
            }, () -> open(bukkitPlayer, draft));
         }
      });
   }

   private static GuiElement timeSlot(Player bukkitPlayer, net.momirealms.craftengine.core.entity.player.Player viewer, TeapotRecipeDraft draft) {
      Item icon = MenuIcons.icon(
         MenuButton.ROTATION,
         viewer,
         MenuIcons.text("熬煮 " + draft.time() + " tick", NamedTextColor.GOLD),
         MenuIcons.lore("约 " + String.format("%.1f", draft.time() / 20.0) + " 秒", "左键修改")
      );
      return MenuIcons.button(icon, () -> MenuInput.requestInt(bukkitPlayer, "熬煮时间", "tick", draft.time(), 1, 72000, value -> {
         draft.time(value);
         open(bukkitPlayer, draft);
      }, () -> open(bukkitPlayer, draft)));
   }

   private static GuiElement idSlot(Player bukkitPlayer, net.momirealms.craftengine.core.entity.player.Player viewer, TeapotRecipeDraft draft) {
      Item icon = MenuIcons.icon(
         MenuButton.CREATE, viewer, MenuIcons.text("食谱 id", NamedTextColor.GOLD), MenuIcons.lore(draft.id().asString(), "左键修改")
      );
      return MenuIcons.button(icon, () -> MenuInput.requestText(bukkitPlayer, "食谱 id", "id", draft.id().asString(), raw -> {
         Key key = RecipeMenus.parseKey(raw);
         if (key == null) {
            RecipeMenus.message(bukkitPlayer, "食谱 id 格式不正确");
         } else {
            draft.id(key);
         }

         open(bukkitPlayer, draft);
      }, () -> open(bukkitPlayer, draft)));
   }

   private static GuiElement saveSlot(Player bukkitPlayer, net.momirealms.craftengine.core.entity.player.Player viewer, TeapotRecipeDraft draft) {
      Item icon = MenuIcons.icon(MenuButton.SAVE, viewer, MenuIcons.text("保存", NamedTextColor.GREEN));
      return MenuIcons.button(icon, () -> {
         String error = RecipeEditService.saveTeapot(draft);
         if (error != null) {
            RecipeMenus.message(bukkitPlayer, error);
            open(bukkitPlayer, draft);
         } else {
            RecipeMenus.message(bukkitPlayer, "已保存 " + draft.id().asString());
            RecipeListMenu.open(bukkitPlayer, ApplianceType.TEAPOT, true);
         }
      });
   }

   private static GuiElement deleteSlot(Player bukkitPlayer, net.momirealms.craftengine.core.entity.player.Player viewer, TeapotRecipeDraft draft) {
      if (draft.isNew()) {
         return MenuIcons.filler(viewer);
      }

      Item icon = MenuIcons.icon(MenuButton.DELETE, viewer, MenuIcons.text("删除", NamedTextColor.RED));
      return MenuIcons.button(
         icon,
         () -> ConfirmMenu.open(
            bukkitPlayer,
            "删除茶壶食谱",
            List.of(draft.originalId().asString()),
            () -> {
               TeapotRecipe existing = draft.originalRecipe();
               if (existing == null) {
                  RecipeMenus.message(bukkitPlayer, "食谱已经不存在");
                  RecipeListMenu.open(bukkitPlayer, ApplianceType.TEAPOT, true);
               } else {
                  RecipeMenus.message(bukkitPlayer, "正在删除食谱...");
                  RecipeEditService.deleteTeapot(existing)
                     .thenAccept(
                        success -> MenuTasks.runFor(
                           bukkitPlayer,
                           () -> {
                              RecipeMenus.message(
                                 bukkitPlayer,
                                 success
                                    ? "已删除 " + draft.originalId().asString()
                                    : "配置文件写入失败，食谱未删除"
                              );
                              RecipeListMenu.open(bukkitPlayer, ApplianceType.TEAPOT, true);
                           }
                        )
                     );
               }
            },
            () -> open(bukkitPlayer, draft)
         )
      );
   }
}
