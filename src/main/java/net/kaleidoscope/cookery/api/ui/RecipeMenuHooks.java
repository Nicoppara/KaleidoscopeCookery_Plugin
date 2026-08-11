package net.kaleidoscope.cookery.api.ui;

import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RecipeMenuHooks {
   private static final RecipeMenuHooks INSTANCE = new RecipeMenuHooks();
   private volatile RecipeMenuProvider provider;

   private RecipeMenuHooks() {
   }

   public static RecipeMenuHooks instance() {
      return INSTANCE;
   }

   public void provider(@Nullable RecipeMenuProvider provider) {
      this.provider = provider;
   }

   @Nullable
   public RecipeMenuProvider provider() {
      return this.provider;
   }

   public boolean dispatchHome(@NotNull Player player, boolean editable) {
      RecipeMenuProvider p = this.provider;
      if (p == null) {
         return false;
      }

      try {
         return p.openHome(player, editable);
      } catch (Throwable t) {
         this.warn("openHome", t);
         return false;
      }
   }

   public boolean dispatchList(@NotNull Player player, @NotNull ApplianceType cook, boolean editable) {
      RecipeMenuProvider p = this.provider;
      if (p == null) {
         return false;
      }

      try {
         return p.openList(player, cook, editable);
      } catch (Throwable t) {
         this.warn("openList", t);
         return false;
      }
   }

   public boolean dispatchDetail(@NotNull Player player, @NotNull ApplianceType cook, @Nullable Key recipeId) {
      RecipeMenuProvider p = this.provider;
      if (p == null) {
         return false;
      }

      try {
         return p.openDetail(player, cook, recipeId);
      } catch (Throwable t) {
         this.warn("openDetail", t);
         return false;
      }
   }

   private void warn(String stage, Throwable t) {
      KaleidoscopeCookeryPlugin.instance()
         .getLogger()
         .warning("食谱菜单外部实现 " + stage + " 抛异常 已回落到内置菜单: " + t);
   }
}
