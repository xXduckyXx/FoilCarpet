package carpet.folia;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.RootCommandNode;

import carpet.script.CarpetScriptServer;
import carpet.script.EntityEventsGroup;
import carpet.helpers.EntityPlayerActionPack;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PotentialCalculator;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * Drop-in replacement for the mixin-provided interfaces on platforms (stock Folia / Paper)
 * where Sponge Mixin cannot apply. Each method either leverages the public NMS API, uses
 * reflection, or falls back to a no-op / default value. Any behaviour that fundamentally
 * required a mixin is degraded gracefully instead of throwing {@link ClassCastException}.
 */
public final class MixinCompat
{
    private static final Map<MinecraftServer, CarpetScriptServer> SCRIPT_SERVERS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Entity, EntityEventsGroup> ENTITY_EVENT_CONTAINERS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Entity, Boolean> PERMANENT_VEHICLES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Entity, Integer> PUBLIC_NETHER_PORTAL_COOLDOWNS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Entity, Boolean> INVALID_ENTITY_REFERENCES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ServerPlayer, EntityPlayerActionPack> PLAYER_ACTION_PACKS = Collections.synchronizedMap(new WeakHashMap<>());

    private MixinCompat()
    {
    }

    // ------------------------------------------------------------------
    // MinecraftServer
    // ------------------------------------------------------------------

    public static void server_forceTick(MinecraftServer server, BooleanSupplier sup)
    {
        // Folia regional scheduler does not expose a force-tick; just poll the predicate
        sup.getAsBoolean();
    }

    public static int server_getTickCount(MinecraftServer server)
    {
        // Folia disables MinecraftServer.getTickCount (throws), so surface the plugin's own
        // per-tick counter instead so features relying on getTickCount keep working.
        return CarpetFoliaPlugin.getTick();
    }

    public static LevelStorageSource.LevelStorageAccess server_storageSource(MinecraftServer server)
    {
        return server.storageSource;
    }

    public static Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, ServerLevel> server_worlds(MinecraftServer server)
    {
        Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, ServerLevel> ret = new HashMap<>();
        if (server != null)
        {
            server.getAllLevels().forEach(w -> ret.put(w.dimension(), w));
        }
        return ret;
    }

    public static void server_addScriptServer(MinecraftServer server, CarpetScriptServer scriptServer)
    {
        SCRIPT_SERVERS.put(server, scriptServer);
    }

    public static CarpetScriptServer server_getScriptServer(MinecraftServer server)
    {
        return SCRIPT_SERVERS.get(server);
    }

    // ------------------------------------------------------------------
    // ServerLevel / ServerPlayerGameMode
    // ------------------------------------------------------------------

    public static ServerLevelData level_worldProperties(ServerLevel world)
    {
        if (world.getLevelData() instanceof ServerLevelData serverLevelData)
        {
            return serverLevelData;
        }
        return null;
    }

    public static BlockPos gameMode_getCurrentBreakingBlock(ServerPlayerGameMode gameMode)
    {
        if (!readBoolean(gameMode, "isDestroyingBlock"))
        {
            return null;
        }
        return readObject(gameMode, "destroyPos");
    }

    public static int gameMode_getCurrentBlockBreakingProgress(ServerPlayerGameMode gameMode)
    {
        if (!readBoolean(gameMode, "isDestroyingBlock"))
        {
            return -1;
        }
        return readInt(gameMode, "lastSentState");
    }

    public static void gameMode_setBlockBreakingProgress(ServerPlayerGameMode gameMode, int progress)
    {
        int clamped = Mth.clamp(progress, -1, 10);
        writeInt(gameMode, "lastSentState", clamped);
        BlockPos destroyPos = readObject(gameMode, "destroyPos");
        ServerLevel level = readObject(gameMode, "level");
        ServerPlayer player = readObject(gameMode, "player");
        if (level != null && destroyPos != null && player != null)
        {
            level.destroyBlockProgress(-player.getId(), destroyPos, clamped);
        }
    }

    public static boolean player_isInvalidEntityObject(ServerPlayer player)
    {
        return Boolean.TRUE.equals(INVALID_ENTITY_REFERENCES.get(player));
    }

    public static void player_invalidateEntityObjectReference(Entity player)
    {
        INVALID_ENTITY_REFERENCES.put(player, Boolean.TRUE);
    }

