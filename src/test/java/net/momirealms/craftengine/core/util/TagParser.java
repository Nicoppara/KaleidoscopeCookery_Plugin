package net.momirealms.craftengine.core.util;

/**
 * The published CE API excludes this private runtime helper. Template tests still execute CE's real
 * TemplateManager, with this deliberately narrow literal-default adapter on the test classpath only.
 * Full SNBT and the production parser are exercised by the separate Paper acceptance suite.
 */
public final class TagParser {
    private TagParser() {}

    public static Object parseObjectFully(String input) {
        String value = input.trim();
        if ("null".equals(value)) return null;
        if ("true".equals(value)) return Boolean.TRUE;
        if ("false".equals(value)) return Boolean.FALSE;
        if (value.matches("[-+]?[0-9]+")) return Integer.valueOf(value);
        if (value.matches("[A-Za-z_][A-Za-z0-9_.+-]*")) return value;
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'")) && !value.contains("\\")) {
            return value.substring(1, value.length() - 1);
        }
        throw new UnsupportedOperationException("The unit fixture supports literal defaults only: " + value);
    }
}
