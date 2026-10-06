package net.kaleidoscope.cookery.recipe;

import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.ResourceOperationCoordinator;

import java.util.concurrent.TimeUnit;

/** CE skips dependent stages after a failed stage, including our normal publication stage. */
public final class RecipeLoadRecovery {
    private static volatile boolean closed;

    private RecipeLoadRecovery() {}

    static void watch(long token) {
        if (!pending(token)) return;
        CraftEngine ce = CraftEngine.instance();
        ce.scheduler().asyncLater(() -> {
            if (pending(token)) ce.scheduler().platform().run(() -> inspect(ce, token));
        }, 500, TimeUnit.MILLISECONDS);
    }

    private static boolean pending(long token) {
        return !closed && FoodRecipeRegistry.instance().hasConfigurationLoad(token);
    }

    // Lifecycle flags are read on CE's platform thread, including Folia's global thread.
    private static void inspect(CraftEngine ce, long token) {
        if (!pending(token) || ce.isStopping() || ce.isDisabled()) return;
        if (ce.isEnabling() || ce.isReloading()) {
            watch(token);
            return;
        }
        ResourceOperationCoordinator.Lease lease;
        try {
            // Exclude a new reload between this lifecycle check and rollback.
            lease = ce.resourceOperations().acquire();
        } catch (ResourceOperationCoordinator.BusyException busy) {
            watch(token);
            return;
        }
        try {
            ce.scheduler().executeAsync(() -> {
                try {
                    if (!pending(token)) return;
                    FoodRecipeRegistry.instance().abortConfigurationLoad(token);
                    KaleidoscopeCookeryPlugin.instance().getLogger().warning(
                            "CraftEngine 配方加载未完成，已恢复上一份完整配方；请修正加载错误后重新加载。");
                } finally {
                    lease.close();
                }
            });
        } catch (RuntimeException rejected) {
            lease.close();
            throw rejected;
        }
    }

    public static void close() { closed = true; }
}
