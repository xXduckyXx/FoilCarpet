package carpet.script.utils;

import carpet.script.external.Vanilla;

import net.minecraft.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;

import org.jspecify.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class WorldTools
{

    public static boolean canHasChunk(ServerLevel world, ChunkPos chpos, @Nullable Map<String, RegionFile> regionCache, boolean deepcheck)
    {
        if (world.getChunk(chpos.x, chpos.z, ChunkStatus.STRUCTURE_STARTS, false) != null)
        {
            return true;
        }
        String currentRegionName = "r." + chpos.getRegionX() + "." + chpos.getRegionZ() + ".mca";
        if (regionCache != null && regionCache.containsKey(currentRegionName))
        {
            RegionFile region = regionCache.get(currentRegionName);
            if (region == null)
            {
                return false;
            }
            return region.hasChunk(chpos);
        }
        Path regionsFolder = Vanilla.MinecraftServer_storageSource(world.getServer()).getDimensionPath(world.dimension()).resolve("region");
        Path regionPath = regionsFolder.resolve(currentRegionName);
        if (!regionPath.toFile().exists())
        {
            if (regionCache != null)
            {
                regionCache.put(currentRegionName, null);
            }
            return false;
        }
        if (!deepcheck)
        {
            return true;
        }
        try
        {
            RegionStorageInfo levelStorageInfo = new RegionStorageInfo(Vanilla.MinecraftServer_storageSource(world.getServer()).getLevelId(), world.dimension(), "chunk");
            RegionFile region = new RegionFile(levelStorageInfo, regionPath, regionsFolder, true);
            if (regionCache != null)
            {
                regionCache.put(currentRegionName, region);
            }
            return region.hasChunk(chpos);
        }
        catch (IOException ignored)
        {
        }
        return true;
    }

    public static void forceChunkUpdate(BlockPos pos, ServerLevel world)
    {
        ChunkPos chunkPos = new ChunkPos(pos);
        LevelChunk worldChunk = world.getChunkSource().getChunk(chunkPos.x, chunkPos.z, false);
        if (worldChunk != null)
        {
            List<ServerPlayer> players = world.getChunkSource().chunkMap.getPlayers(chunkPos, false);
            if (!players.isEmpty())
            {
                ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(worldChunk, world.getLightEngine(), null, null);
                players.forEach(p -> p.connection.send(packet));
            }
        }
    }

}
