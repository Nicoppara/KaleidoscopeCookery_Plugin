package net.kaleidoscope.cookery.util;

import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 统一从 CE 的 ConfigSection 读取带默认值 支持多写法 key 的配置项
public final class BehaviorConfig {
    private static final Key AIR = Key.of("minecraft:air");

    private BehaviorConfig() {}

    public static int getInt(ConfigSection section, int def, String... keys) {
        return section.getInt(keys, def);
    }

    public static boolean getBoolean(ConfigSection section, boolean def, String... keys) {
        return section.getBoolean(keys, def);
    }

    public static String getString(ConfigSection section, String def, String... keys) {
        return section.getString(keys, def);
    }

    public static double getDouble(ConfigSection section, double def, String... keys) {
        return section.getDouble(keys, def);
    }

    public static float getFloat(ConfigSection section, float def, String... keys) {
        return section.getFloat(keys, def);
    }

    public static Vector3f getVector3f(ConfigSection section, Vector3f def, String... keys) {
        return section.getVector3f(keys, def);
    }

    public static List<String> getStringList(ConfigSection section, List<String> def, String... keys) {
        return section.getStringList(keys, def);
    }

    public static Key getKey(ConfigSection section, Key def, String... keys) {
        String raw = section.getString(keys, def == null ? null : def.asString());
        return raw == null || raw.isBlank() ? null : Key.of(raw.trim());
    }

    // 盛装容器 模板没法条件性省略键 所以留空或写 minecraft:air 都表示空手就能盛
    public static Key getCarrier(ConfigSection section, Key def, String... keys) {
        Key key = getKey(section, def, keys);
        return key == null || AIR.equals(key) ? null : key;
    }

    // 物品或方块 id 列表 解析期一次转成 Key 集合 别把 Key.of 留在热路径上
    public static Set<Key> getKeySet(ConfigSection section, List<String> def, String... keys) {
        return keySet(section.getStringList(keys, def));
    }

    private static Set<Key> keySet(List<String> ids) {
        Set<Key> result = new HashSet<>(ids.size());
        ids.forEach(id -> result.add(Key.of(id)));
        return Set.copyOf(result);
    }
}
