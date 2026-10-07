package net.kaleidoscope.cookery.ui;

import net.kaleidoscope.cookery.api.ui.MenuButton;
import net.kaleidoscope.cookery.api.ui.RecipeMenuStyle;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.item.ItemNames;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.TeapotLiquid;
import net.kaleidoscope.cookery.ui.input.DialogChoicePrompt;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.util.Localization;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.gui.GuiElement;
import net.momirealms.craftengine.core.plugin.gui.PagedGui;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.AdventureHelper;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.libraries.adventure.text.format.NamedTextColor;
import net.momirealms.craftengine.libraries.adventure.text.format.TextDecoration;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 菜单图标与常用按钮的构建 图标只在当前页构建 别在这里做任何缓存
public final class MenuIcons {
    private MenuIcons() {
    }

    public static Component text(String value, NamedTextColor color) {
        return Component.text(value).color(color).decoration(TextDecoration.ITALIC, false);
    }

    // 菜单里一律显示物品名
    // 必须反序列化 取不到名字才退回 id 的路径段
    public static Component itemName(Key id) {
        if (id == null) {
            return text("未设置", NamedTextColor.DARK_GRAY);
        }
        String name = ItemNames.displayName(id);
        if (name == null || name.isEmpty()) {
            return text(id.value(), NamedTextColor.WHITE);
        }
        return AdventureHelper.miniMessage().deserialize(name)
                .decoration(TextDecoration.ITALIC, false);
    }

    // 标题模板是 MiniMessage 字符串 只能拿名字的原文 拼进去后整体反序列化
    public static String itemNameText(Key id) {
        if (id == null) {
            return "未设置";
        }
        String name = ItemNames.displayName(id);
        return name == null || name.isEmpty() ? id.value() : name;
    }