    public static EntityPlayerActionPack player_getActionPack(ServerPlayer player)
    {
        if (player == null)
        {
            return null;
        }
        return PLAYER_ACTION_PACKS.computeIfAbsent(player, EntityPlayerActionPack::new);
    }

    public static void player_tickActionPacks(MinecraftServer server)
    {
        if (server == null || server.getPlayerList() == null)
        {
            return;
        }
        org.bukkit.plugin.Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("Carpet-Folia");
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            EntityPlayerActionPack actionPack = PLAYER_ACTION_PACKS.get(player);
            boolean hasActions = actionPack != null;
            boolean noClip = carpet.CarpetSettings.creativeNoClip
                    && player.isCreative()
                    && player.getAbilities().flying;
            if (hasActions || noClip)
            {
                // Action packs execute level queries (getEntities, ray tracing), and setting
                // noPhysics touches the player's movement state, both of which on Folia must
                // run on the player's own region thread, not the global plugin ticker.
                try
                {
                    org.bukkit.World bukkitWorld = player.level().getWorld();
                    net.minecraft.world.level.ChunkPos cpos = new net.minecraft.world.level.ChunkPos(
                            net.minecraft.core.BlockPos.containing(player.position()));
                    final boolean fNoClip = noClip;
                    org.bukkit.Bukkit.getRegionScheduler().execute(plugin, bukkitWorld, cpos.x, cpos.z, () -> {
                        if (fNoClip)
                        {
                            // Replicates Player_creativeNoClipMixin's tick redirect: a creative
                            // flying player gets noPhysics so they pass through blocks.
                            player.noPhysics = true;
                        }
                        if (actionPack != null)
                        {
                            actionPack.onUpdate();
                        }
                    });
                }
                catch (Throwable ignored)
                {
                    // player may be between regions / level being unloaded
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Mob / Entity / LivingEntity / ItemEntity / FoodData
    // ------------------------------------------------------------------

    public static GoalSelector mob_getAI(Mob mob, boolean target)
    {
        return target ? mob.targetSelector : mob.goalSelector;
    }

    public static Map<String, Goal> mob_getTemporaryTasks(Mob mob)
    {
        Object tasks = readObject(mob, "temporaryTasks");
        if (tasks instanceof Map<?, ?> map)
        {
            return (Map<String, Goal>) map;
        }
        return Map.of();
    }

    public static void mob_setPersistence(Mob mob, boolean what)
    {
        mob.setPersistenceRequired(what);
    }

    public static EntityEventsGroup entity_getEventContainer(Entity entity)
    {
        return ENTITY_EVENT_CONTAINERS.computeIfAbsent(entity, e -> new EntityEventsGroup(e));
    }

    public static boolean entity_isPermanentVehicle(Entity entity)
    {
        return Boolean.TRUE.equals(PERMANENT_VEHICLES.get(entity));
    }

    public static void entity_setPermanentVehicle(Entity entity, boolean permanent)
    {
        PERMANENT_VEHICLES.put(entity, permanent);
    }

    public static int entity_getPortalTimer(Entity entity)
    {
        return entity.getPortalCooldown();
    }

    public static void entity_setPortalTimer(Entity entity, int amount)
    {
        entity.setPortalCooldown(amount);
    }

    public static int entity_getPublicNetherPortalCooldown(Entity entity)
    {
        return PUBLIC_NETHER_PORTAL_COOLDOWNS.getOrDefault(entity, 0);
    }

    public static void entity_setPublicNetherPortalCooldown(Entity entity, int what)
    {
        PUBLIC_NETHER_PORTAL_COOLDOWNS.put(entity, what);
    }

    public static int itemEntity_getPickupDelay(ItemEntity entity)
    {
        return entity.pickupDelay;
    }

    public static boolean livingEntity_isJumping(LivingEntity entity)
    {
        return entity.isJumping();
    }

    public static void livingEntity_setJumping(LivingEntity entity)
    {
        entity.setJumping(true);
    }

    public static Container horse_getInventory(AbstractHorse horse)
    {
        return horse.inventory;
    }

    public static double foodData_getExhaustion(FoodData foodData)
    {
        try
        {
            Field f = FoodData.class.getDeclaredField("exhaustionLevel");
            f.setAccessible(true);
            return f.getFloat(foodData);
        }
        catch (Exception e)
        {
            return 0.0;
        }
    }

    public static void foodData_setExhaustion(FoodData foodData, float exhaustion)
    {
        try
        {
            Field f = FoodData.class.getDeclaredField("exhaustionLevel");
            f.setAccessible(true);
            f.setFloat(foodData, exhaustion);
        }
        catch (Exception ignored)
        {
        }
    }

    // ------------------------------------------------------------------
    // World / spawn / scores
    // ------------------------------------------------------------------

    public static PotentialCalculator spawnState_getPotentialCalculator(NaturalSpawner.SpawnState spawnState)
    {
        return readObject(spawnState, "spawnPotential");
    }

    public static void objective_setCriterion(Objective objective, ObjectiveCriteria criterion)
    {
        try
        {
            Field f = Objective.class.getDeclaredField("criteria");
            f.setAccessible(true);
            f.set(objective, criterion);
        }
        catch (Exception ignored)
        {
        }
    }

    public static Map<ObjectiveCriteria, List<Objective>> scoreboard_getObjectivesByCriterion(Scoreboard scoreboard)
    {
        Object map = readObject(scoreboard, "objectivesByCriteria");
        if (map instanceof Map<?, ?> m)
        {
            return (Map<ObjectiveCriteria, List<Objective>>) m;
        }
        return Map.of();
    }

    public static Long2ObjectOpenHashMap<List<net.minecraft.server.level.Ticket>> ticketManager_getTicketsByPosition(DistanceManager ticketManager)
    {
        return new Long2ObjectOpenHashMap<>();
    }

    public static DensityFunction.Visitor randomState_getVisitor(RandomState randomState)
    {
        return null;
    }

    public static CompoundTag blockInput_getTag(BlockInput blockInput)
    {
        return blockInput.tag;
    }

    public static Biome.ClimateSettings biome_getClimateSettings(Biome biome)
    {
        return biome.climateSettings;
    }

    public static int poiRecord_getFreeTickets(Object poiRecord)
    {
        try
        {
            net.minecraft.world.entity.ai.village.poi.PoiRecord record = (net.minecraft.world.entity.ai.village.poi.PoiRecord) poiRecord;
            return record.getFreeTickets();
        }
        catch (Exception e)
        {
            return 0;
        }
    }

    public static void poiRecord_callAcquireTicket(Object poiRecord)
    {
        try
        {
            Method m = poiRecord.getClass().getDeclaredMethod("acquireTicket");
            m.setAccessible(true);
            m.invoke(poiRecord);
        }
        catch (Exception ignored)
        {
        }
    }

    // ------------------------------------------------------------------
    // Containers / commands / unregister
    // ------------------------------------------------------------------

    public static DataSlot containerMenu_getDataSlot(AbstractContainerMenu handler, int index)
    {
        List<DataSlot> dataSlots = handler.dataSlots;
        if (index >= 0 && index < dataSlots.size())
        {
            return dataSlots.get(index);
        }
        return null;
    }

    public static void commandDispatcher_unregisterCommand(CommandDispatcher<CommandSourceStack> dispatcher, String name)
    {
        Object root = null;
        try
        {
            Field f = CommandDispatcher.class.getDeclaredField("root");
            f.setAccessible(true);
            root = f.get(dispatcher);
        }
        catch (Exception ignored)
        {
            return;
        }
        if (root instanceof RootCommandNode<?> rootNode)
        {
            try
            {
                Field children = RootCommandNode.class.getSuperclass().getDeclaredField("children");
                children.setAccessible(true);
                Object m = children.get(rootNode);
                if (m instanceof Map<?, ?> map)
                {
                    ((Map<Object, Object>) map).remove(name);
                }
            }
            catch (Exception ignored)
            {
            }
        }
    }


    // ------------------------------------------------------------------
    // reflection helpers
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static <T> T readObject(Object target, String fieldName)
    {
        try
        {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return (T) f.get(target);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static boolean readBoolean(Object target, String fieldName)
    {
        Object value = readObject(target, fieldName);
        return value instanceof Boolean b && b;
    }

    private static int readInt(Object target, String fieldName)
    {
        Object value = readObject(target, fieldName);
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static void writeInt(Object target, String fieldName, int value)
    {
        try
        {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.setInt(target, value);
        }
        catch (Exception ignored)
        {
        }
    }
}