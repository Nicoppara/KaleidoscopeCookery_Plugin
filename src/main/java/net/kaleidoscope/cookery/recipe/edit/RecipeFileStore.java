package net.kaleidoscope.cookery.recipe.edit;

import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 配方 YAML 的读改写 只碰目标配方那一个节点 兄弟节点与其注释原样保留
// 全部方法都做磁盘 IO 必须在 async 调度器上调用 见 RecipeEditService
public final class RecipeFileStore {
    private static final String PACK_NAMESPACE = "kaleidoscopecookery";
    private static final String RECIPE_FOLDER = "recipe";

    private static final String[] ACCURATE_SECTIONS = {"accurate_foods", "accurate-foods"};
    private static final String[] POT_FLEX_SECTIONS = {"pot_flex_foods", "pot-flex-foods"};
    private static final String[] STOCK_FLEX_SECTIONS = {"stock_flex_foods", "stock-flex-foods"};
    private static final String[] CHOPPING_SECTIONS = {"chopping_board_raws", "chopping-board-raws"};
    private static final String[] TEAPOT_SECTIONS = {"teapot_result", "teapot-result"};
    private static final String[] STOCK_RAW_SECTIONS = {"stock_food_raw", "stock-food-raw"};
    private static final String LIQUID_KEY = "liquid";

    private static final String ACCURATE_FILE = "accurate.yml";
    private static final String POT_FILE = "pot.yml";
    private static final String STOCKPOT_FILE = "stockpot.yml";
    private static final String CHOPPING_FILE = "chopping_board.yml";
    private static final String TEAPOT_FILE = "teapot.yml";

    private RecipeFileStore() {
    }

    public static String[] accurateSections() {
        return ACCURATE_SECTIONS;
    }

    public static String[] flexSections(ApplianceType cook) {
        return cook == ApplianceType.STOCKPOT ? STOCK_FLEX_SECTIONS : POT_FLEX_SECTIONS;
    }

    // 新建配方落到哪个文件 已有配方原地改写走 RecipeSourceIndex
    public static Path defaultAccurateFile() {
        return recipeFolder().resolve(ACCURATE_FILE);
    }

    public static String[] choppingSections() {
        return CHOPPING_SECTIONS;
    }

    public static String[] teapotSections() {
        return TEAPOT_SECTIONS;
    }

    public static Path defaultChoppingFile() {
        return recipeFolder().resolve(CHOPPING_FILE);
    }

    public static Path defaultTeapotFile() {
        return recipeFolder().resolve(TEAPOT_FILE);
    }

    public static Path defaultFlexFile(ApplianceType cook) {
        return recipeFolder().resolve(cook == ApplianceType.STOCKPOT ? STOCKPOT_FILE : POT_FILE);
    }

    // 汤底表是 stock_food_raw.liquid 下的一个列表 不是 id 键控的节点
    // 所以走不了 write/delete 那套 单开一对方法整段重写这个列表
    public static void writeSoupBase(Key bucket, Key show) throws IOException {
        Path file = defaultFlexFile(ApplianceType.STOCKPOT);
        Files.createDirectories(file.getParent());
        File target = file.toFile();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(target);
        ConfigurationSection section = resolveNamedSection(config, STOCK_RAW_SECTIONS, true);
        List<Map<?, ?>> list = new ArrayList<>(section.getMapList(LIQUID_KEY));
        list.removeIf(entry -> bucket.asString().equals(String.valueOf(entry.get("item"))));
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("item", bucket.asString());
        node.put("show", show.asString());
        list.add(node);
        section.set(LIQUID_KEY, list);
        config.save(target);
    }

