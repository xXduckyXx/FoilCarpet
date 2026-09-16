package carpet.script.annotation;

import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import carpet.script.Context;
import carpet.script.value.ListValue;
import carpet.script.value.MapValue;
import carpet.script.value.Value;

import org.jspecify.annotations.Nullable;

class MapConverter<K, V> implements ValueConverter<Map<K, V>>
{
    protected final ValueConverter<K> keyConverter;
    protected final ValueConverter<V> valueConverter;

    @Override
    public String getTypeName()
    {
        return "map with " + keyConverter.getTypeName() + "s as the key and " + valueConverter.getTypeName() + "s as the value";
    }

    @Override
    public Map<K, V> convert(Value value, @Nullable Context context)
    {
        Map<K, V> result = new HashMap<>();
        if (value instanceof MapValue)
        {
            for (Entry<Value, Value> entry : ((MapValue) value).getMap().entrySet())
            {
                K key = keyConverter.convert(entry.getKey(), context);
                V val = valueConverter.convert(entry.getValue(), context);
                if (key == null || val == null)
                {
                    return null;
                }
                result.put(key, val);
            }
            return result;
        }
        return null;
    }

    private MapConverter(AnnotatedType keyType, AnnotatedType valueType)
    {
        super();
        keyConverter = ValueConverter.fromAnnotatedType(keyType);
        valueConverter = ValueConverter.fromAnnotatedType(valueType);
    }

    static MapConverter<?, ?> fromAnnotatedType(AnnotatedType annotatedType)
    {
        AnnotatedType[] annotatedGenerics = ((AnnotatedParameterizedType) annotatedType).getAnnotatedActualTypeArguments();
        return annotatedType.isAnnotationPresent(Param.KeyValuePairs.class)
                ? new PairConverter<>(annotatedGenerics[0], annotatedGenerics[1], annotatedType.getAnnotation(Param.KeyValuePairs.class))
                : new MapConverter<>(annotatedGenerics[0], annotatedGenerics[1]);
    }

    private static final class PairConverter<K, V> extends MapConverter<K, V>
    {
        private final boolean acceptMultiParam;

        private PairConverter(AnnotatedType keyType, AnnotatedType valueType, Param.KeyValuePairs config)
        {
            super(keyType, valueType);
            acceptMultiParam = config.allowMultiparam();
        }

        @Override
        public boolean consumesVariableArgs()
        {
            return acceptMultiParam;
        }

        @Nullable
        @Override
        public Map<K, V> convert(Value value, @Nullable Context context) {
            return value instanceof MapValue ? super.convert(value, context)
                    : value instanceof ListValue ? convertList(((ListValue)value).getItems(), context)
                            : null;
        }

        @Nullable
        private Map<K, V> convertList(List<Value> valueList, @Nullable Context context)
        {
            if (valueList.size() % 2 == 1)
            {
                return null;
            }
            Map<K, V> map = new HashMap<>();
            Iterator<Value> val = valueList.iterator();
            while (val.hasNext())
            {
                K key = keyConverter.convert(val.next(), context);
                V value = valueConverter.convert(val.next(), context);
                if (key == null || value == null)
                {
                    return null;
                }
                map.put(key, value);
            }
            return map;
        }

        @Nullable
        @Override
        public Map<K, V> checkAndConvert(Iterator<Value> valueIterator, Context context, Context.Type theLazyT)
        {
            if (!valueIterator.hasNext())
            {
                return null;
            }
            Value val = valueIterator.next();
            if (!acceptMultiParam || val instanceof MapValue || (val instanceof ListValue && !(keyConverter instanceof ListConverter)))
            {
                return convert(val, context);
            }

            Map<K, V> map = new HashMap<>();
            K key = keyConverter.convert(val, context);
            V value = valueConverter.checkAndConvert(valueIterator, context, theLazyT);
            if (key == null || value == null)
            {
                return null;
            }
            map.put(key, value);
            while (valueIterator.hasNext())
            {
                key = keyConverter.checkAndConvert(valueIterator, context, theLazyT);
                value = valueConverter.checkAndConvert(valueIterator, context, theLazyT);
                if (key == null || value == null)
                {
                    return null;
                }
                map.put(key, value);
            }
            return map;
        }

        @Override
        public String getTypeName()
        {
            return "either a map of key-value pairs" + (acceptMultiParam ? "," : " or") + " a list in the form of [key, value, key2, value2,...]"
                    + (acceptMultiParam ? " or those key-value pairs in the function" : "") + " (keys being " + keyConverter.getTypeName()
                    + "s and values being " + valueConverter.getTypeName() + "s)";
        }
    }
}
