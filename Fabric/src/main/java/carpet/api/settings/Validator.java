package carpet.api.settings;

import org.jetbrains.annotations.Nullable;

import carpet.utils.Messenger;
import net.minecraft.commands.CommandSourceStack;

public abstract class Validator<T>
{

    public abstract T validate(@Nullable CommandSourceStack source, CarpetRule<T> changingRule, T newValue, String userInput);

    public String description() {return null;}

    public void notifyFailure(CommandSourceStack source, CarpetRule<T> currentRule, String providedValue)
    {
        Messenger.m(source, "r Wrong value for " + currentRule.name() + ": " + providedValue);
        if (description() != null)
            Messenger.m(source, "r " + description());
    }
}
