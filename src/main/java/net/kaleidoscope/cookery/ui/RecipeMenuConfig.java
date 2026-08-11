package net.kaleidoscope.cookery.ui;

import java.util.Locale;
import net.kaleidoscope.cookery.api.ui.MenuButton;
import net.kaleidoscope.cookery.api.ui.MenuScreen;
import net.kaleidoscope.cookery.api.ui.RecipeMenuStyle;
import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.configuration.ConfigurationSection;

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
      ConfigurationSection root = KaleidoscopeCookeryPlugin.instance().getConfig().getConfigurationSection("recipe_menu");
      if (root != null) {
         loadButtons(style, root.getConfigurationSection("buttons"));
         loadAppliances(style, root.getConfigurationSection("appliances"));
         loadTitles(style, root.getConfigurationSection("titles"));
      }
   }

   private static void loadButtons(RecipeMenuStyle style, ConfigurationSection section) {
      if (section != null) {
         for (String rawKey : section.getKeys(false)) {
            MenuButton button = parse(MenuButton.class, rawKey, "recipe_menu.buttons");
            Key itemId = parseKey(section.getString(rawKey), "recipe_menu.buttons." + rawKey);
            if (button != null && itemId != null) {
               style.configIcon(button, itemId);
            }
         }
      }
   }

   private static void loadAppliances(RecipeMenuStyle style, ConfigurationSection section) {
      if (section != null) {
         for (String rawKey : section.getKeys(false)) {
            ApplianceType cook = parse(ApplianceType.class, rawKey, "recipe_menu.appliances");
            if (cook != null) {
               String path = "recipe_menu.appliances." + rawKey;
               ConfigurationSection sub = section.getConfigurationSection(rawKey);
               if (sub == null) {
                  warn(path + " 应该是一个小节 里面写 icon 与 name");
               } else {
                  Key icon = parseKey(sub.getString("icon"), path + ".icon");
                  if (icon != null) {
                     style.configApplianceIcon(cook, icon);
                  }

                  String name = sub.getString("name");
                  if (name != null && !name.isEmpty()) {
                     style.configApplianceName(cook, name);
                  }
               }
            }
         }
      }
   }

   private static void loadTitles(RecipeMenuStyle style, ConfigurationSection section) {
      if (section != null) {
         for (String rawKey : section.getKeys(false)) {
            MenuScreen screen = parse(MenuScreen.class, rawKey, "recipe_menu.titles");
            String template = section.getString(rawKey);
            if (screen != null && template != null && !template.isEmpty()) {
               style.configTitle(screen, template);
            }
         }
      }
   }

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
      if (raw != null && !raw.isBlank()) {
         try {
            return Key.of(raw.trim());
         } catch (RuntimeException e) {
            warn(path + " 的物品 id " + raw + " 格式不正确 已跳过");
            return null;
         }
      } else {
         return null;
      }
   }

   private static void warn(String message) {
      KaleidoscopeCookeryPlugin.instance().getLogger().warning("食谱菜单配置 " + message);
   }
}
