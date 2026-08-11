package net.kaleidoscope.cookery.recipe.edit;

import net.momirealms.craftengine.core.util.Key;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 记录编辑菜单中每条食谱对应的精确配置节点。 */
public final class RecipeSourceIndex {
    public enum Kind {
        ACCURATE,
        POT_FLEX,
        STOCK_FLEX,
        CHOPPING,
        TEAPOT
    }

    private record SourceKey(Kind kind, Key id, Path file, RecipeFileStore.SourceTarget target) {
        private SourceKey {
            file = file.toAbsolutePath().normalize();
        }

        private String nodePath() {
            return target.generatedNode();
        }
    }

    private static final RecipeSourceIndex INSTANCE = new RecipeSourceIndex();

    private final Map<SourceKey, Object> sources = new ConcurrentHashMap<>();
    private final Map<Object, SourceKey> byRecipe = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Set<Object> duplicates = Collections.newSetFromMap(
            Collections.synchronizedMap(new IdentityHashMap<>()));
    // 删除标记只在磁盘已经删除后覆盖内存收尾阶段 防止进行中的 CE 重载读回旧快照
    // 标记丢失也不会复活食谱 因为下一次启动只会读取已经删除节点的 YAML
    private final Set<SourceKey> deleted = ConcurrentHashMap.newKeySet();
    private final Set<SourceKey> pendingRestore = ConcurrentHashMap.newKeySet();
    private final Map<Kind, Integer> activeLoads = new ConcurrentHashMap<>();
    private final Map<Kind, List<Runnable>> pendingActions = new ConcurrentHashMap<>();

    private RecipeSourceIndex() {
    }

    public static RecipeSourceIndex instance() {
        return INSTANCE;
    }

    public void put(Kind kind, Key id, Path file, String nodePath, Object recipe, boolean duplicate) {
        put(kind, id, file, RecipeFileStore.SourceTarget.direct(nodePath), recipe, duplicate);
    }

    public void put(Kind kind, Key id, Path file, RecipeFileStore.SourceTarget target,
                    Object recipe, boolean duplicate) {
        if (kind == null || id == null || file == null || target == null || recipe == null) {
            return;
        }
        SourceKey source = new SourceKey(kind, id, file, target);
        sources.put(source, recipe);
        byRecipe.put(recipe, source);
        if (duplicate) {
            duplicates.add(recipe);
        }
    }

    public Path get(Object recipe) {
        SourceKey source = byRecipe.get(recipe);
        return source == null ? null : source.file();
    }

    public Kind kind(Object recipe) {
        SourceKey source = byRecipe.get(recipe);
        return source == null ? null : source.kind();
    }

    public String nodePath(Object recipe) {
        SourceKey source = byRecipe.get(recipe);
        return source == null ? null : source.nodePath();
    }

    public RecipeFileStore.SourceTarget target(Object recipe) {
        SourceKey source = byRecipe.get(recipe);
        return source == null ? null : source.target();
    }

    public boolean isDuplicate(Object recipe) {
        return recipe != null && duplicates.contains(recipe);
    }

    public boolean hasOtherSource(Kind kind, Key id, Path file, String nodePath) {
        if (kind == null || id == null) {
            return false;
        }
        Path normalized = file == null ? null : file.toAbsolutePath().normalize();
        RecipeFileStore.SourceTarget target = RecipeFileStore.SourceTarget.direct(nodePath);
        for (SourceKey source : sources.keySet()) {
            boolean sameNode = normalized != null && source.file().equals(normalized)
                    && source.target().equals(target);
            if (source.kind() == kind && source.id().equals(id) && !sameNode) {
                return true;
            }
        }
        return false;
    }

