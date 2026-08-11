package net.kaleidoscope.cookery.api.ui;

import net.kaleidoscope.cookery.item.ItemKeys;
import net.momirealms.craftengine.core.util.Key;

public enum MenuButton {
   FILLER(ItemKeys.MENU_FILLER),
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
   CARRIER_NONE(ItemKeys.MENU_CARRIER_NONE);

   private final Key defaultIcon;

   MenuButton(Key defaultIcon) {
      this.defaultIcon = defaultIcon;
   }

   public Key defaultIcon() {
      return this.defaultIcon;
   }
}
