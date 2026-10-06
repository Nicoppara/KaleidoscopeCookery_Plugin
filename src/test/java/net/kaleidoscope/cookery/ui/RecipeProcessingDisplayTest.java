package net.kaleidoscope.cookery.ui;

import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.edit.RecipeFileStore;
import net.kaleidoscope.cookery.recipe.edit.RecipeProcessingMetadata;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecipeProcessingDisplayTest {
    @Test void browsingShowsActualApplianceDefaultAndSource() {
        var view = RecipeProcessingDisplay.describe(ApplianceType.STEAMER, 0, 0, false, false,
                target("cooking_time", setting(0, RecipeProcessingMetadata.Origin.DEFAULT, 0, true)),
                Path.of("pack/recipe/steamer.yml"), 237);
        assertEquals("11.85 秒", view.value());
        assertEquals("厨具默认", view.source());
        assertTrue(view.provenance().contains("源文件：steamer.yml"));
        assertTrue(view.provenance().stream().anyMatch(line -> line.contains("配方节点：")));
    }

    @Test void templateValueAndPendingRestoreHaveDifferentLabels() {
        var inherited = new RecipeProcessingMetadata.Setting(47, RecipeProcessingMetadata.Origin.TEMPLATE,
                "test:base", 47, true, RecipeProcessingMetadata.Origin.TEMPLATE, "test:base");
        var current = RecipeProcessingDisplay.describe(ApplianceType.STEAMER, 47, 47, true, false,
                target("cooking_time", inherited), null, 200);
        assertEquals("2.35 秒", current.value());
        assertEquals("模板 test:base", current.source());
        var restore = RecipeProcessingDisplay.describe(ApplianceType.STEAMER, 47, 0, true, false,
                target("cooking_time", inherited), null, 200);
        assertEquals("2.35 秒", restore.value());
        assertEquals("恢复继承（待保存）", restore.source());
        assertEquals("继承目标：模板 test:base", restore.inheritance());
    }

    @Test void localTemplateOverrideRestoreDoesNotPretendToKnowTheHiddenInheritedValue() {
        var setting = new RecipeProcessingMetadata.Setting(47, RecipeProcessingMetadata.Origin.TEMPLATE_OVERRIDE,
                "test:base", 0, false, RecipeProcessingMetadata.Origin.TEMPLATE, "test:base");
        var restore = RecipeProcessingDisplay.describe(ApplianceType.STEAMER, 47, 0, true, false,
                target("cooking_time", setting), null, 200);
        assertEquals("保存后确认", restore.value());
        assertEquals("继承目标：模板 test:base，保存后确认", restore.inheritance());
        assertFalse(restore.provenance().stream().anyMatch(line -> line.contains("厨具默认值暂不可用")));
    }

    @Test void everyApplianceUsesItsOwnUnitAndTeaShowsPreparationSeparately() {
        assertEquals("3 次", plain(ApplianceType.POT, 3).value());
        assertEquals("3 圈", plain(ApplianceType.MILLSTONE, 3).value());
        assertEquals("3 刀", plain(ApplianceType.CHOPPING_BOARD, 3).value());
        assertEquals("0.15 秒", plain(ApplianceType.SHAWARMA, 3).value());
        assertEquals("0.15 秒", plain(ApplianceType.STOCKPOT, 3).value());
        var tea = plain(ApplianceType.TEAPOT, 3);
        assertEquals("0.15 秒", tea.value());
        assertTrue(tea.provenance().contains("另有固定 10 秒准备阶段"));
    }

    @Test void pendingExplicitOverrideKeepsItsValueEvenIfApplianceDefinitionIsUnavailable() {
        var override = RecipeProcessingDisplay.describe(ApplianceType.STEAMER, 0, 47, true, false,
                null, null, 0);
        assertEquals("2.35 秒", override.value());
        assertEquals("配方覆盖（待保存）", override.source());
        assertTrue(override.provenance().isEmpty());
    }

    private static RecipeProcessingDisplay.View plain(ApplianceType cook, int value) {
        return RecipeProcessingDisplay.describe(cook, value, value, false, false, null, null, 200);
    }

    private static RecipeProcessingMetadata.Setting setting(int value, RecipeProcessingMetadata.Origin origin,
                                                            int inherited, boolean known) {
        return new RecipeProcessingMetadata.Setting(value, origin, "", inherited, known,
                RecipeProcessingMetadata.Origin.DEFAULT, "");
    }

    private static RecipeFileStore.SourceTarget target(String field, RecipeProcessingMetadata.Setting setting) {
        return RecipeFileStore.SourceTarget.direct("accurate_foods.test:x").withProcessing(Map.of(field, setting));
    }
}
