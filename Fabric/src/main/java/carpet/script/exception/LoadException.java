package carpet.script.exception;

public class LoadException extends RuntimeException implements ResolvedException
{
    public LoadException()
    {
        super();
    }
    public LoadException(String message)
    {
        super(message);
    }
}
