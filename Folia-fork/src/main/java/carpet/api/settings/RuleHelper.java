package carpet.api.settings;

import java.util.Locale;

import carpet.utils.TranslationKeys;
import carpet.utils.Translations;
import net.minecraft.commands.CommandSourceStack;

public final class RuleHelper {
    private RuleHelper() {}

    public static boolean getBooleanValue(CarpetRule<?> rule) {
        if (rule.type() == Boolean.class) return (boolean) rule.value();
        if (Number.class.isAssignableFrom(rule.type())) return ((Number) rule.value()).doubleValue() > 0;
        return false;
    }

    public static String toRuleString(Object value) {
        if (value instanceof Enum) return ((Enum<?>) value).name().toLowerCase(Locale.ROOT);
        return value.toString();
    }

    public static boolean isInDefaultValue(CarpetRule<?> rule) {
        return rule.defaultValue().equals(rule.value());
    }

    public static <T> void resetToDefault(CarpetRule<T> rule, CommandSourceStack source) {
        try {
            rule.set(source, rule.defaultValue());
        } catch (InvalidRuleValueException e) {
            throw new IllegalStateException("Rule couldn't be set to default value!", e);
        }
    }

    public static String translatedName(CarpetRule<?> rule) {
        String key = String.format(TranslationKeys.RULE_NAME_PATTERN, rule.settingsManager().identifier(), rule.name());
        return Translations.hasTranslation(key) ? Translations.tr(key) + String.format(" (%s)", rule.name()): rule.name();
    }

    public static String translatedDescription(CarpetRule<?> rule) {
        return Translations.tr(String.format(TranslationKeys.RULE_DESC_PATTERN, rule.settingsManager().identifier(), rule.name()));
    }

    public static String translatedCategory(String manager, String category) {
        return Translations.tr(TranslationKeys.CATEGORY_PATTERN.formatted(manager, category), category);
    }
}
