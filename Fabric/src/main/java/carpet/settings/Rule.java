package carpet.settings;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Deprecated(forRemoval = true)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Rule
{

    String name() default "";

    String desc();

    String[] extra() default {};

    String[] category();

    String[] options() default {};

    boolean strict() default true;

    String appSource() default "";

    @SuppressWarnings("rawtypes")
    Class<? extends carpet.api.settings.Validator>[] validate() default {};

    Class<? extends carpet.api.settings.Rule.Condition>[] condition() default {};
}
