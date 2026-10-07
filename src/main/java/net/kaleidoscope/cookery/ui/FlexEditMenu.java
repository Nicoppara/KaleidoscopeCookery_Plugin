package net.kaleidoscope.cookery.ui;
import net.kaleidoscope.cookery.api.ui.MenuButton;

import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.FlexFoodRecipe;
import net.kaleidoscope.cookery.recipe.FoodGroups;
import net.kaleidoscope.cookery.recipe.SoupBaseRegistry;
import net.kaleidoscope.cookery.recipe.edit.FlexRecipeDraft;
import net.kaleidoscope.cookery.recipe.edit.RecipeEditService;
import net.kaleidoscope.cookery.ui.input.DialogChoicePrompt;
import net.kaleidoscope.cookery.ui.input.MenuInput;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.gui.BasicGuiImpl;
import net.momirealms.craftengine.core.plugin.gui.Gui;
import net.momirealms.craftengine.core.plugin.gui.GuiElement;
import net.momirealms.craftengine.core.plugin.gui.GuiLayout;
import net.momirealms.craftengine.core.plugin.gui.Ingredient;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

// 模糊食谱编辑页 perfect 同时声明必需食材和理想配比
public final class FlexEditMenu {

    // 盛装容器的常用预设 顺序即 dialog 上按钮的顺序
    private static final List<DialogChoicePrompt.Choice> CARRIER_CHOICES = List.of(
            new DialogChoicePrompt.Choice("空手", null),
            new DialogChoicePrompt.Choice("碗", "minecraft:bowl"),
            new DialogChoicePrompt.Choice("花盆", "minecraft:flower_pot"),
            new DialogChoicePrompt.Choice("玻璃瓶", "minecraft:glass_bottle"));

    private static final int MAX_INGREDIENTS = 14;
    private static final int MAX_PORTION = 64;

    private FlexEditMenu() {
    }

    public static void open(org.bukkit.entity.Player bukkitPlayer, FlexRecipeDraft draft) {
        Player viewer = RecipeMenus.adapt(bukkitPlayer);
        if (viewer == null) {
            return;
        }
        GuiLayout layout = new GuiLayout(
                "####?####",
                "#R#T#L#C#",
                "#PPPPPPP#",
                "#PPPPPPP#",
                "#########",
                "BHE#S#G#D");
        layout.addIngredient('#', Ingredient.simple(MenuIcons.filler(viewer)));
        layout.addIngredient('?', MenuIcons.editingHelp(viewer,
                "原料与成品：左键替换，光标持物品可直接选取",
                "原料：右键修改配比，Shift 右键删除",
                "容器：左键选择，右键恢复空手",
                "汤底：左键添加，Shift 右键清空"));
        layout.addIngredient('R', resultSlot(bukkitPlayer, viewer, draft));
        layout.addIngredient('T', idSlot(bukkitPlayer, viewer, draft));
        layout.addIngredient('L', liquidSlot(bukkitPlayer, viewer, draft));
        layout.addIngredient('C', carrierSlot(bukkitPlayer, viewer, draft));
        layout.addIngredient('P', perfectSlots(bukkitPlayer, viewer, draft));
        layout.addIngredient('B', MenuIcons.back(viewer,
                () -> RecipeListMenu.open(bukkitPlayer, draft.cook(), true)));
        layout.addIngredient('E', equivalentSlot(bukkitPlayer, viewer, draft));
        layout.addIngredient('G', seasoningSlot(bukkitPlayer, viewer, draft));
        layout.addIngredient('H', draft.cook() == ApplianceType.POT
                ? RecipeProcessingControls.count(bukkitPlayer, viewer, draft.originalRecipe(), draft.cook(), "翻炒次数", draft.stirFryCount(),
                    Integer.MAX_VALUE, draft::stirFryCount, () -> open(bukkitPlayer, draft))
                : RecipeProcessingControls.time(bukkitPlayer, viewer, draft.originalRecipe(), draft.cook(), draft.cookingTime(),
                    draft::cookingTime, () -> open(bukkitPlayer, draft)));
        layout.addIngredient('S', saveSlot(bukkitPlayer, viewer, draft));
        layout.addIngredient('D', deleteSlot(bukkitPlayer, viewer, draft));

        Gui gui = BasicGuiImpl.builder()
                .layout(layout)
                .inventoryClickConsumer(RecipeMenus.inventoryGuard())
                .build();
        gui.title(MenuIcons.text((draft.isNew() ? "新建模糊食谱 - " : "编辑模糊食谱 - ")
                        + MenuIcons.displayName(draft.cook()), NamedTextColor.DARK_GRAY))
                .refresh()
                .open(viewer);
    }

