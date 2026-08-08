package net.kaleidoscope.cookery.ui;

import net.kaleidoscope.cookery.util.FoliaUtil;

// 菜单回调统一切回玩家所属 region 线程再碰容器
// dialog 回调与异步落盘完成后都可能落在别的线程 直接开容器在 Folia 上会抛
public final class MenuTasks {
    // 关闭当前容器与打开下一个容器同 tick 会被客户端吞掉后者
    private static final long REOPEN_DELAY = 1L;

    private MenuTasks() {
    }

    // 玩家已下线时实体调度器不会执行 任务自然丢弃 不需要额外判存活
    public static void runFor(org.bukkit.entity.Player player, Runnable task) {
        FoliaUtil.run(task, null, player);
    }

    // 延迟一 tick 再开容器
    public static void reopenFor(org.bukkit.entity.Player player, Runnable task) {
        FoliaUtil.runLater(task, null, REOPEN_DELAY, player);
    }
}
