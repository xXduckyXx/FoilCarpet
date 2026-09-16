package carpet.folia;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.chunk.LevelChunk;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;

import carpet.CarpetSettings;

public final class RailFolia implements Listener
{
    private static final int POWER_SCAN_INTERVAL = 10;
    private static final int RESCAN_INTERVAL = 600;

    private static final ConcurrentHashMap<String, ConcurrentHashMap<Long, Set<BlockPos>>> RAILS =
            new ConcurrentHashMap<>();

    private static int lastLimit = 9;

    private final Plugin plugin;

    RailFolia(Plugin plugin)
    {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRailPlace(BlockPlaceEvent event)
    {
        if (event.getBlockPlaced().getType() == Material.POWERED_RAIL)
        {
            track(event.getBlockPlaced());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRailBreak(BlockBreakEvent event)
    {
        if (event.getBlock().getType() == Material.POWERED_RAIL)
        {
            untrack(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event)
    {
        World world = event.getWorld();
        ServerLevel level = ((CraftWorld) world).getHandle();
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        try
        {
            Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ,
                    () -> rescanChunk(level, chunkX, chunkZ));
        }
        catch (Throwable ignored)
        {
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event)
    {
        ConcurrentHashMap<Long, Set<BlockPos>> byChunk = RAILS.get(event.getWorld().getUID().toString());
        if (byChunk != null)
        {
            byChunk.remove(packChunk(event.getChunk().getX(), event.getChunk().getZ()));
        }
    }

    public static void tick(Plugin plugin, MinecraftServer server)
    {
        if (server == null)
        {
            return;
        }
        int limitSetting = CarpetSettings.railPowerLimit;
        boolean active = limitSetting != 9;
        if (active && lastLimit == 9)
        {

            rescanAll(plugin, server);
        }
        else if (!active && lastLimit != 9)
        {

            powerPass(plugin, server, 8);
        }
        lastLimit = limitSetting;
        if (!active)
        {
            return;
        }
        int tick = CarpetFoliaPlugin.getTick();
        if (tick % POWER_SCAN_INTERVAL == 0)
        {
            powerPass(plugin, server, limitSetting - 1);
        }
        if (tick % RESCAN_INTERVAL == 0)
        {
            rescanAll(plugin, server);
        }
    }

    private static void track(Block block)
    {
        Set<BlockPos> rails = railsFor(block.getWorld().getUID().toString(), block.getX() >> 4, block.getZ() >> 4);
        rails.add(new BlockPos(block.getX(), block.getY(), block.getZ()));
    }

    private static void untrack(Block block)
    {
        ConcurrentHashMap<Long, Set<BlockPos>> byChunk = RAILS.get(block.getWorld().getUID().toString());
        if (byChunk == null)
        {
            return;
        }
        Set<BlockPos> rails = byChunk.get(packChunk(block.getX() >> 4, block.getZ() >> 4));
        if (rails != null)
        {
            rails.remove(new BlockPos(block.getX(), block.getY(), block.getZ()));
        }
    }

    private static Set<BlockPos> railsFor(String worldUid, int chunkX, int chunkZ)
    {
        ConcurrentHashMap<Long, Set<BlockPos>> byChunk = RAILS.computeIfAbsent(worldUid,
                k -> new ConcurrentHashMap<>());
        return byChunk.computeIfAbsent(packChunk(chunkX, chunkZ), k -> ConcurrentHashMap.newKeySet());
    }

    private static long packChunk(int x, int z)
    {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static void rescanAll(Plugin plugin, MinecraftServer server)
    {
        List<ServerLevel> levels = new ArrayList<>();
        server.getAllLevels().forEach(levels::add);
        for (ServerLevel level : levels)
        {
            World world = level.getWorld();
            if (world == null)
            {
                continue;
            }
            try
            {
                List<long[]> chunks = ChunkRegistry.snapshot(world);
                if (chunks.isEmpty())
                {
                    continue;
                }
                for (long[] chunkPos : chunks)
                {
                    final int chunkX = (int) chunkPos[0];
                    final int chunkZ = (int) chunkPos[1];
                    try
                    {
                        Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ,
                                () -> rescanChunk(level, chunkX, chunkZ));
                    }
                    catch (Throwable ignored)
                    {
                    }
                }
            }
            catch (Throwable ignored)
            {
            }
        }
    }

    private static void rescanChunk(ServerLevel level, int chunkX, int chunkZ)
    {
        LevelChunk chunk = level.getChunkIfLoaded(chunkX, chunkZ);
        Set<BlockPos> found = ConcurrentHashMap.newKeySet();
        if (chunk != null)
        {
            int minY = level.getWorld().getMinHeight();
            int maxY = level.getWorld().getMaxHeight();
            int baseX = chunkX << 4;
            int baseZ = chunkZ << 4;
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int y = minY; y < maxY; y++)
            {
                for (int x = 0; x < 16; x++)
                {
                    for (int z = 0; z < 16; z++)
                    {
                        cursor.set(baseX + x, y, baseZ + z);
                        if (chunk.getBlockState(cursor).is(Blocks.POWERED_RAIL))
                        {
                            found.add(cursor.immutable());
                        }
                    }
                }
            }
        }
        World world = level.getWorld();
        if (world == null)
        {
            return;
        }
        RAILS.computeIfAbsent(world.getUID().toString(), k -> new ConcurrentHashMap<>())
                .put(packChunk(chunkX, chunkZ), found);

        fixRails(level, found, CarpetSettings.railPowerLimit - 1);
    }

    private static void powerPass(Plugin plugin, MinecraftServer server, int limit)
    {
        for (ServerLevel level : server.getAllLevels())
        {
            World world = level.getWorld();
            if (world == null)
            {
                continue;
            }
            ConcurrentHashMap<Long, Set<BlockPos>> byChunk = RAILS.get(world.getUID().toString());
            if (byChunk == null || byChunk.isEmpty())
            {
                continue;
            }
            try
            {
                for (Map.Entry<Long, Set<BlockPos>> entry : byChunk.entrySet())
                {
                    final long chunkKey = entry.getKey();
                    final Set<BlockPos> rails = entry.getValue();
                    if (rails == null || rails.isEmpty())
                    {
                        continue;
                    }
                    final int chunkX = (int) (chunkKey >> 32);
                    final int chunkZ = (int) chunkKey;
                    try
                    {
                        Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ,
                                () -> fixRails(level, rails, limit));
                    }
                    catch (Throwable ignored)
                    {
                    }
                }
            }
            catch (Throwable ignored)
            {
            }
        }
    }

    private static void fixRails(ServerLevel level, Set<BlockPos> rails, int limit)
    {
        Set<BlockPos> found = new HashSet<>(rails);
        for (BlockPos pos : rails)
        {
            BlockState state = level.getBlockState(pos);
            if (!state.is(Blocks.POWERED_RAIL))
            {

                continue;
            }
            boolean powered = level.hasNeighborSignal(pos) || chainPowered(level, pos, limit);
            boolean currently = state.getValue(BlockStateProperties.POWERED);
            if (currently != powered)
            {

                level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, powered), 2);
            }
            found.remove(pos);
        }
    }

    private static boolean chainPowered(ServerLevel level, BlockPos start, int limit)
    {
        if (level.hasNeighborSignal(start))
        {
            return true;
        }
        if (limit <= 0)
        {
            return false;
        }
        Set<BlockPos> seen = new HashSet<>();
        seen.add(start);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        gatherCandidates(level, start, seen, queue);
        int depth = 1;
        while (!queue.isEmpty())
        {
            int levelSize = queue.size();
            for (int i = 0; i < levelSize; i++)
            {
                BlockPos pos = queue.poll();
                if (level.hasNeighborSignal(pos))
                {
                    return true;
                }
                if (depth < limit)
                {
                    gatherCandidates(level, pos, seen, queue);
                }
            }
            depth++;
            if (depth > limit)
            {
                break;
            }
        }
        return false;
    }

    private static void gatherCandidates(ServerLevel level, BlockPos pos, Set<BlockPos> seen, ArrayDeque<BlockPos> queue)
    {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PoweredRailBlock))
        {
            return;
        }

