package net.kaleidoscope.cookery.plugin;

import net.kaleidoscope.cookery.block.listener.SteamerFallingBlockListener;
import net.kaleidoscope.cookery.block.listener.CustomBlockPlaceProtectionListener;
import net.kaleidoscope.cookery.block.listener.DisplayTrackingListener;
import net.kaleidoscope.cookery.block.listener.MillstoneAnimalListener;
import net.kaleidoscope.cookery.block.listener.MillstoneDamageListener;
import net.kaleidoscope.cookery.block.listener.MillstonePlaceListener;
import net.kaleidoscope.cookery.block.listener.TrashCanLandListener;
import net.kaleidoscope.cookery.block.listener.TrashCanListener;
import net.kaleidoscope.cookery.block.listener.ScarecrowTrampleListener;
import net.kaleidoscope.cookery.block.listener.PaddyTillListener;
import net.kaleidoscope.cookery.block.listener.TrashCanRespawnListener;
import net.kaleidoscope.cookery.block.behavior.SteamerBehavior;
import net.kaleidoscope.cookery.block.entity.FruitBasketController;
import net.kaleidoscope.cookery.block.entity.MillstoneController;
import net.kaleidoscope.cookery.block.entity.ScarecrowController;
import net.kaleidoscope.cookery.block.entity.TrashCanController;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplaySet;
import net.kaleidoscope.cookery.entity.cat.FruitBasketCatGoal;
import net.kaleidoscope.cookery.entity.cat.FruitBasketCatListener;
import net.kaleidoscope.cookery.item.listener.CaterpillarListener;
import net.kaleidoscope.cookery.item.listener.DishCarrierListener;
import net.kaleidoscope.cookery.item.listener.LunchBagListener;
import net.kaleidoscope.cookery.api.BlockTags;
import net.kaleidoscope.cookery.api.ItemTags;
import net.kaleidoscope.cookery.api.MillstoneAnimals;
import net.kaleidoscope.cookery.command.RecipeCommand;
import net.kaleidoscope.cookery.recipe.DishCarriers;
import net.kaleidoscope.cookery.recipe.FoodGroups;
import net.kaleidoscope.cookery.recipe.FoodRecipeManager;
import net.kaleidoscope.cookery.ui.RecipeMenuConfig;
import net.kaleidoscope.cookery.ui.input.AnvilTextPrompt;
import net.kaleidoscope.cookery.item.ItemIcons;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.ConsoleMessages;
import net.kaleidoscope.cookery.util.FoliaUtil;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.UUIDUtils;

import java.util.UUID;
import net.kaleidoscope.cookery.util.PlacementGuard;
import net.kaleidoscope.cookery.util.UniverseSpigotUtil;
import net.momirealms.antigrieflib.AntiGriefLib;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class KaleidoscopeCookeryPlugin extends JavaPlugin {
    // bStats 插件 ID：https://bstats.org/plugin/bukkit/KaleidoscopeCookeryPlugin/32444
    private static final int BSTATS_PLUGIN_ID = 32444;
    private static final String PLACEHOLDER_EXPANSION_CLASS =
            "net.kaleidoscope.cookery.papi.KaleidoscopeCookeryExpansion";

    // 各 region 线程都会读 onEnable 的写入靠 volatile 保证可见
    private static volatile KaleidoscopeCookeryPlugin instance;
    private AntiGriefLib antiGrief;
    private Metrics metrics;
    private Object placeholderExpansion;

    @Override
    public void onEnable() {
        instance = this;
        warmUpShutdownClasses();
        saveDefaultConfig();
        ConsoleMessages.load(this);
        RecipeMenuConfig.load();
        ItemIcons.generate();
        this.antiGrief = AntiGriefLib.builder(this)
                .ignoreOP(true)
                .bypassPermission("kaleidoscopecookery.antigrief.bypass")
                .build();
        FoodRecipeManager.registerParsers();
        MillstoneAnimals.registerParser();
        ItemTags.registerParser();
        FoodGroups.registerParser();
        DishCarriers.registerParser();
        BlockTags.registerParser();
        getServer().getPluginManager().registerEvents(new DishCarrierListener(), this);
        getServer().getPluginManager().registerEvents(new CaterpillarListener(), this);
        getServer().getPluginManager().registerEvents(new MillstoneDamageListener(), this);
        getServer().getPluginManager().registerEvents(new MillstoneAnimalListener(), this);
        getServer().getPluginManager().registerEvents(new MillstonePlaceListener(), this);
        getServer().getPluginManager().registerEvents(new SteamerFallingBlockListener(), this);
        getServer().getPluginManager().registerEvents(new CustomBlockPlaceProtectionListener(), this);
        getServer().getPluginManager().registerEvents(new FruitBasketCatListener(this), this);
        getServer().getPluginManager().registerEvents(new TrashCanListener(), this);
        if (UniverseSpigotUtil.isUniverseSpigot()) {
            getServer().getPluginManager().registerEvents(new TrashCanLandListener(), this);
        }
        getServer().getPluginManager().registerEvents(new ScarecrowTrampleListener(), this);
        getServer().getPluginManager().registerEvents(new PaddyTillListener(), this);
        getServer().getPluginManager().registerEvents(new DisplayTrackingListener(), this);
        getServer().getPluginManager().registerEvents(new LunchBagListener(), this);
        getServer().getPluginManager().registerEvents(new CraftEngineRegistryCheckListener(this), this);
        if (FoliaUtil.isFolia()) {
            TrashCanRespawnListener.registerFoliaPackets(this);
        } else {
            TrashCanRespawnListener.registerBukkitEvents(this);
        }
        getServer().getPluginManager().registerEvents(new AnvilTextPrompt(), this);
        registerRecipeCommand();
        LootFormulas.register();
        Conditions.register();
        BlockBehaviors.register();
        ItemBehaviors.register();
        FurnitureBehaviors.register();
        setupPlaceholders();
        setupMetrics();
        getLogger().info(ConsoleMessages.t("plugin.enabled"));
    }

    @Override
    public void onDisable() {
        FoliaUtil.shutdown();
        // 关服时把还在垃圾桶里的玩家放出来 还原模式与头盔
        if (FoliaUtil.isFolia()) {
            TrashCanRespawnListener.uninstallAll();
        }
        TrashCanController.releaseAll();
        ScarecrowController.clearIndex();
        MillstoneController.clearAll();
        SteamerBehavior.clearAll();
        FruitBasketCatGoal.clearAll();
        FruitBasketController.clearIndex();
        ItemDisplaySet.clearAll();
        AnvilTextPrompt.clearAll();
        PlacementGuard.clear();
        unregisterPlaceholders();
    }

    private void warmUpShutdownClasses() {
        UUIDUtils.uuidToIntArray(new UUID(0L, 0L));
        BlockEntityNbt.itemTag(Item.empty());
    }

    private void registerRecipeCommand() {
        PluginCommand command = getCommand("kcrecipe");
        if (command == null) {
            getLogger().warning("plugin.yml 缺少 kcrecipe 指令定义 食谱菜单无法使用");
            return;
        }
        RecipeCommand executor = new RecipeCommand();
        command.setExecutor(executor);
        command.setTabCompleter(executor);
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
        return instance.antiGrief;
    }
}
