package net.kaleidoscope.cookery.ui;

import net.kaleidoscope.cookery.api.ItemTags;
import net.kaleidoscope.cookery.api.ui.MenuButton;
import net.kaleidoscope.cookery.api.ui.MenuScreen;
import net.kaleidoscope.cookery.api.ui.RecipeMenuStyle;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.recipe.FoodGroups;
import net.kaleidoscope.cookery.recipe.edit.FoodGroupDraft;
import net.kaleidoscope.cookery.recipe.edit.RecipeEditService;
import net.kaleidoscope.cookery.ui.input.MenuInput;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.gui.GuiElement;
import net.momirealms.craftengine.core.plugin.gui.GuiLayout;
import net.momirealms.craftengine.core.plugin.gui.Ingredient;
import net.momirealms.craftengine.core.plugin.gui.ItemWithAction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.libraries.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;

// 食材分组一览 等效食材与调味品共用一张表 用途写在每条自己身上
// 这层是全局的 与单条配方的两个开关不是一回事 配方那两个只决定认不认这张表
public final class FoodGroupMenu {
    private FoodGroupMenu() {
    }

    public static void open(org.bukkit.entity.Player bukkitPlayer) {
        Player viewer = RecipeMenus.adapt(bukkitPlayer);
        if (viewer == null) {
            return;
        }
        List<Key> tags = FoodGroups.instance().tags();
        GuiLayout layout = new GuiLayout(
                "#########",
                "#XXXXXXX#",
                "#XXXXXXX#",
                "B<##N##>#");
        layout.addIngredient('#', Ingredient.simple(MenuIcons.filler(viewer)));
        layout.addIngredient('X', Ingredient.paged());
        layout.addIngredient('<', Ingredient.simple(MenuIcons.previousPage(viewer)));
        layout.addIngredient('>', Ingredient.simple(MenuIcons.nextPage(viewer)));
        layout.addIngredient('B', Ingredient.simple(MenuIcons.back(viewer,
                () -> RecipeMenus.openHome(bukkitPlayer, true))));
        layout.addIngredient('N', Ingredient.simple(createSlot(bukkitPlayer, viewer)));

        LazyPagedGui gui = new LazyPagedGui(layout, RecipeMenus.inventoryGuard(), tags.size(),
                (from, count) -> {
                    List<ItemWithAction> items = new ArrayList<>(count);
                    for (int i = from; i < from + count && i < tags.size(); i++) {
                        items.add(entry(bukkitPlayer, viewer, tags.get(i)));
                    }
                    return items;
                });
        gui.title(RecipeMenuStyle.instance().title(MenuScreen.FOOD_GROUP,
                        "count", String.valueOf(tags.size())))
                .refresh()
                .open(viewer);
    }

    // 图标用组里第一个成员 组空了才退回默认图标
    private static ItemWithAction entry(org.bukkit.entity.Player bukkitPlayer, Player viewer, Key tag) {
        FoodGroupDraft draft = FoodGroupDraft.editing(tag);
        FoodGroups.Kind kind = draft.kind();
        List<Key> members = draft.memberList();
        List<Component> lore = new ArrayList<>();
        lore.add(MenuIcons.grayWith(tag));
        lore.add(MenuIcons.text("用途 " + kind.displayName(),
                kind == FoodGroups.Kind.SEASONING ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.AQUA));
        for (Key member : members.size() > 5 ? members.subList(0, 5) : members) {
            lore.add(MenuIcons.grayWith("· ", member, ""));
        }
        if (members.size() > 5) {
            lore.add(MenuIcons.text("… 共 " + members.size() + " 个", NamedTextColor.DARK_GRAY));
        }
        lore.add(MenuIcons.text("左键编辑", NamedTextColor.YELLOW));
        lore.add(MenuIcons.text("Shift 右键删除", NamedTextColor.RED));

        Key iconKey = members.isEmpty() ? MenuIcons.iconKey(MenuButton.ADD) : members.getFirst();
        Item icon = MenuIcons.icon(iconKey, viewer,
                MenuIcons.text(tag.value(), NamedTextColor.GOLD), lore);
        return new ItemWithAction(icon, (element, click) -> {
            click.cancel();
            if ("SHIFT_RIGHT".equals(click.type())) {
                ConfirmMenu.open(bukkitPlayer, "删除食材分组", List.of(tag.asString()),
                        () -> RecipeEditService.deleteFoodGroup(tag).thenAccept(ok ->
                                MenuTasks.runFor(bukkitPlayer, () -> {
                                    RecipeMenus.message(bukkitPlayer, ok
                                            ? "已删除分组 " + tag.asString() : "删除失败 分组未改动");
                                    open(bukkitPlayer);
                                })),
                        () -> open(bukkitPlayer));
                return;
            }
            FoodGroupEditMenu.open(bukkitPlayer, FoodGroupDraft.editing(tag));
        });
    }

    private static GuiElement createSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer) {
        Item icon = MenuIcons.icon(MenuButton.CREATE, viewer,
                MenuIcons.text("新建食材分组", NamedTextColor.GREEN),
                MenuIcons.lore("左键输入标签 id", "再往里加物品并选用途"));
        return MenuIcons.button(icon, () -> MenuInput.requestText(bukkitPlayer, "标签 id", "id",
                ItemKeys.NAMESPACE + ":",
                raw -> {
                    Key key = RecipeMenus.parseKey(raw);
                    if (key == null) {
                        RecipeMenus.message(bukkitPlayer, "标签 id 格式不正确");
                        open(bukkitPlayer);
                    } else if (ItemTags.instance().exists(key)) {
                        RecipeMenus.message(bukkitPlayer, "标签 " + key.asString() + " 已存在");
                        open(bukkitPlayer);
                    } else {
                        FoodGroupEditMenu.open(bukkitPlayer, FoodGroupDraft.creating(key));
                    }
                },
                () -> open(bukkitPlayer)));
    }
}
