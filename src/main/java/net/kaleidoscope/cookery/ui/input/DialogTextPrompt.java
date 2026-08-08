package net.kaleidoscope.cookery.ui.input;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kaleidoscope.cookery.ui.MenuTasks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

// Paper 的 dialog API 是 1.21.6 才有的 这个类只允许从 MenuInput 的版本分支里触达
// 类在首次执行到那条分支时才被链接 低版本永远不加载 不会 NoClassDefFoundError
final class DialogTextPrompt {
    private static final String INPUT_KEY = "value";
    private static final int MAX_LENGTH = 128;
    // 玩家挂机不填也要能过期回收 回调注册表不能无限增长
    private static final Duration CALLBACK_LIFETIME = Duration.ofMinutes(5);

    private DialogTextPrompt() {
    }

    static void open(Player player, String title, String label, String initial,
                     Consumer<String> onConfirm, Runnable onCancel) {
        ClickCallback.Options options = ClickCallback.Options.builder()
                .uses(1)
                .lifetime(CALLBACK_LIFETIME)
                .build();
        DialogAction confirm = DialogAction.customClick((response, audience) -> {
            String raw = response.getText(INPUT_KEY);
            MenuTasks.runFor(player, () -> {
                if (raw == null) {
                    onCancel.run();
                    return;
                }
                onConfirm.accept(raw);
            });
        }, options);
        DialogAction cancel = DialogAction.customClick(
                (response, audience) -> MenuTasks.runFor(player, onCancel), options);

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(title))
                        .canCloseWithEscape(true)
                        .inputs(List.of(DialogInput.text(INPUT_KEY, Component.text(label))
                                .initial(initial)
                                .maxLength(MAX_LENGTH)
                                .build()))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.create(Component.text("确认"), null, 100, confirm),
                        ActionButton.create(Component.text("取消"), null, 100, cancel))));

        player.closeInventory();
        player.showDialog(dialog);
    }
}
