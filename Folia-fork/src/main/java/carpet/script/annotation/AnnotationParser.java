package carpet.script.annotation;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import carpet.script.CarpetContext;
import net.minecraft.core.RegistryAccess;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ClassUtils;

import com.google.common.base.Suppliers;

import carpet.script.Context;
import carpet.script.Expression;
import carpet.script.Fluff.AbstractLazyFunction;
import carpet.script.Fluff.TriFunction;
import carpet.script.Fluff.UsageProvider;
import carpet.script.exception.InternalExpressionException;
import carpet.script.LazyValue;
import carpet.script.value.Value;

public final class AnnotationParser
{
    static final int UNDEFINED_PARAMS = -2;
    static final String USE_METHOD_NAME = "$METHOD_NAME_MARKER$";
    private static final List<ParsedFunction> functionList = new ArrayList<>();

    public static void parseFunctionClass(Class<?> clazz)
    {

        Supplier<Object> instanceSupplier = Suppliers.memoize(() -> {
            if (Modifier.isAbstract(clazz.getModifiers()))
            {
                throw new IllegalArgumentException("Function class must be concrete to support non-static methods! Class: " + clazz.getSimpleName());
            }
            try
            {
                return clazz.getConstructor().newInstance();
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalArgumentException(
                        "Couldn't create instance of given " + clazz + ". This is needed for non-static methods. Make sure default constructor is available", e);
            }
        });
        Method[] methodz = clazz.getDeclaredMethods();
        for (Method method : methodz)
        {
            if (!method.isAnnotationPresent(ScarpetFunction.class))
            {
                continue;
            }

            if (method.getExceptionTypes().length != 0)
            {
                throw new IllegalArgumentException("Annotated method '" + method.getName() + "', provided in '" + clazz + "' must not declare checked exceptions");
            }

            ParsedFunction function = new ParsedFunction(method, clazz, instanceSupplier);
            functionList.add(function);
        }
    }

    public static void apply(Expression expr)
    {
        for (ParsedFunction function : functionList)
        {
            expr.addLazyFunction(function.name, function.scarpetParamCount, function);
        }
    }

    private static class ParsedFunction implements TriFunction<Context, Context.Type, List<LazyValue>, LazyValue>, UsageProvider
    {
        private final String name;
        private final boolean isMethodVarArgs;
        private final int methodParamCount;
        private final ValueConverter<?>[] valueConverters;
        private final Class<?> varArgsType;
        private final boolean primitiveVarArgs;
        private final ValueConverter<?> varArgsConverter;
        private final OutputConverter<Object> outputConverter;
        private final boolean isEffectivelyVarArgs;
        private final int minParams;
        private final int maxParams;
        private final MethodHandle handle;
        private final int scarpetParamCount;
        private final Context.Type contextType;

        private ParsedFunction(Method method, Class<?> originClass, Supplier<Object> instance)
        {
            ScarpetFunction annotation = method.getAnnotation(ScarpetFunction.class);
            this.name = USE_METHOD_NAME.equals(annotation.functionName()) ? method.getName() : annotation.functionName();
            this.isMethodVarArgs = method.isVarArgs();
            this.methodParamCount = method.getParameterCount();

            Parameter[] methodParameters = method.getParameters();
            this.valueConverters = new ValueConverter[isMethodVarArgs ? methodParamCount - 1 : methodParamCount];
            for (int i = 0; i < this.methodParamCount; i++)
            {
                Parameter param = methodParameters[i];
                if (!isMethodVarArgs || i != this.methodParamCount - 1)
                {
                    this.valueConverters[i] = ValueConverter.fromAnnotatedType(param.getAnnotatedType());
                }
            }
            Class<?> originalVarArgsType = isMethodVarArgs ? methodParameters[methodParamCount - 1].getType().getComponentType() : null;
            this.varArgsType = ClassUtils.primitiveToWrapper(originalVarArgsType);
            this.primitiveVarArgs = originalVarArgsType != null && originalVarArgsType.isPrimitive();
            this.varArgsConverter = isMethodVarArgs ? ValueConverter.fromAnnotatedType(methodParameters[methodParamCount - 1].getAnnotatedType()) : null;
            @SuppressWarnings("unchecked")
            OutputConverter<Object> converter = OutputConverter.get((Class<Object>) method.getReturnType());
            this.outputConverter = converter;

            this.isEffectivelyVarArgs = isMethodVarArgs || Arrays.stream(valueConverters).anyMatch(ValueConverter::consumesVariableArgs);
            this.minParams = Arrays.stream(valueConverters).mapToInt(ValueConverter::valueConsumption).sum();
            int setMaxParams = this.minParams;
            if (this.isEffectivelyVarArgs)
            {
                setMaxParams = annotation.maxParams();
                if (setMaxParams == UNDEFINED_PARAMS)
                {
                    throw new IllegalArgumentException("No maximum number of params specified for " + name + ", use ScarpetFunction.UNLIMITED_PARAMS for unlimited. "
                            + "Provided in " + originClass);
                }
                if (setMaxParams == ScarpetFunction.UNLIMITED_PARAMS)
                {
                    setMaxParams = Integer.MAX_VALUE;
                }
                if (setMaxParams < this.minParams)
                {
                    throw new IllegalArgumentException("Provided maximum number of params for " + name + " is smaller than method's param count."
                            + "Provided in " + originClass);
                }
            }
            this.maxParams = setMaxParams;

            try
            {
                MethodHandle tempHandle = MethodHandles.publicLookup().unreflect(method).asFixedArity().asSpreader(Object[].class, this.methodParamCount);
                tempHandle = tempHandle.asType(tempHandle.type().changeReturnType(Object.class));
                this.handle = Modifier.isStatic(method.getModifiers()) ? tempHandle : tempHandle.bindTo(instance.get());
            }
            catch (IllegalAccessException e)
            {
                throw new IllegalArgumentException(e);
            }

            this.scarpetParamCount = this.isEffectivelyVarArgs ? -1 : this.minParams;
            this.contextType = annotation.contextType();
        }

