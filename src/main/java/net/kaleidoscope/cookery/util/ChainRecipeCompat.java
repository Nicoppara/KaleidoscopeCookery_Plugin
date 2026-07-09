package net.kaleidoscope.cookery.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 版本兼容：minecraft:chain 在 MC 1.21.11 被改名为 minecraft:iron_chain。
 *
 * <p>发布的资源包默认使用新名 {@code minecraft:iron_chain}。CraftEngine 在解析配方原料时，
 * 若物品在当前版本注册表里取不到会直接报“原料物品不存在”并跳过整条配方（shawarma_spit 做不出来）。
 * 由于该判断发生在原料解析阶段、早于 recipe 的 condition，所以纯配置条件无法消除警告。</p>
 *
 * <p>因此在插件 {@code onLoad}（早于 CraftEngine 读取配方）阶段，按运行版本把 shawarma_spit
 * 配方里的链条物品归一化为当前版本正确的 ID：≥1.21.11 用 {@code iron_chain}，&lt;1.21.11 用 {@code chain}。
 * 该操作幂等——仅当文件里的 ID 与当前版本不符时才改写一次。</p>
 */
public final class ChainRecipeCompat {
    private ChainRecipeCompat() {}

    private static final String MODERN = "minecraft:iron_chain"; // ≥ 1.21.11
    private static final String LEGACY = "minecraft:chain";      // < 1.21.11

    /** 相对 plugins/ 目录的配方文件路径（CraftEngine 资源包 KaleidoscopeCookery）。 */
    private static final String RECIPE_REL =
            "CraftEngine/resources/KaleidoscopeCookery/configuration/recipe/generated_crafting_shaped.yml";

    public static void apply(JavaPlugin plugin) {
        final boolean modern;
        try {
            modern = isAtLeast_1_21_11();
        } catch (Throwable t) {
            // 版本号解析异常时保持资源包默认（iron_chain，面向新版本），不改动
            plugin.getLogger().warning("[版本兼容] 链条材料版本检测失败，保持默认 iron_chain：" + t.getMessage());
            return;
        }

        final String desired = modern ? MODERN : LEGACY;
        final String other = modern ? LEGACY : MODERN;

        try {
            Path file = plugin.getDataFolder().getParentFile().toPath().resolve(RECIPE_REL);
            if (!Files.isRegularFile(file)) {
                return; // 资源包尚未部署到 CraftEngine 目录，跳过
            }
            String content = Files.readString(file, StandardCharsets.UTF_8);
            // 注意 "minecraft:chain" 不是 "minecraft:iron_chain" 的子串，替换互不误伤
            if (!content.contains(other)) {
                return; // 已是当前版本正确的 ID，无需改动
            }
            Files.writeString(file, content.replace(other, desired), StandardCharsets.UTF_8);
            plugin.getLogger().info("[版本兼容] 已将 shawarma_spit 配方链条材料归一化为 " + desired
                    + "（MC " + (modern ? "≥" : "<") + "1.21.11）");
        } catch (IOException e) {
            plugin.getLogger().warning("[版本兼容] 归一化 shawarma_spit 链条材料失败：" + e.getMessage());
        }
    }

    /** 判断当前服务端 MC 版本是否 ≥ 1.21.11。 */
    private static boolean isAtLeast_1_21_11() {
        // Bukkit.getBukkitVersion() 形如 "1.21.11-R0.1-SNAPSHOT"
        String base = Bukkit.getBukkitVersion().split("-")[0];
        String[] p = base.split("\\.");
        int major = p.length > 0 ? Integer.parseInt(p[0]) : 0;
        int minor = p.length > 1 ? Integer.parseInt(p[1]) : 0;
        int patch = p.length > 2 ? Integer.parseInt(p[2]) : 0;
        if (major != 1) return major > 1;
        if (minor != 21) return minor > 21;
        return patch >= 11;
    }
}
