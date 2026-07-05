package net.kaleidoscope.cookery.plugin;

import net.kaleidoscope.cookery.block.listener.SteamerFallingBlockListener;
import net.kaleidoscope.cookery.block.listener.MillstoneAnimalListener;
import net.kaleidoscope.cookery.block.listener.MillstoneDamageListener;
import net.kaleidoscope.cookery.block.listener.MillstonePlaceListener;
import net.kaleidoscope.cookery.block.listener.TrashCanListener;
import net.kaleidoscope.cookery.block.listener.TrashCanRespawnListener;
import net.kaleidoscope.cookery.block.entity.TrashCanController;
import net.kaleidoscope.cookery.entity.cat.FruitBasketCatListener;
import net.kaleidoscope.cookery.api.MillstoneAnimals;
import net.kaleidoscope.cookery.recipe.FoodRecipeManager;
import net.kaleidoscope.cookery.util.FoliaUtil;
import net.momirealms.antigrieflib.AntiGriefLib;
import org.bukkit.plugin.java.JavaPlugin;

public final class KaleidoscopeCookeryPlugin extends JavaPlugin {
    private static KaleidoscopeCookeryPlugin instance;
    private AntiGriefLib antiGrief;

    @Override
    public void onEnable() {
        instance = this;
        FoodRecipeManager.registerParsers();
        MillstoneAnimals.registerParser();
        getServer().getPluginManager().registerEvents(new MillstoneDamageListener(), this);
        getServer().getPluginManager().registerEvents(new MillstoneAnimalListener(), this);
        getServer().getPluginManager().registerEvents(new MillstonePlaceListener(), this);
        getServer().getPluginManager().registerEvents(new SteamerFallingBlockListener(), this);
        getServer().getPluginManager().registerEvents(new FruitBasketCatListener(this), this);
        getServer().getPluginManager().registerEvents(new TrashCanListener(), this);
        if (FoliaUtil.isFolia()) {
            TrashCanRespawnListener.registerFoliaPackets(this);
        } else {
            TrashCanRespawnListener.registerBukkitEvents(this);
        }
        BlockBehaviors.register();
        ItemBehaviors.register();
        FurnitureBehaviors.register();
        getLogger().info("KaleidoscopeCookery 已启动！");
    }

    @Override
    public void onDisable() {
        // 关服时把还在垃圾桶里的玩家放出来 还原模式与头盔
        if (FoliaUtil.isFolia()) {
            TrashCanRespawnListener.uninstallAll();
        }
        TrashCanController.releaseAll();
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
