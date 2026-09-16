package carpet.settings;

import carpet.CarpetServer;
import carpet.utils.CommandHelper;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.flag.FeatureFlags;
import java.util.Collection;
import java.util.List;

@Deprecated(forRemoval = true)
@SuppressWarnings("removal")
public class SettingsManager extends carpet.api.settings.SettingsManager
{

    @Deprecated(forRemoval = true)
    public SettingsManager(String version, String identifier)
    {
        this(version, identifier, identifier);
    }

    @Deprecated(forRemoval = true)
    public SettingsManager(String version, String identifier, String fancyName)
    {
        super(version, identifier, fancyName);
    }

    @Deprecated(forRemoval = true)
    public String getIdentifier() {
        return identifier();
    }

    @Deprecated(forRemoval = true)
    public ParsedRule<?> getRule(String name)
    {
        return getCarpetRule(name) instanceof ParsedRule<?> pr ? pr : null;
    }

    @Deprecated(forRemoval = true)
    public Collection<ParsedRule<?>> getRules()
    {
        return List.of(getCarpetRules().stream().filter(ParsedRule.class::isInstance).map(ParsedRule.class::cast).toArray(ParsedRule[]::new));
    }

    @Deprecated(forRemoval = true)
    public int printAllRulesToLog(String category) {
        return dumpAllRulesToStream(System.out, category);
    }

    @Deprecated(forRemoval = true)
    public void notifyPlayersCommandsChanged()
    {
        CommandHelper.notifyPlayersCommandsChanged(CarpetServer.minecraft_server);
    }

    @Deprecated(forRemoval = true)
    public static boolean canUseCommand(CommandSourceStack source, Object commandLevel)
    {
        return CommandHelper.canUseCommand(source, commandLevel);
    }

    @Deprecated(forRemoval = true)
    public void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        final CommandBuildContext context = CommandBuildContext.simple(RegistryAccess.EMPTY, FeatureFlags.VANILLA_SET);
        registerCommand(dispatcher, context);
    }
}
