package carpet.script.utils;

import org.jspecify.annotations.Nullable;
import java.util.function.Supplier;

public class GlocalFlag extends ThreadLocal<Boolean>
{
    private final boolean initial;

    public GlocalFlag(boolean initial)
    {
        this.initial = initial;
    }

    @Override
    public Boolean initialValue()
    {
        return initial;
    }

    public <T> T getWhileDisabled(Supplier<T> action)
    {
        return whileValueReturn(!initial, action);
    }

    private <T> T whileValueReturn(boolean what, Supplier<T> action)
    {
        T result;
        boolean previous;
        synchronized (this)
        {
            previous = get();
            set(what);
        }
        try
        {
            result = action.get();
        }
        finally
        {
            set(previous);
        }
        return result;
    }

    @Nullable
    public <T> T runIfEnabled(Supplier<T> action)
    {
        synchronized (this)
        {
            if (get() != initial)
            {
                return null;
            }
            set(!initial);
        }
        T result;
        try
        {
            result = action.get();
        }
        finally
        {
            set(initial);
        }
        return result;
    }
}