    public static void deleteSoupBase(Key bucket) throws IOException {
        Path file = defaultFlexFile(ApplianceType.STOCKPOT);
        File target = file.toFile();
        if (!target.isFile()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(target);
        ConfigurationSection section = resolveNamedSection(config, STOCK_RAW_SECTIONS, false);
        if (section == null) {
            return;
        }
        List<Map<?, ?>> list = new ArrayList<>(section.getMapList(LIQUID_KEY));
        if (!list.removeIf(entry -> bucket.asString().equals(String.valueOf(entry.get("item"))))) {
            return;
        }
        section.set(LIQUID_KEY, list);
        config.save(target);
    }

    // 按顶层段名找 不带 id 匹配 同名多段时取第一个已含 liquid 的
    private static ConfigurationSection resolveNamedSection(YamlConfiguration config, String[] aliases,
                                                            boolean createIfAbsent) {
        ConfigurationSection first = null;
        for (String rootKey : config.getKeys(false)) {
            int hash = rootKey.indexOf('#');
            String base = hash < 0 ? rootKey : rootKey.substring(0, hash);
            if (!matchesAlias(base, aliases)) {
                continue;
            }
            ConfigurationSection candidate = config.getConfigurationSection(rootKey);
            if (candidate == null) {
                continue;
            }
            if (candidate.contains(LIQUID_KEY)) {
                return candidate;
            }
            if (first == null) {
                first = candidate;
            }
        }
        if (first != null) {
            return first;
        }
        return createIfAbsent ? config.createSection(aliases[0]) : null;
    }

    // 同步菜品退还容器要写 item 目录 那不在 recipe 下 单独暴露一个入口
    static Path configurationFolder() {
        return pack().configurationFolder();
    }

    private static Path recipeFolder() {
        return pack().configurationFolder().resolve(RECIPE_FOLDER);
    }

    // 本插件自带资源包按命名空间认 找不到就退回首个已加载包 让写入至少落在 CE 的解析范围内
    private static Pack pack() {
        Pack fallback = null;
        for (Pack p : CraftEngine.instance().packManager().loadedPacks()) {
            if (PACK_NAMESPACE.equals(p.namespace())) {
                return p;
            }
            if (fallback == null) {
                fallback = p;
            }
        }
        if (fallback == null) {
            throw new IllegalStateException("CraftEngine 未加载任何资源包 无法写入配方");
        }
        return fallback;
    }

    // 写入或覆盖一个配方节点 返回实际写入的文件
    public static void write(Path file, String[] sectionAliases, Key id, Map<String, Object> node) throws IOException {
        Files.createDirectories(file.getParent());
        File target = file.toFile();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(target);
        ConfigurationSection section = resolveSection(config, sectionAliases, id, true);
        section.set(id.asString(), node);
        config.save(target);
    }

    // 删除一个配方节点 节点不存在时静默返回
    public static void delete(Path file, String[] sectionAliases, Key id) throws IOException {
        File target = file.toFile();
        if (!target.isFile()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(target);
        ConfigurationSection section = resolveSection(config, sectionAliases, id, false);
        if (section == null || !section.contains(id.asString())) {
            return;
        }
        section.set(id.asString(), null);
        config.save(target);
    }

    // CE 允许 blocks#1 这种带 # 后缀的重复顶层键 匹配时要按 # 前的部分比
    // 优先返回已经含有该 id 的那一段 否则返回首个同名段 都没有时按需新建
    private static ConfigurationSection resolveSection(YamlConfiguration config, String[] aliases,
                                                       Key id, boolean createIfAbsent) {
        ConfigurationSection first = null;
        for (String rootKey : config.getKeys(false)) {
            int hash = rootKey.indexOf('#');
            String base = hash < 0 ? rootKey : rootKey.substring(0, hash);
            if (!matchesAlias(base, aliases)) {
                continue;
            }
            ConfigurationSection candidate = config.getConfigurationSection(rootKey);
            if (candidate == null) {
                continue;
            }
            if (candidate.contains(id.asString())) {
                return candidate;
            }
            if (first == null) {
                first = candidate;
            }
        }
        if (first != null) {
            return first;
        }
        return createIfAbsent ? config.createSection(aliases[0]) : null;
    }

    private static boolean matchesAlias(String base, String[] aliases) {
        for (String alias : aliases) {
            if (alias.equals(base)) {
                return true;
            }
        }
        return false;
    }

    // 目标文件写坏时至少留下痕迹 调用方已在 async 线程 这里不再切换
    public static void logFailure(String action, Key id, Throwable error) {
        KaleidoscopeCookeryPlugin.instance().getLogger().warning(
                "配方 " + id.asString() + " " + action + " 失败: " + error.getMessage());
    }

}
