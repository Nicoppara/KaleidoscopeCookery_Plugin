package net.kaleidoscope.cookery.ui.input;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kaleidoscope.cookery.ui.MenuTasks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// 带预设选项的 dialog 常用值直接给按钮
// 选项之外还留一个自定义入口 走原来的文本输入
public final class DialogChoicePrompt {
    private DialogChoicePrompt() {
    }

    // 一个预设选项 value 为 null 表示清空
    // translationKey 非空时按钮走翻译组件 dialog 由客户端渲染 翻译有效 只有 plainTextContent 不吃翻译
    public record Choice(String label, String translationKey, String value) {
        public Choice(String label, String value) {
            this(label, null, value);
        }

        public static Choice translated(String key, String value) {
            return new Choice(key, key, value);
        }
    }

    private static Component label(Choice choice) {
        return choice.translationKey() == null
                ? Component.text(choice.label())
                : Component.translatable(choice.translationKey());
    }

    // 预设按钮上限 汤底表默认就有 8 种 上限 5 会把一半悄悄砍掉
    // 超出的仍可走自定义手输 id
    private static final int MAX_BUTTONS = 10;
    // customClick 的 options 不能省 传 null 会在注册回调时 NPE
    // 每个按钮只按一次 之后菜单会重开 回调放着不用白占内存
    private static final Duration CALLBACK_LIFETIME = Duration.ofMinutes(5);

    public static void open(Player player, String title, String description, List<Choice> choices,
                            Consumer<String> onPick, Runnable onCustom, Runnable onCancel) {
        ClickCallback.Options options = ClickCallback.Options.builder()
                .uses(1)
                .lifetime(CALLBACK_LIFETIME)
                .build();
        List<ActionButton> buttons = new ArrayList<>();
        for (Choice choice : choices) {
            if (buttons.size() >= MAX_BUTTONS) {
                break;
            }
            DialogActionCallback callback = (response, audience) ->
                    MenuTasks.runFor(player, () -> onPick.accept(choice.value()));
            buttons.add(ActionButton.create(label(choice), null, 90,
                    DialogAction.customClick(callback, options)));
        }
        buttons.add(ActionButton.create(Component.text("自定义"), null, 90,
                DialogAction.customClick(
                        (response, audience) -> MenuTasks.runFor(player, onCustom), options)));
        buttons.add(ActionButton.create(Component.text("取消"), null, 90,
                DialogAction.customClick(
                        (response, audience) -> MenuTasks.runFor(player, onCancel), options)));

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(title))
                        .canCloseWithEscape(true)
                        .body(List.of(io.papermc.paper.registry.data.dialog.body.DialogBody
                                .plainMessage(Component.text(description))))
                        .build())
                .type(DialogType.multiAction(buttons, null, 2)));

        player.closeInventory();
        player.showDialog(dialog);
    }
}
