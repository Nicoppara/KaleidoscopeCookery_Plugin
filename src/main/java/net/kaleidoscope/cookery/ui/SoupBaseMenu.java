package net.kaleidoscope.cookery.ui;
import net.kaleidoscope.cookery.api.ui.MenuButton;
import net.kaleidoscope.cookery.api.ui.MenuScreen;
import net.kaleidoscope.cookery.api.ui.RecipeMenuStyle;

import net.kaleidoscope.cookery.recipe.SoupBaseRegistry;
import net.kaleidoscope.cookery.recipe.edit.RecipeEditService;
import net.kaleidoscope.cookery.ui.input.MenuInput;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.gui.GuiElement;
import net.momirealms.craftengine.core.plugin.gui.GuiLayout;
import net.momirealms.craftengine.core.plugin.gui.Ingredient;
import net.momirealms.craftengine.core.plugin.gui.ItemWithAction;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.libraries.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;

// 汤底表编辑 对应 stock_food_raw.liquid 桶 -> 液面展示模型
// 这一层是全局注册表 与单条配方的限定汤底不是一回事 所以单开一个菜单
public final class SoupBaseMenu {
    private SoupBaseMenu() {
    }

    public static void open(org.bukkit.entity.Player bukkitPlayer) {
        Player viewer = RecipeMenus.adapt(bukkitPlayer);
        if (viewer == null) {
            return;
        }
        List<Key> bases = SoupBaseRegistry.instance().keys();
        GuiLayout layout = new GuiLayout(
                "#########",
                "#XXXXXXX#",
                "#XXXXXXX#",
                "B<##N##>#");
        layout.addIngredient('#', Ingredient.simple(MenuIcons.filler(viewer)));
        layout.addIngredient('X', Ingredient.paged());
        layout.addIngredient('<', Ingredient.simple(MenuIcons.previousPage(viewer)));
        layout.addIngredient('>', Ingredient.simple(MenuIcons.nextPage(viewer)));
        layout.addIngredient('B', Ingredient.simple(MenuIcons.back(viewer, () -> RecipeMenus.openHome(bukkitPlayer, true))));
        layout.addIngredient('N', Ingredient.simple(createSlot(bukkitPlayer, viewer)));

        LazyPagedGui gui = new LazyPagedGui(layout, RecipeMenus.inventoryGuard(), bases.size(),
                (from, count) -> {
                    List<ItemWithAction> items = new ArrayList<>(count);
                    for (int i = from; i < from + count && i < bases.size(); i++) {
                        items.add(entry(bukkitPlayer, viewer, bases.get(i)));
                    }
                    return items;
                });
        gui.title(RecipeMenuStyle.instance().title(MenuScreen.SOUP_BASE, "count", String.valueOf(bases.size())))
                .refresh()
                .open(viewer);
    }

    // 图标用桶本身 液面模型是 show: 命名空间的展示物品 拿它当图标多半渲染不出来
    private static ItemWithAction entry(org.bukkit.entity.Player bukkitPlayer, Player viewer, Key bucket) {
        Key show = SoupBaseRegistry.instance().registeredShow(bucket);
        List<Component> lore = new ArrayList<>();
        lore.add(MenuIcons.grayWith(bucket));
        lore.add(MenuIcons.grayWith("液面 ", show == null ? SoupBaseRegistry.DEFAULT_SHOW : show, ""));
        lore.add(MenuIcons.text("左键改液面模型 id", NamedTextColor.YELLOW));
        lore.add(MenuIcons.text("Shift 右键删除", NamedTextColor.RED));
        Item icon = MenuIcons.icon(bucket, viewer,
                MenuIcons.itemName(bucket).color(NamedTextColor.GOLD), lore);
        return new ItemWithAction(icon, (element, click) -> {
            click.cancel();
            if ("SHIFT_RIGHT".equals(click.type())) {
                ConfirmMenu.open(bukkitPlayer, "删除汤底", List.of(bucket.asString()),
                        () -> {
                            RecipeEditService.deleteSoupBase(bucket);
                            RecipeMenus.message(bukkitPlayer, "已删除汤底 " + bucket.asString());
                            open(bukkitPlayer);
                        },
                        () -> open(bukkitPlayer));
                return;
            }
            askShow(bukkitPlayer, bucket, show);
        });
    }

    // 新增汤底 光标上拿着桶就直接用它 否则手输 id
    private static GuiElement createSlot(org.bukkit.entity.Player bukkitPlayer, Player viewer) {
        Item icon = MenuIcons.icon(MenuButton.CREATE, viewer,
                MenuIcons.text("新增汤底", NamedTextColor.GREEN),
                MenuIcons.lore("光标持物品左键 直接用它当汤底",
                        "空手左键 手动输入物品 id"));
        return GuiElement.constant(icon, (element, click) -> {
            click.cancel();
            if (!ItemUtils.isEmpty(click.itemOnCursor())) {
                askShow(bukkitPlayer, click.itemOnCursor().id(), null);
                return;
            }
            MenuInput.requestText(bukkitPlayer, "汤底物品 id", "id", "minecraft:",
                    raw -> {
                        Key key = RecipeMenus.parseKey(raw);
                        if (key == null) {
                            RecipeMenus.message(bukkitPlayer, "物品 id 格式不正确");
                            open(bukkitPlayer);
                        } else {
                            askShow(bukkitPlayer, key, null);
                        }
                    },
                    () -> open(bukkitPlayer));
        });
    }

    // 液面模型留空即默认水面 只加新桶时不必先做模型
    private static void askShow(org.bukkit.entity.Player bukkitPlayer, Key bucket, Key current) {
        Key initial = current == null ? SoupBaseRegistry.DEFAULT_SHOW : current;
        MenuInput.requestText(bukkitPlayer, "液面模型 id", "留空用默认水面", initial.asString(),
                raw -> {
                    Key show = raw == null || raw.isBlank() ? null : RecipeMenus.parseKey(raw);
                    if (raw != null && !raw.isBlank() && show == null) {
                        RecipeMenus.message(bukkitPlayer, "液面模型 id 格式不正确");
                        open(bukkitPlayer);
                        return;
                    }
                    String error = RecipeEditService.saveSoupBase(bucket, show);
                    RecipeMenus.message(bukkitPlayer,
                            error != null ? error : "已登记汤底 " + bucket.asString());
                    open(bukkitPlayer);
                },
                () -> open(bukkitPlayer));
    }
}
