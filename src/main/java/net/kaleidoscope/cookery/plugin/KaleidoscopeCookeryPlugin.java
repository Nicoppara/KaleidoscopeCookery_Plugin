package net.kaleidoscope.cookery.plugin;

import net.kaleidoscope.cookery.block.listener.SteamerFallingBlockListener;
import net.kaleidoscope.cookery.block.listener.MillstoneAnimalListener;
import net.kaleidoscope.cookery.block.listener.MillstoneDamageListener;
import net.kaleidoscope.cookery.block.listener.MillstonePlaceListener;
import net.kaleidoscope.cookery.block.listener.TrashCanListener;
import net.kaleidoscope.cookery.block.listener.TrashCanRespawnListener;
import net.kaleidoscope.cookery.block.entity.TrashCanController;
import net.kaleidoscope.cookery.entity.cat.FruitBasketCatListener;
import net.kaleidoscope.cookery.item.listener.LunchBagListener;
import net.kaleidoscope.cookery.api.MillstoneAnimals;
import net.kaleidoscope.cookery.item.StrawHatListener;
import net.kaleidoscope.cookery.recipe.FoodRecipeManager;
import net.kaleidoscope.cookery.util.ConsoleMessages;
import net.kaleidoscope.cookery.util.FoliaUtil;
import net.momirealms.antigrieflib.AntiGriefLib;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

public final class KaleidoscopeCookeryPlugin extends JavaPlugin {
    // bStats 插件 ID：https://bstats.org/plugin/bukkit/KaleidoscopeCookeryPlugin/32444
    private static final int BSTATS_PLUGIN_ID = 32444;
    private static final String PLACEHOLDER_EXPANSION_CLASS =
            "net.kaleidoscope.cookery.papi.KaleidoscopeCookeryExpansion";

    private static KaleidoscopeCookeryPlugin instance;
    private AntiGriefLib antiGrief;
    private Metrics metrics;
    private Object placeholderExpansion;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        ConsoleMessages.load(this);
        FoodRecipeManager.registerParsers();
        MillstoneAnimals.registerParser();
        getServer().getPluginManager().registerEvents(new MillstoneDamageListener(), this);
        getServer().getPluginManager().registerEvents(new MillstoneAnimalListener(), this);
        getServer().getPluginManager().registerEvents(new MillstonePlaceListener(), this);
        getServer().getPluginManager().registerEvents(new SteamerFallingBlockListener(), this);
        getServer().getPluginManager().registerEvents(new FruitBasketCatListener(this), this);
        getServer().getPluginManager().registerEvents(new TrashCanListener(), this);
        getServer().getPluginManager().registerEvents(new StrawHatListener(), this);
        getServer().getPluginManager().registerEvents(new LunchBagListener(), this);
        getServer().getPluginManager().registerEvents(new StrawHatListener(), this);
        getServer().getPluginManager().registerEvents(new CraftEngineRegistryCheckListener(this), this);
        if (FoliaUtil.isFolia()) {
            TrashCanRespawnListener.registerFoliaPackets(this);
        } else {
            TrashCanRespawnListener.registerBukkitEvents(this);
        }
        BlockBehaviors.register();
        ItemBehaviors.register();
        FurnitureBehaviors.register();
        setupPlaceholders();
        setupMetrics();
        getLogger().info(ConsoleMessages.t("plugin.enabled"));
    }

    @Override
    public void onDisable() {
        // 关服时把还在垃圾桶里的玩家放出来 还原模式与头盔
        if (FoliaUtil.isFolia()) {
            TrashCanRespawnListener.uninstallAll();
        }
        TrashCanController.releaseAll();
        unregisterPlaceholders();
    }

    // 根据配置决定是否启用 bStats 匿名统计（config.yml 中 metrics.enabled，默认 true）
    private void setupMetrics() {
        if (getConfig().getBoolean("metrics.enabled", true)) {
            this.metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        } else {
            getLogger().info(ConsoleMessages.t("metrics.disabled"));
        }
    }

    private void setupPlaceholders() {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        try {
            Class<?> expansionClass = Class.forName(
                    PLACEHOLDER_EXPANSION_CLASS,
                    true,
                    KaleidoscopeCookeryPlugin.class.getClassLoader());
            Object expansion = expansionClass
                    .getConstructor(KaleidoscopeCookeryPlugin.class)
                    .newInstance(this);
            Object registered = expansionClass.getMethod("register").invoke(expansion);
            if (Boolean.TRUE.equals(registered)) {
                this.placeholderExpansion = expansion;
                getLogger().info("Registered PlaceholderAPI expansion: kaleidoscopecookery");
            } else {
                getLogger().warning("PlaceholderAPI expansion registration returned false.");
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            getLogger().warning("Failed to register PlaceholderAPI expansion: " + e.getMessage());
        }
    }

    private void unregisterPlaceholders() {
        if (placeholderExpansion == null) {
            return;
        }
        try {
            placeholderExpansion.getClass().getMethod("unregister").invoke(placeholderExpansion);
        } catch (ReflectiveOperationException | LinkageError e) {
            getLogger().warning("Failed to unregister PlaceholderAPI expansion: " + e.getMessage());
        } finally {
            placeholderExpansion = null;
        }
    }

    public static KaleidoscopeCookeryPlugin instance() {
        return instance;
    }

    public static AntiGriefLib antiGrief() {
        if (instance.antiGrief == null) {
            instance.antiGrief = AntiGriefLib.builder(instance)
                    .ignoreOP(true)
                    .bypassPermission("kaleidoscopecookery.antigrief.bypass")
                    .build();
        }
        return instance.antiGrief;
    }
}
