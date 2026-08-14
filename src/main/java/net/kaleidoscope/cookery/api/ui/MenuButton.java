package net.kaleidoscope.cookery.api.ui;

import net.kaleidoscope.cookery.item.ItemKeys;
import net.momirealms.craftengine.core.util.Key;

/**
 * Buttons in the recipe menus whose icon can be replaced.
 * Each constant carries the built-in default; override one through
 * {@link RecipeMenuStyle#icon(MenuButton, Key)} with any vanilla or
 * CraftEngine custom item id.
 */
public enum MenuButton {
    /** Border filler. */
    FILLER(ItemKeys.MENU_FILLER),
    /** Shown when an item id fails to resolve. */
    INVALID(ItemKeys.MENU_INVALID),
    BACK(ItemKeys.MENU_BACK),
    PREVIOUS_PAGE(ItemKeys.MENU_PREV),
    NEXT_PAGE(ItemKeys.MENU_NEXT),
    CREATE(ItemKeys.MENU_CREATE),
    SAVE(ItemKeys.MENU_SAVE),
    DELETE(ItemKeys.MENU_DELETE),
    ADD(ItemKeys.MENU_ADD),
    COUNT(ItemKeys.MENU_COUNT),
    MODE(ItemKeys.MENU_MODE),
    ROTATION(ItemKeys.MENU_ROTATION),
    LIQUID(ItemKeys.MENU_LIQUID),
    /** Served bare-handed, i.e. the recipe needs no bowl. */
    CARRIER_NONE(ItemKeys.MENU_CARRIER_NONE),
    /** Whether the recipe honours the equivalent-food tags. Defaults to the generic mode icon. */
    EQUIVALENT_FOODS(ItemKeys.MENU_MODE),
    /** Whether the recipe honours the seasoning tags. Defaults to the generic mode icon. */
    SEASONINGS(ItemKeys.MENU_MODE);

    private final Key defaultIcon;

    MenuButton(Key defaultIcon) {
        this.defaultIcon = defaultIcon;
    }

    public Key defaultIcon() {
        return defaultIcon;
    }
}
