package net.kaleidoscope.cookery.ui;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Identity-based guard; released even if the player disconnects during the asynchronous save. */
final class RecipeEditSaves {
    private static final Map<Object, Boolean> ACTIVE = Collections.synchronizedMap(new IdentityHashMap<>());
    private RecipeEditSaves() {}

    static CompletableFuture<String> save(Object draft, Supplier<CompletableFuture<String>> action) {
        synchronized (ACTIVE) {
            if (ACTIVE.putIfAbsent(draft, Boolean.TRUE) != null) return null;
        }
        try {
            return action.get().whenComplete((result, error) -> ACTIVE.remove(draft));
        } catch (RuntimeException error) {
            ACTIVE.remove(draft);
            return CompletableFuture.completedFuture("食谱保存失败，请查看控制台");
        }
    }
}
