package carpet.folia;

import carpet.CarpetServer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.flag.FeatureFlags;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.event.player.PlayerFailMoveEvent;

import java.util.concurrent.atomic.AtomicReference;

public final class CarpetFoliaPlugin extends JavaPlugin implements Listener {

    private static CarpetFoliaPlugin instance;

    private static volatile int tickCounter;

    public static int getTick()
    {
        return tickCounter;
    }

    private final AtomicReference<ServerPlayer> lastQuitPlayer = new AtomicReference<>();
    private MinecraftServer server;

    @Override
    public void onLoad() {
        instance = this;
        PlatformCompat.init(this);
        CarpetServer.onGameStarted();
        getLogger().info("Carpet Folia initialized");
    }

    @Override
    public void onEnable() {
        try {
            this.server = ((CraftServer) Bukkit.getServer()).getServer();

            CarpetServer.onServerLoaded(server);
            registerCarpetCommands();

            Bukkit.getPluginManager().registerEvents(this, this);
            ChunkRegistry.register(this);
            ParrotFolia.attach(this);
            TntFolia tntFolia = new TntFolia();
            ParrotFolia parrotFolia = new ParrotFolia();
            ShulkerFolia shulkerFolia = new ShulkerFolia();
            RailFolia railFolia = new RailFolia(this);
            Bukkit.getPluginManager().registerEvents(tntFolia, this);
            Bukkit.getPluginManager().registerEvents(parrotFolia, this);
            Bukkit.getPluginManager().registerEvents(shulkerFolia, this);
            Bukkit.getPluginManager().registerEvents(railFolia, this);

            // worlds are ready at this point: initialize scarpet and remaining state (also covers reloads)
            Bukkit.getGlobalRegionScheduler().run(this, task -> CarpetServer.onServerLoadedWorlds(server));

            // per-tick updates (script server + HUD). One failing app/feature must never
            // kill the per-tick loop, so isolate each tick and keep the scheduler alive.
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, task -> {
                tickCounter++;
                try
                {
                    CarpetServer.tick(server);
                }
                catch (Throwable t)
                {
                    getLogger().severe("Error in Carpet per-tick update: " + t);
                }
                try
                {
                    MixinCompat.player_tickActionPacks(server);
                }
                catch (Throwable t)
                {
                    getLogger().severe("Error in Carpet action pack tick: " + t);
                }
                try
                {
                    MovableBlockEntities.tickScan(this, server);
                }
                catch (Throwable t)
                {
                    getLogger().severe("Error in movable block entity scan: " + t);
                }
                try
                {
                    RailFolia.tick(this, server);
                }
                catch (Throwable t)
                {
                    getLogger().severe("Error in rail power limit scan: " + t);
                }
                try
                {
                    TntFolia.tickMergeScan(this, server);
                }
                catch (Throwable t)
                {
                    getLogger().severe("Error in TNT scan: " + t);
                }
                try
                {
                    TntFolia.tickCleanup();
                }
                catch (Throwable t)
                {
                    getLogger().severe("Error in TNT placement cleanup: " + t);
                }
                try
                {
                    ShulkerFolia.tickScan(this, server);
                }
                catch (Throwable t)
                {
                    getLogger().severe("Error in shulker scan: " + t);
                }
                try
                {
                    ParrotFolia.tick(server);
                }
                catch (Throwable t)
                {
                    getLogger().severe("Error in parrot tick: " + t);
                }
                try
                {
                    ProfileFolia.tick(server);
                }
                catch (Throwable t)
                {
                    getLogger().severe("Error in profile tick: " + t);
                }
            }, 1, 1);

            getLogger().info("Carpet Folia enabled");
        } catch (Throwable t) {
            getLogger().severe("Failed to enable Carpet-Folia: " + t);
            t.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private void registerCarpetCommands() {
        Commands commands = server.getCommands();
        CommandBuildContext context = CommandBuildContext.simple(server.registryAccess(), FeatureFlags.DEFAULT_FLAGS);
        CarpetServer.registerCarpetCommands(commands.getDispatcher(), Commands.CommandSelection.DEDICATED, context);
    }

    @Override
    public void onDisable() {
        try {
            if (server != null) {
                CarpetServer.onServerClosed(null);
            }
            CarpetServer.onServerDoneClosing(server);
        } catch (Throwable t) {
            getLogger().severe("Error during Carpet-Folia disable: " + t);
        }
        getLogger().info("Carpet Folia disabled");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        ServerPlayer player = ((CraftPlayer) event.getPlayer()).getHandle();
        try {
            CarpetServer.onPlayerLoggedIn(player);
        } catch (Throwable t) {
            getLogger().severe("Error in Carpet onPlayerLoggedIn: " + t);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        ServerPlayer player = ((CraftPlayer) event.getPlayer()).getHandle();
        lastQuitPlayer.set(player);
        try {
            CarpetServer.onPlayerLoggedOut(player, Component.literal("player quit"));
        } catch (Throwable t) {
            getLogger().severe("Error in Carpet onPlayerLoggedOut: " + t);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerLoad(ServerLoadEvent event) {
        if (event.getType() == ServerLoadEvent.LoadType.RELOAD) {
            try {
                CarpetServer.onServerLoadedWorlds(server);
                registerCarpetCommands();
            } catch (Throwable t) {
                getLogger().severe("Error in Carpet onServerLoadedWorlds: " + t);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerFailMove(PlayerFailMoveEvent event) {
        // When "creative fly speed" custom speed is applied to a player (e.g. via scarpet
        // `fly_speed`), moving faster than the vanilla server expects trips the anti-cheat
        // and rubber-bands the player back: "moved too quickly" when the server can't keep
        // up with the movement rate, and "moved into unloaded chunk" when the player
        // outruns chunk generation. If a player is flying faster than the vanilla maximum,
        // allow the movement so it doesn't teleport the player back every tick.
        Player player = event.getPlayer();
        try {
            if (player.isFlying()
                    && player.getFlySpeed() > 0.1f
                    && (event.getFailReason() == PlayerFailMoveEvent.FailReason.MOVED_TOO_QUICKLY
                    || event.getFailReason() == PlayerFailMoveEvent.FailReason.MOVED_INTO_UNLOADED_CHUNK))
            {
                event.setAllowed(true);
                event.setLogWarning(false);
            }
        } catch (Throwable ignored) {
        }
    }

    public static CarpetFoliaPlugin get() {
        return instance;
    }
}