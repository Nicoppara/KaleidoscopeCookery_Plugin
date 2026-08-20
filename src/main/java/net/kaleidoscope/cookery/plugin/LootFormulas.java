package net.kaleidoscope.cookery.plugin;

import net.kaleidoscope.cookery.loot.UniformBonusCountFormula;
import net.momirealms.craftengine.core.loot.function.formula.FormulaType;
import net.momirealms.craftengine.core.loot.function.formula.Formulas;
import net.momirealms.craftengine.core.util.Key;

// CE 未内置但原模组掉落表需要的数量公式
public final class LootFormulas {
    private static final Key UNIFORM_BONUS_COUNT_ID =
            Key.of("kaleidoscopecookery:uniform_bonus_count");

    private static volatile FormulaType<?> uniformBonusCount;

    private LootFormulas() {}

    public static void register() {
        if (uniformBonusCount == null) {
            uniformBonusCount = Formulas.register(
                    UNIFORM_BONUS_COUNT_ID, UniformBonusCountFormula.FACTORY);
        }
    }
}
