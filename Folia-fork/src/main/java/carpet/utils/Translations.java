package carpet.utils;

import carpet.CarpetExtension;
import carpet.CarpetServer;
import carpet.CarpetSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Translations
{
    private static Map<String, String> translationMap = Collections.emptyMap();

    public static String tr(String key)
    {
        return translationMap.getOrDefault(key, key);
    }

    public static String trOrNull(String key)
    {
        return translationMap.get(key);
    }

    public static String tr(String key, String str)
    {
        return translationMap.getOrDefault(key, str);
    }

    public static boolean hasTranslations()
    {
        return !translationMap.isEmpty();
    }

    public static boolean hasTranslation(String key)
    {
        return translationMap.containsKey(key);
    }

    public static Map<String, String> getTranslationFromResourcePath(String path)
    {
        InputStream langFile = Translations.class.getClassLoader().getResourceAsStream(path);
        if (langFile == null) {

            return Collections.emptyMap();
        }
        Gson gson = new GsonBuilder().setLenient().create();
        return gson.fromJson(new InputStreamReader(langFile, StandardCharsets.UTF_8),
                new TypeToken<Map<String, String>>() {});
    }

    public static void updateLanguage()
    {
        Map<String, String> translations = new HashMap<>();
        translations.putAll(getTranslationFromResourcePath(String.format("assets/carpet/lang/%s.json", CarpetSettings.language)));

        for (CarpetExtension ext : CarpetServer.extensions)
        {
            Map<String, String> extMappings = ext.canHasTranslations(CarpetSettings.language);
            if (extMappings == null) continue;
            boolean warned = false;
            for (var entry : extMappings.entrySet()) {
                var key = entry.getKey();

                if (!key.startsWith("carpet.")) {
                    if (key.startsWith("rule.")) {

                        key = TranslationKeys.BASE_RULE_NAMESPACE.formatted("carpet") + key.substring(5);
                    } else if (key.startsWith("category.")) {
                        key = TranslationKeys.CATEGORY_PATTERN.formatted("carpet", key.substring(9));
                    }
                    if (!warned && key != entry.getKey()) {
                        CarpetSettings.LOG.warn("""
                                Found outdated translation keys in extension '%s'!
                                These won't be supported in a later Carpet version!
                                Carpet will now try to map them to the correct keys in a best-effort basis""".formatted(ext.getClass().getName()));
                        warned = true;
                    }
                }
                translations.putIfAbsent(key, entry.getValue());
            }
        }
        translations.keySet().removeIf(e -> {
            if (e.startsWith("//")) {
                CarpetSettings.LOG.warn("""
                        Found translation key starting with // while preparing translations!
                        Doing this is deprecated and may cause issues in later versions! Consider settings GSON to "lenient" mode and
                        using regular comments instead!
                        Translation key is '%s'""".formatted(e));
                return true;
            } else
                return false;
        });

        addFallbacksTo(translations);
        translationMap = translations;
    }

    public static boolean isValidLanguage(String newValue)
    {

        return true;
    }

    private static final Map<String, String> FALLBACKS = new HashMap<>();

    @Deprecated(forRemoval = true)
    public static void registerFallbackTranslation(String key, String description) {
        FALLBACKS.put(key, description);
    }

    private static void addFallbacksTo(Map<String, String> translationMap) {
        FALLBACKS.forEach(translationMap::putIfAbsent);
    }
}
