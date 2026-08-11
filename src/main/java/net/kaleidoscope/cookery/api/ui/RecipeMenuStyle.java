package net.kaleidoscope.cookery.api.ui;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.momirealms.craftengine.core.util.AdventureHelper;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.libraries.adventure.text.format.NamedTextColor;
import net.momirealms.craftengine.libraries.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.ApiStatus.Internal;

public final class RecipeMenuStyle {
   private static final RecipeMenuStyle INSTANCE = new RecipeMenuStyle();
   private static final Map<ApplianceType, String> DEFAULT_NAMES = new EnumMap<>(ApplianceType.class);
   private static final Map<ApplianceType, Key> DEFAULT_ICONS = new EnumMap<>(ApplianceType.class);
   private final Map<MenuButton, Key> buttonIcons = new ConcurrentHashMap<>();
   private final Map<ApplianceType, Key> applianceIcons = new ConcurrentHashMap<>();
   private final Map<ApplianceType, String> applianceNames = new ConcurrentHashMap<>();
   private final Map<MenuScreen, String> titles = new ConcurrentHashMap<>();
   private final Map<MenuButton, Key> configButtonIcons = new ConcurrentHashMap<>();
   private final Map<ApplianceType, Key> configApplianceIcons = new ConcurrentHashMap<>();
   private final Map<ApplianceType, String> configApplianceNames = new ConcurrentHashMap<>();
   private final Map<MenuScreen, String> configTitles = new ConcurrentHashMap<>();

   private RecipeMenuStyle() {
   }

   public static RecipeMenuStyle instance() {
      return INSTANCE;
   }

   public void icon(@NotNull MenuButton button, @Nullable Key itemId) {
      if (itemId == null) {
         this.buttonIcons.remove(button);
      } else {
         this.buttonIcons.put(button, itemId);
      }
   }

   @NotNull
   public Key icon(@NotNull MenuButton button) {
      Key api = this.buttonIcons.get(button);
      if (api != null) {
         return api;
      }

      Key fromConfig = this.configButtonIcons.get(button);
      return fromConfig != null ? fromConfig : button.defaultIcon();
   }

   public void applianceIcon(@NotNull ApplianceType cook, @Nullable Key itemId) {
      if (itemId == null) {
         this.applianceIcons.remove(cook);
      } else {
         this.applianceIcons.put(cook, itemId);
      }
   }

   @NotNull
   public Key applianceIcon(@NotNull ApplianceType cook) {
      Key api = this.applianceIcons.get(cook);
      if (api != null) {
         return api;
      }

      Key fromConfig = this.configApplianceIcons.get(cook);
      return fromConfig != null ? fromConfig : DEFAULT_ICONS.get(cook);
   }

   public void applianceName(@NotNull ApplianceType cook, @Nullable String name) {
      if (name == null) {
         this.applianceNames.remove(cook);
      } else {
         this.applianceNames.put(cook, name);
      }
   }

   @NotNull
   public String applianceName(@NotNull ApplianceType cook) {
      String api = this.applianceNames.get(cook);
      if (api != null) {
         return api;
      }

      String fromConfig = this.configApplianceNames.get(cook);
      return fromConfig != null ? fromConfig : DEFAULT_NAMES.getOrDefault(cook, cook.name());
   }

   public void title(@NotNull MenuScreen screen, @Nullable String template) {
      if (template == null) {
         this.titles.remove(screen);
      } else {
         this.titles.put(screen, template);
      }
   }

   @NotNull
   public String titleTemplate(@NotNull MenuScreen screen) {
      String api = this.titles.get(screen);
      if (api != null) {
         return api;
      }

      String fromConfig = this.configTitles.get(screen);
      return fromConfig != null ? fromConfig : screen.defaultTitle();
   }

   @NotNull
   public Component title(@NotNull MenuScreen screen, String... placeholders) {
      String template = this.titleTemplate(screen);

      for (int i = 0; i + 1 < placeholders.length; i += 2) {
         template = template.replace("<" + placeholders[i] + ">", placeholders[i + 1]);
      }

      return AdventureHelper.miniMessage().deserialize(template).colorIfAbsent(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false);
   }

   public void reset() {
      this.buttonIcons.clear();
      this.applianceIcons.clear();
      this.applianceNames.clear();
      this.titles.clear();
   }

   @Internal
   public void clearConfigLayer() {
      this.configButtonIcons.clear();
      this.configApplianceIcons.clear();
      this.configApplianceNames.clear();
      this.configTitles.clear();
   }

   @Internal
   public void configIcon(@NotNull MenuButton button, @NotNull Key itemId) {
      this.configButtonIcons.put(button, itemId);
   }

   @Internal
   public void configApplianceIcon(@NotNull ApplianceType cook, @NotNull Key itemId) {
      this.configApplianceIcons.put(cook, itemId);
   }

   @Internal
   public void configApplianceName(@NotNull ApplianceType cook, @NotNull String name) {
      this.configApplianceNames.put(cook, name);
   }

   @Internal
   public void configTitle(@NotNull MenuScreen screen, @NotNull String template) {
      this.configTitles.put(screen, template);
   }

   static {
      DEFAULT_NAMES.put(ApplianceType.POT, "炒锅");
      DEFAULT_NAMES.put(ApplianceType.STOCKPOT, "高汤锅");
      DEFAULT_NAMES.put(ApplianceType.STEAMER, "蒸笼");
      DEFAULT_NAMES.put(ApplianceType.SHAWARMA, "沙威玛烤架");
      DEFAULT_NAMES.put(ApplianceType.MILLSTONE, "石磨");
      DEFAULT_NAMES.put(ApplianceType.CHOPPING_BOARD, "砧板");
      DEFAULT_NAMES.put(ApplianceType.TEAPOT, "茶壶");
      DEFAULT_ICONS.put(ApplianceType.POT, ItemKeys.MENU_POT);
      DEFAULT_ICONS.put(ApplianceType.STOCKPOT, ItemKeys.MENU_STOCKPOT);
      DEFAULT_ICONS.put(ApplianceType.STEAMER, ItemKeys.MENU_STEAMER);
      DEFAULT_ICONS.put(ApplianceType.SHAWARMA, ItemKeys.MENU_SHAWARMA);
      DEFAULT_ICONS.put(ApplianceType.MILLSTONE, ItemKeys.MENU_MILLSTONE);
      DEFAULT_ICONS.put(ApplianceType.CHOPPING_BOARD, ItemKeys.MENU_CHOPPING_BOARD);
      DEFAULT_ICONS.put(ApplianceType.TEAPOT, ItemKeys.MENU_TEAPOT);
   }
}
