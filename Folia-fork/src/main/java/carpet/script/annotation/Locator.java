package carpet.script.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedType;
import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import com.google.common.collect.Lists;

import carpet.script.CarpetContext;
import carpet.script.Context;
import carpet.script.argument.Argument;
import carpet.script.argument.BlockArgument;
import carpet.script.argument.FunctionArgument;
import carpet.script.argument.Vector3Argument;
import carpet.script.Module;
import carpet.script.value.BlockValue;
import carpet.script.value.FunctionValue;
import carpet.script.value.Value;

import org.jspecify.annotations.Nullable;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

public interface Locator
{

    @Documented
    @Retention(RUNTIME)
    @Target({ PARAMETER, TYPE_USE })
    @interface Block
    {

        boolean acceptString() default false;

        boolean optional() default false;

        boolean anyString() default false;
    }

    @Documented
    @Retention(RUNTIME)
    @Target({ PARAMETER, TYPE_USE })
    @interface Vec3d
    {

        boolean optionalDirection() default false;

        boolean optionalEntity() default false;
    }

    @Documented
    @Retention(RUNTIME)
    @Target({ PARAMETER, TYPE_USE })
    @interface Function
    {

        boolean allowNone() default false;

        boolean checkArgs();
    }

    final class Locators
    {
        private Locators()
        {
            super();
        }

        static <R> ValueConverter<R> fromAnnotatedType(AnnotatedType annoType, Class<R> type)
        {
            if (annoType.isAnnotationPresent(Block.class))
            {
                return new BlockLocator<>(annoType.getAnnotation(Block.class), type);
            }
            if (annoType.isAnnotationPresent(Function.class))
            {
                return new FunctionLocator<>(annoType.getAnnotation(Function.class), type);
            }
            if (annoType.isAnnotationPresent(Vec3d.class))
            {
                return new Vec3dLocator<>(annoType.getAnnotation(Vec3d.class), type);
            }
            throw new IllegalStateException("Locator#fromAnnotatedType got called with an incompatible AnnotatedType");
        }

        private static class BlockLocator<R> extends AbstractLocator<R>
        {
            private final java.util.function.Function<BlockArgument, R> returnFunction;
            private final boolean acceptString;
            private final boolean anyString;
            private final boolean optional;

            public BlockLocator(Block annotation, Class<R> type)
            {
                super();
                this.acceptString = annotation.acceptString();
                this.anyString = annotation.anyString();
                this.optional = annotation.optional();
                if (type != BlockArgument.class && (anyString || optional))
                {
                    throw new IllegalArgumentException("Can only use anyString or optional parameters of Locator.Block if targeting a BlockArgument");
                }
                this.returnFunction = getReturnFunction(type);
                if (returnFunction == null)
                {
                    throw new IllegalArgumentException("Locator.Block can only be used against BlockArgument, BlockValue, BlockPos or BlockState types!");
                }
            }

            @SuppressWarnings("unchecked")
            private static <R> java.util.function.@Nullable Function<BlockArgument, R> getReturnFunction(Class<R> type)
            {
                if (type == BlockArgument.class)
                {
                    return r -> (R) r;
                }
                if (type == BlockValue.class)
                {
                    return r -> (R) r.block;
                }
                if (type == BlockPos.class)
                {
                    return r -> (R) r.block.getPos();
                }
                if (type == BlockState.class)
                {
                    return r -> (R) r.block.getBlockState();
                }
                return null;
            }

            @Override
            public String getTypeName()
            {
                return "block";
            }

            @Override
            public R checkAndConvert(Iterator<Value> valueIterator, Context context, Context.Type theLazyT)
            {
                BlockArgument locator = BlockArgument.findIn((CarpetContext) context, valueIterator, 0, acceptString, optional, anyString);
                return returnFunction.apply(locator);
            }
        }

        private static class Vec3dLocator<R> extends AbstractLocator<R>
        {
            private final boolean optionalDirection;
            private final boolean optionalEntity;
            private final boolean returnVec3d;

            public Vec3dLocator(Vec3d annotation, Class<R> type)
            {
                this.optionalDirection = annotation.optionalDirection();
                this.optionalEntity = annotation.optionalEntity();
                this.returnVec3d = type == net.minecraft.world.phys.Vec3.class;
                if (returnVec3d && optionalDirection)
                {
                    throw new IllegalArgumentException("optionalDirection Locator.Vec3d cannot be used for Vec3d type, use Vector3Argument instead");
                }
                if (!returnVec3d && type != Vector3Argument.class)
                {
                    throw new IllegalArgumentException("Locator.Vec3d can only be used in Vector3Argument or Vec3d types");
                }
            }

            @Override
            public String getTypeName()
            {
                return "position";
            }

            @Override
            public R checkAndConvert(Iterator<Value> valueIterator, Context context, Context.Type theLazyT)
            {
                Vector3Argument locator = Vector3Argument.findIn(valueIterator, 0, optionalDirection, optionalEntity);
                @SuppressWarnings("unchecked") R ret = (R) (returnVec3d ? locator.vec : locator);
                return ret;
            }
        }

        private static class FunctionLocator<R> extends AbstractLocator<R>
        {
            private final boolean returnFunctionValue;
            private final boolean allowNone;
            private final boolean checkArgs;

            FunctionLocator(Function annotation, Class<R> type)
            {
                super();
                this.returnFunctionValue = type == FunctionValue.class;
                if (!returnFunctionValue && type != FunctionArgument.class)
                {
                    throw new IllegalArgumentException("Params annotated with Locator.Function must be of either FunctionArgument or FunctionValue type");
                }
                this.allowNone = annotation.allowNone();
                this.checkArgs = annotation.checkArgs();
                if (returnFunctionValue && allowNone)
                {
                    throw new IllegalArgumentException("Cannot use allowNone of Locator.Function in FunctionValue types, use FunctionArgument");
                }
            }

            @Override
            public R checkAndConvert(Iterator<Value> valueIterator, Context context, Context.Type theLazyT)
            {
                Module module = context.host.main;
                FunctionArgument locator = FunctionArgument.findIn(context, module, Lists.newArrayList(valueIterator), 0, allowNone, checkArgs);
                @SuppressWarnings("unchecked") R ret = (R) (returnFunctionValue ? locator.function : locator);
                return ret;
            }

            @Override
            public String getTypeName()
            {
                return "function";
            }
        }

        private abstract static class AbstractLocator<R> implements ValueConverter<R>, Locator
        {
            @Override
            public R convert(Value value, @Nullable Context context)
            {
                throw new UnsupportedOperationException("Cannot call a locator in a parameter that doesn't contain a context!");
            }

            @Override
            public boolean consumesVariableArgs()
            {
                return true;
            }

            @Override
            public int valueConsumption()
            {
                return 1;
            }

            @Override
            public abstract R checkAndConvert(Iterator<Value> valueIterator, Context context, Context.Type theLazyT);
        }

    }
}
