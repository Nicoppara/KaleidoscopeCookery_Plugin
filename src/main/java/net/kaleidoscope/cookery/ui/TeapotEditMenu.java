package net.kaleidoscope.cookery.ui;
import net.kaleidoscope.cookery.api.ui.MenuButton;

import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.TeapotRecipe;
import net.kaleidoscope.cookery.recipe.edit.RecipeEditService;
import net.kaleidoscope.cookery.recipe.edit.TeapotRecipeDraft;
import net.kaleidoscope.cookery.ui.input.DialogChoicePrompt;
import net.kaleidoscope.cookery.ui.input.MenuInput;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.gui.BasicGuiImpl;
import net.momirealms.craftengine.core.plugin.gui.Gui;
import net.momirealms.craftengine.core.plugin.gui.GuiElement;
import net.momirealms.craftengine.core.plugin.gui.GuiLayout;
import net.momirealms.craftengine.core.plugin.gui.Ingredient;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;

// 茶壶配方编辑 F 液体 I 原料 R 成品 T id C 耗时
// 液体与成品都有登记要求 液体要在 teapot_liquid 里 成品要在 tea_cup 里有模型
public final class TeapotEditMenu {
    private TeapotEditMenu() {
    }

    public static void open(org.bukkit.entity.Player bukkitPlayer, TeapotRecipeDraft draft) {
        Player viewer = RecipeMenus.adapt(bukkitPlayer);
        if (viewer == null) {
            return;
        }
        GuiLayout layout = new GuiLayout(
                "####?####",
                "#F#I#R#T#",
                "#####C###",
                "B###S###D");
        layout.addIngredient('#', Ingredient.simple(MenuIcons.filler(viewer)));
        layout.addIngredient('?', MenuIcons.editingHelp(viewer,
                "液体：左键从已登记的液体里选择",
                "原料与成品：左键替换，光标持物品可直接选取",
                "原料与成品：右键修改对应数量",
                "成品须有茶杯模型；熬煮时间使用独立按钮"));
        layout.addIngredient('F', fluidSlot(bukkitPlayer, viewer, draft));
        layout.addIngredient('I', inputSlot(bukkitPlayer, viewer, draft));
        layout.addIngredient('R', resultSlot(bukkitPlayer, viewer, draft));
        layout.addIngredient('T', idSlot(bukkitPlayer, viewer, draft));
        layout.addIngredient('C', timeSlot(bukkitPlayer, viewer, draft));
        layout.addIngredient('B', MenuIcons.back(viewer,
                () -> RecipeListMenu.open(bukkitPlayer, ApplianceType.TEAPOT, true)));
        layout.addIngredient('S', saveSlot(bukkitPlayer, viewer, draft));
        layout.addIngredient('D', deleteSlot(bukkitPlayer, viewer, draft));

        Gui gui = BasicGuiImpl.builder()
                .layout(layout)
                .inventoryClickConsumer(RecipeMenus.inventoryGuard())
                .build();
        gui.title(MenuIcons.text(draft.isNew() ? "新建茶壶食谱" : "编辑茶壶食谱", NamedTextColor.DARK_GRAY))
                .refresh()
                .open(viewer);
    }

