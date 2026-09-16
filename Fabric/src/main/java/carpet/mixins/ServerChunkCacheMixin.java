package carpet.mixins;

import carpet.utils.SpawnReporter;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;

@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin
{
    @Shadow @Final private ServerLevel level;

    @Shadow @Final private DistanceManager distanceManager;

    @Redirect(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/DistanceManager;getNaturalSpawnChunkCount()I"
    ))

    private int setupTracking(DistanceManager chunkTicketManager)
    {
        int j = chunkTicketManager.getNaturalSpawnChunkCount();
        ResourceKey<Level> dim = this.level.dimension();

        SpawnReporter.chunkCounts.put(dim, j);

        if (SpawnReporter.trackingSpawns())
        {

            SpawnReporter.local_spawns = new Object2LongOpenHashMap<>();
            SpawnReporter.first_chunk_marker = new HashSet<>();
            for (MobCategory cat : SpawnReporter.cachedMobCategories())
            {
                Pair<ResourceKey<Level>, MobCategory> key = Pair.of(dim, cat);
                SpawnReporter.overall_spawn_ticks.addTo(key, SpawnReporter.spawn_tries.get(cat));
            }
        }
        return j;
    }

    @Inject(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V", at = @At("RETURN"))
    private void onFinishSpawnWorldCycle(CallbackInfo ci)
    {
        LevelData levelData = this.level.getLevelData();
        boolean boolean_3 = levelData.getGameTime() % 400L == 0L;
        if (SpawnReporter.trackingSpawns() && SpawnReporter.local_spawns != null)
        {
            for (MobCategory cat: SpawnReporter.cachedMobCategories())
            {
                ResourceKey<Level> dim = level.dimension();
                Pair<ResourceKey<Level>, MobCategory> key = Pair.of(dim, cat);
                int spawnTries = SpawnReporter.spawn_tries.get(cat);
                if (!SpawnReporter.local_spawns.containsKey(cat))
                {
                    if (!cat.isPersistent() || boolean_3)
                    {

                        SpawnReporter.spawn_ticks_full.addTo(key, spawnTries);
                    }

                }
                else if (SpawnReporter.local_spawns.getLong(cat) > 0)
                {

                    SpawnReporter.spawn_ticks_succ.addTo(key, spawnTries);
                    SpawnReporter.spawn_ticks_spawns.addTo(key, SpawnReporter.local_spawns.getLong(cat));

                }
                else
                {

                    SpawnReporter.spawn_ticks_fail.addTo(key, spawnTries);
                }
            }
        }
        SpawnReporter.local_spawns = null;
    }

}
