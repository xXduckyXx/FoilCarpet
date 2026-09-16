package carpet.script.annotation;

import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import carpet.script.Context;
import carpet.script.value.ListValue;
import carpet.script.value.Value;

import org.jspecify.annotations.Nullable;

final class ListConverter<T> implements ValueConverter<List<T>>
{
    private final ValueConverter<T> itemConverter;
    private final boolean allowSingletonCreation;

    @Override
    public String getTypeName()
    {
        return (allowSingletonCreation ? itemConverter.getTypeName() + " or " : "") + "list of " + itemConverter.getTypeName() + "s";
    }

    @Nullable
    @Override
    public List<T> convert(Value value, @Nullable Context context)
    {
        return value instanceof ListValue ? convertListValue((ListValue) value, context) : allowSingletonCreation ? convertSingleton(value, context) : null;
    }

    @Nullable
    private List<T> convertListValue(ListValue values, @Nullable Context context)
    {
        List<T> list = new ArrayList<>(values.getItems().size());
        for (Value value : values)
        {
            T converted = itemConverter.convert(value, context);
            if (converted == null)
            {
                return null;
            }
            list.add(converted);
        }
        return list;
    }

    @Nullable
    private List<T> convertSingleton(Value val, @Nullable Context context)
    {
        T converted = itemConverter.convert(val, context);
        if (converted == null)
        {
            return null;
        }
        return Collections.singletonList(converted);

    }

    private ListConverter(AnnotatedType itemType, boolean allowSingletonCreation)
    {
        itemConverter = ValueConverter.fromAnnotatedType(itemType);
        this.allowSingletonCreation = allowSingletonCreation;
    }

    static ListConverter<?> fromAnnotatedType(AnnotatedType annotatedType)
    {
        AnnotatedParameterizedType paramType = (AnnotatedParameterizedType) annotatedType;
        AnnotatedType itemType = paramType.getAnnotatedActualTypeArguments()[0];
        boolean allowSingletonCreation = annotatedType.isAnnotationPresent(Param.AllowSingleton.class);
        return new ListConverter<>(itemType, allowSingletonCreation);
    }

}
