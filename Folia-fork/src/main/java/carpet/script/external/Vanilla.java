package carpet.script.external;

import carpet.CarpetSettings;
import carpet.folia.MixinCompat;
import carpet.folia.PlatformCompat;
import carpet.network.ServerNetworkHandler;
import carpet.script.CarpetScriptServer;
import carpet.script.EntityEventsGroup;
import carpet.script.value.MapValue;
import carpet.script.value.StringValue;
import carpet.script.value.Value;
import carpet.utils.CommandHelper;
import carpet.utils.SpawnReporter;
import com.mojang.brigadier.CommandDispatcher;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PotentialCalculator;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public class Vanilla
{
    public static void MinecraftServer_forceTick(MinecraftServer server, BooleanSupplier sup)
    {
        MixinCompat.server_forceTick(server, sup);
    }

    public static void ChunkMap_relightChunk(ChunkMap chunkMap, ChunkPos pos)
    {
        // not supported on Folia (Moonrise chunk system)
    }

    public static Map<String, Integer> ChunkMap_regenerateChunkRegion(ChunkMap chunkMap, List<ChunkPos> requestedChunks)
    {
        return Map.of();
    }

    public static int NaturalSpawner_MAGIC_NUMBER()
    {
        return SpawnReporter.MAGIC_NUMBER;
    }

    public static PotentialCalculator SpawnState_getPotentialCalculator(NaturalSpawner.SpawnState spawnState)
    {
        return MixinCompat.spawnState_getPotentialCalculator(spawnState);
    }

    public static void Objective_setCriterion(Objective objective, ObjectiveCriteria criterion)
    {
        MixinCompat.objective_setCriterion(objective, criterion);
    }

    public static Map<ObjectiveCriteria, List<Objective>> Scoreboard_getObjectivesByCriterion(Scoreboard scoreboard)
    {
        return MixinCompat.scoreboard_getObjectivesByCriterion(scoreboard);
    }

    public static ServerLevelData ServerLevel_getWorldProperties(ServerLevel world)
    {
        return MixinCompat.level_worldProperties(world);
    }

    public static Long2ObjectOpenHashMap<List<Ticket>> ChunkTicketManager_getTicketsByPosition(DistanceManager ticketManager)
    {
        return MixinCompat.ticketManager_getTicketsByPosition(ticketManager);
    }

    public static DensityFunction.Visitor RandomState_getVisitor(RandomState randomState)
    {
        return MixinCompat.randomState_getVisitor(randomState);
    }

    public static CompoundTag BlockInput_getTag(BlockInput blockInput)
    {
        return MixinCompat.blockInput_getTag(blockInput);
    }

    public static CarpetScriptServer MinecraftServer_getScriptServer(MinecraftServer server)
    {
        return MixinCompat.server_getScriptServer(server);
    }

    public static Biome.ClimateSettings Biome_getClimateSettings(Biome biome)
    {
        return MixinCompat.biome_getClimateSettings(biome);
    }

    public static ThreadLocal<Boolean> skipGenerationChecks(ServerLevel level)
    { // not sure does vanilla care at all - needs checking
        return CarpetSettings.skipGenerationChecks;
    }

    public static void sendScarpetShapesDataToPlayer(ServerPlayer player, Tag data)
    { // dont forget to add the packet to vanilla packed handler and call ShapesRenderer.addShape to handle on client
        ServerNetworkHandler.sendCustomCommand(player, "scShapes", data);
    }

    public static PermissionSet MinecraftServer_getRunPermissionLevel(MinecraftServer server)
    {
        return CarpetSettings.runPermissionLevel;
    }

    public static int [] MinecraftServer_getReleaseTarget(MinecraftServer server)
    {
        return CarpetSettings.releaseTarget;
    }

    public static boolean isDevelopmentEnvironment()
    {
        return PlatformCompat.isDevelopmentEnvironment();
    }

    public static MapValue getServerMods(MinecraftServer server)
    {
        Map<Value, Value> ret = new HashMap<>();
        ret.put(new StringValue("minecraft"), new StringValue(PlatformCompat.getMinecraftVersionString()));
        ret.put(new StringValue("carpet"), new StringValue(PlatformCompat.getCarpetVersion()));
        ret.put(new StringValue("folia"), new StringValue(org.bukkit.Bukkit.getBukkitVersion()));
        return MapValue.wrap(ret);
    }

    public static LevelStorageSource.LevelStorageAccess MinecraftServer_storageSource(MinecraftServer server)
    {
        return MixinCompat.server_storageSource(server);
    }

    public static BlockPos ServerPlayerGameMode_getCurrentBlockPosition(ServerPlayerGameMode gameMode)
    {
        return MixinCompat.gameMode_getCurrentBreakingBlock(gameMode);
    }

    public static int ServerPlayerGameMode_getCurrentBlockBreakingProgress(ServerPlayerGameMode gameMode)
    {
        return MixinCompat.gameMode_getCurrentBlockBreakingProgress(gameMode);
    }

    public static void ServerPlayerGameMode_setBlockBreakingProgress(ServerPlayerGameMode gameMode, int progress)
    {
        MixinCompat.gameMode_setBlockBreakingProgress(gameMode, progress);
    }

    public static boolean ServerPlayer_isInvalidEntityObject(ServerPlayer player)
    {
        return MixinCompat.player_isInvalidEntityObject(player);
    }

    public static GoalSelector Mob_getAI(Mob mob, boolean target)
    {
        return MixinCompat.mob_getAI(mob, target);
    }

    public static Map<String, Goal> Mob_getTemporaryTasks(Mob mob)
    {
        return MixinCompat.mob_getTemporaryTasks(mob);
    }

    public static void Mob_setPersistence(Mob mob, boolean what)
    {
        MixinCompat.mob_setPersistence(mob, what);
    }

    public static EntityEventsGroup Entity_getEventContainer(Entity entity)
    {
        return MixinCompat.entity_getEventContainer(entity);
    }

    public static boolean Entity_isPermanentVehicle(Entity entity)
    {
        return MixinCompat.entity_isPermanentVehicle(entity);
    }

    public static void Entity_setPermanentVehicle(Entity entity, boolean permanent)
    {
        MixinCompat.entity_setPermanentVehicle(entity, permanent);
    }

    public static int Entity_getPortalTimer(Entity entity)
    {
        return MixinCompat.entity_getPortalTimer(entity);
    }

    public static void Entity_setPortalTimer(Entity entity, int amount)
    {
        MixinCompat.entity_setPortalTimer(entity, amount);
    }

    public static int Entity_getPublicNetherPortalCooldown(Entity entity)
    {
        return MixinCompat.entity_getPublicNetherPortalCooldown(entity);
    }

    public static void Entity_setPublicNetherPortalCooldown(Entity entity, int what)
    {
        MixinCompat.entity_setPublicNetherPortalCooldown(entity, what);
    }

    public static int ItemEntity_getPickupDelay(ItemEntity entity)
    {
        return MixinCompat.itemEntity_getPickupDelay(entity);
    }

    public static boolean LivingEntity_isJumping(LivingEntity entity)
    {
        return MixinCompat.livingEntity_isJumping(entity);
    }

    public static void LivingEntity_setJumping(LivingEntity entity)
    {
        MixinCompat.livingEntity_setJumping(entity);
    }

    public static Container AbstractHorse_getInventory(AbstractHorse horse)
    {
        return MixinCompat.horse_getInventory(horse);
    }

    public static DataSlot AbstractContainerMenu_getDataSlot(AbstractContainerMenu handler, int index)
    {
        return MixinCompat.containerMenu_getDataSlot(handler, index);
    }

    public static void CommandDispatcher_unregisterCommand(CommandDispatcher<CommandSourceStack> dispatcher, String name)
    {
        MixinCompat.commandDispatcher_unregisterCommand(dispatcher, name);
    }

    public static boolean MinecraftServer_doScriptsAutoload(MinecraftServer server)
    {
        return CarpetSettings.scriptsAutoload;
    }

    public static void MinecraftServer_notifyPlayersCommandsChanged(MinecraftServer server)
    {
        CommandHelper.notifyPlayersCommandsChanged(server);
    }

    public static boolean ScriptServer_scriptOptimizations(MinecraftServer scriptServer)
    {
        return CarpetSettings.scriptsOptimization;
    }

    public static boolean ScriptServer_scriptDebugging(MinecraftServer server)
    {
        return CarpetSettings.scriptsDebugging;
    }

    public static boolean ServerPlayer_canScriptACE(CommandSourceStack player)
    {
        return CommandHelper.canUseCommand(player, CarpetSettings.commandScriptACE);
    }

    public static boolean ServerPlayer_canScriptGeneral(CommandSourceStack player)
    {
        return CommandHelper.canUseCommand(player, CarpetSettings.commandScript);
    }

    public static int PoiRecord_getFreeTickets(PoiRecord record)
    {
        return MixinCompat.poiRecord_getFreeTickets(record);
    }

    public static void PoiRecord_callAcquireTicket(PoiRecord record)
    {
        MixinCompat.poiRecord_callAcquireTicket(record);
    }

    public static double FoodData_getExhaustion(FoodData foodData) {
        return MixinCompat.foodData_getExhaustion(foodData);
    }

    public static void FoodData_setExhaustion(FoodData foodData, float exhaustion) {
        MixinCompat.foodData_setExhaustion(foodData, exhaustion);
    }

    public record BlockPredicatePayload(BlockState state, TagKey<Block> tagKey, Map<Value, Value> properties, CompoundTag tag) {
        public static BlockPredicatePayload of(Predicate<BlockInWorld> blockPredicate)
        {
            return new BlockPredicatePayload(null, null, Map.of(), null);
        }
    }
}