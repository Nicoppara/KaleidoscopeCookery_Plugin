package net.kaleidoscope.cookery.plugin;

import net.kaleidoscope.cookery.block.listener.MillstoneAnimalListener;
import net.kaleidoscope.cookery.block.listener.MillstoneDamageListener;
import net.kaleidoscope.cookery.block.listener.MillstonePlaceListener;
import net.kaleidoscope.cookery.recipe.food.FoodRecipeManager;
import net.kaleidoscope.cookery.plugin.BlockBehaviors;
import net.kaleidoscope.cookery.plugin.FurnitureBehaviors;
import net.kaleidoscope.cookery.plugin.ItemBehaviors;
import net.momirealms.antigrieflib.AntiGriefLib;
import org.bukkit.plugin.java.JavaPlugin;

public final class KaleidoscopeCookeryPlugin extends JavaPlugin {
    private static KaleidoscopeCookeryPlugin instance;
    private AntiGriefLib antiGrief;

    @Override
    public void onEnable() {
        instance = this;
        FoodRecipeManager.registerParsers();
        getServer().getPluginManager().registerEvents(new MillstoneDamageListener(), this);
        getServer().getPluginManager().registerEvents(new MillstoneAnimalListener(), this);
        getServer().getPluginManager().registerEvents(new MillstonePlaceListener(), this);
        BlockBehaviors.register();
        ItemBehaviors.register();
        FurnitureBehaviors.register();
        getLogger().info("KaleidoscopeCookery 已启动！");
    }

    // 延迟初始化，等领地/保护插件都加载完再建，绕过权限 kaleidoscopecookery.antigrief.bypass
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
