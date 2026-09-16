package carpet.folia;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import carpet.CarpetSettings;

public final class ParrotFolia implements Listener
{
    private static final int PRUNE_TICKS = 20;

    private static Plugin plugin;
    private static volatile Map<UUID, CompoundTag[]> shoulderSnapshot = Collections.emptyMap();
    private static final Map<ServerPlayer, DamageInfo> damageTicks = new ConcurrentHashMap<>();

    private static final class DamageInfo
    {
        final long tick;
        final float damage;

        DamageInfo(long tick, float damage)
        {
            this.tick = tick;
            this.damage = damage;
        }
    }

    ParrotFolia()
    {
    }

    public static void attach(Plugin owner)
    {
        plugin = owner;
    }

    public static void tick(MinecraftServer server)
    {
        if (server == null || !CarpetSettings.persistentParrots)
        {
            return;
        }
        Map<UUID, CompoundTag[]> snapshot = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            CompoundTag left = player.getShoulderEntityLeft();
            CompoundTag right = player.getShoulderEntityRight();
            if (!left.isEmpty() || !right.isEmpty())
            {
                snapshot.put(player.getUUID(), new CompoundTag[]
                        { left.isEmpty() ? null : left.copy(), right.isEmpty() ? null : right.copy() });
            }
        }
        shoulderSnapshot = snapshot;
        if (!damageTicks.isEmpty())
        {
            long now = CarpetFoliaPlugin.getTick();
            damageTicks.entrySet().removeIf(e -> now - e.getValue().tick > PRUNE_TICKS);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event)
    {
        if (!(event.getEntity() instanceof Player) || !CarpetSettings.persistentParrots)
        {
            return;
        }
        try
        {
            ServerPlayer player = ((CraftPlayer) event.getEntity()).getHandle();
            damageTicks.put(player, new DamageInfo(Bukkit.getCurrentTick(), (float) event.getFinalDamage()));
        }
        catch (Throwable ignored)
        {
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onParrotSpawn(EntitySpawnEvent event)
    {
        if (!CarpetSettings.persistentParrots || !(event.getEntity() instanceof org.bukkit.entity.Parrot))
        {
            return;
        }
        try
        {
            UUID spawnedUuid = event.getEntity().getUniqueId();

            UUID ownerId = null;
            boolean isLeft = false;
            boolean isRight = false;
            Map<UUID, CompoundTag[]> snapshot = shoulderSnapshot;
            for (Map.Entry<UUID, CompoundTag[]> entry : snapshot.entrySet())
            {
                CompoundTag[] tags = entry.getValue();
                boolean matchLeft = tags[0] != null
                        && spawnedUuid.toString().equals(tags[0].getString("UUID"));
                boolean matchRight = tags[1] != null
                        && spawnedUuid.toString().equals(tags[1].getString("UUID"));
                if (matchLeft || matchRight)
                {
                    ownerId = entry.getKey();
                    isLeft = matchLeft;
                    isRight = matchRight;
                    break;
                }
            }
            if (ownerId == null || plugin == null)
            {
                return;
            }
            Player bukkitPlayer = Bukkit.getPlayer(ownerId);
            if (bukkitPlayer == null || !bukkitPlayer.isOnline())
            {
                return;
            }
            ServerPlayer player = ((CraftPlayer) bukkitPlayer).getHandle();

            DamageInfo dmg = damageTicks.get(player);
            boolean damagePath = dmg != null && dmg.tick == Bukkit.getCurrentTick();

            boolean keep;
            if (damagePath)
            {

                keep = player.isShiftKeyDown() || !(player.getRandom().nextFloat() < dmg.damage / 15.0F);
            }
            else
            {

                boolean carpetRemoves = (player.getAbilities().invulnerable && player.fallDistance > 0.5F)
                        || player.isInWater() || player.getAbilities().flying
                        || player.isSleeping() || player.isInPowderSnow;
                keep = !carpetRemoves;
            }
            if (!keep)
            {
                return;
            }

            event.setCancelled(true);
            CompoundTag[] tags = snapshot.get(ownerId);
            final CompoundTag restoreTag = isLeft ? tags[0] : tags[1];
            scheduleRestore(event, player, isLeft, restoreTag);
        }
        catch (Throwable ignored)
        {
        }
    }

    private static void scheduleRestore(EntitySpawnEvent event, ServerPlayer owner, boolean left, CompoundTag tag)
    {
        if (tag == null || plugin == null)
        {
            return;
        }
        World world = event.getEntity().getWorld();
        if (world == null)
        {
            return;
        }
        int chunkX = event.getEntity().getLocation().getBlockX() >> 4;
        int chunkZ = event.getEntity().getLocation().getBlockZ() >> 4;
        Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, () ->
        {
            try
            {

                if (left)
                {
                    if (owner.getShoulderEntityLeft().isEmpty())
                    {
                        owner.setShoulderEntityLeft(tag);
                    }
                }
                else if (owner.getShoulderEntityRight().isEmpty())
                {
                    owner.setShoulderEntityRight(tag);
                }
            }
            catch (Throwable ignored)
            {
            }
        });
    }
}
