package carpet.settings;

import carpet.CarpetSettings;
import carpet.api.settings.CarpetRule;
import carpet.api.settings.Validators;
import carpet.utils.CommandHelper;
import carpet.utils.Messenger;
import net.minecraft.commands.CommandSourceStack;

@Deprecated(forRemoval = true)
@SuppressWarnings("removal")
public abstract class Validator<T> extends carpet.api.settings.Validator<T>
{
	{

	    CarpetSettings.LOG.warn("""
                Validator '%s' is implementing the old Validator class! This class is deprecated and will be removed \
                and crash in later Carpet versions!""".formatted(getClass().getName()));
	}

    @Override
    public final T validate(CommandSourceStack source, CarpetRule<T> changingRule, T newValue, String stringInput) {

        if (!(changingRule instanceof ParsedRule<T> parsedRule))

            throw new IllegalArgumentException("Passed a non-ParsedRule to a validator using the outdated method!");
        return validate(source, parsedRule, newValue, stringInput);
    }

    @Deprecated(forRemoval = true)
    public abstract T validate(CommandSourceStack source, ParsedRule<T> currentRule, T newValue, String string);

    @Deprecated(forRemoval = true)
    public static class _COMMAND_LEVEL_VALIDATOR extends Validators.CommandLevel {}

    @Deprecated(forRemoval = true)
    public static class NONNEGATIVE_NUMBER<T extends Number> extends Validators.NonNegativeNumber<T> {}

    @Deprecated(forRemoval = true)
    public static class PROBABILITY <T extends Number> extends Validators.Probablity<T> {}

    static class _COMMAND<T> extends carpet.api.settings.Validator<T>
    {
        @Override
        public T validate(CommandSourceStack source, CarpetRule<T> currentRule, T newValue, String string)
        {
            if (source != null)
                CommandHelper.notifyPlayersCommandsChanged(source.getServer());
            return newValue;
        }
        @Override
        public String description() { return "It has an accompanying command";}
    }

    static class _CLIENT<T> extends carpet.api.settings.Validator<T>
    {
        @Override
        public T validate(CommandSourceStack source, CarpetRule<T> currentRule, T newValue, String string)
        {
            return newValue;
        }
        @Override
        public String description() { return "Its a client command so can be issued and potentially be effective when connecting to non-carpet/vanilla servers. " +
                "In these situations (on vanilla servers) it will only affect the executing player, so each player needs to type it" +
                " separately for the desired effect";}
    }

    static class ScarpetValidator<T> extends carpet.api.settings.Validator<T> {
        @Override
        public T validate(CommandSourceStack source, CarpetRule<T> currentRule, T newValue, String string)
        {
            return newValue;
        }
        @Override public String description() {
            return "It controls an accompanying Scarpet App";
        }
    }

    static class StrictValidator<T> extends carpet.api.settings.Validator<T>
    {
        @Override
        public T validate(CommandSourceStack source, CarpetRule<T> currentRule, T newValue, String string)
        {
            if (!currentRule.suggestions().contains(string))
            {
                Messenger.m(source, "r Valid options: " + currentRule.suggestions().toString());
                return null;
            }
            return newValue;
        }
    }
}
