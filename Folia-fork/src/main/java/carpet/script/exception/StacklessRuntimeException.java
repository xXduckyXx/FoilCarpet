package carpet.script.exception;

import carpet.script.external.Carpet;

public abstract class StacklessRuntimeException extends RuntimeException
{
    public StacklessRuntimeException()
    {
        super();
    }

    public StacklessRuntimeException(String message)
    {
        super(message);
    }

    @Override
    public Throwable fillInStackTrace()
    {
        if (Carpet.isDebugEnabled())
        {
            return super.fillInStackTrace();
        }
        return this;
    }
}
