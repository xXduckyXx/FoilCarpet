package net.minecraft.server.level;

import java.lang.reflect.Method;
import java.util.function.IntSupplier;

public final class CarpetTaskTypeAccess
{
    public static final ThreadedLevelLightEngine.TaskType PRE_UPDATE = ThreadedLevelLightEngine.TaskType.PRE_UPDATE;
    public static final ThreadedLevelLightEngine.TaskType POST_UPDATE = ThreadedLevelLightEngine.TaskType.POST_UPDATE;

    private static final Method ADD_TASK = findAddTask();

    public static void addTask(ThreadedLevelLightEngine engine, int x, int z, IntSupplier completedLevelSupplier, ThreadedLevelLightEngine.TaskType stage, Runnable task)
    {
        try
        {
            ADD_TASK.invoke(engine, x, z, completedLevelSupplier, stage, task);
        }
        catch (ReflectiveOperationException e)
        {
            throw new RuntimeException("Failed to schedule light engine task", e);
        }
    }

    private static Method findAddTask()
    {
        try
        {
            Method m = ThreadedLevelLightEngine.class.getDeclaredMethod("addTask", int.class, int.class, IntSupplier.class, ThreadedLevelLightEngine.TaskType.class, Runnable.class);
            m.setAccessible(true);
            return m;
        }
        catch (ReflectiveOperationException e)
        {
            throw new RuntimeException("Unable to access ThreadedLevelLightEngine.addTask", e);
        }
    }

    private CarpetTaskTypeAccess()
    {
    }
}
