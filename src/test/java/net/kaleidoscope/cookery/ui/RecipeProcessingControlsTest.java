package net.kaleidoscope.cookery.ui;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class RecipeProcessingControlsTest {
    @Test void secondsUseExactTicksAndNeverSilentlyRound() {
        assertEquals(1, RecipeProcessingControls.secondsToTicks("0.05"));
        assertEquals(47, RecipeProcessingControls.secondsToTicks("2.35"));
        assertEquals(72000, RecipeProcessingControls.secondsToTicks("3600"));
        assertEquals(72001, RecipeProcessingControls.secondsToTicks("3600.05"));
        assertEquals(Integer.MAX_VALUE, RecipeProcessingControls.secondsToTicks("107374182.35"));
        assertEquals("2.35", RecipeProcessingControls.seconds(47));
        assertThrows(ArithmeticException.class, () -> RecipeProcessingControls.secondsToTicks("0.051"));
        assertThrows(IllegalArgumentException.class, () -> RecipeProcessingControls.secondsToTicks("0"));
        assertThrows(ArithmeticException.class, () -> RecipeProcessingControls.secondsToTicks("107374182.40"));
        assertThrows(NumberFormatException.class, () -> RecipeProcessingControls.secondsToTicks("NaN"));
    }

    @Test void acceptedTimeAndStirCountAreNeverSilentlyCappedByDrafts() {
        var id = net.momirealms.craftengine.core.util.Key.of("test:recipe");
        var tea = net.kaleidoscope.cookery.recipe.edit.TeapotRecipeDraft.creating(id);
        tea.time(RecipeProcessingControls.secondsToTicks("107374182.35"));
        assertEquals(Integer.MAX_VALUE, tea.time());
        var pot = net.kaleidoscope.cookery.recipe.edit.FlexRecipeDraft.creating(
                net.kaleidoscope.cookery.recipe.ApplianceType.POT, id);
        pot.stirFryCount(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, pot.stirFryCount());
    }

    @Test void repeatedSaveClickIsIgnoredUntilTheFirstSaveCompletes() {
        Object session = new Object();
        CompletableFuture<String> write = new CompletableFuture<>();
        assertNotNull(RecipeEditSaves.save(session, () -> write));
        assertNull(RecipeEditSaves.save(session, () -> { fail("Second save must not start"); return null; }));
        write.complete("failure");
        assertNotNull(RecipeEditSaves.save(session, () -> CompletableFuture.completedFuture(null)));
    }
}
