package net.kaleidoscope.cookery.recipe;

import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.SectionConfigParser;
import net.momirealms.craftengine.core.plugin.config.lifecycle.LoadingStage;
import net.momirealms.craftengine.core.util.Key;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 菜品吃完退还什么容器 两条进食路径都查这里 不往物品存 NBT 所以老物品也跟着最新配方走
// 两个数据源 模糊配方的 carrier 与 dish_carrier 配置段 后者优先
public final class DishCarriers {
    public static final LoadingStage DISH_CARRIERS = new LoadingStage("dish carriers");

    private DishCarriers() {
    }

    private static volatile Map<Key, Key> fromRecipes = Map.of();
    private static final Map<Key, Key> FROM_CONFIG = new ConcurrentHashMap<>();
    // 成品 id -> 容器 查询在进食热路径上 两张表合并后缓存住
    private static volatile Map<Key, Key> cache = Map.of();

    // 同一个成品被多条配方产出时取先注册的那条 这种情况本来就该避免
    public static void rebuild(Iterable<FlexFoodRecipe> recipes) {
        Map<Key, Key> map = new HashMap<>();
        for (FlexFoodRecipe recipe : recipes) {
            if (recipe.carrier() != null) {
                map.putIfAbsent(recipe.result(), recipe.carrier());
            }
        }
        fromRecipes = Map.copyOf(map);
        merge();
    }

    private static void merge() {
        Map<Key, Key> map = new HashMap<>(fromRecipes);
        map.putAll(FROM_CONFIG);
        cache = Map.copyOf(map);
    }

    // 没有容器的菜返回 null 调用方什么都不用给
    public static Key of(Key result) {
        return result == null ? null : cache.get(result);
    }

    public static boolean isEmpty() {
        return cache.isEmpty();
    }

    public static void registerParser() {
        CraftEngine.instance().packManager().registerConfigSectionParser(new DishCarrierParser());
    }

    private static final class DishCarrierParser extends SectionConfigParser {
        private int count;

        @Override
        public Key type() {
            return Key.of("kaleidoscopecookery:dish_carrier");
        }

        @Override
        public String[] sectionId() {
            return new String[]{"dish_carrier", "dish-carrier", "dish_carriers", "dish-carriers"};
        }

        @Override
        public LoadingStage loadingStage() {
            return DISH_CARRIERS;
        }

        @Override
        public List<LoadingStage> dependencies() {
            return List.of();
        }

        @Override
        public int count() {
            return this.count;
        }

        @Override
        public void preProcess() {
            this.count = 0;
            FROM_CONFIG.clear();
        }

        @Override
        public void postProcess() {
            merge();
        }

        @Override
        protected void parseSection(Pack pack, Path path, ConfigSection section) {
            for (String dish : section.keySet()) {
                String carrier = section.getString(dish);
                if (carrier == null || carrier.isBlank()) {
                    KaleidoscopeCookeryPlugin.instance().getLogger().warning(
                            "[dish_carrier] " + dish + " 没有写退还的容器 已跳过");
                    continue;
                }
                FROM_CONFIG.put(Key.of(dish.trim()), Key.of(carrier.trim()));
                this.count++;
            }
        }
    }
}
