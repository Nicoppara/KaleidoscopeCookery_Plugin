package net.kaleidoscope.cookery.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ConsoleMessages {
    public static final String DEFAULT_LANGUAGE = "zh_cn";

    private static final List<String> SUPPORTED_LANGUAGES = List.of(
            "zh_cn", "zh_tw", "zh_hk", "lzh",
            "en_us", "es_es", "ja_jp", "ko_kr",
            "fr_fr", "ru_ru", "it_it", "de_de"
    );
    private static final String SUPPORTED_LANGUAGE_LIST = String.join(", ", SUPPORTED_LANGUAGES);
    private static final Map<String, Map<String, String>> MESSAGES = loadMessages();
    private static volatile String language = DEFAULT_LANGUAGE;

    private ConsoleMessages() {
    }

    public static void load(JavaPlugin plugin) {
        setLanguage(plugin, plugin.getConfig().getString("console.language", DEFAULT_LANGUAGE));
    }

    public static void reloadFromDisk(JavaPlugin plugin) {
        plugin.reloadConfig();
        load(plugin);
    }

    public static String t(String key, Object... args) {
        Map<String, String> messages = MESSAGES.getOrDefault(language, MESSAGES.get(DEFAULT_LANGUAGE));
        String template = messages.get(key);
        if (template == null) {
            template = MESSAGES.get(DEFAULT_LANGUAGE).getOrDefault(key, key);
        }
        return format(template, args);
    }

    public static String language() {
        return language;
    }

    private static void setLanguage(JavaPlugin plugin, String configured) {
        String normalized = normalize(configured);
        if (!SUPPORTED_LANGUAGES.contains(normalized)) {
            language = DEFAULT_LANGUAGE;
            plugin.getLogger().warning(t("console.language.unsupported",
                    configured == null ? "" : configured, DEFAULT_LANGUAGE, SUPPORTED_LANGUAGE_LIST));
            return;
        }
        language = normalized;
    }

    private static String normalize(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        String value = configured.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (value) {
            case "zh", "zh_cn", "zh_hans", "cn", "chinese", "simplified", "simplified_chinese" -> "zh_cn";
            case "zh_tw", "zh_hant", "tw", "traditional", "traditional_chinese" -> "zh_tw";
            case "zh_hk", "hk", "hong_kong", "hong_kong_chinese" -> "zh_hk";
            case "lzh", "wenyan", "classical_chinese", "literary_chinese" -> "lzh";
            case "en", "en_us", "english" -> "en_us";
            case "es", "es_es", "spanish" -> "es_es";
            case "ja", "ja_jp", "jp", "japanese" -> "ja_jp";
            case "ko", "ko_kr", "kr", "korean" -> "ko_kr";
            case "fr", "fr_fr", "french" -> "fr_fr";
            case "ru", "ru_ru", "russian" -> "ru_ru";
            case "it", "it_it", "italian" -> "it_it";
            case "de", "de_de", "german" -> "de_de";
            default -> value;
        };
    }

    private static String format(String template, Object... args) {
        String result = template;
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return result;
    }

    private static Map<String, Map<String, String>> loadMessages() {
        Map<String, Map<String, String>> all = new LinkedHashMap<>();
        ClassLoader loader = ConsoleMessages.class.getClassLoader();
        for (String language : SUPPORTED_LANGUAGES) {
            all.put(language, loadLanguage(loader, language));
        }
        return Map.copyOf(all);
    }

    private static Map<String, String> loadLanguage(ClassLoader loader, String language) {
        String path = "console_lang/" + language + ".yml";
        try (InputStream input = loader.getResourceAsStream(path)) {
            if (input == null) {
                return Map.of();
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            Map<String, String> messages = new HashMap<>();
            for (String key : yaml.getKeys(true)) {
                if (yaml.isString(key)) {
                    messages.put(key, yaml.getString(key, ""));
                }
            }
            return Map.copyOf(messages);
        } catch (IOException ignored) {
            return Map.of();
        }
    }
}
