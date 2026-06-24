package net.kaleidoscope.cookery.util;

import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

public final class EventUtils {
    private EventUtils() {}

    public static void fire(Event event) {
        Bukkit.getPluginManager().callEvent(event);
    }

    // 触发并返回是否被监听器取消
    public static <T extends Event & Cancellable> boolean fireAndCheckCancel(T event) {
        Bukkit.getPluginManager().callEvent(event);
        return event.isCancelled();
    }
}
