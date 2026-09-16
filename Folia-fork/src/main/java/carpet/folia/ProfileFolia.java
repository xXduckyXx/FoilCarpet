package carpet.folia;

import net.minecraft.server.MinecraftServer;

import carpet.utils.CarpetProfiler;

public final class ProfileFolia
{
    private ProfileFolia()
    {
    }

    public static void tick(MinecraftServer server)
    {
        if (server == null || CarpetProfiler.tick_health_requested == 0)
        {
            return;
        }
        try
        {
            CarpetProfiler.end_tick_profiling(server);
        }
        catch (Throwable ignored)
        {
        }
        try
        {
            CarpetProfiler.start_tick_profiling();
        }
        catch (Throwable ignored)
        {
        }
    }
}
