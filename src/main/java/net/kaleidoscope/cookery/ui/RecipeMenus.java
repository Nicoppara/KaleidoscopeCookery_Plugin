package net.kaleidoscope.cookery.ui;
import net.kaleidoscope.cookery.api.ui.RecipeMenuHooks;

import net.kaleidoscope.cookery.api.ui.MenuButton;
import net.kaleidoscope.cookery.api.ui.MenuScreen;
import net.kaleidoscope.cookery.api.ui.RecipeMenuStyle;

import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.FoodGroups;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.SoupBaseRegistry;
import net.kaleidoscope.cookery.recipe.edit.AccurateRecipeDraft;
import net.kaleidoscope.cookery.recipe.edit.ChoppingRecipeDraft;
import net.kaleidoscope.cookery.recipe.edit.TeapotRecipeDraft;
import net.kaleidoscope.cookery.recipe.edit.FlexRecipeDraft;
import net.kaleidoscope.cookery.ui.input.MenuInput;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.plugin.gui.BasicGuiImpl;
import net.momirealms.craftengine.core.plugin.gui.Click;
import net.momirealms.craftengine.core.plugin.gui.Gui;
import net.momirealms.craftengine.core.plugin.gui.GuiElement;
import net.momirealms.craftengine.core.plugin.gui.GuiLayout;
import net.momirealms.craftengine.core.plugin.gui.Ingredient;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.libraries.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

// 食谱菜单的入口与共用导航 首页选厨具 再进该厨具的配方列表
public final class RecipeMenus {
    // 菜谱菜单支持的厨具
    public static final List<ApplianceType> SUPPORTED = List.of(
            ApplianceType.POT,
            ApplianceType.STOCKPOT,
            ApplianceType.STEAMER,
            ApplianceType.SHAWARMA,
            ApplianceType.MILLSTONE,
            ApplianceType.CHOPPING_BOARD,
            ApplianceType.TEAPOT);

    // 只有炒锅与高汤锅有模糊配方 解析器也只给这两种注册了 见 FoodRecipeManager
    private static final Set<ApplianceType> FLEX_CAPABLE =
            Set.of(ApplianceType.POT, ApplianceType.STOCKPOT);

    // shift 点击与双击能把玩家背包的物品塞进菜单 必须拦 其余菜单槽点击由各元素自己 cancel
    private static final Set<String> MOVE_INTO_GUI = Set.of("SHIFT_LEFT", "SHIFT_RIGHT", "DOUBLE_CLICK");

    private RecipeMenus() {
    }

    public static boolean supportsFlex(ApplianceType cook) {
        return FLEX_CAPABLE.contains(cook);
    }

    public static Consumer<Click> inventoryGuard() {
        return click -> {
            if (MOVE_INTO_GUI.contains(click.type())) {
                click.cancel();
            }
        };
    }

    public static Player adapt(org.bukkit.entity.Player player) {
        return BukkitAdaptor.adapt(player);
    }

    // 首页 editable 决定进的是编辑列表还是只读浏览列表
    // 外部插件注册了 RecipeMenuProvider 且接管了本屏 就不再开内置菜单
    public static void openHome(org.bukkit.entity.Player bukkitPlayer, boolean editable) {
        if (RecipeMenuHooks.instance().dispatchHome(bukkitPlayer, editable)) {
            return;
        }
        Player viewer = adapt(bukkitPlayer);
        if (viewer == null) {
            return;
        }
        // 槽位字符要够 SUPPORTED 里所有厨具用 少了会被静默丢掉
        GuiLayout layout = new GuiLayout(
                "#########",
                "#ABCDEFG#",
                "###L#Z###");
        layout.addIngredient('#', Ingredient.simple(MenuIcons.filler(viewer)));
        char slot = 'A';
        for (ApplianceType cook : SUPPORTED) {
            layout.addIngredient(slot++, applianceButton(bukkitPlayer, viewer, cook, editable));
        }
        // 厨具数少于槽位时把剩下的字符补成边框 否则那几格会是空的
        while (slot <= 'G') {
            layout.addIngredient(slot++, Ingredient.simple(MenuIcons.filler(viewer)));
        }
        // 汤底表与食材分组都是全局注册表 不属于任何一条配方 只在编辑模式露出来
        layout.addIngredient('L', Ingredient.simple(editable
                ? soupBaseButton(bukkitPlayer, viewer)
                : MenuIcons.filler(viewer)));
        layout.addIngredient('Z', Ingredient.simple(editable
                ? foodGroupButton(bukkitPlayer, viewer)
                : MenuIcons.filler(viewer)));
        Gui gui = BasicGuiImpl.builder()
                .layout(layout)
                .inventoryClickConsumer(inventoryGuard())
                .build();
        gui.title(RecipeMenuStyle.instance().title(editable ? MenuScreen.HOME_EDIT : MenuScreen.HOME_BROWSE))
                .refresh()
                .open(viewer);
    }

    // 高汤锅的汤底表入口 桶 -> 液面 加了新桶后各配方的限定汤底里就能选到
    private static GuiElement soupBaseButton(org.bukkit.entity.Player bukkitPlayer, Player viewer) {
        int count = SoupBaseRegistry.instance().keys().size();
        return MenuIcons.button(
                MenuIcons.icon(MenuButton.LIQUID, viewer,
                        MenuIcons.text("汤底表", NamedTextColor.AQUA),
                        MenuIcons.lore("已登记 " + count + " 种",
                                "决定高汤锅能烧什么 以及灶口画成什么液面",
                                "左键进入编辑")),
                () -> SoupBaseMenu.open(bukkitPlayer));
    }

