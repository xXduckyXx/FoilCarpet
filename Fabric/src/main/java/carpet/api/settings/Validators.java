package carpet.api.settings;

import java.util.List;

import carpet.utils.CommandHelper;
import net.minecraft.commands.CommandSourceStack;

public final class Validators {
    private Validators() {};

    public static class CommandLevel extends Validator<String> {
        @Deprecated(forRemoval = true)
        public static final List<String> OPTIONS = List.of("true", "false", "ops", "0", "1", "2", "3", "4");
        @Override
        public String validate(CommandSourceStack source, CarpetRule<String> currentRule, String newValue, String userString) {
            if (!OPTIONS.contains(newValue))
            {
                return null;
            }
            return newValue;
        }
        @Override public String description() { return "Can be limited to 'ops' only, true/false for everyone/no one, or a custom permission level";}
    }

    public static class NonNegativeNumber<T extends Number> extends Validator<T>
    {
        @Override
        public T validate(CommandSourceStack source, CarpetRule<T> currentRule, T newValue, String string)
        {
            return newValue.doubleValue() >= 0 ? newValue : null;
        }
        @Override
        public String description() { return "Must be a positive number or 0";}
    }

    public static class Probablity<T extends Number> extends Validator<T>
    {
        @Override
        public T validate(CommandSourceStack source, CarpetRule<T> currentRule, T newValue, String string)
        {
            return (newValue.doubleValue() >= 0 && newValue.doubleValue() <= 1 )? newValue : null;
        }
        @Override
        public String description() { return "Must be between 0 and 1";}
    }
}
