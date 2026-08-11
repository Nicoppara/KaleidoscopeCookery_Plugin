package net.kaleidoscope.cookery.api.ui;

import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.momirealms.craftengine.core.util.AdventureHelper;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.libraries.adventure.text.format.NamedTextColor;
import net.momirealms.craftengine.libraries.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Light-weight reskin of the built-in recipe menus: button icons, screen titles
 * and appliance display names. Use this when the built-in layout is fine and
 * only the look needs to change; to replace a whole screen with your own
 * inventory, register a {@link RecipeMenuProvider} on {@link RecipeMenuHooks}.
 * Overrides apply the next time a menu opens and are not persisted, so set them
 * during your enable phase on every startup.
 * Values set here win over the {@code recipe_menu} section of config.yml, and a
 * config reload never clobbers them.
 */
public final class RecipeMenuStyle {
    private static final RecipeMenuStyle INSTANCE = new RecipeMenuStyle();

    // Built-in appliance names, overridable one by one
    private static final Map<ApplianceType, String> DEFAULT_NAMES = new EnumMap<>(ApplianceType.class);
    private static final Map<ApplianceType, Key> DEFAULT_ICONS = new EnumMap<>(ApplianceType.class);

    static {
        DEFAULT_NAMES.put(ApplianceType.POT, "炒锅");
        DEFAULT_NAMES.put(ApplianceType.STOCKPOT, "高汤锅");
        DEFAULT_NAMES.put(ApplianceType.STEAMER, "蒸笼");
        DEFAULT_NAMES.put(ApplianceType.SHAWARMA, "沙威玛烤架");
        DEFAULT_NAMES.put(ApplianceType.MILLSTONE, "石磨");
        DEFAULT_NAMES.put(ApplianceType.CHOPPING_BOARD, "砧板");
        DEFAULT_NAMES.put(ApplianceType.TEAPOT, "茶壶");

        DEFAULT_ICONS.put(ApplianceType.POT, net.kaleidoscope.cookery.item.ItemKeys.MENU_POT);
        DEFAULT_ICONS.put(ApplianceType.STOCKPOT, net.kaleidoscope.cookery.item.ItemKeys.MENU_STOCKPOT);
        DEFAULT_ICONS.put(ApplianceType.STEAMER, net.kaleidoscope.cookery.item.ItemKeys.MENU_STEAMER);
        DEFAULT_ICONS.put(ApplianceType.SHAWARMA, net.kaleidoscope.cookery.item.ItemKeys.MENU_SHAWARMA);
        DEFAULT_ICONS.put(ApplianceType.MILLSTONE, net.kaleidoscope.cookery.item.ItemKeys.MENU_MILLSTONE);
        DEFAULT_ICONS.put(ApplianceType.CHOPPING_BOARD, net.kaleidoscope.cookery.item.ItemKeys.MENU_CHOPPING_BOARD);
        DEFAULT_ICONS.put(ApplianceType.TEAPOT, net.kaleidoscope.cookery.item.ItemKeys.MENU_TEAPOT);
    }

    // Two layers so a config reload rebuilds its own without touching API values.
    // Concurrent because menus build on region threads while overrides arrive on enable threads.
    private final Map<MenuButton, Key> buttonIcons = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<ApplianceType, Key> applianceIcons = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<ApplianceType, String> applianceNames = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<MenuScreen, String> titles = new java.util.concurrent.ConcurrentHashMap<>();

    private final Map<MenuButton, Key> configButtonIcons = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<ApplianceType, Key> configApplianceIcons = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<ApplianceType, String> configApplianceNames = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<MenuScreen, String> configTitles = new java.util.concurrent.ConcurrentHashMap<>();

    private RecipeMenuStyle() {
    }

    public static RecipeMenuStyle instance() {
        return INSTANCE;
    }

    /**
     * Replaces a button icon. Pass {@code null} to fall back to the default.
     *
     * @param button the button to reskin
     * @param itemId vanilla or CraftEngine item id
     */
    public void icon(@NotNull MenuButton button, @Nullable Key itemId) {
        if (itemId == null) {
            buttonIcons.remove(button);
        } else {
            buttonIcons.put(button, itemId);
        }
    }

