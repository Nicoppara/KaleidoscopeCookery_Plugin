package net.kaleidoscope.cookery.item;

import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// 聊天与 lore 里的物品图标 走 CE 的字体图像 尺寸由 height/ascent 定 sprite 组件行高固定改不了
// 字体图像要逐条登记 所以启动时扫资源包自动生成整份配置
public final class ItemIcons {
    private ItemIcons() {}

    // 生成到 CE 的配置目录 由 CE 在解析阶段照常读取
    // CE 的配置解析发生在所有插件启用之后
    private static final String GENERATED_FILE = "configuration/font/generated_item_icons.yml";
    private static final String TEXTURES = "resourcepack/assets/minecraft/textures";
    // 只扫物品贴图 方块贴图不做图标
    private static final String ITEM_DIR = "item";

    private static final String FONT = "kaleidoscopecookery:food";
    private static final String ID_PREFIX = "kaleidoscopecookery:icon_";
    // 想调图标大小改这两个 ascent 一般取 height - 3
    private static final int HEIGHT = 16;
    private static final int ASCENT = 13;

    // 原版物品的贴图不在资源包里 没法扫 只能从配置里把用到的 id 找出来
    private static final Pattern VANILLA_ID = Pattern.compile("minecraft:([a-z_]{3,40})");
    // 这些是方块/流体/杂项 不是物品图标 生成了也是缺失贴图
    private static final Set<String> VANILLA_SKIP = Set.of(
            "water", "lava", "air", "blocks", "items", "misc", "entity", "block", "item",
            "campfire", "soul_campfire", "cobblestone", "smooth_stone", "oak_planks",
            "oak_pressure_plate", "short_grass", "lily_pad", "brick", "chest", "pumpkin");

    // 物品 id -> 生成出来的图标 id 查不到就回退成物品名
    private static final Map<Key, String> ICONS = new TreeMap<>(
            (a, b) -> a.asString().compareTo(b.asString()));

    public static String iconId(Key key) {
        return ICONS.get(key);
    }

    // 启动时跑一次 写配置 + 建索引 失败只是没图标 不该拦住插件启动
    public static void generate() {
        ICONS.clear();
        Plugin ce = Bukkit.getPluginManager().getPlugin("CraftEngine");
        if (ce == null) {
            return;
        }
        try {
            Path resourcesRoot = ce.getDataFolder().toPath().resolve("resources");
            Path packRoot = resolvePackRoot(resourcesRoot);
            Map<String, String> entries = new TreeMap<>();
            collectPackTextures(packRoot, entries);
            collectVanillaIds(packRoot, entries);
            writeConfig(packRoot.resolve(GENERATED_FILE), entries);
            KaleidoscopeCookeryPlugin.instance().getLogger().info(
                    "[icon] 已在资源包 " + packRoot.getFileName() + " 生成 " + entries.size() + " 条物品图标字体");
        } catch (IOException e) {
            KaleidoscopeCookeryPlugin.instance().getLogger().warning(
                    "[icon] 生成物品图标失败 lore 将回退为文字: " + e.getMessage());
        }
    }

    // 包目录名允许玩家自定义 只认 pack.yml 里的固定命名空间
    static Path resolvePackRoot(Path resourcesRoot) throws IOException {
        if (!Files.isDirectory(resourcesRoot)) {
            throw new IOException("CraftEngine 资源包目录不存在: " + resourcesRoot);
        }
        Path matched = null;
        try (Stream<Path> children = Files.list(resourcesRoot)) {
            for (Path candidate : children.filter(Files::isDirectory).toList()) {
                Path metaFile = candidate.resolve("pack.yml");
                if (!Files.isRegularFile(metaFile)) {
                    continue;
                }
                YamlConfiguration meta = YamlConfiguration.loadConfiguration(metaFile.toFile());
                if (!meta.getBoolean("enable", true)
                        || !ItemKeys.NAMESPACE.equals(meta.getString("namespace"))) {
                    continue;
                }
                if (matched != null) {
                    throw new IOException("存在多个 namespace 为 " + ItemKeys.NAMESPACE
                            + " 的资源包: " + matched.getFileName() + ", " + candidate.getFileName());
                }
                matched = candidate;
            }
        }
        if (matched == null) {
            throw new IOException("找不到已启用且 pack.yml 中 namespace 为 "
                    + ItemKeys.NAMESPACE + " 的资源包");
        }
        return matched;
    }

