package carpet.script.exception;

import carpet.script.Context;
import carpet.script.Expression;
import carpet.script.Token;
import carpet.script.value.StringValue;
import carpet.script.value.Value;

public class ThrowStatement extends InternalExpressionException
{
    private final Throwables type;
    private final Value data;

    public ThrowStatement(Value data, Throwables type)
    {
        super(type.getId());
        this.data = data;
        this.type = type;
    }

    public ThrowStatement(Value data, Throwables parent, String subtype)
    {
        super(subtype);
        this.data = data;
        this.type = new Throwables(subtype, parent);
    }

    public ThrowStatement(String message, Throwables type)
    {
        super(type.getId());
        this.data = StringValue.of(message);
        this.type = type;
    }

    @Override
    public ExpressionException promote(Context c, Expression e, Token token)
    {
        return new ProcessedThrowStatement(c, e, token, stack, type, data);
    }
}
