package carpet.script.external;

import carpet.CarpetServer;
import carpet.CarpetSettings;
import carpet.api.settings.CarpetRule;
import carpet.api.settings.RuleHelper;
import carpet.api.settings.SettingsManager;
import carpet.fakes.MinecraftServerInterface;
import carpet.logging.HUDController;
import carpet.network.ServerNetworkHandler;
import carpet.patches.EntityPlayerMPFake;
import carpet.script.CarpetEventServer;
import carpet.script.CarpetExpression;
import carpet.script.CarpetScriptHost;
import carpet.script.CarpetScriptServer;
import carpet.script.Module;
import carpet.script.Module;
import carpet.script.exception.LoadException;
import carpet.script.value.MapValue;
import carpet.script.value.StringValue;
import carpet.utils.CarpetProfiler;
import carpet.utils.Messenger;
import carpet.folia.MixinCompat;
import carpet.folia.PlatformCompat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.jspecify.annotations.Nullable;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class Carpet
{
    public static Map<String, Component> getScarpetHeaders()
    {
        return HUDController.scarpet_headers;
    }

    public static Map<String, Component> getScarpetFooters()
    {
        return HUDController.scarpet_footers;
    }

    public static void updateScarpetHUDs(MinecraftServer server, List<ServerPlayer> players)
    {
        HUDController.update_hud(server, players);
    }

    public static Component Messenger_compose(Object... messages)
    {
        return Messenger.c(messages);
    }

    public static void Messenger_message(CommandSourceStack source, Object... messages)
    {
        Messenger.m(source, messages);
    }

    public static ThreadLocal<Boolean> getImpendingFillSkipUpdates()
    {
        return CarpetSettings.impendingFillSkipUpdates;
    }

    public static Runnable startProfilerSection(String name)
    {
        CarpetProfiler.ProfilerToken token = CarpetProfiler.start_section(null, name, CarpetProfiler.TYPE.GENERAL);
        return () -> CarpetProfiler.end_current_section(token);
    }

    public static void MinecraftServer_addScriptServer(MinecraftServer server, CarpetScriptServer scriptServer)
    {
        MixinCompat.server_addScriptServer(server, scriptServer);
    }

    public static boolean isValidCarpetPlayer(ServerPlayer player)
    {
        return ServerNetworkHandler.isValidCarpetPlayer(player);
    }

    public static String getPlayerStatus(ServerPlayer player)
    {
        return ServerNetworkHandler.getPlayerStatus(player);
    }

    public static MapValue getAllCarpetRules()
    {
        Collection<CarpetRule<?>> rules = CarpetServer.settingsManager.getCarpetRules();
        MapValue carpetRules = new MapValue(Collections.emptyList());
        rules.forEach(rule -> carpetRules.put(new StringValue(rule.name()), new StringValue(RuleHelper.toRuleString(rule.value()))));
        CarpetServer.extensions.forEach(e -> {
            SettingsManager manager = e.extensionSettingsManager();
            if (manager == null)
            {
                return;
            }
            manager.getCarpetRules().forEach(rule -> carpetRules.put(new StringValue(manager.identifier() + ":" + rule.name()), new StringValue(RuleHelper.toRuleString(rule.value()))));
        });
        return carpetRules;
    }

    public static String getCarpetVersion()
    {
        return CarpetSettings.carpetVersion;
    }

    @Nullable
    public static String isModdedPlayer(Player p)
    {
        if (p instanceof final EntityPlayerMPFake fake)
        {
            return fake.isAShadow ? "shadow" : "fake";
        }
        return null;
    }

    public static void handleExtensionsAPI(CarpetExpression expression)
    {
        CarpetServer.extensions.forEach(e -> e.scarpetApi(expression));
    }

    public static boolean getFillUpdates()
    {
        return CarpetSettings.fillUpdates;
    }

    public static boolean isDebugEnabled()
    {
        return CarpetSettings.superSecretSetting;
    }

    @Nullable
    public static Module fetchGlobalModule(String name, boolean allowLibraries) throws IOException
    {
        if (!PlatformCompat.isServer())
        {
            Path globalFolder = PlatformCompat.getConfigDir().resolve("carpet/scripts");
            if (!Files.exists(globalFolder))
            {
                Files.createDirectories(globalFolder);
            }
            try (Stream<Path> folderWalker = Files.walk(globalFolder))
            {
                Optional<Path> scriptPath = folderWalker
                        .filter(script -> script.getFileName().toString().equalsIgnoreCase(name + ".sc") ||
                                (allowLibraries && script.getFileName().toString().equalsIgnoreCase(name + ".scl")))
                        .findFirst();
                if (scriptPath.isPresent())
                {
                    return Module.fromPath(scriptPath.get());
                }
            }
        }
        return null;
    }

    public static void addGlobalModules(final List<String> moduleNames, boolean includeBuiltIns) throws IOException
    {
        if (includeBuiltIns && !PlatformCompat.isServer())
        {
            Path globalScripts = PlatformCompat.getConfigDir().resolve("carpet/scripts");
            if (!Files.exists(globalScripts))
            {
                Files.createDirectories(globalScripts);
            }
            try (Stream<Path> folderWalker = Files.walk(globalScripts, FileVisitOption.FOLLOW_LINKS))
            {
                folderWalker
                        .filter(f -> f.toString().endsWith(".sc"))
                        .forEach(f -> moduleNames.add(f.getFileName().toString().replaceFirst("\\.sc$", "").toLowerCase(Locale.ROOT)));
            }
        }
    }

    public static void assertRequirementMet(CarpetScriptHost host, String requiredModId, String stringPredicate)
    {
        String presentVersion = PlatformCompat.getModVersion(requiredModId);
        if (presentVersion == null)
        {
            throw new LoadException(String.format("%s requires mod '%s' matching '%s', which is missing!", host.getVisualName(), requiredModId, stringPredicate));
        }
        if (!PlatformCompat.meetsVersionPredicate(presentVersion, stringPredicate))
        {
            throw new LoadException(String.format("%s requires mod '%s' version matching '%s', but found '%s'!", host.getVisualName(), requiredModId, stringPredicate, presentVersion));
        }
    }

    public static void initCarpetEvents() {
        CarpetEventServer.Event carpetRuleChanges = new CarpetEventServer.Event("carpet_rule_changes", 2, true)
        {
            @Override
            public void handleAny(final Object... args)
            {
                CarpetRule<?> rule = (CarpetRule<?>) args[0];
                CommandSourceStack source = (CommandSourceStack) args[1];
                String id = rule.settingsManager().identifier();
                String namespace;
                if (!id.equals("carpet"))
                {
                    namespace = id + ":";
                } else
                {
                    namespace = "";
                }
                handler.call(() -> Arrays.asList(
                                new StringValue(namespace + rule.name()),
                                new StringValue(RuleHelper.toRuleString(rule.value()))
                        ), () -> source);
            }
        };
        SettingsManager.registerGlobalRuleObserver((source, changedRule, userInput) -> carpetRuleChanges.handleAny(changedRule, source));
    }
}
