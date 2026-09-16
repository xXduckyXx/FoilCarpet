package carpet.script.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Optional;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import carpet.script.Context;
import carpet.script.LazyValue;
import carpet.script.value.Value;

@Documented
@Target(METHOD)
@Retention(RUNTIME)
public @interface ScarpetFunction
{

    int UNLIMITED_PARAMS = -1;

    int maxParams() default AnnotationParser.UNDEFINED_PARAMS;

    String functionName() default AnnotationParser.USE_METHOD_NAME;

    Context.Type contextType() default Context.Type.NONE;
}
