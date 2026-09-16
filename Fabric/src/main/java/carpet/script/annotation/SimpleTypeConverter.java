package carpet.script.annotation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import carpet.script.CarpetContext;
import carpet.script.Context;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import carpet.script.exception.InternalExpressionException;
import carpet.script.value.EntityValue;
import carpet.script.value.FormattedTextValue;
import carpet.script.value.NumericValue;
import carpet.script.value.Value;
import carpet.script.value.ValueConversions;

import org.jspecify.annotations.Nullable;

public final class SimpleTypeConverter<T extends Value, R> implements ValueConverter<R>
{
    private static final Map<Class<?>, SimpleTypeConverter<? extends Value, ?>> byResult = new HashMap<>();
    static
    {
        registerType(Value.class, ServerPlayer.class, (v, c) -> EntityValue.getPlayerByValue(((CarpetContext)c).server(), v), "online player");
        registerType(EntityValue.class, Entity.class, EntityValue::getEntity, "entity");
        registerType(Value.class, Level.class, (v, c) -> ValueConversions.dimFromValue(v, ((CarpetContext)c).server()), "dimension");
        registerType(Value.class, Component.class, FormattedTextValue::getTextByValue, "text");
        registerType(Value.class, String.class, Value::getString, "string");

        registerType(NumericValue.class, Long.class, NumericValue::getLong, "number");
        registerType(NumericValue.class, Double.class, NumericValue::getDouble, "number");
        registerType(NumericValue.class, Integer.class, NumericValue::getInt, "number");
        registerType(Value.class, Boolean.class, Value::getBoolean, "boolean");
    }

    private final BiFunction<T, Context, R> converter;
    private final Class<T> valueClass;
    private final String typeName;

    public SimpleTypeConverter(Class<T> inputType, Function<T, R> converter, String typeName)
    {
        this(inputType, (v, c) -> converter.apply(v), typeName);
    }

    public SimpleTypeConverter(Class<T> inputType, BiFunction<T, Context, R> converter, String typeName)
    {
        super();
        this.converter = converter;
        this.valueClass = inputType;
        this.typeName = typeName;
    }

    @Override
    public String getTypeName()
    {
        return typeName;
    }

    @SuppressWarnings("unchecked")
    static <R> SimpleTypeConverter<Value, R> get(Class<R> outputType)
    {
        return (SimpleTypeConverter<Value, R>) byResult.get(outputType);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public R convert(Value value, @Nullable Context context)
    {
        return valueClass.isInstance(value) ? converter.apply((T)value, context) : null;
    }

    public static <T extends Value, R> void registerType(Class<T> requiredInputType, Class<R> outputType,
                                                         Function<T, R> converter, String typeName)
    {
        registerType(requiredInputType, outputType, (val, ctx) -> converter.apply(val), typeName);
    }

    public static <T extends Value, R> void registerType(Class<T> requiredInputType, Class<R> outputType,
                                                         BiFunction<T, Context, R> converter, String typeName)
    {
        SimpleTypeConverter<T, R> type = new SimpleTypeConverter<>(requiredInputType, converter, typeName);
        byResult.put(outputType, type);
    }
}
