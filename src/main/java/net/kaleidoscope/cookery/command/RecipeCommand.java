package net.kaleidoscope.cookery.command;

import net.kaleidoscope.cookery.ui.RecipeMenus;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

// 食谱菜单入口指令 浏览权限默认关闭 由管理员按需下放
public final class RecipeCommand implements CommandExecutor, TabCompleter {
    public static final String PERMISSION_BROWSE = "kaleidoscopecookery.recipe.browse";
    public static final String PERMISSION_EDIT = "kaleidoscopecookery.recipe.edit";

    private static final String SUB_EDIT = "edit";

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("该指令只能由玩家执行");
            return true;
        }
        boolean edit = args.length > 0 && SUB_EDIT.equalsIgnoreCase(args[0]);
        String permission = edit ? PERMISSION_EDIT : PERMISSION_BROWSE;
        if (!player.hasPermission(permission)) {
            player.sendMessage("你没有权限使用该指令 缺少 " + permission);
            return true;
        }
        // 指令本身就跑在该玩家所属的 region 线程上 直接开容器即可
        RecipeMenus.openHome(player, edit);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !sender.hasPermission(PERMISSION_EDIT)) {
            return List.of();
        }
        return SUB_EDIT.startsWith(args[0].toLowerCase()) ? List.of(SUB_EDIT) : List.of();
    }
}
