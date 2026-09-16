package carpet.api.settings;

import carpet.utils.Messenger;
import net.minecraft.commands.CommandSourceStack;

public class InvalidRuleValueException extends Exception {

    public InvalidRuleValueException(String cause) {
        super(cause);
    }

    public InvalidRuleValueException() {
        super();
    }

    public void notifySource(String ruleName, CommandSourceStack source) {
        if (getMessage() != null)
            Messenger.m(source, "r Couldn't set value for rule " + ruleName + ": "+ getMessage());
    }
}
