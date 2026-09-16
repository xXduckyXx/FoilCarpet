package carpet.folia;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;

public final class ChunkRegistry
{
    private static final ConcurrentHashMap<String, Set<Long>> LOADED = new ConcurrentHashMap<>();

    private static final Listener HANDLER = new Listener()
    {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onChunkLoad(ChunkLoadEvent event)
        {
            add(event.getChunk());
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onChunkUnload(ChunkUnloadEvent event)
        {
            remove(event.getChunk());
        }
    };

    private ChunkRegistry()
    {
    }

    public static void register(Plugin plugin)
    {
        plugin.getServer().getPluginManager().registerEvents(HANDLER, plugin);
    }

    private static String key(World world)
    {
        return world.getUID().toString();
    }

    private static long pack(int x, int z)
    {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static void add(Chunk chunk)
    {
        World world = chunk.getWorld();
        if (world == null)
        {
            return;
        }
        Set<Long> set = LOADED.computeIfAbsent(key(world), k -> ConcurrentHashMap.newKeySet());
        set.add(pack(chunk.getX(), chunk.getZ()));
    }

    private static void remove(Chunk chunk)
    {
        World world = chunk.getWorld();
        if (world == null)
        {
            return;
        }
        Set<Long> set = LOADED.get(key(world));
        if (set != null)
        {
            set.remove(pack(chunk.getX(), chunk.getZ()));
        }
    }

    public static List<long[]> snapshot(World world)
    {
        List<long[]> result = new ArrayList<>();
        if (world == null)
        {
            return result;
        }
        Set<Long> set = LOADED.get(key(world));
        if (set == null)
        {
            return result;
        }
        for (long packed : set)
        {
            result.add(new long[] { (int) (packed >> 32), (int) packed });
        }
        return result;
    }
}
