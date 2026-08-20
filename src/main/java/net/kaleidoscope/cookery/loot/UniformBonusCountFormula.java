package net.kaleidoscope.cookery.loot;

import net.momirealms.craftengine.core.loot.function.formula.Formula;
import net.momirealms.craftengine.core.loot.function.formula.FormulaFactory;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.random.RandomUtils;

// 每级附魔在零到倍率之间均匀增加数量
public final class UniformBonusCountFormula implements Formula {
    public static final FormulaFactory<UniformBonusCountFormula> FACTORY =
            UniformBonusCountFormula::create;

    private final int multiplier;

    private UniformBonusCountFormula(int multiplier) {
        this.multiplier = Math.max(0, multiplier);
    }

    @Override
    public int apply(int initialCount, int enchantmentLevel) {
        int level = Math.max(0, enchantmentLevel);
        long maximumBonus = (long) level * this.multiplier;
        int maximumExclusive = maximumBonus >= Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) maximumBonus + 1;
        return initialCount + RandomUtils.generateRandomInt(0, maximumExclusive);
    }

    private static UniformBonusCountFormula create(ConfigSection section) {
        return new UniformBonusCountFormula(section.getInt("multiplier", 1));
    }
}