    private static GuiElement resultSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                         FlexRecipeDraft draft) {
        Item icon = draft.result() == null
                ? MenuIcons.icon(MenuButton.INVALID, viewer, MenuIcons.text("设置成品", NamedTextColor.GOLD),
                        MenuIcons.lore("光标持物品左键选取，空手左键输入物品 id"))
                : MenuIcons.nativePreview(draft.result(), viewer);
        return GuiElement.constant(icon, (element, click) -> {
            click.cancel();
            AccurateEditMenu.pickItem(bukkitPlayer, click, "设置成品", draft.result(),
                    draft::result, () -> open(bukkitPlayer, draft));
        });
    }

    private static GuiElement idSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                     FlexRecipeDraft draft) {
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

    // 盛装容器 留空表示空手就能盛出 右键清空
    private static GuiElement carrierSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                          FlexRecipeDraft draft) {
        Key carrier = draft.carrier();
        Item icon = carrier == null
                ? MenuIcons.icon(MenuButton.CARRIER_NONE, viewer,
                        MenuIcons.text("盛装容器 空手", NamedTextColor.GREEN),
                        MenuIcons.lore("这道菜空手就能盛出",
                                "左键 从常用容器里选",
                                "光标持物品左键 直接设为容器"))
                : MenuIcons.nativePreview(carrier, viewer);
        return GuiElement.constant(icon, (element, click) -> {
            click.cancel();
            if ("RIGHT".equals(click.type()) || "SHIFT_RIGHT".equals(click.type())) {
                draft.carrier(null);
                open(bukkitPlayer, draft);
                return;
            }
            // 光标上有东西就按那个走
            if (!ItemUtils.isEmpty(click.itemOnCursor())) {
                draft.carrier(click.itemOnCursor().id());
                open(bukkitPlayer, draft);
                return;
            }
            Runnable reopen = () -> open(bukkitPlayer, draft);
            DialogChoicePrompt.open(bukkitPlayer, "设置盛装容器",
                    "选一个常用容器 或自定义物品 id",
                    CARRIER_CHOICES,
                    value -> {
                        draft.carrier(value == null ? null : Key.of(value));
                        reopen.run();
                    },
                    () -> AccurateEditMenu.pickItem(bukkitPlayer, click, "设置盛装容器", carrier,
                            draft::carrier, reopen),
                    reopen);
        });
    }

    // 等效食物 开启后 perfect 里写的食材可以被同一等效标签内的任意食材顶替
    private static GuiElement equivalentSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                             FlexRecipeDraft draft) {
        return toggleSlot(bukkitPlayer, viewer, draft, MenuButton.EQUIVALENT_FOODS, "等效食物",
                draft.useEquivalentFoods(), FoodGroups.instance().equivalentTagCount(),
                "同一等效标签内的食材可以互相顶替",
                draft::useEquivalentFoods);
    }

    // 调味品 开启后调味品表里的东西只占位 不进配比 不算杂料 不影响品质
    private static GuiElement seasoningSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                            FlexRecipeDraft draft) {
        return toggleSlot(bukkitPlayer, viewer, draft, MenuButton.SEASONINGS, "调味品",
                draft.useSeasonings(), FoodGroups.instance().seasoningTagCount(),
                "调味品只占一格 不影响品质",
                draft::useSeasonings);
    }

    private static GuiElement toggleSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                         FlexRecipeDraft draft, MenuButton button, String title,
                                         boolean enabled, int tagCount, String explain,
                                         Consumer<Boolean> setter) {
        String state = enabled ? "已开启" : "已关闭";
        String source = tagCount == 0 ? "配置里还没登记任何标签 开着也不生效" : "已登记 " + tagCount + " 个标签";
        Item icon = MenuIcons.icon(button, viewer,
                MenuIcons.text(title + " " + state, enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                MenuIcons.lore(explain, source, "左键切换"));
        return MenuIcons.button(icon, () -> {
            setter.accept(!enabled);
            open(bukkitPlayer, draft);
        });
    }

    // 汤底限定只对高汤锅有意义 炒锅没有液体这一维 恒空
    // 使用首个汤底作为图标，使图标与当前选择保持一致
    private static GuiElement liquidSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                         FlexRecipeDraft draft) {
        if (draft.cook() != ApplianceType.STOCKPOT) {
            return MenuIcons.filler(viewer);
        }
        Item icon = draft.liquids().isEmpty()
                ? MenuIcons.icon(MenuButton.LIQUID, viewer, MenuIcons.text("不限汤底", NamedTextColor.GOLD),
                        MenuIcons.lore("光标持桶左键添加，空手左键选择汤底", "Shift 右键清空"))
                : MenuIcons.nativePreview(MenuIcons.liquidIconKey(draft.liquids().get(0)), viewer);
        return GuiElement.constant(icon, (element, click) -> {
            click.cancel();
            if ("SHIFT_RIGHT".equals(click.type())) {
                draft.liquids().clear();
                open(bukkitPlayer, draft);
                return;
            }
            // 光标上拿着桶就直接认 和放食材一个手感
            if (!ItemUtils.isEmpty(click.itemOnCursor())) {
                addLiquid(draft, click.itemOnCursor().id());
                open(bukkitPlayer, draft);
                return;
            }
            Runnable reopen = () -> open(bukkitPlayer, draft);
            DialogChoicePrompt.open(bukkitPlayer, "添加限定汤底",
                    "选一个已登记的汤底 或自定义物品 id",
                    liquidChoices(),
                    value -> {
                        addLiquid(draft, Key.of(value));
                        reopen.run();
                    },
                    () -> AccurateEditMenu.pickItem(bukkitPlayer, click, "添加限定汤底", null,
                            key -> addLiquid(draft, key), reopen),
                    reopen);
        });
    }

    private static void addLiquid(FlexRecipeDraft draft, Key liquid) {
        if (liquid != null && !draft.liquids().contains(liquid)) {
            draft.liquids().add(liquid);
        }
    }

    // 已登记的汤底优先 一个都没有时至少给水和岩浆兜底
    private static List<DialogChoicePrompt.Choice> liquidChoices() {
        List<Key> registered = SoupBaseRegistry.instance().keys();
        if (registered.isEmpty()) {
            return List.of(
                    MenuIcons.liquidChoice(ItemKeys.WATER_BUCKET),
                    MenuIcons.liquidChoice(ItemKeys.LAVA_BUCKET));
        }
        List<DialogChoicePrompt.Choice> out = new ArrayList<>();
        for (Key key : registered) {
            out.add(MenuIcons.liquidChoice(key));
        }
        return out;
    }

    private static Ingredient perfectSlots(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                           FlexRecipeDraft draft) {
        return new Ingredient() {
            private int index = 0;

            @Override
            public GuiElement element(Gui gui) {
                return perfectSlot(bukkitPlayer, viewer, draft, this.index++);
            }
        };
    }

    private static GuiElement perfectSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                          FlexRecipeDraft draft, int index) {
        List<Map.Entry<Key, Integer>> entries = new ArrayList<>(draft.perfect().entrySet());
        if (index < entries.size()) {
            return ingredientSlot(bukkitPlayer, viewer, draft, entries, index);
        }
        if (index > entries.size() || entries.size() >= MAX_INGREDIENTS) {
            return MenuIcons.empty();
        }
        Item icon = MenuIcons.icon(MenuButton.ADD, viewer,
                MenuIcons.text("添加原料", NamedTextColor.GREEN),
                MenuIcons.lore("光标持物品左键 直接取该物品", "空手左键 手动输入物品 id"));
        return GuiElement.constant(icon, (element, click) -> {
            click.cancel();
            AccurateEditMenu.pickItem(bukkitPlayer, click, "添加原料", null,
                    key -> draft.perfect().putIfAbsent(key, 1),
                    () -> open(bukkitPlayer, draft));
        });
    }

    private static GuiElement ingredientSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                             FlexRecipeDraft draft, List<Map.Entry<Key, Integer>> entries,
                                             int index) {
        Map.Entry<Key, Integer> entry = entries.get(index);
        Key key = entry.getKey();
        int weight = entry.getValue();
        Item icon = MenuIcons.nativePreview(key, viewer);
        return GuiElement.constant(icon, (element, click) -> {
            click.cancel();
            String type = click.type();
            if ("SHIFT_RIGHT".equals(type)) {
                draft.perfect().remove(key);
                open(bukkitPlayer, draft);
                return;
            }
            if ("RIGHT".equals(type)) {
                MenuInput.requestInt(bukkitPlayer, "理想配比", "份数", weight, 1, MAX_PORTION,
                        value -> {
                            draft.perfect().put(key, value);
                            open(bukkitPlayer, draft);
                        },
                        () -> open(bukkitPlayer, draft));
                return;
            }
            AccurateEditMenu.pickItem(bukkitPlayer, click, "更换原料", key,
                    newKey -> replaceKey(draft.perfect(), key, newKey, weight),
                    () -> open(bukkitPlayer, draft));
        });
    }

    private static void replaceKey(Map<Key, Integer> perfect, Key oldKey, Key newKey, int weight) {
        if (oldKey.equals(newKey)) {
            return;
        }
        Map<Key, Integer> rebuilt = new LinkedHashMap<>(perfect.size());
        for (Map.Entry<Key, Integer> e : perfect.entrySet()) {
            if (e.getKey().equals(oldKey)) {
                rebuilt.put(newKey, weight);
            } else if (!e.getKey().equals(newKey)) {
                rebuilt.put(e.getKey(), e.getValue());
            }
        }
        perfect.clear();
        perfect.putAll(rebuilt);
    }

    private static GuiElement saveSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                       FlexRecipeDraft draft) {
        Item icon = MenuIcons.icon(MenuButton.SAVE, viewer,
                MenuIcons.text("保存", NamedTextColor.GREEN),
                MenuIcons.lore("写回原配置，下一批次使用新值"));
        return MenuIcons.button(icon, () -> {
            RecipeMenus.message(bukkitPlayer, "正在保存食谱...");
            var saving = RecipeEditSaves.save(draft, () -> RecipeEditService.saveFlex(draft));
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
                        RecipeListMenu.open(bukkitPlayer, draft.cook(), true);
                    }));
        });
    }

    private static GuiElement deleteSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                         FlexRecipeDraft draft) {
        if (draft.isNew()) {
            return MenuIcons.filler(viewer);
        }
        Item icon = MenuIcons.icon(MenuButton.DELETE, viewer,
                MenuIcons.text("删除该食谱", NamedTextColor.RED),
                MenuIcons.lore("同时从配置文件里移除"));
        return MenuIcons.button(icon, () -> ConfirmMenu.open(bukkitPlayer, "删除食谱",
                List.of(draft.originalId().asString()),
                () -> {
                    // 用 originalId 回查注册表里的原始配方 draft 上的 id 与内容都可能已被改过
                    FlexFoodRecipe existing = draft.originalRecipe();
                    if (existing == null) {
                        RecipeMenus.message(bukkitPlayer, "食谱已经不存在");
                        RecipeListMenu.open(bukkitPlayer, draft.cook(), true);
                        return;
                    }
                    RecipeMenus.message(bukkitPlayer, "正在删除食谱...");
                    RecipeEditService.deleteFlex(existing).thenAccept(success ->
                            MenuTasks.runFor(bukkitPlayer, () -> {
                                RecipeMenus.message(bukkitPlayer, success
                                        ? "已删除 " + draft.originalId().asString()
                                        : "配置文件写入失败，食谱未删除");
                                RecipeListMenu.open(bukkitPlayer, draft.cook(), true);
                            }));
                },
                () -> open(bukkitPlayer, draft)));
    }
}
