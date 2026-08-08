package net.kaleidoscope.cookery.util;

public final class UniverseSpigotUtil {
    private static final boolean UNIVERSE_SPIGOT = detect();

    private UniverseSpigotUtil() {}

    public static boolean isUniverseSpigot() {
        return UNIVERSE_SPIGOT;
    }

    private static boolean detect() {
        try {
            Class.forName("com.universeprojects.config.UniverseConfig");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
