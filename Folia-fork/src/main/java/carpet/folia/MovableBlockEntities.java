package carpet.folia;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import carpet.CarpetSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Replicates Carpet's {@code movableBlockEntities} on stock Folia, where the MOVE_BLOCK_ENTITY
 * subsystem cannot be mixin-ed in. Vanilla refuses to push any block with a block entity, so on
 * the vanilla/Folia builds a "stuck" piston is a powered, retracted piston whose push line
 * contains (at least) one block entity. Since no vanilla path or Bukkit event can ever move such
 * a line, this class periodically scans loaded chunks (on their own region threads), detects
 * stuck pistons and instantly moves the whole push line far-to-near, copying each block entity
 * through its serialized NBT (same data the chunk format uses). The piston itself is left in the
 * extended state and a piston head is placed in the freed space so that any queued vanilla extend
 * event becomes a no-op.
 *
 * <p>This is deliberately tolerant to edge cases: redstone-clock driven pistons work, they just
 * animate instantly instead of smoothly. Chains that cross into a foreign region abort and
 * retry on the next sweep.
 */
public final class MovableBlockEntities
{
    private static final int SCAN_INTERVAL = 2;
    private static final int MAX_DISTANCE = 12;
    private static final int FLAG_SRC_AIR = 594;
    private static final int FLAG_DEST = 82;
    private static final int FLAG_PISTON = 19;

    private static final Set<Long> MOVING_NOW = ConcurrentHashMap.newKeySet();
    private static long movingEpoch = -1;

    private MovableBlockEntities()
    {
    }

