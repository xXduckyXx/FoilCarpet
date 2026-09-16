package carpet.settings;

import carpet.CarpetSettings;

@Deprecated(forRemoval = true)
public interface Condition extends carpet.api.settings.Rule.Condition {
    boolean isTrue();

    @Override
    default boolean shouldRegister() {
        CarpetSettings.LOG.warn("""
                Extension is referencing outdated Condition class! Class that caused this was: %s
                This won't be supported in later Carpet versions and will crash the game!""".formatted(getClass().getName()));
        return isTrue();
    }
}
