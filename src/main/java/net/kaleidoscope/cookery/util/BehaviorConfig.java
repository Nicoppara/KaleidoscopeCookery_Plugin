package net.kaleidoscope.cookery.util;

import net.momirealms.craftengine.core.plugin.config.ConfigSection;

// 统一从 CraftEngine 的 ConfigSection 读取带默认值、支持多写法 key 的配置项
public final class BehaviorConfig {
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
}
