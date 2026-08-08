package net.kaleidoscope.cookery.ui.input;

import net.momirealms.craftengine.core.util.VersionHelper;
import org.bukkit.entity.Player;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

// 菜单里一切文本与数字输入的统一入口
// 1.21.6 起客户端支持 dialog 协议 更低版本降级到铁砧改名 两条路都在玩家所属 region 线程回调
public final class MenuInput {
    private MenuInput() {
    }

    // 取消输入不回调 调用方负责在取消时自行重开菜单
    public static void requestText(Player player, String title, String label, String initial,
                                   Consumer<String> onConfirm, Runnable onCancel) {
        if (VersionHelper.isOrAbove1_21_6) {
            DialogTextPrompt.open(player, title, label, initial, onConfirm, onCancel);
            return;
        }
        AnvilTextPrompt.open(player, title, initial, onConfirm, onCancel);
    }

    // 数字输入 解析失败或越界一律当作取消 免得把非法值写进配方
    public static void requestInt(Player player, String title, String label, int initial,
                                  int min, int max, IntConsumer onConfirm, Runnable onCancel) {
        requestText(player, title, label, String.valueOf(initial), raw -> {
            int value;
            try {
                value = Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                onCancel.run();
                return;
            }
            if (value < min || value > max) {
                onCancel.run();
                return;
            }
            onConfirm.accept(value);
        }, onCancel);
    }
}
