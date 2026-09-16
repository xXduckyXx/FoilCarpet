package carpet.folia;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftItem;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

import carpet.CarpetSettings;

/**
 * Folia-native implementation of Carpet's {@code stackableShulkerBoxes} rule.
 *
 * <p>Carpet achieves this by hooking ItemStack.getMaxStackSize so empty shulker boxes stack.
 * That hook cannot run on stock Folia (the max count lives in the stack's own data components,
 * not in a field that can be patched), so this class enforces the same effect differently: every
 * few ticks it consolidates empty shulker stacks inside each player's inventory, and when an
 * empty shulker is dropped it merges it into nearby identical ones. Non-empty shulker boxes never
 * merge, exactly like Carpet.
 *
 * <p>Known limitation: containers other than the player's own inventory and automatic stacking
 * (hoppers, chests) are not covered.
 */
public final class ShulkerFolia implements Listener
{
    private static final int SCAN_INTERVAL = 40;

    ShulkerFolia()
    {
    }

    public static void tickScan(Plugin plugin, MinecraftServer server)
    {
        if (server == null || CarpetSettings.shulkerBoxStackSize <= 1)
        {
            return;
        }
        if (CarpetFoliaPlugin.getTick() % SCAN_INTERVAL != 0)
        {
            return;
        }
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        for (ServerPlayer player : players)
        {
            if (player.isRemoved())
            {
                continue;
            }
            ServerLevel level = (ServerLevel) player.level();
            World world = level.getWorld();
            if (world == null)
            {
                continue;
            }
            try
            {
                Bukkit.getRegionScheduler().execute(plugin, world,
                        player.getBlockX() >> 4, player.getBlockZ() >> 4,
                        () -> consolidatePlayer(player));
            }
            catch (Throwable ignored)
            {
            }
        }
    }

    private static void consolidatePlayer(ServerPlayer player)
    {
        try
        {
            Inventory inventory = player.getInventory();
            int max = CarpetSettings.shulkerBoxStackSize;
            int size = inventory.getContainerSize();
            for (int i = 0; i < size; i++)
            {
                ItemStack first = inventory.getItem(i);
                if (first.isEmpty() || !isStackableShulker(first) || first.getCount() >= max)
                {
                    continue;
                }
                for (int j = i + 1; j < size; j++)
                {
                    ItemStack second = inventory.getItem(j);
                    if (second.isEmpty() || !isStackableShulker(second))
                    {
                        continue;
                    }
                    if (!ItemStack.isSameItem(first, second))
                    {
                        continue;
                    }
                    int room = max - first.getCount();
                    if (room <= 0)
                    {
                        break;
                    }
                    int taken = Math.min(room, second.getCount());
                    inventory.setItem(i, first.copyWithCount(first.getCount() + taken));
                    int left = second.getCount() - taken;
                    if (left <= 0)
                    {
                        inventory.setItem(j, ItemStack.EMPTY);
                    }
                    else
                    {
                        inventory.setItem(j, second.copyWithCount(left));
                    }
                    first = inventory.getItem(i);
                }
            }
        }
        catch (Throwable ignored)
        {
        }
    }

    private static boolean isStackableShulker(ItemStack stack)
    {
        if (!(stack.getItem() instanceof BlockItem blockItem))
        {
            return false;
        }
        if (!(blockItem.getBlock() instanceof ShulkerBoxBlock))
        {
            return false;
        }
        ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        return !contents.stream().findAny().isPresent();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemSpawn(EntitySpawnEvent event)
    {
        if (CarpetSettings.shulkerBoxStackSize <= 1 || !(event.getEntity() instanceof org.bukkit.entity.Item))
        {
            return;
        }
        try
        {
            ItemEntity spawn = ((CraftItem) event.getEntity()).getHandle();
            ItemStack stack = spawn.getItem();
            if (stack.isEmpty() || !isStackableShulker(stack) || stack.getCount() >= CarpetSettings.shulkerBoxStackSize)
            {
                return;
            }
            for (org.bukkit.entity.Entity nearby : event.getEntity().getNearbyEntities(2.0D, 2.0D, 2.0D))
            {
                if (!(nearby instanceof org.bukkit.entity.Item) || nearby == event.getEntity())
                {
                    continue;
                }
                ItemEntity other = ((CraftItem) nearby).getHandle();
                if (other.isRemoved())
                {
                    continue;
                }
                ItemStack otherStack = other.getItem();
                if (otherStack.isEmpty() || !isStackableShulker(otherStack))
                {
                    continue;
                }
                if (!ItemStack.isSameItem(stack, otherStack))
                {
                    continue;
                }
                int room = CarpetSettings.shulkerBoxStackSize - otherStack.getCount();
                if (room <= 0)
                {
                    continue;
                }
                int taken = Math.min(room, stack.getCount());
                other.setItem(otherStack.copyWithCount(otherStack.getCount() + taken));
                int left = stack.getCount() - taken;
                if (left <= 0)
                {
                    spawn.setItem(ItemStack.EMPTY);
                    spawn.discard();
                }
                else
                {
                    spawn.setItem(stack.copyWithCount(left));
                }
                return;
            }
        }
        catch (Throwable ignored)
        {
        }
    }
}