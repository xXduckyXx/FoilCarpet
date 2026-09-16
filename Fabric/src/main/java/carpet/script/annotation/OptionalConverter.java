package carpet.script.annotation;

import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Optional;

import carpet.script.Context;
import carpet.script.value.Value;

import org.jspecify.annotations.Nullable;

final class OptionalConverter<R> implements ValueConverter<Optional<R>>
{
    private final ValueConverter<R> typeConverter;

    @Override
    public String getTypeName()
    {
        return "optional " + typeConverter.getTypeName();
    }

    private OptionalConverter(AnnotatedType type)
    {
        typeConverter = ValueConverter.fromAnnotatedType(type);
    }

    @Nullable
    @Override
    public Optional<R> convert(Value value, @Nullable Context context)
    {
        if (value.isNull())
        {
            return Optional.empty();
        }
        R converted = typeConverter.convert(value, context);
        if (converted == null)
        {
            return null;
        }
        return Optional.of(converted);
    }

    @Nullable
    @Override
    public Optional<R> checkAndConvert(Iterator<Value> valueIterator, Context context, Context.Type theLazyT)
    {
        if (!valueIterator.hasNext() || valueIterator.next().isNull())
        {
            return Optional.empty();
        }
        ((ListIterator<Value>) valueIterator).previous();
        R converted = typeConverter.checkAndConvert(valueIterator, context, theLazyT);
        if (converted == null)
        {
            return null;
        }
        return Optional.of(converted);
    }

    @Override
    public boolean consumesVariableArgs()
    {
        return true;
    }

    @Override
    public int valueConsumption()
    {
        return 0;
    }

    static OptionalConverter<?> fromAnnotatedType(AnnotatedType annotatedType)
    {
        AnnotatedParameterizedType paramType = (AnnotatedParameterizedType) annotatedType;
        AnnotatedType wrappedType = paramType.getAnnotatedActualTypeArguments()[0];
        return new OptionalConverter<>(wrappedType);
    }
}
