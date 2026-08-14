package net.kaleidoscope.cookery.recipe.edit;

import net.kaleidoscope.cookery.recipe.FoodGroups;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 一个分组要同时改标签成员与两张用途表 只写半套就会出现认不出用途的孤儿标签
class FoodGroupFileTest {

    private static final Key EGG = Key.of("kaleidoscopecookery:equivalent_egg");
    private static final Key SPICE = Key.of("kaleidoscopecookery:seasoning");

    private static YamlConfiguration read(Path file) {
        return YamlConfiguration.loadConfiguration(file.toFile());
    }

    private static List<String> tagsOf(YamlConfiguration config, String section) {
        return config.getStringList(section + ".tags");
    }

    @Test
    void writesMembersAndMembership(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("food_groups.yml");
        RecipeFileStore.writeFoodGroup(file, EGG,
                List.of(Key.of("minecraft:egg"), Key.of("minecraft:brown_egg")),
                FoodGroups.Kind.EQUIVALENT);

        YamlConfiguration config = read(file);
        assertEquals(List.of("minecraft:egg", "minecraft:brown_egg"),
                config.getStringList("item_tags." + EGG.asString()));
        assertEquals(List.of("#" + EGG.asString()), tagsOf(config, "equivalent_foods"));
        assertTrue(tagsOf(config, "seasonings").isEmpty());
    }

    @Test
    void keepsBothTablesSeparate(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("food_groups.yml");
        RecipeFileStore.writeFoodGroup(file, EGG, List.of(Key.of("minecraft:egg")),
                FoodGroups.Kind.EQUIVALENT);
        RecipeFileStore.writeFoodGroup(file, SPICE, List.of(Key.of("minecraft:sugar")),
                FoodGroups.Kind.SEASONING);

        YamlConfiguration config = read(file);
        assertEquals(List.of("#" + EGG.asString()), tagsOf(config, "equivalent_foods"));
        assertEquals(List.of("#" + SPICE.asString()), tagsOf(config, "seasonings"));
    }

    // 换用途必须从另一张表里摘掉 留在两边会让 kindOf 认成先声明的那个
    @Test
    void switchingKindMovesTagBetweenTables(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("food_groups.yml");
        RecipeFileStore.writeFoodGroup(file, EGG, List.of(Key.of("minecraft:egg")),
                FoodGroups.Kind.EQUIVALENT);
        RecipeFileStore.writeFoodGroup(file, EGG, List.of(Key.of("minecraft:egg")),
                FoodGroups.Kind.SEASONING);

        YamlConfiguration config = read(file);
        assertTrue(tagsOf(config, "equivalent_foods").isEmpty());
        assertEquals(List.of("#" + EGG.asString()), tagsOf(config, "seasonings"));
    }

    // 重写同一个标签是整体替换 删掉的成员不能残留
    @Test
    void rewritingReplacesMembers(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("food_groups.yml");
        RecipeFileStore.writeFoodGroup(file, EGG,
                List.of(Key.of("minecraft:egg"), Key.of("minecraft:brown_egg")),
                FoodGroups.Kind.EQUIVALENT);
        RecipeFileStore.writeFoodGroup(file, EGG, List.of(Key.of("minecraft:egg")),
                FoodGroups.Kind.EQUIVALENT);

        assertEquals(List.of("minecraft:egg"),
                read(file).getStringList("item_tags." + EGG.asString()));
    }

    @Test
    void deleteRemovesMembersAndMembership(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("food_groups.yml");
        RecipeFileStore.writeFoodGroup(file, EGG, List.of(Key.of("minecraft:egg")),
                FoodGroups.Kind.EQUIVALENT);
        RecipeFileStore.writeFoodGroup(file, SPICE, List.of(Key.of("minecraft:sugar")),
                FoodGroups.Kind.SEASONING);
        RecipeFileStore.deleteFoodGroup(file, EGG);

        YamlConfiguration config = read(file);
        assertFalse(config.contains("item_tags." + EGG.asString()));
        assertTrue(tagsOf(config, "equivalent_foods").isEmpty());
        // 另一组不能被误伤
        assertEquals(List.of("minecraft:sugar"),
                config.getStringList("item_tags." + SPICE.asString()));
        assertEquals(List.of("#" + SPICE.asString()), tagsOf(config, "seasonings"));
    }

    @Test
    void deleteOnMissingFileIsNoOp(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("food_groups.yml");
        RecipeFileStore.deleteFoodGroup(file, EGG);
        assertFalse(Files.exists(file));
    }

    // 手写的其它标签不能被菜单写入冲掉
    @Test
    void keepsUnrelatedTags(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("food_groups.yml");
        Files.writeString(file, """
                item_tags:
                  kaleidoscopecookery:hand_written:
                    - minecraft:stone
                """);
        RecipeFileStore.writeFoodGroup(file, EGG, List.of(Key.of("minecraft:egg")),
                FoodGroups.Kind.EQUIVALENT);

        assertEquals(List.of("minecraft:stone"),
                read(file).getStringList("item_tags.kaleidoscopecookery:hand_written"));
    }
}
