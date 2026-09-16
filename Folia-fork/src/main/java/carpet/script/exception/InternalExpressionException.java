package carpet.script.exception;

import carpet.script.Context;
import carpet.script.Expression;
import carpet.script.Token;
import carpet.script.value.FunctionValue;

import java.util.ArrayList;
import java.util.List;

public class InternalExpressionException extends StacklessRuntimeException
{
    public List<FunctionValue> stack = new ArrayList<>();

    public InternalExpressionException(String message)
    {
        super(message);
    }

    public ExpressionException promote(Context c, Expression e, Token token)
    {
        return new ExpressionException(c, e, token, getMessage(), stack);
    }
}