    public @NotNull Key icon(@NotNull MenuButton button) {
        Key api = buttonIcons.get(button);
        if (api != null) {
            return api;
        }
        Key fromConfig = configButtonIcons.get(button);
        return fromConfig != null ? fromConfig : button.defaultIcon();
    }

    /**
     * Replaces the icon shown for one appliance on the home screen.
     *
     * @param cook the appliance
     * @param itemId vanilla or CraftEngine item id, {@code null} to reset
     */
    public void applianceIcon(@NotNull ApplianceType cook, @Nullable Key itemId) {
        if (itemId == null) {
            applianceIcons.remove(cook);
        } else {
            applianceIcons.put(cook, itemId);
        }
    }

    public @NotNull Key applianceIcon(@NotNull ApplianceType cook) {
        Key api = applianceIcons.get(cook);
        if (api != null) {
            return api;
        }
        Key fromConfig = configApplianceIcons.get(cook);
        return fromConfig != null ? fromConfig : DEFAULT_ICONS.get(cook);
    }

    /**
     * Replaces the display name of one appliance. Used in titles and buttons.
     *
     * @param cook the appliance
     * @param name plain text, {@code null} to reset
     */
    public void applianceName(@NotNull ApplianceType cook, @Nullable String name) {
        if (name == null) {
            applianceNames.remove(cook);
        } else {
            applianceNames.put(cook, name);
        }
    }

    public @NotNull String applianceName(@NotNull ApplianceType cook) {
        String api = applianceNames.get(cook);
        if (api != null) {
            return api;
        }
        String fromConfig = configApplianceNames.get(cook);
        return fromConfig != null ? fromConfig : DEFAULT_NAMES.getOrDefault(cook, cook.name());
    }

    /**
     * Replaces a screen title. See {@link MenuScreen} for the placeholders.
     *
     * @param screen the screen
     * @param template MiniMessage template, {@code null} to reset
     */
    public void title(@NotNull MenuScreen screen, @Nullable String template) {
        if (template == null) {
            titles.remove(screen);
        } else {
            titles.put(screen, template);
        }
    }

    public @NotNull String titleTemplate(@NotNull MenuScreen screen) {
        String api = titles.get(screen);
        if (api != null) {
            return api;
        }
        String fromConfig = configTitles.get(screen);
        return fromConfig != null ? fromConfig : screen.defaultTitle();
    }

    /**
     * Renders a screen title.
     *
     * @param screen the screen
     * @param placeholders alternating name and value, e.g. {@code "appliance", "Wok"}
     * @return the rendered title component
     */
    public @NotNull Component title(@NotNull MenuScreen screen, String... placeholders) {
        String template = titleTemplate(screen);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            template = template.replace("<" + placeholders[i] + ">", placeholders[i + 1]);
        }
        return AdventureHelper.miniMessage().deserialize(template)
                .colorIfAbsent(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Drops every API override. Values loaded from config.yml are kept. */
    public void reset() {
        buttonIcons.clear();
        applianceIcons.clear();
        applianceNames.clear();
        titles.clear();
    }

    // Config layer write path, for RecipeMenuConfig only.
    // A reload calls clearConfigLayer first; API values are untouched.

    @ApiStatus.Internal
    public void clearConfigLayer() {
        configButtonIcons.clear();
        configApplianceIcons.clear();
        configApplianceNames.clear();
        configTitles.clear();
    }

    @ApiStatus.Internal
    public void configIcon(@NotNull MenuButton button, @NotNull Key itemId) {
        configButtonIcons.put(button, itemId);
    }

    @ApiStatus.Internal
    public void configApplianceIcon(@NotNull ApplianceType cook, @NotNull Key itemId) {
        configApplianceIcons.put(cook, itemId);
    }

    @ApiStatus.Internal
    public void configApplianceName(@NotNull ApplianceType cook, @NotNull String name) {
        configApplianceNames.put(cook, name);
    }

    @ApiStatus.Internal
    public void configTitle(@NotNull MenuScreen screen, @NotNull String template) {
        configTitles.put(screen, template);
    }
}
