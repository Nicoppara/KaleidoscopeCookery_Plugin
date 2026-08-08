package net.kaleidoscope.cookery.recipe.edit;

import net.momirealms.craftengine.core.util.Key;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 配方 id 到来源配置文件的索引 解析期由各 parser 登记 编辑时据此原地改写
// 解析跑在 CE 的多线程配置加载上 必须并发容器
public final class RecipeSourceIndex {
    private static final RecipeSourceIndex INSTANCE = new RecipeSourceIndex();

    private final Map<Key, Path> sources = new ConcurrentHashMap<>();

    private RecipeSourceIndex() {
    }

    public static RecipeSourceIndex instance() {
        return INSTANCE;
    }

    public void put(Key id, Path file) {
        if (id == null || file == null) {
            return;
        }
        sources.put(id, file);
    }

    public Path get(Key id) {
        return sources.get(id);
    }

    public void remove(Key id) {
        sources.remove(id);
    }

    // 每次配置重载前清空 重载会重新登记全部来源
    public void clear() {
        sources.clear();
    }
}
