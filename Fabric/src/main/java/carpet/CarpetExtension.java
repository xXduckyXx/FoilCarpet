package carpet;

import carpet.script.CarpetExpression;
import carpet.api.settings.SettingsManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.Map;

public interface CarpetExtension
{

    default void onGameStarted() {}

    default void onServerLoaded(MinecraftServer server) {}

    default void onServerLoadedWorlds(MinecraftServer server) {}

    default void onTick(MinecraftServer server) {}

    @Deprecated(forRemoval = true)
    default void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {}

    default void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, final CommandBuildContext commandBuildContext) {
        registerCommands(dispatcher);
    }

    default SettingsManager extensionSettingsManager() {
        return null;
    }

    default void onPlayerLoggedIn(ServerPlayer player) {}

    default void onPlayerLoggedOut(ServerPlayer player) {}

    default void onServerClosed(MinecraftServer server) {}

    default void onReload(MinecraftServer server) {}

    default String version() {return null;}

    default void registerLoggers() {}

    default Map<String, String> canHasTranslations(String lang) { return Collections.emptyMap();}

    default void scarpetApi(CarpetExpression expression) {}

}
