package carpet.folia;

import net.minecraft.server.MinecraftServer;

import carpet.utils.CarpetProfiler;

/**
 * Drives Carpet's {@code /profile} reports on stock Folia.
 *
 * <p>The profiling data is normally collected by CarpetProfiler mixins (MinecraftServer_coreMixin,
 * EntityMixin, …) that call start_tick_profiling / end_tick_profiling around every server tick.
 * Those do not run on stock Folia, so {@code /profile health} would silently produce an empty
 * report. The CarpetProfiler API itself is public, so we open and close the profiling window from
 * the plugin's own per-tick loop instead; the measured interval is one full server tick.
 *
 * <p>Entity-level section sampling ({@code /profile entities}) still requires per-entity mixin
 * tokens and currently only reports the tick-time summary on Folia.
 */
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