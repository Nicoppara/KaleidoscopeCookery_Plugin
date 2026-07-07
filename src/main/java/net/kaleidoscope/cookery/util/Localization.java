package net.kaleidoscope.cookery.util;

import net.momirealms.craftengine.core.util.AdventureHelper;
import net.momirealms.craftengine.libraries.adventure.text.Component;

import java.util.regex.Pattern;

public final class Localization {
    private static final Pattern TRANSLATION_KEY = Pattern.compile("[a-z0-9_.-]+(?:\\.[a-z0-9_.-]+)+");

    private Localization() {
    }

    public static Component component(String value) {
        if (isTranslationKey(value)) {
            return Component.translatable(value);
        }
        return literal(value);
    }

    public static Component component(String value, Component... arguments) {
        if (isTranslationKey(value)) {
            return Component.translatable(value).arguments(arguments);
        }
        return literal(value);
    }

    public static Component componentWithReplacement(String value, String placeholder, String replacement) {
        if (isTranslationKey(value)) {
            return Component.translatable(value).arguments(Component.text(replacement));
        }
        return literal(value == null ? "" : value.replace(placeholder, replacement));
    }

    public static boolean isTranslationKey(String value) {
        return value != null
                && value.indexOf(' ') < 0
                && value.indexOf(':') < 0
                && TRANSLATION_KEY.matcher(value).matches();
    }

    private static Component literal(String value) {
        if (value == null || value.isEmpty()) {
            return Component.empty();
        }
        return AdventureHelper.miniMessage().deserialize(AdventureHelper.legacyToMiniMessage(value));
    }
}
