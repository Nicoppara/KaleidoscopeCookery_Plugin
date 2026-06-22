package top.nicoppa.nicoPengRen;

import org.bukkit.plugin.java.JavaPlugin;
import top.nicoppa.nicoPengRen.recipe.food.FoodRecipeManager;
import top.nicoppa.nicoPengRen.registry.BlockBehaviors;
import top.nicoppa.nicoPengRen.content.millstone.MillstoneAnimalListener;
import top.nicoppa.nicoPengRen.content.millstone.MillstoneDamageListener;
import top.nicoppa.nicoPengRen.content.millstone.MillstonePlaceListener;
import top.nicoppa.nicoPengRen.registry.FurnitureBehaviors;
import top.nicoppa.nicoPengRen.registry.ItemBehaviors;

public final class NicoPengRen extends JavaPlugin {
    @Override
    public void onEnable() {
        FoodRecipeManager.registerParsers();
        getServer().getPluginManager().registerEvents(new MillstoneDamageListener(), this);
        getServer().getPluginManager().registerEvents(new MillstoneAnimalListener(), this);
        getServer().getPluginManager().registerEvents(new MillstonePlaceListener(), this);
        BlockBehaviors.register();
        ItemBehaviors.register();
        FurnitureBehaviors.register();
        getLogger().info("Nico烹饪插件已启动！");
    }
}