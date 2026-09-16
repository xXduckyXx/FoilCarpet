package carpet.api.settings;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Rule
{

    String[] categories();

    String[] options() default {};

    boolean strict() default true;

    String appSource() default "";

    @SuppressWarnings("rawtypes")
    Class<? extends Validator>[] validators() default {};

    Class<? extends Condition>[] conditions() default {};

    interface Condition {

        boolean shouldRegister();
    }
}
