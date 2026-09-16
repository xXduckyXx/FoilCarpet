package carpet.commands;

import carpet.CarpetSettings;
import carpet.helpers.HopperCounter;
import carpet.utils.Messenger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;

import static net.minecraft.commands.Commands.literal;

public class CounterCommand
{

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext commandBuildContext)
    {
        LiteralArgumentBuilder<CommandSourceStack> commandBuilder = literal("counter")
                .requires(c -> CarpetSettings.hopperCounters)
                .executes(c -> listAllCounters(c.getSource(), false))
                .then(literal("reset")
                        .executes(c -> resetCounters(c.getSource())));

        for (DyeColor dyeColor : DyeColor.values())
        {
            commandBuilder.then(
                    literal(dyeColor.toString())
                            .executes(c -> displayCounter(c.getSource(), dyeColor, false))
                            .then(literal("reset")
                                    .executes(c -> resetCounter(c.getSource(), dyeColor)))
                            .then(literal("realtime")
                                    .executes(c -> displayCounter(c.getSource(), dyeColor, true)))
                    );
        }
        dispatcher.register(commandBuilder);
    }

    private static int displayCounter(CommandSourceStack source, DyeColor color, boolean realtime)
    {
        HopperCounter counter = HopperCounter.getCounter(color);

        for (Component message: counter.format(source.getServer(), realtime, false))
        {
            source.sendSuccess(() -> message, false);
        }
        return 1;
    }

    private static int resetCounters(CommandSourceStack source)
    {
        HopperCounter.resetAll(source.getServer(), false);
        Messenger.m(source, "w Restarted all counters");
        return 1;
    }

    private static int resetCounter(CommandSourceStack source, DyeColor color)
    {
        HopperCounter.getCounter(color).reset(source.getServer());
        Messenger.m(source, "w Restarted " + color + " counter");
        return 1;
    }

    private static int listAllCounters(CommandSourceStack source, boolean realtime)
    {
        for (Component message: HopperCounter.formatAll(source.getServer(), realtime))
        {
            source.sendSuccess(() -> message, false);
        }
        return 1;
    }
}