    public static Component grayWith(String prefix, Key id, String suffix) {
        Component line = Component.empty();
        if (!prefix.isEmpty()) {
            line = line.append(Component.text(prefix));
        }
        line = line.append(itemName(id));
        if (!suffix.isEmpty()) {
            line = line.append(Component.text(suffix));
        }
        return line.colorIfAbsent(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    public static Component grayWith(Key id) {
        return grayWith("", id, "");
    }

    public static Component liquidName(Key id) {
        if (id == null) {
            return text("未设置", NamedTextColor.DARK_GRAY);
        }
        String label = liquidLabel(id);
        if (label != null) {
            return Localization.component(label)
                    .decoration(TextDecoration.ITALIC, false);
        }
        return itemName(id);
    }

    // 液体菜单与选择对话框必须走同一份名称来源 避免一边叫岩浆一边套原版翻译叫熔岩
    public static DialogChoicePrompt.Choice liquidChoice(Key id) {
        String value = id.asString();
        String label = liquidLabel(id);
        if (label != null) {
            return Localization.isTranslationKey(label)
                    ? DialogChoicePrompt.Choice.translated(label, value)
                    : new DialogChoicePrompt.Choice(label, value);
        }
        Material material = Material.matchMaterial(value);
        return material == null
                ? new DialogChoicePrompt.Choice(id.value(), value)
                : DialogChoicePrompt.Choice.translated(material.getItemTranslationKey(), value);
    }

    public static Component grayLiquidWith(String prefix, Key id, String suffix) {
        Component line = Component.empty();
        if (!prefix.isEmpty()) {
            line = line.append(Component.text(prefix));
        }
        line = line.append(liquidName(id));
        if (!suffix.isEmpty()) {
            line = line.append(Component.text(suffix));
        }
        return line.colorIfAbsent(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    public static Key liquidIconKey(Key id) {
        return RecipeMenuStyle.instance().liquidIcon(id);
    }

    private static Key fluidOf(Key id) {
        if (!id.value().endsWith("_bucket")) {
            return id;
        }
        return Key.of(id.namespace(), id.value().substring(0, id.value().length() - "_bucket".length()));
    }

    private static String liquidLabel(Key id) {
        Key fluid = fluidOf(id);
        TeapotLiquid liquid = FoodRecipeRegistry.instance().getTeapotLiquid(fluid);
        if (liquid != null && liquid.displayName() != null && !liquid.displayName().isEmpty()) {
            return liquid.displayName();
        }
        if (ItemKeys.WATER.equals(fluid)) {
            return "kaleidoscopecookery.message.teapot.liquid.water";
        }
        if (ItemKeys.LAVA.equals(fluid)) {
            return "kaleidoscopecookery.message.teapot.liquid.lava";
        }
        return null;
    }

    public static Component gray(String value) {
        return text(value, NamedTextColor.GRAY);
    }

    // 食谱中的真实物品直接使用 CE 在当前玩家语境下生成的名称和说明。
    public static Item nativePreview(Key id, Player viewer) {
        Item item = InventoryUtils.createOrEmpty(id, viewer);
        if (ItemUtils.isEmpty(item)) {
            item = InventoryUtils.createOrEmpty(iconKey(MenuButton.INVALID), viewer);
        }
        return ItemUtils.isEmpty(item) ? item : item.copy();
    }

    // 按 id 建图标 id 无效时退回屏障
    public static Item icon(Key id, Player viewer, Component name, List<Component> lore) {
        Item item = nativePreview(id, viewer);
        if (ItemUtils.isEmpty(item)) {
            return item;
        }
        // 控件提示只修改菜单副本；真实物品预览直接使用 nativePreview。
        if (name != null) {
            item.customNameComponent(name);
        }
        return appendLore(item, lore);
    }

    // 在菜单自有图标上追加提示，不重解析或覆盖 CE 已生成的物品说明。
    static Item appendLore(Item item, List<Component> extra) {
        if (ItemUtils.isEmpty(item) || extra == null || extra.isEmpty()) {
            return item;
        }
        List<Component> combined = new ArrayList<>(item.loreComponent().orElse(List.of()));
        combined.addAll(extra);
        item.loreComponent(combined);
        return item;
    }

    public static Item icon(Key id, Player viewer, Component name) {
        return icon(id, viewer, name, List.of());
    }

    // 按钮材质一律经这里取 外部插件可用 RecipeMenuStyle 逐个替换
    public static Key iconKey(MenuButton button) {
        return RecipeMenuStyle.instance().icon(button);
    }

    public static Item icon(MenuButton button, Player viewer, Component name, List<Component> lore) {
        return icon(iconKey(button), viewer, name, lore);
    }

    public static Item icon(MenuButton button, Player viewer, Component name) {
        return icon(iconKey(button), viewer, name, List.of());
    }

    // 边框玻璃 隐藏 tooltip
    public static GuiElement filler(Player viewer) {
        Item item = InventoryUtils.createOrEmpty(iconKey(MenuButton.FILLER), viewer);
        if (!ItemUtils.isEmpty(item)) {
            item.customNameComponent(Component.empty());
            hideTooltip(item);
        }
        return GuiElement.constant(item, (e, click) -> click.cancel());
    }

    private static void hideTooltip(Item item) {
        try {
            item.setComponent(Key.of("minecraft:tooltip_display"), Map.of("hide_tooltip", true));
        } catch (Exception ignored) {
            try {
                item.setComponent(Key.of("minecraft:hide_tooltip"), Map.of());
            } catch (Exception ignored2) {
            }
        }
    }

    public static GuiElement empty() {
        return GuiElement.constant(null, (e, click) -> click.cancel());
    }

    // 按钮一律先 cancel 再跑逻辑 任何一条分支漏 cancel 都是刷物品口子
    public static GuiElement button(Item item, Runnable action) {
        return GuiElement.constant(item, (e, click) -> {
            click.cancel();
            action.run();
        });
    }

    public static GuiElement back(Player viewer, Runnable action) {
        return button(icon(MenuButton.BACK, viewer, text("返回", NamedTextColor.YELLOW)), action);
    }

    static GuiElement editingHelp(Player viewer, String... lines) {
        return button(icon(MenuButton.CREATE, viewer, text("编辑操作", NamedTextColor.AQUA), lore(lines)), () -> {});
    }

    public static GuiElement previousPage(Player viewer) {
        return GuiElement.paged(e -> {
            int page = ((PagedGui) e.gui()).currentPage();
            if (page <= 1) {
                return null;
            }
            return icon(MenuButton.PREVIOUS_PAGE, viewer, text("上一页 " + (page - 1), NamedTextColor.AQUA));
        }, false);
    }

    public static GuiElement nextPage(Player viewer) {
        return GuiElement.paged(e -> {
            PagedGui gui = (PagedGui) e.gui();
            if (!gui.hasNextPage()) {
                return null;
            }
            return icon(MenuButton.NEXT_PAGE, viewer, text("下一页 " + (gui.currentPage() + 1), NamedTextColor.AQUA));
        }, true);
    }

    // 厨具名与厨具图标的默认值在 RecipeMenuStyle 里 外部可逐个覆盖
    public static String displayName(ApplianceType cook) {
        return RecipeMenuStyle.instance().applianceName(cook);
    }

    public static Key iconOf(ApplianceType cook) {
        return RecipeMenuStyle.instance().applianceIcon(cook);
    }

    // 首行放物品名 其余是说明 物品名必须保持组件形态
    public static List<Component> loreNamed(Key id, String... lines) {
        List<Component> out = new ArrayList<>(lines.length + 1);
        out.add(grayWith(id));
        for (String line : lines) {
            out.add(gray(line));
        }
        return out;
    }

    public static List<Component> lore(String... lines) {
        List<Component> out = new ArrayList<>(lines.length);
        for (String line : lines) {
            out.add(gray(line));
        }
        return out;
    }
}
