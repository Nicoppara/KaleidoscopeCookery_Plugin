package net.kaleidoscope.cookery.ui;

import net.kaleidoscope.cookery.api.ui.MenuButton;
import net.kaleidoscope.cookery.api.ui.MenuScreen;
import net.kaleidoscope.cookery.api.ui.RecipeMenuStyle;
import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

// 读 config.yml 的 recipe_menu 段 写进 RecipeMenuStyle 的配置层
// 配置层优先级低于插件调 API 设的值 所以重载不会冲掉别的插件的设置
public final class RecipeMenuConfig {
    private static final String ROOT = "recipe_menu";
    private static final String BUTTONS = "buttons";
    private static final String APPLIANCES = "appliances";
    private static final String TITLES = "titles";
    private static final String APPLIANCE_ICON = "icon";
    private static final String APPLIANCE_NAME = "name";

    private RecipeMenuConfig() {
    }

    public static void load() {
        RecipeMenuStyle style = RecipeMenuStyle.instance();
        style.clearConfigLayer();

        ConfigurationSection root = KaleidoscopeCookeryPlugin.instance().getConfig().getConfigurationSection(ROOT);
        if (root == null) {
            return;
        }
        loadButtons(style, root.getConfigurationSection(BUTTONS));
        loadAppliances(style, root.getConfigurationSection(APPLIANCES));
        loadTitles(style, root.getConfigurationSection(TITLES));
    }

    private static void loadButtons(RecipeMenuStyle style, ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String rawKey : section.getKeys(false)) {
            MenuButton button = parse(MenuButton.class, rawKey, ROOT + "." + BUTTONS);
            Key itemId = parseKey(section.getString(rawKey), ROOT + "." + BUTTONS + "." + rawKey);
            if (button != null && itemId != null) {
                style.configIcon(button, itemId);
            }
        }
    }

    private static void loadAppliances(RecipeMenuStyle style, ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String rawKey : section.getKeys(false)) {
            ApplianceType cook = parse(ApplianceType.class, rawKey, ROOT + "." + APPLIANCES);
            if (cook == null) {
                continue;
            }
            String path = ROOT + "." + APPLIANCES + "." + rawKey;
            // 只写 name 不写 icon 是常见用法 两个都可以单独省略
            ConfigurationSection sub = section.getConfigurationSection(rawKey);
            if (sub == null) {
                warn(path + " 应该是一个小节 里面写 icon 与 name");
                continue;
            }
            Key icon = parseKey(sub.getString(APPLIANCE_ICON), path + "." + APPLIANCE_ICON);
            if (icon != null) {
                style.configApplianceIcon(cook, icon);
            }
            String name = sub.getString(APPLIANCE_NAME);
            if (name != null && !name.isEmpty()) {
                style.configApplianceName(cook, name);
            }
        }
    }

    private static void loadTitles(RecipeMenuStyle style, ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String rawKey : section.getKeys(false)) {
            MenuScreen screen = parse(MenuScreen.class, rawKey, ROOT + "." + TITLES);
            String template = section.getString(rawKey);
            if (screen != null && template != null && !template.isEmpty()) {
                style.configTitle(screen, template);
            }
        }
    }

    // 配置键同时接受 snake_case 与 kebab-case 认不出来的键报出来而不是静默丢掉
    private static <E extends Enum<E>> E parse(Class<E> type, String rawKey, String path) {
        String normalized = rawKey.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(type, normalized);
        } catch (IllegalArgumentException e) {
            warn(path + " 里的 " + rawKey + " 不是可用的名字 已跳过");
            return null;
        }
    }

    private static Key parseKey(String raw, String path) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Key.of(raw.trim());
        } catch (RuntimeException e) {
            warn(path + " 的物品 id " + raw + " 格式不正确 已跳过");
            return null;
        }
    }

    private static void warn(String message) {
        KaleidoscopeCookeryPlugin.instance().getLogger().warning("食谱菜单配置 " + message);
    }
}