    // 资源包里 textures/item/** 的每张图各出一条 文件名即物品 id
    private static void collectPackTextures(Path packRoot, Map<String, String> entries) throws IOException {
        Path items = packRoot.resolve(TEXTURES).resolve(ITEM_DIR);
        if (!Files.isDirectory(items)) {
            return;
        }
        try (Stream<Path> files = Files.walk(items)) {
            files.filter(p -> p.getFileName().toString().endsWith(".png")).forEach(p -> {
                String rel = items.relativize(p).toString().replace('\\', '/');
                String name = rel.substring(rel.lastIndexOf('/') + 1, rel.length() - 4);
                // 同名贴图取先扫到的 深一层的目录一般才是成品图标
                entries.putIfAbsent(name, "minecraft:" + ITEM_DIR + "/" + rel);
                ICONS.putIfAbsent(Key.of(ItemKeys.NAMESPACE + ":" + name), ID_PREFIX + name);
            });
        }
    }

    // 原版食材的贴图在客户端自带资源里 扫不到 只能把配置里出现过的 minecraft: id 收集出来
    // 生成多了顶多是一条用不上的字体 生成少了才是图标缺失
    private static void collectVanillaIds(Path packRoot, Map<String, String> entries) throws IOException {
        Path config = packRoot.resolve("configuration");
        if (!Files.isDirectory(config)) {
            return;
        }
        Set<String> ids = new TreeSet<>();
        try (Stream<Path> files = Files.walk(config)) {
            for (Path p : files.filter(p -> p.toString().endsWith(".yml")).toList()) {
                // lang 目录全是译文 里面的 minecraft: 都不是物品
                if (p.toString().replace('\\', '/').contains("/lang/")) {
                    continue;
                }
                Matcher m = VANILLA_ID.matcher(Files.readString(p, StandardCharsets.UTF_8));
                while (m.find()) {
                    String id = m.group(1);
                    if (!VANILLA_SKIP.contains(id)) {
                        ids.add(id);
                    }
                }
            }
        }
        for (String id : ids) {
            // 方块的贴图在 block/ 下 item/<id>.png 不存在 生成了就是缺失贴图
            // 认不出的 id 多半是配置里的其它字段 一并跳过
            Material material = Material.matchMaterial("minecraft:" + id);
            if (material == null || material.isBlock()) {
                continue;
            }
            entries.putIfAbsent(id, "minecraft:" + ITEM_DIR + "/" + id + ".png");
            ICONS.putIfAbsent(Key.of("minecraft:" + id), ID_PREFIX + id);
        }
    }

    private static void writeConfig(Path target, Map<String, String> entries) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# 本文件由插件在启动时自动生成 请勿手改 改了下次启动会被覆盖\n");
        sb.append("# 来源 资源包 textures/item/** 与各配置里引用到的原版物品 id\n");
        sb.append("# 图标尺寸改 ItemIcons 里的 HEIGHT / ASCENT\n\n");
        sb.append("images:\n");
        for (Map.Entry<String, String> e : entries.entrySet()) {
            sb.append("  ").append(ID_PREFIX).append(e.getKey()).append(":\n");
            sb.append("    font: ").append(FONT).append('\n');
            sb.append("    file: \"").append(e.getValue()).append("\"\n");
            sb.append("    height: ").append(HEIGHT).append('\n');
            sb.append("    ascent: ").append(ASCENT).append('\n');
        }
        Files.createDirectories(target.getParent());
        Files.writeString(target, sb.toString(), StandardCharsets.UTF_8);
    }
}
