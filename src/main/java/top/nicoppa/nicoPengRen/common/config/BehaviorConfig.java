package top.nicoppa.nicoPengRen.common.config;

import net.momirealms.craftengine.core.plugin.config.ConfigSection;

/**
 * 统一从 CraftEngine 的 {@link ConfigSection} 读取带默认值、支持下划线/连字符多写法的配置项
 */
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
