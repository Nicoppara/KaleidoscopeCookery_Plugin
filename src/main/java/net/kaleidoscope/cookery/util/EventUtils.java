package net.kaleidoscope.cookery.util;

import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

public final class EventUtils {
    private EventUtils() {}

    // 没人监听时连事件对象都别建 范围操作里每格发一次事件的地方先问这个
    public static boolean hasListeners(HandlerList handlers) {
        return handlers.getRegisteredListeners().length > 0;
    }

    public static void fire(Event event) {
        Bukkit.getPluginManager().callEvent(event);
    }

    // 触发并返回是否被监听器取消
    public static <T extends Event & Cancellable> boolean fireAndCheckCancel(T event) {
        Bukkit.getPluginManager().callEvent(event);
        return event.isCancelled();
    }

    // 只为让日志插件记上一笔 取消结果不理会 权限已逐格走过 InteractGuard
    // 再听一遍取消会叠成两套权限系统 且范围操作里任何一格被取消都会让整体不可预测
    public static void logBlockBreak(Block block, Player player) {
        if (!hasListeners(BlockBreakEvent.getHandlerList())) {
            return;
        }
        fire(new BlockBreakEvent(block, player));
    }
}
