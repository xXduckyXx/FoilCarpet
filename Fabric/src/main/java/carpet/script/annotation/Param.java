package carpet.script.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import carpet.script.Context;
import carpet.script.value.BooleanValue;
import carpet.script.value.EntityValue;
import carpet.script.value.FormattedTextValue;
import carpet.script.value.NumericValue;
import carpet.script.value.StringValue;
import carpet.script.value.Value;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

public interface Param
{

    @Documented
    @Retention(RUNTIME)
    @Target({ PARAMETER, TYPE_USE })
    @interface AllowSingleton
    {

    }

    @Documented
    @Retention(RUNTIME)
    @Target({ PARAMETER, TYPE_USE })
    @interface KeyValuePairs
    {

        boolean allowMultiparam() default true;
    }

    @Documented
    @Retention(RUNTIME)
    @Target({ PARAMETER, TYPE_USE })
    @interface Custom
    {

    }

    @Documented
    @Retention(RUNTIME)
    @Target({ PARAMETER, TYPE_USE })
    @interface Strict
    {

        boolean shallow() default false;
    }

    final class Params
    {

        static final ValueConverter<Context> CONTEXT_PROVIDER = new ValueConverter<>()
        {
            @Nullable
            @Override
            public String getTypeName()
            {
                return null;
            }

            @Override
            public Context convert(Value value, @Nullable Context context)
            {
                throw new UnsupportedOperationException("Called convert() with Value in Context Provider converter, where only checkAndConvert is supported");
            }

            @Override
            public Context checkAndConvert(Iterator<Value> valueIterator, Context context, Context.Type theLazyT)
            {
                return context;
            }

            @Override
            public int valueConsumption()
            {
                return 0;
            }
        };

        static final ValueConverter<Context.Type> CONTEXT_TYPE_PROVIDER = new ValueConverter<>()
        {
            @Nullable
            @Override
            public String getTypeName()
            {
                return null;
            }

            @Override
            public Context.Type convert(Value value, @Nullable Context context)
            {
                throw new UnsupportedOperationException("Called convert() with a Value in TheLazyT Provider, where only checkAndConvert is supported");
            }

            @Override
            public Context.Type checkAndConvert(Iterator<Value> valueIterator, Context context, Context.Type theLazyT)
            {
                return theLazyT;
            }

            @Override
            public int valueConsumption()
            {
                return 0;
            }
        };

        record StrictConverterInfo(Class<?> type, boolean shallow) {}
        private static final Map<StrictConverterInfo, ValueConverter<?>> strictParamsByClassAndShallowness = new HashMap<>();
        static
        {
            registerStrictConverter(String.class, false, new SimpleTypeConverter<>(StringValue.class, StringValue::getString, "string"));
            registerStrictConverter(Component.class, false, new SimpleTypeConverter<>(FormattedTextValue.class, FormattedTextValue::getText, "text"));
            registerStrictConverter(Component.class, true, new SimpleTypeConverter<>(StringValue.class, FormattedTextValue::getTextByValue, "text"));
            registerStrictConverter(ServerPlayer.class, false, new SimpleTypeConverter<>(EntityValue.class,
                    v -> EntityValue.getPlayerByValue(v.getEntity().level().getServer(), v), "online player entity"));
            registerStrictConverter(Boolean.class, false, new SimpleTypeConverter<>(BooleanValue.class, BooleanValue::getBoolean, "boolean"));
            registerStrictConverter(Boolean.class, true, new SimpleTypeConverter<>(NumericValue.class, NumericValue::getBoolean, "boolean"));
        }

        static ValueConverter<?> getStrictConverter(AnnotatedType type)
        {
            boolean shallow = type.getAnnotation(Strict.class).shallow();
            Class<?> clazz = (Class<?>) type.getType();
            StrictConverterInfo key = new StrictConverterInfo(clazz, shallow);
            ValueConverter<?> converter = strictParamsByClassAndShallowness.get(key);
            if (converter != null)
            {
                return converter;
            }
            throw new IllegalArgumentException("Incorrect use of @Param.Strict annotation");
        }

        public static <T> void registerStrictConverter(Class<T> type, boolean shallow, ValueConverter<T> converter)
        {
            StrictConverterInfo key = new StrictConverterInfo(type, shallow);
            if (strictParamsByClassAndShallowness.containsKey(key))
            {
                throw new IllegalArgumentException(type + " already has a registered " + (shallow ? "" : "non-") + "shallow StrictConverter");
            }
            strictParamsByClassAndShallowness.put(key, converter);
        }

        private static final List<BiFunction<AnnotatedType, Class<?>, ValueConverter<?>>> customFactories = new ArrayList<>();

        @SuppressWarnings("unchecked")
        public static <T> void registerCustomConverterFactory(BiFunction<AnnotatedType, Class<T>, ValueConverter<T>> factory)
        {
            customFactories.add((BiFunction<AnnotatedType, Class<?>, ValueConverter<?>>) (Object) factory);
        }

        @SuppressWarnings("unchecked")
        static <R> ValueConverter<R> getCustomConverter(AnnotatedType annoType, Class<R> type)
        {
            ValueConverter<R> result;
            for (BiFunction<AnnotatedType, Class<?>, ValueConverter<?>> factory : customFactories)
            {
                if ((result = (ValueConverter<R>) factory.apply(annoType, type)) != null)
                {
                    return result;
                }
            }
            throw new IllegalArgumentException("No custom converter found for Param.Custom annotated param with type " + annoType.getType().getTypeName());
        }
    }
}
