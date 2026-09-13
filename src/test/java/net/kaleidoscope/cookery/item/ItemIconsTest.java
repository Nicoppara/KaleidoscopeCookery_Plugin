package net.kaleidoscope.cookery.item;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemIconsTest {
    @TempDir
    Path resources;

    @Test
    void findsRenamedPackFromMetadata() throws IOException {
        Path renamed = createPack("玩家改过的目录", "kaleidoscopecookery", true);
        createPack("其它包", "other", true);

        assertEquals(renamed, ItemIcons.resolvePackRoot(resources));
    }

    @Test
    void ignoresDisabledMatchingPack() throws IOException {
        createPack("已禁用", "kaleidoscopecookery", false);

        assertThrows(IOException.class, () -> ItemIcons.resolvePackRoot(resources));
    }

    @Test
    void rejectsDuplicateNamespaces() throws IOException {
        createPack("第一个", "kaleidoscopecookery", true);
        createPack("第二个", "kaleidoscopecookery", true);

        assertThrows(IOException.class, () -> ItemIcons.resolvePackRoot(resources));
    }

    @Test
    void findsMigratedItemTextureInNestedItemDirectory() throws IOException {
        Path pack = Files.createDirectories(resources.resolve("资源包"));
        Path texture = pack.resolve(
                "resourcepack/assets/minecraft/textures/block/custom/cook/block/food/item/braised_fish.png");
        Files.createDirectories(texture.getParent());
        Files.write(texture, new byte[]{0});
        Map<String, String> entries = new HashMap<>();

        ItemIcons.collectPackTextures(pack, entries);

        assertEquals("minecraft:block/custom/cook/block/food/item/braised_fish.png",
                entries.get("braised_fish"));
    }

    private Path createPack(String folder, String namespace, boolean enabled) throws IOException {
        Path root = Files.createDirectories(resources.resolve(folder));
        Files.writeString(root.resolve("pack.yml"),
                "namespace: " + namespace + "\nenable: " + enabled + "\n");
        return root;
    }
}
