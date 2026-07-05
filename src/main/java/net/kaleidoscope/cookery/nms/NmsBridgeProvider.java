package net.kaleidoscope.cookery.nms;

import org.bukkit.Bukkit;

public final class NmsBridgeProvider {
    private static final NmsBridge BRIDGE = create();

    private NmsBridgeProvider() {}

    public static NmsBridge bridge() {
        return BRIDGE;
    }

    private static NmsBridge create() {
        String version = minecraftVersion();
        String className = implementationClassName(version);
        try {
            return (NmsBridge) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to load NMS bridge for " + version + ": " + className, e);
        }
    }

    private static String minecraftVersion() {
        String version = Bukkit.getBukkitVersion();
        int dash = version.indexOf('-');
        if (dash > 0) {
            version = version.substring(0, dash);
        }
        int build = version.indexOf(".build");
        if (build > 0) {
            version = version.substring(0, build);
        }
        return version;
    }

    private static String implementationClassName(String version) {
        if (version.startsWith("26.2")) return name("v26_2_R1", "NmsV26_2_R1");
        if (version.startsWith("26.1")) return name("v26_1_R1", "NmsV26_1_R1");

        return switch (version) {
            case "1.21.1" -> name("v1_21_R1", "NmsV1_21_R1");
            case "1.21.3" -> name("v1_21_R2", "NmsV1_21_R2");
            case "1.21.4" -> name("v1_21_R3", "NmsV1_21_R3");
            case "1.21.5" -> name("v1_21_R4", "NmsV1_21_R4");
            case "1.21.6", "1.21.7", "1.21.8" -> name("v1_21_R5", "NmsV1_21_R5");
            case "1.21.9", "1.21.10" -> name("v1_21_R6", "NmsV1_21_R6");
            case "1.21.11" -> name("v1_21_R7", "NmsV1_21_R7");
            default -> throw new IllegalStateException("Unsupported server version: " + version);
        };
    }

    private static String name(String packageName, String className) {
        return "net.kaleidoscope.cookery.nms." + packageName + "." + className;
    }
}
