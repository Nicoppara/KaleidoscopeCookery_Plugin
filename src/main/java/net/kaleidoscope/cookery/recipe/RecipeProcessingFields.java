package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import java.math.BigInteger;

/** Strict optional positive integers. Zero is an internal inheritance sentinel, never a YAML value. */
public final class RecipeProcessingFields {
    private RecipeProcessingFields() {}

    public static int optionalPositive(ConfigSection section, String... aliases) {
        int parsed = 0;
        for (String alias : aliases) {
            Object value = section.get(alias);
            if (value == null) continue;
            String raw = value.toString().trim();
            if (value instanceof Number && !(value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long || value instanceof BigInteger)
                    || !raw.matches("[0-9]+")) {
                throw new IllegalArgumentException(section.path() + "." + alias + " requires a positive integer");
            }
            final int current;
            try { current = new BigInteger(raw).intValueExact(); }
            catch (ArithmeticException overflow) {
                throw new IllegalArgumentException(section.path() + "." + alias + " exceeds the integer range", overflow);
            }
            if (current < 1) throw new IllegalArgumentException(section.path() + "." + alias + " must be greater than zero");
            if (parsed != 0 && parsed != current) throw new IllegalArgumentException(section.path() + " contains conflicting processing aliases");
            parsed = current;
        }
        return parsed;
    }
}