    // 食材分组入口 等效食材与调味品 只影响炒锅与高汤锅的模糊匹配
    private static GuiElement foodGroupButton(org.bukkit.entity.Player bukkitPlayer, Player viewer) {
        FoodGroups groups = FoodGroups.instance();
        return MenuIcons.button(
                MenuIcons.icon(MenuButton.MODE, viewer,
                        MenuIcons.text("食材分组", NamedTextColor.AQUA),
                        MenuIcons.lore("等效食材 " + groups.equivalentTagCount() + " 组"
                                        + " 调味品 " + groups.seasoningTagCount() + " 组",
                                "等效食材同组互相顶替 调味品只占位不影响品质",
                                "左键进入编辑")),
                () -> FoodGroupMenu.open(bukkitPlayer));
    }

    private static GuiElement applianceButton(
            org.bukkit.entity.Player bukkitPlayer, Player viewer, ApplianceType cook, boolean editable) {
        int count = FoodRecipeRegistry.instance().recipeCount(cook);
        return MenuIcons.button(
                MenuIcons.icon(MenuIcons.iconOf(cook), viewer,
                        MenuIcons.text(MenuIcons.displayName(cook), NamedTextColor.GOLD),
                        MenuIcons.lore("已有配方 " + count + " 条", editable ? "左键进入编辑" : "左键查看")),
                () -> RecipeListMenu.open(bukkitPlayer, cook, editable));
    }

    // 创建入口 两类配方都存在时先让管理员选类型 只有精准可选时直接问 id
    public static void startCreate(org.bukkit.entity.Player bukkitPlayer, ApplianceType cook) {
        // 这两个厨具只有一种配方形态 不用先问精准还是模糊
        if (cook == ApplianceType.CHOPPING_BOARD || cook == ApplianceType.TEAPOT) {
            askIdThenCreate(bukkitPlayer, cook, true);
            return;
        }
        if (!supportsFlex(cook)) {
            askIdThenCreate(bukkitPlayer, cook, true);
            return;
        }
        Player viewer = adapt(bukkitPlayer);
        if (viewer == null) {
            return;
        }
        GuiLayout layout = new GuiLayout(
                "#########",
                "#  A#B  #",
                "R########");
        layout.addIngredient('#', Ingredient.simple(MenuIcons.filler(viewer)));
        layout.addIngredient('R', Ingredient.simple(
                MenuIcons.back(viewer, () -> RecipeListMenu.open(bukkitPlayer, cook, true))));
        layout.addIngredient('A', MenuIcons.button(
                MenuIcons.icon(MenuButton.CREATE, viewer,
                        MenuIcons.text("新建精准食谱", NamedTextColor.GREEN),
                        MenuIcons.lore("单一原料对应成品", "成品可多个 可设概率")),
                () -> askIdThenCreate(bukkitPlayer, cook, true)));
        layout.addIngredient('B', MenuIcons.button(
                MenuIcons.icon(MenuButton.ADD, viewer,
                        MenuIcons.text("新建模糊食谱", NamedTextColor.GREEN),
                        MenuIcons.lore("按理想配比匹配最近邻", "投料越贴近配比品质越高")),
                () -> askIdThenCreate(bukkitPlayer, cook, false)));
        Gui gui = BasicGuiImpl.builder()
                .layout(layout)
                .inventoryClickConsumer(inventoryGuard())
                .build();
        gui.title(RecipeMenuStyle.instance().title(MenuScreen.CREATE_PICK_TYPE, "appliance", MenuIcons.displayName(cook)))
                .refresh()
                .open(viewer);
    }

    private static void askIdThenCreate(org.bukkit.entity.Player bukkitPlayer, ApplianceType cook, boolean accurate) {
        String suggested = ItemKeys.NAMESPACE + ":" + cook.name().toLowerCase() + "_new";
        MenuInput.requestText(bukkitPlayer, "新建食谱", "食谱 id", suggested,
                raw -> {
                    Key id = parseKey(raw);
                    if (id == null) {
                        message(bukkitPlayer, "食谱 id 格式不正确");
                        RecipeListMenu.open(bukkitPlayer, cook, true);
                        return;
                    }
                    if (cook == ApplianceType.CHOPPING_BOARD) {
                        ChoppingEditMenu.open(bukkitPlayer, ChoppingRecipeDraft.creating(id));
                    } else if (cook == ApplianceType.TEAPOT) {
                        TeapotEditMenu.open(bukkitPlayer, TeapotRecipeDraft.creating(id));
                    } else if (accurate) {
                        AccurateEditMenu.open(bukkitPlayer, AccurateRecipeDraft.creating(cook, id));
                    } else {
                        FlexEditMenu.open(bukkitPlayer, FlexRecipeDraft.creating(cook, id));
                    }
                },
                () -> RecipeListMenu.open(bukkitPlayer, cook, true));
    }

    // 玩家手输的 id 一律走这里 Key.of 对非法输入会抛 不能让它冒到点击处理里
    public static Key parseKey(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Key.of(trimmed);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static void message(org.bukkit.entity.Player bukkitPlayer, String text) {
        Player viewer = adapt(bukkitPlayer);
        if (viewer != null) {
            viewer.sendMessage(Component.text(text).color(NamedTextColor.RED), false);
        }
    }
}
