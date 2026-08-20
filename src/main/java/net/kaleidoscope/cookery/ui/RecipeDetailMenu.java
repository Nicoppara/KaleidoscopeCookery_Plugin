package net.kaleidoscope.cookery.ui;

import net.kaleidoscope.cookery.api.ui.RecipeMenuHooks;

import net.kaleidoscope.cookery.api.ui.MenuButton;
import net.kaleidoscope.cookery.api.ui.MenuScreen;
import net.kaleidoscope.cookery.api.ui.RecipeMenuStyle;

import net.kaleidoscope.cookery.recipe.AccurateFoodRecipe;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.ChoppingBoardRecipe;
import net.kaleidoscope.cookery.recipe.ChoppingResult;
import net.kaleidoscope.cookery.recipe.FlexFoodRecipe;
import net.kaleidoscope.cookery.recipe.TeapotRecipe;
import net.kaleidoscope.cookery.recipe.WeightedResult;
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
import java.util.Map;

// 只读的食谱详情 原料与成品各占一排真实物品图标 比挤在 lore 里直观
// 没有任何可写入口 所有点击一律 cancel 浏览权限的玩家也能安全打开
public final class RecipeDetailMenu {
    private RecipeDetailMenu() {
    }

    // R 成品 I 原料 C 盛装容器 L 汤底 B 返回
    private static final String[] LAYOUT = {
            "#########",
            "#IIIIIII#",
            "#########",
            "##R#C#L##",
            "#B#######"};

    private static final int MAX_SLOTS = 7;

    public static void openAccurate(org.bukkit.entity.Player bukkitPlayer, AccurateFoodRecipe recipe,
                                    Runnable back) {
        if (RecipeMenuHooks.instance().dispatchDetail(bukkitPlayer, recipe.cook(), recipe.id())) {
            return;
        }
        Player viewer = RecipeMenus.adapt(bukkitPlayer);
        if (viewer == null) {
            return;
        }
        List<Item> results = new ArrayList<>();
        for (WeightedResult r : recipe.results()) {
            List<Component> lore = MenuIcons.lore("产出 " + recipe.resultCount() + " 份");
            lore.add(MenuIcons.gray(r.weight() >= 100 ? "必定产出" : "概率 " + r.weight() + "%"));
            results.add(MenuIcons.icon(r.key(), viewer,
                    MenuIcons.itemName(r.key()).colorIfAbsent(NamedTextColor.GOLD), lore));
        }
        List<Item> inputs = List.of(MenuIcons.icon(recipe.input(), viewer,
                MenuIcons.itemName(recipe.input()).colorIfAbsent(NamedTextColor.WHITE),
                MenuIcons.lore("需要 1 个")));

        List<Component> extra = new ArrayList<>();
        if (recipe.cook() == ApplianceType.MILLSTONE && recipe.rotations() > 0) {
            extra.add(MenuIcons.gray("研磨圈数 " + recipe.rotations()));
        }
        openTitled(viewer, RecipeMenuStyle.instance().title(MenuScreen.DETAIL_ACCURATE, "recipe",
                MenuIcons.itemNameText(recipe.primaryResult())), inputs, results, null, List.of(), extra, back);
    }

    public static void openFlex(org.bukkit.entity.Player bukkitPlayer, FlexFoodRecipe recipe, Runnable back) {
        if (RecipeMenuHooks.instance().dispatchDetail(bukkitPlayer, recipe.cook(), recipe.id())) {
            return;
        }
        Player viewer = RecipeMenus.adapt(bukkitPlayer);
        if (viewer == null) {
            return;
        }
        List<Item> inputs = new ArrayList<>();
        for (Map.Entry<Key, Integer> e : recipe.perfect().entrySet()) {
            inputs.add(MenuIcons.icon(e.getKey(), viewer,
                    MenuIcons.itemName(e.getKey()).colorIfAbsent(NamedTextColor.WHITE),
                    MenuIcons.lore("理想配比 " + e.getValue())));
        }
        List<Item> results = List.of(MenuIcons.icon(recipe.result(), viewer,
                MenuIcons.itemName(recipe.result()).colorIfAbsent(NamedTextColor.GOLD),
                MenuIcons.lore("按凑齐的套数产出")));

        List<Component> extra = List.of(
                MenuIcons.gray("投料越接近理想配比 品质越高"),
                MenuIcons.gray("多放杂料会拉低品质"));
        openTitled(viewer, RecipeMenuStyle.instance().title(MenuScreen.DETAIL_FLEX, "recipe",
                MenuIcons.itemNameText(recipe.result())), inputs, results,
                recipe.carrier(), recipe.liquids(), extra, back);
    }


    public static void openChopping(org.bukkit.entity.Player bukkitPlayer,
                                    ChoppingBoardRecipe recipe, Runnable back) {
        if (RecipeMenuHooks.instance().dispatchDetail(bukkitPlayer, ApplianceType.CHOPPING_BOARD, recipe.id())) {
            return;
        }
        Player viewer = RecipeMenus.adapt(bukkitPlayer);
        if (viewer == null) {
            return;
        }
        List<Item> inputs = List.of(MenuIcons.icon(recipe.input(), viewer,
                MenuIcons.itemName(recipe.input()).colorIfAbsent(NamedTextColor.WHITE),
                MenuIcons.lore("需要切 " + recipe.stage() + " 刀")));
        List<Item> results = new ArrayList<>();
        for (ChoppingResult r : recipe.results()) {
            results.add(MenuIcons.icon(r.key(), viewer,
                    MenuIcons.itemName(r.key()).colorIfAbsent(NamedTextColor.GOLD),
                    MenuIcons.lore("数量 " + r.count(), "权重 " + r.weight())));
        }
        List<Component> extra = new ArrayList<>();
        extra.add(MenuIcons.gray("产出模式 " + recipe.mode().name().toLowerCase()));
        if (recipe.values().isEmpty()) {
            extra.add(MenuIcons.gray("切的时候不换模型"));
        }
        Key titleKey = recipe.results().isEmpty() ? recipe.input() : recipe.results().get(0).key();
        openTitled(viewer, RecipeMenuStyle.instance().title(MenuScreen.DETAIL_CHOPPING, "recipe",
                MenuIcons.itemNameText(titleKey)), inputs, results, null, List.of(), extra, back);
    }

