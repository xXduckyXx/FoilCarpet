package carpet.script.exception;

import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;

public class Throwables
{
    private final String id;
    @Nullable
    private final Throwables parent;

    private static final Map<String, Throwables> byId = new HashMap<>();

    public static final Throwables THROWN_EXCEPTION_TYPE = register("exception", null);
    public static final Throwables VALUE_EXCEPTION = register("value_exception", THROWN_EXCEPTION_TYPE);
    public static final Throwables UNKNOWN_ITEM = register("unknown_item", VALUE_EXCEPTION);
    public static final Throwables UNKNOWN_BLOCK = register("unknown_block", VALUE_EXCEPTION);
    public static final Throwables UNKNOWN_BIOME = register("unknown_biome", VALUE_EXCEPTION);
    public static final Throwables UNKNOWN_PARTICLE = register("unknown_particle", VALUE_EXCEPTION);
    public static final Throwables UNKNOWN_POI = register("unknown_poi", VALUE_EXCEPTION);
    public static final Throwables UNKNOWN_DIMENSION = register("unknown_dimension", VALUE_EXCEPTION);
    public static final Throwables UNKNOWN_STRUCTURE = register("unknown_structure", VALUE_EXCEPTION);
    public static final Throwables UNKNOWN_CRITERION = register("unknown_criterion", VALUE_EXCEPTION);
    public static final Throwables UNKNOWN_SCREEN = register("unknown_screen", VALUE_EXCEPTION);
    public static final Throwables IO_EXCEPTION = register("io_exception", THROWN_EXCEPTION_TYPE);
    public static final Throwables NBT_ERROR = register("nbt_error", IO_EXCEPTION);
    public static final Throwables JSON_ERROR = register("json_error", IO_EXCEPTION);
    public static final Throwables B64_ERROR = register("b64_error", IO_EXCEPTION);
    public static final Throwables USER_DEFINED = register("user_exception", THROWN_EXCEPTION_TYPE);

    public static Throwables register(String id, @Nullable Throwables parent)
    {
        Throwables exc = new Throwables(id, parent);
        byId.put(id, exc);
        return exc;
    }

    public Throwables(String id, @Nullable Throwables parent)
    {
        this.id = id;
        this.parent = parent;
    }

    public static Throwables getTypeForException(String type)
    {
        Throwables properType = byId.get(type);
        if (properType == null)
        {
            throw new InternalExpressionException("Unknown exception type: " + type);
        }
        return properType;
    }

    public boolean isRelevantFor(String filter)
    {
        return (id.equals(filter) || (parent != null && parent.isRelevantFor(filter)));
    }

    public boolean isUserException()
    {
        return this == USER_DEFINED || parent == USER_DEFINED;
    }

    public String getId()
    {
        return id;
    }
}