    public static void tickScan(Plugin plugin, MinecraftServer server)
    {
        if (server == null || !CarpetSettings.movableBlockEntities)
        {
            return;
        }
        int tick = CarpetFoliaPlugin.getTick();
        if (tick % SCAN_INTERVAL != 0)
        {
            return;
        }
        long epoch = tick / SCAN_INTERVAL;
        if (epoch != movingEpoch)
        {
            movingEpoch = epoch;
            MOVING_NOW.clear();
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
                        // chunk may have been unloaded / outside the region scheduler
                    }
                }
            }
            catch (Throwable ignored)
            {
                // getLoadedChunks may race with world shutdown
            }
        }
    }

    private static void scanChunk(ServerLevel level, int chunkX, int chunkZ)
    {
        try
        {
            LevelChunk chunk = level.getChunkIfLoaded(chunkX, chunkZ);
            if (chunk == null)
            {
                return;
            }
            Set<BlockPos> positions = chunk.getBlockEntitiesPos();
            if (positions == null || positions.isEmpty())
            {
                return;
            }
            for (BlockPos pos : List.copyOf(positions))
            {
                if (chunk.getBlockEntitiesPos() == null || !chunk.getBlockEntitiesPos().contains(pos))
                {
                    continue;
                }
                attemptMoveFromBlock(pos, level);
            }
        }
        catch (Throwable ignored)
        {
            // cross-region reads / unloaded chunks: retried on the next sweep
        }
    }

    /**
     * For a given block entity position, look for any retracted powered piston whose push line
     * (up to the push limit, in any of the six directions) contains the block entity, and which
     * vanilla would refuse to extend because of it.
     */
    private static void attemptMoveFromBlock(BlockPos bePos, ServerLevel level)
    {
        for (Direction dir : Direction.values())
        {
            for (int distance = 1; distance <= MAX_DISTANCE; distance++)
            {
                BlockPos pistonPos = bePos.relative(dir.getOpposite(), distance);
                if (!level.isInWorldBounds(pistonPos))
                {
                    continue;
                }
                BlockState pistonState = level.getBlockState(pistonPos);
                Block pistonBlock = pistonState.getBlock();
                if (pistonBlock != Blocks.PISTON && pistonBlock != Blocks.STICKY_PISTON)
                {
                    continue;
                }
                if (pistonState.getValue(DirectionalBlock.FACING) != dir)
                {
                    continue;
                }
                if (pistonState.getValue(PistonBaseBlock.EXTENDED))
                {
                    continue;
                }
                if (!isPowered(level, pistonPos, dir))
                {
                    continue;
                }
                List<BlockPos> chain = pushLine(level, pistonPos, dir);
                if (chain == null || chain.isEmpty())
                {
                    continue;
                }
                boolean hasMovableBlockEntity = false;
                for (BlockPos q : chain)
                {
                    if (level.getBlockState(q).hasBlockEntity())
                    {
                        hasMovableBlockEntity = true;
                        break;
                    }
                }
                if (!hasMovableBlockEntity)
                {
                    // vanilla can extend this piston by itself; do not interfere
                    continue;
                }
                moveLine(level, pistonPos, dir, chain, pistonBlock == Blocks.STICKY_PISTON);
                return;
            }
        }
    }

    private static boolean isPowered(ServerLevel level, BlockPos pistonPos, Direction facing)
    {
        for (Direction d : Direction.values())
        {
            if (d != facing && level.hasSignal(pistonPos.relative(d), d))
            {
                return true;
            }
        }
        if (level.hasSignal(pistonPos.relative(Direction.DOWN), Direction.DOWN))
        {
            return true;
        }
        for (Direction d : Direction.values())
        {
            if (d != Direction.DOWN && level.hasSignal(pistonPos.above().relative(d), d))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Walks the straight push line in front of the piston using Carpet's movable-block-entity
     * semantics. Returns the ordered list of blocks to push (closest to the piston first), or
     * {@code null} if the line contains an immovable block or exceeds the push limit, or an
     * empty list if the piston faces air.
     */
    private static List<BlockPos> pushLine(ServerLevel level, BlockPos pistonPos, Direction dir)
    {
        List<BlockPos> chain = new ArrayList<>();
        BlockPos pos = pistonPos.relative(dir);
        int limit = Math.max(1, CarpetSettings.pushLimit);
        while (true)
        {
            BlockState state = level.getBlockState(pos);
            if (state.isAir())
            {
                break;
            }
            if (!isPushable(level, state, pos, dir, false, dir))
            {
                return null;
            }
            if (chain.size() >= limit)
            {
                return null;
            }
            chain.add(pos);
            pos = pos.relative(dir);
        }
        return chain;
    }

    private static boolean isPushable(ServerLevel level, BlockState state, BlockPos pos,
            Direction dir, boolean allowDestroy, Direction pistonDirection)
    {
        if (pos.getY() < level.getMinY() || pos.getY() > level.getMaxY()
                || !level.getWorldBorder().isWithinBounds(pos)
                || !level.getWorldBorder().isWithinBounds(pos.relative(dir)))
        {
            return false;
        }
        if (state.isAir())
        {
            return true;
        }
        Block block = state.getBlock();
        if (block == Blocks.OBSIDIAN || block == Blocks.CRYING_OBSIDIAN
                || block == Blocks.RESPAWN_ANCHOR || block == Blocks.REINFORCED_DEEPSLATE)
        {
            return false;
        }
        if (dir == Direction.DOWN && pos.getY() == level.getMinY())
        {
            return false;
        }
        if (dir == Direction.UP && pos.getY() == level.getMaxY())
        {
            return false;
        }
        if (block == Blocks.PISTON || block == Blocks.STICKY_PISTON)
        {
            if (state.getValue(PistonBaseBlock.EXTENDED))
            {
                return false;
            }
        }
        else
        {
            if (state.getDestroySpeed(level, pos) == -1.0f)
            {
                return false;
            }
            if (CarpetSettings.movableBlockEntities && block instanceof CommandBlock)
            {
                return true;
            }
            PushReaction reaction = CarpetSettings.movableBlockEntities && block == Blocks.GRINDSTONE
                    ? PushReaction.NORMAL : state.getPistonPushReaction();
            switch (reaction)
            {
                case BLOCK -> { return false; }
                case DESTROY -> { return allowDestroy; }
                case IGNORE -> { return dir == pistonDirection; }
                default -> { }
            }
        }
        if (!state.hasBlockEntity())
        {
            return true;
        }
        return CarpetSettings.movableBlockEntities && isMovableBlockEntity(block);
    }

    private static boolean isMovableBlockEntity(Block block)
    {
        return block != Blocks.ENDER_CHEST && block != Blocks.ENCHANTING_TABLE
                && block != Blocks.END_GATEWAY && block != Blocks.END_PORTAL
                && block != Blocks.MOVING_PISTON && block != Blocks.SPAWNER
                && block != Blocks.SCULK_SENSOR && block != Blocks.CALIBRATED_SCULK_SENSOR;
    }

    private static void moveLine(ServerLevel level, BlockPos pistonPos, Direction facing,
            List<BlockPos> chain, boolean sticky)
    {
        long key = level.dimension().identifier().hashCode() * 0x9E3779B97F4A7C15L ^ pistonPos.asLong();
        if (!MOVING_NOW.add(key))
        {
            return;
        }
        try
        {
            int count = chain.size();
            List<BlockState> states = new ArrayList<>(count);
            List<CompoundTag> savedNbt = new ArrayList<>(count);
            for (BlockPos src : chain)
            {
                BlockState state = level.getBlockState(src);
                states.add(state);
                CompoundTag tag = null;
                if (state.hasBlockEntity())
                {
                    BlockEntity blockEntity = level.getBlockEntity(src);
                    if (blockEntity != null)
                    {
                        tag = blockEntity.saveWithFullMetadata(level.registryAccess());
                    }
                }
                savedNbt.add(tag);
            }

            // Move far to near, exactly like vanilla moveBlocks: the destination of block i is
            // block i+1, which has already been cleared when i is processed.
            for (int i = count - 1; i >= 0; i--)
            {
                BlockPos src = chain.get(i);
                BlockPos dest = src.relative(facing);
                BlockState state = states.get(i);
                CompoundTag tag = savedNbt.get(i);
                if (tag != null)
                {
                    level.removeBlockEntity(src);
                }
                level.setBlock(src, Blocks.AIR.defaultBlockState(), FLAG_SRC_AIR);
                level.setBlock(dest, state, FLAG_DEST);
                if (tag != null)
                {
                    try
                    {
                        BlockEntity loaded = BlockEntity.loadStatic(dest, state, tag, level.registryAccess());
                        loaded.setChanged();
                        level.setBlockEntity(loaded);
                    }
                    catch (Throwable t)
                    {
                        // leave the freshly-created empty block entity; round-trip failed
                    }
                }
            }

            BlockState pistonState = level.getBlockState(pistonPos);
            if (pistonState.is(Blocks.PISTON) || pistonState.is(Blocks.STICKY_PISTON))
            {
                level.setBlock(pistonPos, pistonState.setValue(PistonBaseBlock.EXTENDED, true), FLAG_PISTON);
            }
            BlockState head = Blocks.PISTON_HEAD.defaultBlockState()
                    .setValue(DirectionalBlock.FACING, facing)
                    .setValue(PistonHeadBlock.TYPE, sticky ? PistonType.STICKY : PistonType.DEFAULT)
                    .setValue(PistonHeadBlock.SHORT, false);
            level.setBlock(pistonPos.relative(facing), head, FLAG_PISTON);

            Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, facing, null);
            for (int i = count - 1; i >= 0; i--)
            {
                BlockPos dest = chain.get(i).relative(facing);
                level.updateNeighborsAt(dest, states.get(i).getBlock(), orientation);
            }
            level.getChunkAt(pistonPos).markUnsaved();
        }
        catch (Throwable t)
        {
            // cross-region write or other transient failure: the move is partially applied,
            // the next sweep will re-resolve from the actual world state
        }
        finally
        {
            MOVING_NOW.remove(key);
        }
    }
}