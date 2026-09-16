package carpet.folia;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.entity.CraftTNTPrimed;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import carpet.CarpetSettings;
import carpet.logging.LoggerRegistry;
import carpet.logging.logHelpers.TNTLogHelper;

/**
 * Folia-native implementations of Carpet's {@code tntDoNotUpdate} and {@code mergeTNT} rules,
 * plus a native feed for the {@code /log tnt} logger.
 *
 * <p>{@code tntDoNotUpdate} cannot be intercepted at the rule's real hook point on stock Folia:
 * both the {@code onPlace} and {@code neighborChanged} priming paths fire the same indistinct
 * {@code TNTPrimeEvent(REDSTONE)}. Carpet only prevents the {@code onPlace} priming, so the
 * placement is detected from the right-click that precedes it: while holding TNT, a
 * {@code PlayerInteractEvent} records the target position when that position already carries a
 * redstone signal, and the immediately following {@code onPlace} prime is cancelled. A signal
 * change arriving later (no placement flag) still ignites the TNT, matching Carpet exactly.
 *
 * <p>{@code mergeTNT} replicates Carpet's PrimedTnt merge: TNT that has had a chance to move and
 * is now stationary on the exact same position with the same fuse merges into one entity, and on
 * detonation it detonates once per merged TNT.
 */
public final class TntFolia implements Listener
{
    private static final int SCAN_INTERVAL = 2;

    private static final Map<PrimedTnt, MergeState> MERGE_STATE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<PrimedTnt, TNTLogHelper> TNT_LOG = Collections.synchronizedMap(new WeakHashMap<>());

    // world UID -> placed TNT position -> global tick the placement was attempted on.
    // Set by PlayerInteractEvent only when the target already had a redstone signal, so the
    // matching TNTPrimeEvent (same synchronous placement chain) is the onPlace prime.
    private static final ConcurrentHashMap<String, ConcurrentHashMap<BlockPos, Integer>> PLACE_CANDIDATES =
            new ConcurrentHashMap<>();

    private static final class MergeState
    {
        boolean moved;
        int merged = 1;
    }

    TntFolia()
    {
    }

