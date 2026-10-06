package net.kaleidoscope.cookery.recipe;

import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.SectionConfigParser;
import net.momirealms.craftengine.core.plugin.config.lifecycle.LoadingStage;
import net.momirealms.craftengine.core.util.Key;

import java.nio.file.Path;
import java.util.List;

// 菜品吃完退还什么容器 两条进食路径都查这里 不往物品存 NBT 所以老物品也跟着最新配方走
// 两个数据源 模糊配方的 carrier 与 dish_carrier 配置段 后者优先
public final class DishCarriers {
    public static final LoadingStage DISH_CARRIERS = new LoadingStage("dish carriers");

    private DishCarriers() {
    }

    // Derived carriers publish together with the recipes; this facade keeps the existing API.
    public static void rebuild(Iterable<FlexFoodRecipe> recipes) {
        FoodRecipeRegistry.instance().rebuildCarriers(recipes);
    }

    public static Key of(Key result) { return FoodRecipeRegistry.instance().carrierOf(result); }
    public static boolean isEmpty() { return !FoodRecipeRegistry.instance().hasCarriers(); }

    public static void registerParser() {
        CraftEngine.instance().packManager().registerConfigSectionParser(new DishCarrierParser());
    }

    private static final class DishCarrierParser extends SectionConfigParser {
        @Override
        public void setErrorHandler(java.util.function.Consumer<net.momirealms.craftengine.core.plugin.config.ResourceException> handler) {
            super.setErrorHandler(FoodRecipeManager.trackRecipeLoadErrors(handler));
        }
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
            return List.of(FoodRecipeManager.RECIPE_LOAD_BEGIN);
        }

        @Override
        public int count() {
            return this.count;
        }

        @Override
        public void preProcess() {
            this.count = 0;
            FoodRecipeRegistry.instance().configurationUpdate(() -> FoodRecipeRegistry.instance().clearConfiguredCarriers());
        }

        @Override
        public void postProcess() {
            // Final recipe stage publishes carriers and recipes together.
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
                FoodRecipeRegistry.instance().configurationUpdate(() ->
                        FoodRecipeRegistry.instance().registerConfiguredCarrier(Key.of(dish.trim()), Key.of(carrier.trim())));
                this.count++;
            }
        }
    }
}
