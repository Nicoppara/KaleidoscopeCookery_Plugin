package net.kaleidoscope.cookery.entity.cat;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Cat;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

// 给进入世界的猫注入趴果篮 AI Paper 的自定义 Goal 不会持久化 需在每次实体加载时重新注入
public final class FruitBasketCatListener implements Listener {

    private final Plugin plugin;

    public FruitBasketCatListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityAdd(EntityAddToWorldEvent event) {
        if (!(event.getEntity() instanceof Cat cat)) {
            return;
        }
        FruitBasketCatGoal goal = new FruitBasketCatGoal(cat, plugin);
        if (!Bukkit.getMobGoals().hasGoal(cat, goal.getKey())) {
            Bukkit.getMobGoals().addGoal(cat, 4, goal);
        }
    }
}
