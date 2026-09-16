package carpet.script.annotation;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.ParameterizedType;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.ClassUtils;

import carpet.script.Context;
import carpet.script.annotation.Param.Params;
import carpet.script.value.Value;

import org.jspecify.annotations.Nullable;

public interface ValueConverter<R>
{

    @Nullable
    String getTypeName();

    @Nullable R convert(Value value, @Nullable Context context);

    @Nullable
    @Deprecated(forRemoval = true)
    default R convert(Value value)
    {
        try
        {
            return convert(value, null);
        }
        catch (NullPointerException e)
        {
            return null;
        }
    }

    default boolean consumesVariableArgs()
    {
        return false;
    }

    default int valueConsumption()
    {
        return 1;
    }

    @SuppressWarnings("unchecked")
    static <R> ValueConverter<R> fromAnnotatedType(AnnotatedType annoType)
    {
        Class<R> type = annoType.getType() instanceof ParameterizedType ?
                (Class<R>) ((ParameterizedType) annoType.getType()).getRawType() :
                (Class<R>) annoType.getType();

        if (type.isArray())
        {
            type = (Class<R>) type.getComponentType();
        }
        type = (Class<R>) ClassUtils.primitiveToWrapper(type);
        if (type == List.class)
        {
            return (ValueConverter<R>) ListConverter.fromAnnotatedType(annoType);
        }
        if (type == Map.class)
        {
            return (ValueConverter<R>) MapConverter.fromAnnotatedType(annoType);
        }
        if (type == Optional.class)
        {
            return (ValueConverter<R>) OptionalConverter.fromAnnotatedType(annoType);
        }
        if (annoType.getDeclaredAnnotations().length != 0)
        {
            if (annoType.isAnnotationPresent(Param.Custom.class))
            {
                return Params.getCustomConverter(annoType, type);
            }
            if (annoType.isAnnotationPresent(Param.Strict.class))
            {
                return (ValueConverter<R>) Params.getStrictConverter(annoType);
            }
            if (annoType.getAnnotations()[0].annotationType().getEnclosingClass() == Locator.class)
            {
                return Locator.Locators.fromAnnotatedType(annoType, type);
            }
        }

        if (Value.class.isAssignableFrom(type))
        {
            return Objects.requireNonNull(ValueCaster.get(type), "Value subclass " + type + " is not registered. Register it in ValueCaster to use it");
        }
        if (type == Context.class)
        {
            return (ValueConverter<R>) Params.CONTEXT_PROVIDER;
        }
        if (type == Context.Type.class)
        {
            return (ValueConverter<R>) Params.CONTEXT_TYPE_PROVIDER;
        }
        return Objects.requireNonNull(SimpleTypeConverter.get(type), "Type " + type + " is not registered. Register it in SimpleTypeConverter to use it");
    }

    @Nullable
    default R checkAndConvert(Iterator<Value> valueIterator, Context context, Context.Type contextType)
    {
        return !valueIterator.hasNext() ? null : convert(valueIterator.next(), context);
    }
}
