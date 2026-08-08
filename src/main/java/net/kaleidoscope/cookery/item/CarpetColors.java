package net.kaleidoscope.cookery.item;

import net.momirealms.craftengine.core.util.Key;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

// 原版十六色羊毛地毯与对应的展示模型
public final class CarpetColors {
    private CarpetColors() {}

    private static final String[] NAMES = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };
    // 下标即 TableBehavior 的 position 0 单桌 1 左端 2 中段 3 右端
    private static final String[] TABLE_SHAPES = {"single", "left", "middle", "right"};

    private static final Map<Key, Key> CHAIR_MODELS = new LinkedHashMap<>();
    private static final Map<Key, Key[]> TABLE_MODELS = new LinkedHashMap<>();
    private static final Map<String, Key> BY_ID = new LinkedHashMap<>();

    static {
        for (String name : NAMES) {
            Key carpet = Key.of("minecraft:" + name + "_carpet");
            CHAIR_MODELS.put(carpet, Key.of("show:chair_carpet_" + name));
            BY_ID.put(carpet.asString(), carpet);
            Key[] shapes = new Key[TABLE_SHAPES.length];
            for (int i = 0; i < TABLE_SHAPES.length; i++) {
                shapes[i] = Key.of("show:table_carpet_" + name + "_" + TABLE_SHAPES[i]);
            }
            TABLE_MODELS.put(carpet, shapes);
        }
    }

    public static boolean isCarpet(Key item) {
        return CHAIR_MODELS.containsKey(item);
    }

    // 存档里读回来的地毯 id 不认识就返回 null 别让脏数据在区块加载时抛出去
    @Nullable
    public static Key parse(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    // 地毯物品对应的坐垫展示模型 不是地毯返回 null
    @Nullable
    public static Key chairModel(Key carpet) {
        return CHAIR_MODELS.get(carpet);
    }

    // 地毯物品在给定端型下的桌布展示模型 不是地毯返回 null
    @Nullable
    public static Key tableModel(Key carpet, int position) {
        Key[] shapes = TABLE_MODELS.get(carpet);
        if (shapes == null || position < 0 || position >= shapes.length) {
            return null;
        }
        return shapes[position];
    }
}