    // ---------------------------------------------------------------- tntDoNotUpdate

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTntPlaceAttempt(PlayerInteractEvent event)
    {
        if (!CarpetSettings.tntDoNotUpdate || event.isCancelled())
        {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
        {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.TNT)
        {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null)
        {
            return;
        }
        // TNT placed on a replaceable block goes into it, otherwise onto the facing side.
        Block target = clicked.getType().isAir() || clicked.isReplaceable()
                ? clicked : clicked.getRelative(event.getBlockFace());
        ServerLevel level = ((CraftWorld) target.getWorld()).getHandle();
        BlockPos pos = new BlockPos(target.getX(), target.getY(), target.getZ());
        // Only a placement onto an already-powered position can prime during placement, and
        // only those are worth recording (the prime happens in the same synchronous chain).
        // An unpowered placement is never recorded, so powering it up later still ignites it.
        if (!level.hasNeighborSignal(pos))
        {
            return;
        }
        ConcurrentHashMap<BlockPos, Integer> candidates = PLACE_CANDIDATES
                .computeIfAbsent(target.getWorld().getUID().toString(), k -> new ConcurrentHashMap<>());
        candidates.put(pos, CarpetFoliaPlugin.getTick());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTntPrime(TNTPrimeEvent event)
    {
        if (event.isCancelled())
        {
            return;
        }
        if (!CarpetSettings.tntDoNotUpdate)
        {
            return;
        }
        if (event.getCause() != TNTPrimeEvent.PrimeCause.REDSTONE)
        {
            return;
        }
        // Carpet parity: only the placement-time priming (TntBlock.onPlace, which happens while
        // the position already has a signal) is suppressed. prime() is invoked before the block
        // is removed, so cancelling leaves the TNT block in place with no spark. A later signal
        // change (neighborChanged) is NOT accompanied by a placement flag and ignites normally.
        Block block = event.getBlock();
        ConcurrentHashMap<BlockPos, Integer> candidates = PLACE_CANDIDATES
                .get(block.getWorld().getUID().toString());
        if (candidates == null)
        {
            return;
        }
        Integer placedTick = candidates.remove(new BlockPos(block.getX(), block.getY(), block.getZ()));
        if (placedTick != null && CarpetFoliaPlugin.getTick() - placedTick <= 1)
        {
            event.setCancelled(true);
        }
    }

    public static void tickCleanup()
    {
        if (!CarpetSettings.tntDoNotUpdate)
        {
            return;
        }
        int now = CarpetFoliaPlugin.getTick();
        for (ConcurrentHashMap<BlockPos, Integer> candidates : PLACE_CANDIDATES.values())
        {
            candidates.entrySet().removeIf(entry -> now - entry.getValue() > 1);
        }
    }

    // ---------------------------------------------------------------- mergeTNT

    public static void tickMergeScan(Plugin plugin, MinecraftServer server)
    {
        if (server == null || !CarpetSettings.mergeTNT)
        {
            return;
        }
        if (CarpetFoliaPlugin.getTick() % SCAN_INTERVAL != 0)
        {
            return;
        }
        List<ServerLevel> levels = new ArrayList<>();
        server.getAllLevels().forEach(levels::add);
        for (ServerLevel level : levels)
        {
            World bukkitWorld = level.getWorld();
            if (bukkitWorld == null)
            {
                continue;
            }
            try
            {
                for (long[] chunkPos : ChunkRegistry.snapshot(bukkitWorld))
                {
                    final int chunkX = (int) chunkPos[0];
                    final int chunkZ = (int) chunkPos[1];
                    try
                    {
                        Bukkit.getRegionScheduler().execute(plugin, bukkitWorld, chunkX, chunkZ,
                                () -> scanChunk(level, chunkX, chunkZ));
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

    private static void scanChunk(ServerLevel level, int chunkX, int chunkZ)
    {
        LevelChunk chunk = level.getChunkIfLoaded(chunkX, chunkZ);
        if (chunk == null)
        {
            return;
        }
        World world = level.getWorld();
        if (world == null)
        {
            return;
        }
        Object[] entities = ((CraftChunk) world.getChunkAt(chunkX, chunkZ)).getEntities();
        List<PrimedTnt> tnts = new ArrayList<>();
        for (Object entity : entities)
        {
            if (entity instanceof TNTPrimed)
            {
                tnts.add(((CraftTNTPrimed) entity).getHandle());
            }
        }
        if (tnts.isEmpty())
        {
            return;
        }
        for (PrimedTnt tnt : tnts)
        {
            if (tnt.isRemoved())
            {
                continue;
            }
            MergeState state;
            synchronized (MERGE_STATE)
            {
                state = MERGE_STATE.get(tnt);
                if (state == null)
                {
                    state = new MergeState();
                    MERGE_STATE.put(tnt, state);
                }
            }
            Vec3 motion = tnt.getDeltaMovement();
            if (motion.x != 0.0D || motion.y != 0.0D || motion.z != 0.0D)
            {
                state.moved = true;
                continue;
            }
            // only merge TNT that had a chance to move and has come to rest
            if (!state.moved || !tnt.isAlive())
            {
                continue;
            }
            for (PrimedTnt other : tnts)
            {
                if (other == tnt || other.isRemoved())
                {
                    continue;
                }
                Vec3 otherMotion = other.getDeltaMovement();
                if (otherMotion.x != 0.0D || otherMotion.y != 0.0D || otherMotion.z != 0.0D)
                {
                    continue;
                }
                if (other.getX() != tnt.getX() || other.getY() != tnt.getY() || other.getZ() != tnt.getZ())
                {
                    continue;
                }
                if (other.getFuse() != tnt.getFuse())
                {
                    continue;
                }
                MergeState otherState;
                synchronized (MERGE_STATE)
                {
                    otherState = MERGE_STATE.remove(other);
                }
                state.merged += otherState == null ? 1 : otherState.merged;
                synchronized (TNT_LOG)
                {
                    TNT_LOG.remove(other);
                }
                other.discard();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTntSpawn(EntitySpawnEvent event)
    {
        if (event.getEntityType() != EntityType.TNT)
        {
            return;
        }
        try
        {
            PrimedTnt tnt = ((CraftTNTPrimed) event.getEntity()).getHandle();
            if (LoggerRegistry.__tnt)
            {
                TNTLogHelper helper = new TNTLogHelper();
                helper.onPrimed(tnt.getX(), tnt.getY(), tnt.getZ(), tnt.getDeltaMovement());
                synchronized (TNT_LOG)
                {
                    TNT_LOG.put(tnt, helper);
                }
            }
        }
        catch (Throwable ignored)
        {
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTntExplode(EntityExplodeEvent event)
    {
        if (!(event.getEntity() instanceof TNTPrimed))
        {
            return;
        }
        try
        {
            PrimedTnt tnt = ((CraftTNTPrimed) event.getEntity()).getHandle();
            TNTLogHelper helper;
            synchronized (TNT_LOG)
            {
                helper = TNT_LOG.remove(tnt);
            }
            if (helper != null)
            {
                helper.onExploded(tnt.getX(), tnt.getY(), tnt.getZ(), tnt.level().getGameTime());
            }
            if (!CarpetSettings.mergeTNT)
            {
                return;
            }
            MergeState state;
            synchronized (MERGE_STATE)
            {
                state = MERGE_STATE.remove(tnt);
            }
            if (state == null || state.merged <= 1)
            {
                return;
            }
            ServerLevel level = (ServerLevel) tnt.level();
            for (int i = 0; i < state.merged - 1; i++)
            {
                level.explode(tnt, tnt.getX(), tnt.getY() + (double) tnt.getBbHeight() / 16.0F,
                        tnt.getZ(), 4.0F, Level.ExplosionInteraction.TNT);
            }
        }
        catch (Throwable ignored)
        {
        }
    }
}