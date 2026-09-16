package carpet.api.settings;

import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.ClassUtils;

import carpet.network.ServerNetworkHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public interface CarpetRule<T> {

    String name();

    List<Component> extraInfo();

    Collection<String> categories();

    Collection<String> suggestions();

    SettingsManager settingsManager();

    T value();

    boolean canBeToggledClientSide();

    Class<T> type();

    T defaultValue();

    default boolean strict() {
        return false;
    }

    void set(CommandSourceStack source, String value) throws InvalidRuleValueException;

    void set(CommandSourceStack source, T value) throws InvalidRuleValueException;
}
