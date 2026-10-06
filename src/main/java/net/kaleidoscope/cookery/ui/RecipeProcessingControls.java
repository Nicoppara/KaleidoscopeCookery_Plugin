package net.kaleidoscope.cookery.ui;

import net.kaleidoscope.cookery.api.ui.MenuButton;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.ui.input.MenuInput;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.plugin.gui.GuiElement;
import net.momirealms.craftengine.libraries.adventure.text.format.NamedTextColor;

import java.math.BigDecimal;
import java.util.function.IntConsumer;

/** Human-facing seconds, while configuration and controllers continue to use ticks. */
public final class RecipeProcessingControls {
    public static final int MAX_TICKS = Integer.MAX_VALUE;
    private RecipeProcessingControls() {}

    public static int secondsToTicks(String input) {
        int ticks = new BigDecimal(input.trim()).multiply(BigDecimal.valueOf(20)).intValueExact();
        if (ticks < 1 || ticks > MAX_TICKS) throw new IllegalArgumentException("烹饪时间超出范围");
        return ticks;
    }

    public static String seconds(int ticks) {
        return BigDecimal.valueOf(ticks).divide(BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString();
    }

    static GuiElement time(org.bukkit.entity.Player player, Player viewer, Object original, ApplianceType cook, int ticks,
                           IntConsumer setter, Runnable reopen) {
        var display = RecipeProcessingDisplay.editing(original, cook, ticks);
        var lore = display.lore();
        lore.addAll(MenuIcons.lore("左键输入秒数，精度 0.05 秒", "右键恢复继承配置", "已开始的批次保持原值"));
        var icon = MenuIcons.icon(MenuButton.ROTATION, viewer,
                MenuIcons.text(display.label() + "：" + display.value(), NamedTextColor.GOLD), lore);
        return GuiElement.constant(icon, (event, click) -> {
            click.cancel();
            if ("RIGHT".equals(click.type()) || "SHIFT_RIGHT".equals(click.type())) {
                setter.accept(0);
                reopen.run();
                return;
            }
            MenuInput.requestText(player, "烹饪时间", "秒（0.05 秒的整数倍）",
                    display.effectiveValue() > 0 ? seconds(display.effectiveValue()) : "10", raw -> {
                        try { setter.accept(secondsToTicks(raw)); }
                        catch (ArithmeticException | IllegalArgumentException exception) {
                            RecipeMenus.message(player, "请输入正数，须为 0.05 秒的整数倍，且换算后不超过 2147483647 tick");
                        }
                        reopen.run();
                    }, reopen);
        });
    }

    static GuiElement count(org.bukkit.entity.Player player, Player viewer, Object original, ApplianceType cook, String label,
                            int count, int max, IntConsumer setter, Runnable reopen) {
        var display = RecipeProcessingDisplay.editing(original, cook, count);
        var lore = display.lore();
        lore.addAll(MenuIcons.lore("左键修改，右键恢复继承配置", "已开始的批次保持原值"));
        var icon = MenuIcons.icon(MenuButton.ROTATION, viewer,
                MenuIcons.text(label + "：" + display.value(), NamedTextColor.GOLD), lore);
        return GuiElement.constant(icon, (event, click) -> {
            click.cancel();
            if ("RIGHT".equals(click.type()) || "SHIFT_RIGHT".equals(click.type())) {
                setter.accept(0);
                reopen.run();
            } else {
                MenuInput.requestInt(player, label, "次数", Math.min(max, Math.max(1, display.effectiveValue())), 1, max,
                        value -> { setter.accept(value); reopen.run(); }, reopen);
            }
        });
    }
}