        RailShape shape = state.getValue(PoweredRailBlock.SHAPE);
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        switch (shape)
        {
            case NORTH_SOUTH:

                tryRail(level, seen, queue, x, y, z - 1, RailShape.NORTH_SOUTH);
                tryRail(level, seen, queue, x, y - 1, z - 1, RailShape.NORTH_SOUTH);
                tryRail(level, seen, queue, x, y, z + 1, RailShape.NORTH_SOUTH);
                tryRail(level, seen, queue, x, y - 1, z + 1, RailShape.NORTH_SOUTH);
                break;
            case EAST_WEST:
                tryRail(level, seen, queue, x - 1, y, z, RailShape.EAST_WEST);
                tryRail(level, seen, queue, x - 1, y - 1, z, RailShape.EAST_WEST);
                tryRail(level, seen, queue, x + 1, y, z, RailShape.EAST_WEST);
                tryRail(level, seen, queue, x + 1, y - 1, z, RailShape.EAST_WEST);
                break;
            case ASCENDING_EAST:
                tryRail(level, seen, queue, x - 1, y, z, RailShape.EAST_WEST);
                tryRail(level, seen, queue, x + 1, y + 1, z, RailShape.EAST_WEST);
                break;
            case ASCENDING_WEST:
                tryRail(level, seen, queue, x + 1, y, z, RailShape.EAST_WEST);
                tryRail(level, seen, queue, x - 1, y + 1, z, RailShape.EAST_WEST);
                break;
            case ASCENDING_NORTH:
                tryRail(level, seen, queue, x, y, z + 1, RailShape.NORTH_SOUTH);
                tryRail(level, seen, queue, x, y + 1, z - 1, RailShape.NORTH_SOUTH);
                break;
            case ASCENDING_SOUTH:
                tryRail(level, seen, queue, x, y, z - 1, RailShape.NORTH_SOUTH);
                tryRail(level, seen, queue, x, y + 1, z + 1, RailShape.NORTH_SOUTH);
                break;
            default:

                break;
        }
    }

    private static void tryRail(ServerLevel level, Set<BlockPos> seen, ArrayDeque<BlockPos> queue,
                                int x, int y, int z, RailShape family)
    {
        BlockPos target = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(target);
        if (!state.is(Blocks.POWERED_RAIL))
        {
            return;
        }
        RailShape shape = state.getValue(PoweredRailBlock.SHAPE);

        if (family == RailShape.EAST_WEST && (shape == RailShape.NORTH_SOUTH
                || shape == RailShape.ASCENDING_NORTH || shape == RailShape.ASCENDING_SOUTH))
        {
            return;
        }
        if (family == RailShape.NORTH_SOUTH && (shape == RailShape.EAST_WEST
                || shape == RailShape.ASCENDING_EAST || shape == RailShape.ASCENDING_WEST))
        {
            return;
        }
        if (seen.add(target))
        {
            queue.add(target);
        }
    }
}
