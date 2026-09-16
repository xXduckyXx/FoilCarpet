package carpet.script.annotation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.ClassUtils;

import carpet.script.value.BooleanValue;
import carpet.script.value.EntityValue;
import carpet.script.value.FormattedTextValue;
import carpet.script.value.NBTSerializableValue;
import carpet.script.value.NumericValue;
import carpet.script.value.StringValue;
import carpet.script.value.Value;
import carpet.script.value.ValueConversions;

import org.jspecify.annotations.Nullable;

public final class OutputConverter<T>
{
    private static final Map<Class<?>, OutputConverter<?>> byResult = new HashMap<>();
    private static final OutputConverter<Value> VALUE = new OutputConverter<>((v, regs) -> v);
    static
    {

        register(Void.TYPE, v -> Value.NULL);
        register(Boolean.class, BooleanValue::of);
        register(Integer.class, (v, r) -> new NumericValue(v));
        register(Double.class, NumericValue::of);
        register(Float.class, NumericValue::of);
        register(Long.class, (v, r) -> new NumericValue(v));
        register(String.class, StringValue::new);
        register(Entity.class, EntityValue::new);
        register(Component.class, FormattedTextValue::new);
        register(Tag.class, NBTSerializableValue::new);
        register(BlockPos.class, (v, r) -> ValueConversions.of(v));
        register(Vec3.class, (v, r) -> ValueConversions.of(v));
        register(ItemStack.class, (v, r) -> ValueConversions.of(v, r));
        register(Identifier.class, (v, r) -> ValueConversions.of(v));
        register(GlobalPos.class, (v, r) -> ValueConversions.of(v));
    }

    private final BiFunction<T, RegistryAccess, Value> converter;

    private OutputConverter(BiFunction<T, RegistryAccess, Value> converter)
    {
        this.converter = converter;
    }

    @SuppressWarnings("unchecked")

    public static <T> OutputConverter<T> get(Class<T> returnType)
    {
        if (Value.class.isAssignableFrom(returnType))
        {
            return (OutputConverter<T>) VALUE;
        }
        returnType = (Class<T>) ClassUtils.primitiveToWrapper(returnType);
        return (OutputConverter<T>) Objects.requireNonNull(byResult.get(returnType),
                "Unregistered output type: " + returnType + ". Register in OutputConverter");
    }

    public Value convert(@Nullable T input, RegistryAccess regs)
    {
        return input == null ? Value.NULL : converter.apply(input, regs);
    }

    public static <T> void register(Class<T> inputType, BiFunction<T, RegistryAccess, Value> converter)
    {
        OutputConverter<T> instance = new OutputConverter<>(converter);
        if (byResult.containsKey(inputType))
        {
            throw new IllegalArgumentException(inputType + " already has a registered OutputConverter");
        }
        byResult.put(inputType, instance);
    }
    public static <T> void register(Class<T> inputType, Function<T, Value> converter)
    {
        register(inputType, (v, regs) -> converter.apply(v));
    }

    @Deprecated
    public static <T> void registerToValue(Class<T> inputType, BiFunction<T, RegistryAccess, Value> converter)
    {
        register(inputType, converter);
    }
    @Deprecated
    public static <T> void registerToValue(Class<T> inputType, Function<T, Value> converter)
    {
        register(inputType, (v, regs) -> converter.apply(v));
    }
}