    public boolean hasSource(Kind kind, Key id) {
        if (kind == null || id == null) {
            return false;
        }
        for (SourceKey source : sources.keySet()) {
            if (source.kind() == kind && source.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    public boolean isDeleted(Kind kind, Key id, Path file, String nodePath) {
        if (kind == null || id == null || file == null || nodePath == null) {
            return false;
        }
        Path normalized = file.toAbsolutePath().normalize();
        for (SourceKey source : deleted) {
            if (source.kind() == kind && source.id().equals(id) && source.file().equals(normalized)
                    && source.nodePath().equals(nodePath)) {
                return true;
            }
        }
        return false;
    }

    public boolean isDeleted(Kind kind, Key id, Path file, RecipeFileStore.SourceTarget target) {
        return kind != null && id != null && file != null && target != null
                && deleted.contains(new SourceKey(kind, id, file, target));
    }

    public void markDeleted(Object recipe) {
        SourceKey source = byRecipe.get(recipe);
        if (source != null) {
            deleted.add(source);
        }
    }

    public void markDeleted(Kind kind, Key id, Path file, String nodePath) {
        markDeleted(kind, id, file, RecipeFileStore.SourceTarget.direct(nodePath));
    }

    public void markDeleted(Kind kind, Key id, Path file, RecipeFileStore.SourceTarget target) {
        if (kind != null && id != null && file != null && target != null) {
            deleted.add(new SourceKey(kind, id, file, target));
        }
    }

    public synchronized void restore(Kind kind, Key id, Path file, String nodePath) {
        restore(kind, id, file, RecipeFileStore.SourceTarget.direct(nodePath));
    }

    public synchronized void restore(Kind kind, Key id, Path file, RecipeFileStore.SourceTarget target) {
        if (kind != null && id != null && file != null && target != null) {
            SourceKey source = new SourceKey(kind, id, file, target);
            if (activeLoads.getOrDefault(kind, 0) > 0) {
                pendingRestore.add(source);
            } else {
                deleted.remove(source);
            }
        }
    }

    public synchronized void beginLoad(Kind kind) {
        if (kind != null) {
            activeLoads.merge(kind, 1, Integer::sum);
        }
    }

    public synchronized void endLoad(Kind kind) {
        if (kind == null) {
            return;
        }
        int remaining = activeLoads.getOrDefault(kind, 0) - 1;
        if (remaining > 0) {
            activeLoads.put(kind, remaining);
            return;
        }
        activeLoads.remove(kind);
        pendingRestore.removeIf(source -> {
            if (source.kind() != kind) {
                return false;
            }
            deleted.remove(source);
            return true;
        });
        List<Runnable> actions = pendingActions.remove(kind);
        if (actions != null) {
            for (Runnable action : actions) {
                action.run();
            }
        }
    }

    // 删除落盘时若 CE 正在重载 等本类食谱解析完再整理运行时
    // 方法持锁执行收尾 防止新的重载插进判断与收尾之间
    public synchronized void afterCurrentLoad(Kind kind, Runnable action) {
        if (kind != null && activeLoads.getOrDefault(kind, 0) > 0) {
            pendingActions.computeIfAbsent(kind, ignored -> new ArrayList<>()).add(action);
            return;
        }
        action.run();
    }

    public void remove(Object recipe) {
        SourceKey source = byRecipe.remove(recipe);
        duplicates.remove(recipe);
        if (source != null) {
            sources.remove(source, recipe);
        }
    }

    public Object removeSource(Kind kind, Key id, Path file, String nodePath) {
        return removeSource(kind, id, file, RecipeFileStore.SourceTarget.direct(nodePath));
    }

    public Object removeSource(Kind kind, Key id, Path file, RecipeFileStore.SourceTarget target) {
        if (kind == null || id == null || file == null || target == null) {
            return null;
        }
        SourceKey source = new SourceKey(kind, id, file, target);
        Object recipe = sources.remove(source);
        if (recipe != null) {
            byRecipe.remove(recipe);
            duplicates.remove(recipe);
        }
        return recipe;
    }

    public List<RecipeFileStore.SourceTarget> deletedTargets(Kind kind, Key id, Path file) {
        if (kind == null || id == null || file == null) {
            return List.of();
        }
        Path normalized = file.toAbsolutePath().normalize();
        List<RecipeFileStore.SourceTarget> result = new ArrayList<>();
        for (SourceKey source : deleted) {
            if (source.kind() == kind && source.id().equals(id) && source.file().equals(normalized)) {
                result.add(source.target());
            }
        }
        return List.copyOf(result);
    }

    public List<Object> recipes(Kind kind, Key id) {
        if (kind == null || id == null) {
            return List.of();
        }
        List<Object> result = new ArrayList<>();
        for (Map.Entry<SourceKey, Object> entry : sources.entrySet()) {
            if (entry.getKey().kind() == kind && entry.getKey().id().equals(id)) {
                result.add(entry.getValue());
            }
        }
        return List.copyOf(result);
    }

    public void duplicate(Object recipe, boolean duplicate) {
        if (recipe == null) {
            return;
        }
        if (duplicate) {
            duplicates.add(recipe);
        } else {
            duplicates.remove(recipe);
        }
    }

    public void remove(Kind kind, Key id) {
        if (kind == null || id == null) {
            return;
        }
        synchronized (byRecipe) {
            byRecipe.entrySet().removeIf(entry -> {
                SourceKey source = entry.getValue();
                if (source.kind() != kind || !source.id().equals(id)) {
                    return false;
                }
                sources.remove(source, entry.getKey());
                duplicates.remove(entry.getKey());
                return true;
            });
        }
    }

    public void clearKind(Kind kind) {
        if (kind == null) {
            return;
        }
        synchronized (byRecipe) {
            byRecipe.entrySet().removeIf(entry -> {
                SourceKey source = entry.getValue();
                if (source.kind() != kind) {
                    return false;
                }
                sources.remove(source, entry.getKey());
                duplicates.remove(entry.getKey());
                return true;
            });
        }
    }

    public void clear() {
        sources.clear();
        byRecipe.clear();
        duplicates.clear();
        deleted.clear();
        pendingRestore.clear();
        activeLoads.clear();
        pendingActions.clear();
    }
}