    // 液体存的是流体 id minecraft:water 这不是物品 拿它建图标只会得到屏障
    // 所以使用对应的桶作为原物品预览。
    private static GuiElement fluidSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                        TeapotRecipeDraft draft) {
        Key fluid = draft.fluid();
        Item icon = fluid == null
                ? MenuIcons.icon(MenuButton.LIQUID, viewer, MenuIcons.text("设置液体", NamedTextColor.AQUA),
                        MenuIcons.lore("左键从已登记的液体里选择"))
                : MenuIcons.nativePreview(MenuIcons.liquidIconKey(fluid), viewer);
        return MenuIcons.button(icon, () -> DialogChoicePrompt.open(bukkitPlayer, "选择液体",
                "只能用 teapot_liquid 里登记过的 自定义可填别的",
                liquidChoices(),
                value -> {
                    draft.fluid(Key.of(value));
                    open(bukkitPlayer, draft);
                },
                () -> MenuInput.requestText(bukkitPlayer, "液体 id", "id",
                        draft.fluid() == null ? "minecraft:" : draft.fluid().asString(),
                        raw -> {
                            Key key = RecipeMenus.parseKey(raw);
                            if (key == null) {
                                RecipeMenus.message(bukkitPlayer, "液体 id 格式不正确");
                            } else {
                                draft.fluid(key);
                            }
                            open(bukkitPlayer, draft);
                        },
                        () -> open(bukkitPlayer, draft)),
                () -> open(bukkitPlayer, draft)));
    }

    // 按钮列表从已登记的液体生成 加一种就自动多一个按钮
    private static List<DialogChoicePrompt.Choice> liquidChoices() {
        List<DialogChoicePrompt.Choice> out = new ArrayList<>();
        for (Key fluid : FoodRecipeRegistry.instance().teapotLiquidKeys()) {
            out.add(MenuIcons.liquidChoice(fluid));
        }
        if (out.isEmpty()) {
            out.add(MenuIcons.liquidChoice(ItemKeys.WATER));
            out.add(MenuIcons.liquidChoice(ItemKeys.LAVA));
        }
        return out;
    }

    private static GuiElement inputSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                        TeapotRecipeDraft draft) {
        Item icon = draft.input() == null
                ? MenuIcons.icon(MenuButton.INVALID, viewer, MenuIcons.text("设置原料", NamedTextColor.GOLD),
                        MenuIcons.lore("左键选取物品，右键修改消耗数量"))
                : MenuIcons.nativePreview(draft.input(), viewer);
        return GuiElement.constant(icon, (element, click) -> {
            click.cancel();
            if ("RIGHT".equals(click.type()) || "SHIFT_RIGHT".equals(click.type())) {
                MenuInput.requestInt(bukkitPlayer, "消耗数量", "值", draft.ingredientCount(),
                        1, TeapotRecipeDraft.MAX_COUNT,
                        value -> {
                            draft.ingredientCount(value);
                            open(bukkitPlayer, draft);
                        },
                        () -> open(bukkitPlayer, draft));
                return;
            }
            AccurateEditMenu.pickItem(bukkitPlayer, click, "设置原料", draft.input(),
                    draft::input, () -> open(bukkitPlayer, draft));
        });
    }

    private static GuiElement resultSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                         TeapotRecipeDraft draft) {
        Item icon = draft.result() == null
                ? MenuIcons.icon(MenuButton.INVALID, viewer, MenuIcons.text("设置成品", NamedTextColor.GOLD),
                        MenuIcons.lore("左键选取物品，右键修改产出数量", "须有茶杯模型"))
                : MenuIcons.nativePreview(draft.result(), viewer);
        return GuiElement.constant(icon, (element, click) -> {
            click.cancel();
            if ("RIGHT".equals(click.type()) || "SHIFT_RIGHT".equals(click.type())) {
                MenuInput.requestInt(bukkitPlayer, "产出数量", "值", draft.resultCount(),
                        1, TeapotRecipeDraft.MAX_COUNT,
                        value -> {
                            draft.resultCount(value);
                            open(bukkitPlayer, draft);
                        },
                        () -> open(bukkitPlayer, draft));
                return;
            }
            AccurateEditMenu.pickItem(bukkitPlayer, click, "设置成品", draft.result(),
                    draft::result, () -> open(bukkitPlayer, draft));
        });
    }

    private static GuiElement timeSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                       TeapotRecipeDraft draft) {
        return RecipeProcessingControls.time(bukkitPlayer, viewer, draft.originalRecipe(), ApplianceType.TEAPOT, draft.time(), draft::time,
                () -> open(bukkitPlayer, draft));
    }

    private static GuiElement idSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                     TeapotRecipeDraft draft) {
        Item icon = MenuIcons.icon(MenuButton.CREATE, viewer,
                MenuIcons.text("食谱 id", NamedTextColor.GOLD),
                MenuIcons.lore(draft.id().asString(), "左键修改"));
        return MenuIcons.button(icon, () -> MenuInput.requestText(bukkitPlayer, "食谱 id", "id",
                draft.id().asString(),
                raw -> {
                    Key key = RecipeMenus.parseKey(raw);
                    if (key == null) {
                        RecipeMenus.message(bukkitPlayer, "食谱 id 格式不正确");
                    } else {
                        draft.id(key);
                    }
                    open(bukkitPlayer, draft);
                },
                () -> open(bukkitPlayer, draft)));
    }

    private static GuiElement saveSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                       TeapotRecipeDraft draft) {
        Item icon = MenuIcons.icon(MenuButton.SAVE, viewer,
                MenuIcons.text("保存", NamedTextColor.GREEN));
        return MenuIcons.button(icon, () -> {
            RecipeMenus.message(bukkitPlayer, "正在保存食谱...");
            var saving = RecipeEditSaves.save(draft, () -> RecipeEditService.saveTeapot(draft));
            if (saving == null) return;
            saving.thenAccept(error ->
                    MenuTasks.runFor(bukkitPlayer, () -> {
                        if (RecipeEditService.isClosed()) return;
                        if (error != null) {
                            RecipeMenus.message(bukkitPlayer, error);
                            open(bukkitPlayer, draft);
                            return;
                        }
                        RecipeMenus.message(bukkitPlayer, "已保存 " + draft.id().asString());
                        RecipeListMenu.open(bukkitPlayer, ApplianceType.TEAPOT, true);
                    }));
        });
    }

    private static GuiElement deleteSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                         TeapotRecipeDraft draft) {
        if (draft.isNew()) {
            return MenuIcons.filler(viewer);
        }
        Item icon = MenuIcons.icon(MenuButton.DELETE, viewer,
                MenuIcons.text("删除", NamedTextColor.RED));
        return MenuIcons.button(icon, () -> ConfirmMenu.open(bukkitPlayer, "删除茶壶食谱",
                List.of(draft.originalId().asString()),
                () -> {
                    TeapotRecipe existing = draft.originalRecipe();
                    if (existing == null) {
                        RecipeMenus.message(bukkitPlayer, "食谱已经不存在");
                        RecipeListMenu.open(bukkitPlayer, ApplianceType.TEAPOT, true);
                        return;
                    }
                    RecipeMenus.message(bukkitPlayer, "正在删除食谱...");
                    RecipeEditService.deleteTeapot(existing).thenAccept(success ->
                            MenuTasks.runFor(bukkitPlayer, () -> {
                                RecipeMenus.message(bukkitPlayer, success
                                        ? "已删除 " + draft.originalId().asString()
                                        : "配置文件写入失败，食谱未删除");
                                RecipeListMenu.open(bukkitPlayer, ApplianceType.TEAPOT, true);
                            }));
                },
                () -> open(bukkitPlayer, draft)));
    }
}
