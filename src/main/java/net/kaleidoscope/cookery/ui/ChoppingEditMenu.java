package net.kaleidoscope.cookery.ui;
import net.kaleidoscope.cookery.api.ui.MenuButton;

import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.ChoppingBoardRecipe;
import net.kaleidoscope.cookery.recipe.ChoppingMode;
import net.kaleidoscope.cookery.recipe.ChoppingResult;
import net.kaleidoscope.cookery.recipe.edit.ChoppingRecipeDraft;
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
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.libraries.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;

// 砧板配方编辑 I 原料 T id N 切几刀 M 产出模式 V 模型前缀 R 主产物 E 附带产物
public final class ChoppingEditMenu {
    private ChoppingEditMenu() {
    }

    private static final int MAX_RESULTS = 7;

    private static final List<DialogChoicePrompt.Choice> MODE_CHOICES = List.of(
            new DialogChoicePrompt.Choice("单产物 按权重选一个", ChoppingMode.SINGLE.name()),
            new DialogChoicePrompt.Choice("单产物 + 附带", ChoppingMode.SINGLE_EXTRA.name()),
            new DialogChoicePrompt.Choice("多产物 各自判定", ChoppingMode.MULTI_RANDOM.name()));

    public static void open(org.bukkit.entity.Player bukkitPlayer, ChoppingRecipeDraft draft) {
        Player viewer = RecipeMenus.adapt(bukkitPlayer);
        if (viewer == null) {
            return;
        }
        GuiLayout layout = new GuiLayout(
                "#########",
                "#I#T#N#M#",
                "#V#######",
                "#RRRRRRR#",
                "#EEEEEEE#",
                "B###S###D");
        layout.addIngredient('#', Ingredient.simple(MenuIcons.filler(viewer)));
        layout.addIngredient('I', inputSlot(bukkitPlayer, viewer, draft));
        layout.addIngredient('T', idSlot(bukkitPlayer, viewer, draft));
        layout.addIngredient('N', stageSlot(bukkitPlayer, viewer, draft));
        layout.addIngredient('M', modeSlot(bukkitPlayer, viewer, draft));
        layout.addIngredient('V', modelSlot(bukkitPlayer, viewer, draft));
        layout.addIngredient('R', resultSlots(bukkitPlayer, viewer, draft, false));
        layout.addIngredient('E', resultSlots(bukkitPlayer, viewer, draft, true));
        layout.addIngredient('B', MenuIcons.back(viewer,
                () -> RecipeListMenu.open(bukkitPlayer, ApplianceType.CHOPPING_BOARD, true)));
        layout.addIngredient('S', saveSlot(bukkitPlayer, viewer, draft));
        layout.addIngredient('D', deleteSlot(bukkitPlayer, viewer, draft));

        Gui gui = BasicGuiImpl.builder()
                .layout(layout)
                .inventoryClickConsumer(RecipeMenus.inventoryGuard())
                .build();
        gui.title(MenuIcons.text(draft.isNew() ? "新建砧板食谱" : "编辑砧板食谱", NamedTextColor.DARK_GRAY))
                .refresh()
                .open(viewer);
    }

