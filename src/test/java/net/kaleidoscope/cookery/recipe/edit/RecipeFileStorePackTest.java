package net.kaleidoscope.cookery.recipe.edit;

import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.pack.PackMeta;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecipeFileStorePackTest {
    @Test
    void findsRenamedPackFromNamespace() {
        Pack expected = pack("玩家改过的目录", "kaleidoscopecookery", true);
        Pack other = pack("其它包", "other", true);

        assertSame(expected, RecipeFileStore.findPack(List.of(other, expected)));
    }

    @Test
    void neverFallsBackToAnotherPack() {
        Pack other = pack("其它包", "other", true);

        assertThrows(IllegalStateException.class, () -> RecipeFileStore.findPack(List.of(other)));
    }

    @Test
    void rejectsDuplicateNamespaces() {
        Pack first = pack("第一个", "kaleidoscopecookery", true);
        Pack second = pack("第二个", "kaleidoscopecookery", true);

        assertThrows(IllegalStateException.class,
                () -> RecipeFileStore.findPack(List.of(first, second)));
    }

    private Pack pack(String name, String namespace, boolean enabled) {
        return new Pack(Path.of(name), new PackMeta(null, null, null, namespace), enabled, new String[0]);
    }
}
