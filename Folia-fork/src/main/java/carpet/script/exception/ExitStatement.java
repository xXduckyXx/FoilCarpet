package carpet.script.exception;

import carpet.script.value.Value;

import org.jspecify.annotations.Nullable;

public class ExitStatement extends StacklessRuntimeException
{
    @Nullable
    public final Value retval;

    public ExitStatement(@Nullable Value value)
    {
        retval = value;
    }
}
