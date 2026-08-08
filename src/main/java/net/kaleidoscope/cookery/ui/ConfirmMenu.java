package net.kaleidoscope.cookery.ui;
import net.kaleidoscope.cookery.api.ui.MenuButton;

import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.plugin.gui.BasicGuiImpl;
import net.momirealms.craftengine.core.plugin.gui.Gui;
import net.momirealms.craftengine.core.plugin.gui.GuiLayout;
import net.momirealms.craftengine.core.plugin.gui.Ingredient;
import net.momirealms.craftengine.libraries.adventure.text.format.NamedTextColor;

import java.util.List;

// 删除这类不可逆操作的二次确认 默认停在取消一侧
public final class ConfirmMenu {
    private ConfirmMenu() {
    }

    public static void open(org.bukkit.entity.Player bukkitPlayer, String title, List<String> detail,
                            Runnable onConfirm, Runnable onCancel) {
        Player viewer = RecipeMenus.adapt(bukkitPlayer);
        if (viewer == null) {
            return;
        }
        GuiLayout layout = new GuiLayout(
                "#########",
                "###Y#N###",
                "#########");
        layout.addIngredient('#', Ingredient.simple(MenuIcons.filler(viewer)));
        layout.addIngredient('Y', MenuIcons.button(
                MenuIcons.icon(MenuButton.DELETE, viewer,
                        MenuIcons.text("确认", NamedTextColor.RED),
                        MenuIcons.lore(detail.toArray(new String[0]))),
                onConfirm));
        layout.addIngredient('N', MenuIcons.button(
                MenuIcons.icon(MenuButton.BACK, viewer, MenuIcons.text("取消", NamedTextColor.GREEN)),
                onCancel));
        Gui gui = BasicGuiImpl.builder()
                .layout(layout)
                .inventoryClickConsumer(RecipeMenus.inventoryGuard())
                .build();
        gui.title(MenuIcons.text(title, NamedTextColor.DARK_RED))
                .refresh()
                .open(viewer);
    }
}