    public static void openTeapot(org.bukkit.entity.Player bukkitPlayer,
                                  TeapotRecipe recipe, Runnable back) {
        if (RecipeMenuHooks.instance().dispatchDetail(bukkitPlayer, ApplianceType.TEAPOT, recipe.id())) {
            return;
        }
        Player viewer = RecipeMenus.adapt(bukkitPlayer);
        if (viewer == null) {
            return;
        }
        List<Item> inputs = List.of(MenuIcons.icon(recipe.input(), viewer,
                MenuIcons.itemName(recipe.input()).colorIfAbsent(NamedTextColor.WHITE),
                MenuIcons.lore("消耗 " + recipe.ingredientCount() + " 个")));
        List<Item> results = List.of(MenuIcons.icon(recipe.result(), viewer,
                MenuIcons.itemName(recipe.result()).colorIfAbsent(NamedTextColor.GOLD),
                MenuIcons.lore("产出 " + recipe.resultCount() + " 个")));
        List<Component> extra = List.of(MenuIcons.gray("熬煮 " + recipe.time() + " tick"));
        openTitled(viewer, RecipeMenuStyle.instance().title(MenuScreen.DETAIL_TEAPOT, "recipe",
                MenuIcons.itemNameText(recipe.result())), inputs, results,
                null, List.of(recipe.fluid()), extra, back);
    }

    private static void openTitled(Player viewer, Component title, List<Item> inputs, List<Item> results,
                             Key carrier, List<Key> liquids, List<Component> extra, Runnable back) {
        GuiLayout layout = new GuiLayout(LAYOUT);
        layout.addIngredient('#', Ingredient.simple(MenuIcons.filler(viewer)));

        // 原料超过一排就截断 并在成品的 lore 里说明 免得玩家以为配方只有这些
        List<Item> shown = inputs.size() > MAX_SLOTS ? inputs.subList(0, MAX_SLOTS) : inputs;
        List<GuiElement> slots = new ArrayList<>();
        for (Item item : shown) {
            slots.add(MenuIcons.button(item, () -> {}));
        }
        while (slots.size() < MAX_SLOTS) {
            slots.add(MenuIcons.empty());
        }
        // Ingredient 没有列表工厂 按调用顺序逐格取 和编辑页的写法一致
        layout.addIngredient('I', new Ingredient() {
            private int index = 0;

            @Override
            public GuiElement element(Gui gui) {
                return index < slots.size() ? slots.get(index++) : MenuIcons.empty();
            }
        });

        List<Component> resultLore = new ArrayList<>(extra);
        if (inputs.size() > MAX_SLOTS) {
            resultLore.add(MenuIcons.gray("另有 " + (inputs.size() - MAX_SLOTS) + " 种原料未显示"));
        }
        Item result = results.isEmpty()
                ? MenuIcons.icon(MenuButton.INVALID, viewer, MenuIcons.text("无成品", NamedTextColor.RED))
                : results.get(0);
        layout.addIngredient('R', MenuIcons.button(withLore(viewer, result, resultLore), () -> {}));

        layout.addIngredient('C', MenuIcons.button(carrierIcon(viewer, carrier), () -> {}));
        layout.addIngredient('L', MenuIcons.button(liquidIcon(viewer, liquids), () -> {}));
        layout.addIngredient('B', MenuIcons.back(viewer, back));

        Gui gui = BasicGuiImpl.builder()
                .layout(layout)
                .inventoryClickConsumer(RecipeMenus.inventoryGuard())
                .build();
        gui.title(title)
                .refresh()
                .open(viewer);
    }

    private static Item withLore(Player viewer, Item base, List<Component> lore) {
        if (lore.isEmpty()) {
            return base;
        }
        base.loreComponent(lore);
        return base;
    }

    private static Item carrierIcon(Player viewer, Key carrier) {
        if (carrier == null) {
            return MenuIcons.icon(MenuButton.FILLER, viewer,
                    MenuIcons.text("空手盛出", NamedTextColor.GREEN),
                    MenuIcons.lore("这道菜不需要容器"));
        }
        return MenuIcons.icon(carrier, viewer,
                Component.text("盛装容器 ").append(MenuIcons.itemName(carrier)).colorIfAbsent(NamedTextColor.AQUA),
                MenuIcons.lore("手持它右键锅盛出", "每份消耗一个"));
    }

    private static Item liquidIcon(Player viewer, List<Key> liquids) {
        if (liquids.isEmpty()) {
            return MenuIcons.icon(MenuButton.FILLER, viewer,
                    MenuIcons.text("不限汤底", NamedTextColor.GRAY));
        }
        List<Component> lore = new ArrayList<>();
        for (Key liquid : liquids) {
            lore.add(MenuIcons.grayWith(liquid));
        }
        return MenuIcons.icon(MenuButton.LIQUID, viewer,
                MenuIcons.text("限定汤底", NamedTextColor.AQUA), lore);
    }
}
