package carpet.script.annotation;

import java.util.HashMap;
import java.util.Map;

import carpet.script.Context;
import carpet.script.value.AbstractListValue;
import carpet.script.value.BlockValue;
import carpet.script.value.BooleanValue;
import carpet.script.value.EntityValue;
import carpet.script.value.FormattedTextValue;
import carpet.script.value.FunctionValue;
import carpet.script.value.ListValue;
import carpet.script.value.MapValue;
import carpet.script.value.NBTSerializableValue;
import carpet.script.value.NumericValue;
import carpet.script.value.StringValue;
import carpet.script.value.ThreadValue;
import carpet.script.value.Value;

import org.jspecify.annotations.Nullable;

public final class ValueCaster<R> implements ValueConverter<R>
{
    private static final Map<Class<? extends Value>, ValueCaster<? extends Value>> byResult = new HashMap<>();
    static
    {
        register(Value.class, "value");
        register(BlockValue.class, "block");
        register(EntityValue.class, "entity");
        register(FormattedTextValue.class, "formatted text");
        register(FunctionValue.class, "function");
        register(ListValue.class, "list");
        register(MapValue.class, "map");
        register(AbstractListValue.class, "list or similar");
        register(NBTSerializableValue.class, "nbt object");
        register(NumericValue.class, "number");
        register(BooleanValue.class, "boolean");
        register(StringValue.class, "string");
        register(ThreadValue.class, "thread");
    }

    private final Class<R> outputType;
    private final String typeName;

    private ValueCaster(Class<R> outputType, String typeName)
    {
        super();
        this.outputType = outputType;
        this.typeName = typeName;
    }

    @Override
    public String getTypeName()
    {
        return typeName;
    }

    @SuppressWarnings("unchecked")

    public static <R> ValueCaster<R> get(Class<R> outputType)
    {
        return (ValueCaster<R>) byResult.get(outputType);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public R convert(Value value, @Nullable Context context)
    {
        if (!outputType.isInstance(value))
        {
            return null;
        }
        return (R)value;
    }

    public static <R extends Value> void register(Class<R> valueClass, String typeName)
    {
        ValueCaster<R> caster = new ValueCaster<>(valueClass, typeName);
        byResult.putIfAbsent(valueClass, caster);
    }
}
