package net.kaleidoscope.cookery.loot;

import net.kaleidoscope.cookery.plugin.LootFormulas;
import net.momirealms.craftengine.core.loot.function.formula.Formula;
import net.momirealms.craftengine.core.loot.function.formula.Formulas;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniformBonusCountFormulaTest {
    @Test
    void 注册名可解析且奖励数量保持在均匀公式范围内() {
        LootFormulas.register();
        Formula formula = Formulas.fromConfig(ConfigSection.ofRoot(Map.of(
                "type", "kaleidoscopecookery:uniform_bonus_count",
                "multiplier", 2)));

        assertInstanceOf(UniformBonusCountFormula.class, formula);
        assertEquals(4, formula.apply(4, 0));
        for (int i = 0; i < 1_000; i++) {
            int result = formula.apply(4, 3);
            assertTrue(result >= 4 && result <= 10);
        }
    }
}
