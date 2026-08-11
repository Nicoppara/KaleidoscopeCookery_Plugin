package net.kaleidoscope.cookery.api.ui;

import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Registration point for a {@link RecipeMenuProvider}. One provider at a time —
 * registering replaces the previous one. Register during your enable phase.
 */
public final class RecipeMenuHooks {
    private static final RecipeMenuHooks INSTANCE = new RecipeMenuHooks();

    // Written on another plugin enable thread, read on the player region thread
    private volatile RecipeMenuProvider provider;

    private RecipeMenuHooks() {
    }

    public static RecipeMenuHooks instance() {
        return INSTANCE;
    }

    /**
     * Installs the provider, or clears it when {@code null}.
     *
     * @param provider the provider taking over one or more screens
     */
    public void provider(@Nullable RecipeMenuProvider provider) {
        this.provider = provider;
    }

    public @Nullable RecipeMenuProvider provider() {
        return this.provider;
    }

    // Internal dispatch. True means an external provider took over.
    // A throwing provider falls back rather than leaving the player with no screen.

    public boolean dispatchHome(@NotNull Player player, boolean editable) {
        RecipeMenuProvider p = this.provider;
        if (p == null) {
            return false;
        }
        try {
            return p.openHome(player, editable);
        } catch (Throwable t) {
            warn("openHome", t);
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
            warn("openList", t);
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
            warn("openDetail", t);
            return false;
        }
    }

    private void warn(String stage, Throwable t) {
        net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin.instance().getLogger()
                .warning("食谱菜单外部实现 " + stage + " 抛异常 已回落到内置菜单: " + t);
    }
}