    private static GuiElement inputSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                        ChoppingRecipeDraft draft) {
        Item icon = MenuIcons.icon(draft.input(), viewer,
                MenuIcons.text("原料", NamedTextColor.GOLD),
                MenuIcons.loreNamed(draft.input(),
                        "光标持物品左键 直接取该物品",
                        "空手左键 手动输入物品 id"));
        return GuiElement.constant(icon, (element, click) -> {
            click.cancel();
            AccurateEditMenu.pickItem(bukkitPlayer, click, "设置原料", draft.input(),
                    draft::input, () -> open(bukkitPlayer, draft));
        });
    }

    private static GuiElement idSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                     ChoppingRecipeDraft draft) {
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

    private static GuiElement stageSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                        ChoppingRecipeDraft draft) {
        Item icon = MenuIcons.icon(MenuButton.COUNT, viewer,
                MenuIcons.text("需要切 " + draft.stage() + " 刀", NamedTextColor.GOLD),
                MenuIcons.lore("左键修改", "切满这个次数才出成品"));
        return MenuIcons.button(icon, () -> MenuInput.requestInt(bukkitPlayer, "切几刀", "次数",
                draft.stage(), 1, ChoppingRecipeDraft.MAX_STAGE,
                value -> {
                    draft.stage(value);
                    open(bukkitPlayer, draft);
                },
                () -> open(bukkitPlayer, draft)));
    }

    private static GuiElement modeSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                       ChoppingRecipeDraft draft) {
        Item icon = MenuIcons.icon(MenuButton.MODE, viewer,
                MenuIcons.text("产出模式 " + modeName(draft.mode()), NamedTextColor.GOLD),
                MenuIcons.lore("左键切换",
                        "单产物 权重是相对值 必出其一",
                        "其余模式 权重当百分比各自判定"));
        return MenuIcons.button(icon, () -> DialogChoicePrompt.open(bukkitPlayer, "产出模式",
                "决定权重怎么解释 以及有没有附带产物",
                MODE_CHOICES,
                value -> {
                    draft.mode(ChoppingMode.valueOf(value));
                    open(bukkitPlayer, draft);
                },
                () -> open(bukkitPlayer, draft),
                () -> open(bukkitPlayer, draft)));
    }

    private static String modeName(ChoppingMode mode) {
        return switch (mode) {
            case SINGLE -> "单产物";
            case SINGLE_EXTRA -> "单产物+附带";
            case MULTI_RANDOM -> "多产物";
        };
    }

    // 模型前缀留空就不换模型 砧板上直接展示放上去的物品
    private static GuiElement modelSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                        ChoppingRecipeDraft draft) {
        String prefix = draft.modelPrefix();
        List<String> lore = new ArrayList<>();
        lore.add(prefix == null ? "未设置 切的时候不换模型" : prefix);
        lore.add("配方需要 " + draft.stage() + " 个模型 前缀/0 到 前缀/" + (draft.stage() - 1));
        lore.add("左键修改  右键清空");
        Item icon = MenuIcons.icon(prefix == null ? MenuIcons.iconKey(MenuButton.CARRIER_NONE) : MenuIcons.iconKey(MenuButton.CREATE), viewer,
                MenuIcons.text("分阶段模型前缀", prefix == null ? NamedTextColor.GRAY : NamedTextColor.AQUA),
                MenuIcons.lore(lore.toArray(new String[0])));
        return GuiElement.constant(icon, (element, click) -> {
            click.cancel();
            if ("RIGHT".equals(click.type()) || "SHIFT_RIGHT".equals(click.type())) {
                draft.modelPrefix(null);
                open(bukkitPlayer, draft);
                return;
            }
            MenuInput.requestText(bukkitPlayer, "分阶段模型前缀", "前缀",
                    prefix == null ? "" : prefix,
                    raw -> {
                        draft.modelPrefix(raw);
                        open(bukkitPlayer, draft);
                    },
                    () -> open(bukkitPlayer, draft));
        });
    }

    private static Ingredient resultSlots(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                          ChoppingRecipeDraft draft, boolean extra) {
        return new Ingredient() {
            private int index = 0;

            @Override
            public GuiElement element(Gui gui) {
                return resultSlot(bukkitPlayer, viewer, draft, extra, this.index++);
            }
        };
    }

    private static GuiElement resultSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                         ChoppingRecipeDraft draft, boolean extra, int index) {
        // 附带产物只有 SINGLE_EXTRA 用得上 其它模式整排隐藏 免得编辑了却不生效
        if (extra && draft.mode() != ChoppingMode.SINGLE_EXTRA) {
            return MenuIcons.filler(viewer);
        }
        List<ChoppingResult> list = extra ? draft.extras() : draft.results();
        if (index < list.size()) {
            return existingResult(bukkitPlayer, viewer, draft, list, index);
        }
        if (index > list.size() || list.size() >= MAX_RESULTS) {
            return MenuIcons.empty();
        }
        Item icon = MenuIcons.icon(MenuButton.ADD, viewer,
                MenuIcons.text(extra ? "添加附带产物" : "添加成品", NamedTextColor.GREEN),
                MenuIcons.lore("光标持物品左键 直接取该物品", "空手左键 手动输入物品 id"));
        return GuiElement.constant(icon, (element, click) -> {
            click.cancel();
            AccurateEditMenu.pickItem(bukkitPlayer, click, extra ? "添加附带产物" : "添加成品", null,
                    key -> list.add(new ChoppingResult(key, 1, ChoppingRecipeDraft.DEFAULT_WEIGHT)),
                    () -> open(bukkitPlayer, draft));
        });
    }

    private static GuiElement existingResult(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                             ChoppingRecipeDraft draft, List<ChoppingResult> list, int index) {
        ChoppingResult result = list.get(index);
        List<Component> lore = MenuIcons.loreNamed(result.key(),
                "数量 " + result.count(),
                draft.mode() == ChoppingMode.SINGLE ? "相对权重 " + result.weight() : "概率 " + result.weight() + "%");
        lore.add(MenuIcons.text("左键改数量", NamedTextColor.YELLOW));
        lore.add(MenuIcons.text("右键改权重", NamedTextColor.YELLOW));
        lore.add(MenuIcons.text("Shift+右键 移除", NamedTextColor.RED));
        Item icon = MenuIcons.icon(result.key(), viewer,
                MenuIcons.itemName(result.key()).colorIfAbsent(NamedTextColor.GOLD), lore);
        return GuiElement.constant(icon, (element, click) -> {
            click.cancel();
            String type = click.type();
            if ("SHIFT_RIGHT".equals(type)) {
                list.remove(index);
                open(bukkitPlayer, draft);
                return;
            }
            if ("RIGHT".equals(type)) {
                MenuInput.requestInt(bukkitPlayer, "权重", "值", result.weight(), 1, 100,
                        value -> {
                            list.set(index, new ChoppingResult(result.key(), result.count(), value));
                            open(bukkitPlayer, draft);
                        },
                        () -> open(bukkitPlayer, draft));
                return;
            }
            MenuInput.requestInt(bukkitPlayer, "数量", "值", result.count(), 1, 64,
                    value -> {
                        list.set(index, new ChoppingResult(result.key(), value, result.weight()));
                        open(bukkitPlayer, draft);
                    },
                    () -> open(bukkitPlayer, draft));
        });
    }

    private static GuiElement saveSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                       ChoppingRecipeDraft draft) {
        Item icon = MenuIcons.icon(MenuButton.SAVE, viewer,
                MenuIcons.text("保存", NamedTextColor.GREEN));
        return MenuIcons.button(icon, () -> {
            RecipeMenus.message(bukkitPlayer, "正在保存食谱...");
            RecipeEditService.saveChopping(draft).thenAccept(error ->
                    MenuTasks.runFor(bukkitPlayer, () -> {
                        if (error != null) {
                            RecipeMenus.message(bukkitPlayer, error);
                            open(bukkitPlayer, draft);
                            return;
                        }
                        RecipeMenus.message(bukkitPlayer, "已保存 " + draft.id().asString());
                        RecipeListMenu.open(bukkitPlayer, ApplianceType.CHOPPING_BOARD, true);
                    }));
        });
    }

    private static GuiElement deleteSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer,
                                         ChoppingRecipeDraft draft) {
        if (draft.isNew()) {
            return MenuIcons.filler(viewer);
        }
        Item icon = MenuIcons.icon(MenuButton.DELETE, viewer,
                MenuIcons.text("删除", NamedTextColor.RED));
        return MenuIcons.button(icon, () -> ConfirmMenu.open(bukkitPlayer, "删除砧板食谱",
                List.of(draft.originalId().asString()),
                () -> {
                    ChoppingBoardRecipe existing = draft.originalRecipe();
                    if (existing == null) {
                        RecipeMenus.message(bukkitPlayer, "食谱已经不存在");
                        RecipeListMenu.open(bukkitPlayer, ApplianceType.CHOPPING_BOARD, true);
                        return;
                    }
                    RecipeMenus.message(bukkitPlayer, "正在删除食谱...");
                    RecipeEditService.deleteChopping(existing).thenAccept(success ->
                            MenuTasks.runFor(bukkitPlayer, () -> {
                                RecipeMenus.message(bukkitPlayer, success
                                        ? "已删除 " + draft.originalId().asString()
                                        : "配置文件写入失败，食谱未删除");
                                RecipeListMenu.open(bukkitPlayer, ApplianceType.CHOPPING_BOARD, true);
                            }));
                },
                () -> open(bukkitPlayer, draft)));
    }
}