        @Override
        public LazyValue apply(Context context, Context.Type t, List<LazyValue> lazyValues)
        {

            RegistryAccess regs = ((CarpetContext) context).registryAccess();
            List<Value> lv = AbstractLazyFunction.unpackLazy(lazyValues, context, contextType);
            if (isEffectivelyVarArgs)
            {
                if (lv.size() < minParams)
                {
                    throw new InternalExpressionException("Function '" + name + "' expected at least " + minParams + " arguments, got " + lv.size() + ". "
                            + getUsage());
                }
                if (lv.size() > maxParams)
                {
                    throw new InternalExpressionException("Function '" + name + " expected up to " + maxParams + " arguments, got " + lv.size() + ". "
                            + getUsage());
                }
            }
            Object[] params = getMethodParams(lv, context, t);
            try
            {
                Value result = outputConverter.convert(handle.invokeExact(params), regs);
                return (cc, tt) -> result;
            }
            catch (Throwable e)
            {
                if (e instanceof RuntimeException re)
                {
                    throw re;
                }
                throw (Error) e;
            }
        }

        private Object[] getMethodParams(List<Value> lv, Context context, Context.Type theLazyT)
        {
            Object[] params = new Object[methodParamCount];
            ListIterator<Value> lvIterator = lv.listIterator();

            int regularArgs = isMethodVarArgs ? methodParamCount - 1 : methodParamCount;
            for (int i = 0; i < regularArgs; i++)
            {
                params[i] = valueConverters[i].checkAndConvert(lvIterator, context, theLazyT);
                if (params[i] == null)
                {
                    throw new InternalExpressionException("Incorrect argument passsed to '" + name + "' function.\n" + getUsage());
                }
            }
            if (isMethodVarArgs)
            {
                int remaining = lv.size() - lvIterator.nextIndex();
                Object[] varArgs;
                if (varArgsConverter.consumesVariableArgs())
                {
                    List<Object> varArgsList = new ArrayList<>();
                    while (lvIterator.hasNext())
                    {
                        Object obj = varArgsConverter.checkAndConvert(lvIterator, context, theLazyT);
                        if (obj == null)
                        {
                            throw new InternalExpressionException("Incorrect argument passsed to '" + name + "' function.\n" + getUsage());
                        }
                        varArgsList.add(obj);
                    }
                    varArgs = varArgsList.toArray((Object[]) Array.newInstance(varArgsType, 0));
                }
                else
                {
                    varArgs = (Object[]) Array.newInstance(varArgsType, remaining / varArgsConverter.valueConsumption());
                    for (int i = 0; lvIterator.hasNext(); i++)
                    {
                        varArgs[i] = varArgsConverter.checkAndConvert(lvIterator, context, theLazyT);
                        if (varArgs[i] == null)
                        {
                            throw new InternalExpressionException("Incorrect argument passsed to '" + name + "' function.\n" + getUsage());
                        }
                    }
                }
                params[methodParamCount - 1] = primitiveVarArgs ? ArrayUtils.toPrimitive(varArgs) : varArgs;
            }
            return params;
        }

        @Override
        public String getUsage()
        {

            StringBuilder builder = new StringBuilder("Usage: '");
            builder.append(name);
            builder.append('(');
            builder.append(Arrays.stream(valueConverters).map(ValueConverter::getTypeName).filter(Objects::nonNull).collect(Collectors.joining(", ")));
            if (varArgsConverter != null)
            {
                builder.append(", ");
                builder.append(varArgsConverter.getTypeName());
                builder.append("s...)");
            }
            else
            {
                builder.append(')');
            }
            builder.append("'");
            return builder.toString();
        }
    }

    private AnnotationParser()
    {
    }
}
