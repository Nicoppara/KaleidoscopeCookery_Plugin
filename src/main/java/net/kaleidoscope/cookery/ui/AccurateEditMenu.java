package net.kaleidoscope.cookery.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kaleidoscope.cookery.api.ui.MenuButton;
import net.kaleidoscope.cookery.recipe.AccurateFoodRecipe;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.WeightedResult;
import net.kaleidoscope.cookery.recipe.edit.AccurateRecipeDraft;
import net.kaleidoscope.cookery.recipe.edit.RecipeEditService;
import net.kaleidoscope.cookery.ui.input.MenuInput;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.gui.BasicGuiImpl;
import net.momirealms.craftengine.core.plugin.gui.Click;
import net.momirealms.craftengine.core.plugin.gui.Gui;
import net.momirealms.craftengine.core.plugin.gui.GuiElement;
import net.momirealms.craftengine.core.plugin.gui.GuiLayout;
import net.momirealms.craftengine.core.plugin.gui.Ingredient;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.libraries.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public final class AccurateEditMenu {
   private static final int MAX_RESULTS = 7;
   private static final int MAX_RESULT_COUNT = 64;
   private static final int MAX_ROTATIONS = 100;

   private AccurateEditMenu() {
   }

   public static void open(Player bukkitPlayer, AccurateRecipeDraft draft) {
      net.momirealms.craftengine.core.entity.player.Player viewer = RecipeMenus.adapt(bukkitPlayer);
      if (viewer != null) {
         GuiLayout layout = new GuiLayout(new String[]{"#########", "#I##T##C#", "#########", "#RRRRRRR#", "#########", "B#M#O#S#D"});
         layout.addIngredient('#', Ingredient.simple(MenuIcons.filler(viewer)));
         layout.addIngredient('I', inputSlot(bukkitPlayer, viewer, draft));
         layout.addIngredient('T', idSlot(bukkitPlayer, viewer, draft));
         layout.addIngredient('C', countSlot(bukkitPlayer, viewer, draft));
         layout.addIngredient('R', resultSlots(bukkitPlayer, viewer, draft));
         layout.addIngredient('B', backSlot(bukkitPlayer, viewer, draft));
         layout.addIngredient('M', modeSlot(bukkitPlayer, viewer, draft));
         layout.addIngredient('O', rotationSlot(bukkitPlayer, viewer, draft));
         layout.addIngredient('S', saveSlot(bukkitPlayer, viewer, draft));
         layout.addIngredient('D', deleteSlot(bukkitPlayer, viewer, draft));
         Gui gui = BasicGuiImpl.builder().layout(layout).inventoryClickConsumer(RecipeMenus.inventoryGuard()).build();
         gui.title(
               MenuIcons.text(
                  (draft.isNew() ? "新建精准食谱 - " : "编辑精准食谱 - ") + MenuIcons.displayName(draft.cook()),
                  NamedTextColor.DARK_GRAY
               )
            )
            .refresh()
            .open(viewer);
      }
   }

   private static void reopen(Player bukkitPlayer, AccurateRecipeDraft draft) {
      open(bukkitPlayer, draft);
   }

   private static GuiElement inputSlot(Player bukkitPlayer, net.momirealms.craftengine.core.entity.player.Player viewer, AccurateRecipeDraft draft) {
      Item icon = MenuIcons.icon(
         draft.input(),
         viewer,
         MenuIcons.text("原料", NamedTextColor.GOLD),
         MenuIcons.loreNamed(
            draft.input(),
            "光标持物品左键 直接取该物品",
            "空手左键 手动输入物品 id"
         )
      );
      return GuiElement.constant(icon, (element, click) -> {
         click.cancel();
         pickItem(bukkitPlayer, click, "设置原料", draft.input(), key -> draft.input(key), () -> reopen(bukkitPlayer, draft));
      });
   }

   private static GuiElement idSlot(Player bukkitPlayer, net.momirealms.craftengine.core.entity.player.Player viewer, AccurateRecipeDraft draft) {
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

         reopen(bukkitPlayer, draft);
      }, () -> reopen(bukkitPlayer, draft)));
   }

   private static GuiElement countSlot(Player bukkitPlayer, net.momirealms.craftengine.core.entity.player.Player viewer, AccurateRecipeDraft draft) {
      Item icon = MenuIcons.icon(
         MenuButton.COUNT,
         viewer,
         MenuIcons.text("每次产出份数", NamedTextColor.GOLD),
         MenuIcons.lore("当前 " + draft.resultCount(), "左键输入新值")
      );
      return MenuIcons.button(icon, () -> MenuInput.requestInt(bukkitPlayer, "产出份数", "份数", draft.resultCount(), 1, 64, value -> {
         draft.resultCount(value);
         reopen(bukkitPlayer, draft);
      }, () -> reopen(bukkitPlayer, draft)));
   }

   private static Ingredient resultSlots(
      final Player bukkitPlayer, final net.momirealms.craftengine.core.entity.player.Player viewer, final AccurateRecipeDraft draft
   ) {
      return new Ingredient() {
         private int index = 0;

         public GuiElement element(Gui gui) {
            return AccurateEditMenu.resultSlot(bukkitPlayer, viewer, draft, this.index++);
         }
      };
   }

   private static GuiElement resultSlot(Player bukkitPlayer, net.momirealms.craftengine.core.entity.player.Player viewer, AccurateRecipeDraft draft, int index) {
      List<WeightedResult> results = draft.results();
      if (index <= results.size() && (index != results.size() || results.size() < 7)) {
         if (index == results.size()) {
            Item icon = MenuIcons.icon(
               MenuButton.ADD,
               viewer,
               MenuIcons.text("添加成品", NamedTextColor.GREEN),
               MenuIcons.lore(
                  "加到两个及以上就变成按权重随机",
                  "光标持物品左键 直接取该物品",
                  "空手左键 手动输入物品 id"
               )
            );
            return GuiElement.constant(
               icon,
               (element, click) -> {
                  click.cancel();
                  pickItem(
                     bukkitPlayer, click, "添加成品", null, key -> results.add(new WeightedResult(key, 100)), () -> reopen(bukkitPlayer, draft)
                  );
               }
            );
         }

         WeightedResult result = results.get(index);
         List<Component> lore = new ArrayList<>();
         lore.add(MenuIcons.grayWith(result.key()));
         lore.add(MenuIcons.gray(draft.isCertain() ? "百分百产出" : "权重 " + result.weight()));
         lore.add(MenuIcons.text("左键换物品", NamedTextColor.YELLOW));
         if (!draft.isCertain()) {
            lore.add(MenuIcons.text("右键改权重", NamedTextColor.YELLOW));
         }

         lore.add(MenuIcons.text("Shift 右键删除", NamedTextColor.RED));
         Item icon = MenuIcons.icon(result.key(), viewer, MenuIcons.text("成品 " + (index + 1), NamedTextColor.GOLD), lore);
         return GuiElement.constant(
            icon,
            (element, click) -> {
               click.cancel();
               String type = click.type();
               if ("SHIFT_RIGHT".equals(type)) {
                  results.remove(index);
                  reopen(bukkitPlayer, draft);
               } else if ("RIGHT".equals(type)) {
                  if (!draft.isCertain()) {
                     MenuInput.requestInt(bukkitPlayer, "成品权重", "权重", result.weight(), 1, 10000, value -> {
                        results.set(index, new WeightedResult(result.key(), value));
                        reopen(bukkitPlayer, draft);
                     }, () -> reopen(bukkitPlayer, draft));
                  }
               } else {
                  pickItem(
                     bukkitPlayer,
                     click,
                     "更换成品",
                     result.key(),
                     key -> results.set(index, new WeightedResult(key, result.weight())),
                     () -> reopen(bukkitPlayer, draft)
                  );
               }
            }
         );
      } else {
         return MenuIcons.empty();
      }
   }

   private static GuiElement modeSlot(Player bukkitPlayer, net.momirealms.craftengine.core.entity.player.Player viewer, AccurateRecipeDraft draft) {
      List<WeightedResult> results = draft.results();
      boolean certain = draft.isCertain();
      Item icon = MenuIcons.icon(
         MenuButton.MODE,
         viewer,
         MenuIcons.text("产出模式", NamedTextColor.GOLD),
         MenuIcons.lore(
            certain
               ? "当前 百分百产出该成品"
               : "当前 在 " + results.size() + " 个成品里按权重随机一个",
            certain
               ? "在成品栏添加第二个成品即变为随机"
               : "左键只保留第一个成品 改回百分百"
         )
      );
      return certain
         ? MenuIcons.button(icon, () -> reopen(bukkitPlayer, draft))
         : MenuIcons.button(
            icon,
            () -> ConfirmMenu.open(
               bukkitPlayer,
               "改为百分百产出",
               List.of("只保留第一个成品", "其余 " + (results.size() - 1) + " 个会被移除"),
               () -> {
                  WeightedResult first = results.getFirst();
                  results.clear();
                  results.add(new WeightedResult(first.key(), 100));
                  reopen(bukkitPlayer, draft);
               },
               () -> reopen(bukkitPlayer, draft)
            )
         );
   }

   private static GuiElement rotationSlot(Player bukkitPlayer, net.momirealms.craftengine.core.entity.player.Player viewer, AccurateRecipeDraft draft) {
      if (draft.cook() != ApplianceType.MILLSTONE) {
         return MenuIcons.filler(viewer);
      }

      Item icon = MenuIcons.icon(
         MenuButton.ROTATION,
         viewer,
         MenuIcons.text("研磨圈数", NamedTextColor.GOLD),
         MenuIcons.lore(
            draft.rotations() <= 0 ? "跟随石磨默认值" : "当前 " + draft.rotations(),
            "左键输入新值 填 0 用默认"
         )
      );
      return MenuIcons.button(icon, () -> MenuInput.requestInt(bukkitPlayer, "研磨圈数", "圈数", draft.rotations(), 0, 100, value -> {
         draft.rotations(value);
         reopen(bukkitPlayer, draft);
      }, () -> reopen(bukkitPlayer, draft)));
   }

   private static GuiElement saveSlot(Player bukkitPlayer, net.momirealms.craftengine.core.entity.player.Player viewer, AccurateRecipeDraft draft) {
      Item icon = MenuIcons.icon(
         MenuButton.SAVE,
         viewer,
         MenuIcons.text("保存", NamedTextColor.GREEN),
         MenuIcons.lore("立即生效 并写回配置文件")
      );
      return MenuIcons.button(icon, () -> {
         String error = RecipeEditService.saveAccurate(draft);
         if (error != null) {
            RecipeMenus.message(bukkitPlayer, error);
            reopen(bukkitPlayer, draft);
         } else {
            RecipeListMenu.open(bukkitPlayer, draft.cook(), true);
         }
      });
   }

   private static GuiElement deleteSlot(Player bukkitPlayer, net.momirealms.craftengine.core.entity.player.Player viewer, AccurateRecipeDraft draft) {
      if (draft.isNew()) {
         return MenuIcons.filler(viewer);
      }

      Item icon = MenuIcons.icon(
         MenuButton.DELETE,
         viewer,
         MenuIcons.text("删除该食谱", NamedTextColor.RED),
         MenuIcons.lore("同时从配置文件里移除")
      );
      return MenuIcons.button(
         icon,
         () -> ConfirmMenu.open(
            bukkitPlayer,
            "删除食谱",
            List.of(draft.originalId().asString()),
            () -> {
               AccurateFoodRecipe existing = draft.originalRecipe();
               if (existing == null) {
                  RecipeMenus.message(bukkitPlayer, "食谱已经不存在");
                  RecipeListMenu.open(bukkitPlayer, draft.cook(), true);
               } else {
                  RecipeMenus.message(bukkitPlayer, "正在删除食谱...");
                  RecipeEditService.deleteAccurate(existing)
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
                              RecipeListMenu.open(bukkitPlayer, draft.cook(), true);
                           }
                        )
                     );
               }
            },
            () -> reopen(bukkitPlayer, draft)
         )
      );
   }

   private static GuiElement backSlot(Player bukkitPlayer, net.momirealms.craftengine.core.entity.player.Player viewer, AccurateRecipeDraft draft) {
      return MenuIcons.back(viewer, () -> RecipeListMenu.open(bukkitPlayer, draft.cook(), true));
   }

   static void pickItem(Player bukkitPlayer, Click click, String title, Key current, Consumer<Key> apply, Runnable reopen) {
      Item cursor = click.itemOnCursor();
      if (!ItemUtils.isEmpty(cursor)) {
         apply.accept(cursor.id());
         reopen.run();
      } else {
         MenuInput.requestText(bukkitPlayer, title, "物品 id", current == null ? "minecraft:" : current.asString(), raw -> {
            Key key = RecipeMenus.parseKey(raw);
            if (key == null) {
               RecipeMenus.message(bukkitPlayer, "物品 id 格式不正确");
            } else {
               apply.accept(key);
            }

            reopen.run();
         }, reopen);
      }
   }
}
