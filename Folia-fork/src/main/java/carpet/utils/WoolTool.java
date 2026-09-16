package carpet.utils;

import carpet.CarpetSettings;
import carpet.helpers.HopperCounter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import static java.util.Map.entry;

public class WoolTool
{

    private static final Map<Block, DyeColor> WOOL_BLOCK_TO_DYE = Map.ofEntries(
            entry(Blocks.WHITE_WOOL, DyeColor.WHITE),
            entry(Blocks.ORANGE_WOOL, DyeColor.ORANGE),
            entry(Blocks.MAGENTA_WOOL, DyeColor.MAGENTA),
            entry(Blocks.LIGHT_BLUE_WOOL, DyeColor.LIGHT_BLUE),
            entry(Blocks.YELLOW_WOOL, DyeColor.YELLOW),
            entry(Blocks.LIME_WOOL, DyeColor.LIME),
            entry(Blocks.PINK_WOOL, DyeColor.PINK),
            entry(Blocks.GRAY_WOOL, DyeColor.GRAY),
            entry(Blocks.LIGHT_GRAY_WOOL, DyeColor.LIGHT_GRAY),
            entry(Blocks.CYAN_WOOL, DyeColor.CYAN),
            entry(Blocks.PURPLE_WOOL, DyeColor.PURPLE),
            entry(Blocks.BLUE_WOOL, DyeColor.BLUE),
            entry(Blocks.BROWN_WOOL, DyeColor.BROWN),
            entry(Blocks.GREEN_WOOL, DyeColor.GREEN),
            entry(Blocks.RED_WOOL, DyeColor.RED),
            entry(Blocks.BLACK_WOOL, DyeColor.BLACK)
    );

    public static void carpetPlacedAction(DyeColor color, ServerPlayer placer, BlockPos pos, ServerLevel worldIn)
    {
        if (!CarpetSettings.carpets)
        {
            return;
        }
        switch (color)
        {
            case PINK:
                if (!"false".equals(CarpetSettings.commandSpawn))
                    Messenger.send(placer, SpawnReporter.report(pos, worldIn));

                break;
            case BLACK:
                if (!"false".equals(CarpetSettings.commandSpawn))
                    Messenger.send(placer, SpawnReporter.handleWoolAction(pos, worldIn));
                break;
            case BROWN:
                if (!"false".equals(CarpetSettings.commandDistance))
                {
                    CommandSourceStack source = placer.createCommandSourceStack();
                    if (!DistanceCalculator.hasStartingPoint(source) || placer.isShiftKeyDown()) {
                        DistanceCalculator.setStart(source, Vec3.atLowerCornerOf(pos) );
                    }
                    else {
                        DistanceCalculator.setEnd(source, Vec3.atLowerCornerOf(pos));
                    }
                }
                break;
            case GRAY:
                if (!"false".equals(CarpetSettings.commandInfo))
                    Messenger.send(placer, BlockInfo.blockInfo(pos.below(), worldIn));
                break;
            case GREEN:
                if (CarpetSettings.hopperCounters)
                {
                    DyeColor under = getWoolColorAtPosition(worldIn, pos.below());
                    if (under == null) return;
                    HopperCounter counter = HopperCounter.getCounter(under);
                    Messenger.send(placer, counter.format(worldIn.getServer(), false, false));
                }
                break;
            case RED:
                if (CarpetSettings.hopperCounters)
                {
                    DyeColor under = getWoolColorAtPosition(worldIn, pos.below());
                    if (under == null) return;
                    HopperCounter counter = HopperCounter.getCounter(under);
                    counter.reset(placer.level().getServer());
                    List<Component> res = new ArrayList<>();
                    res.add(Messenger.s(String.format("%s counter reset",under.toString())));
                    Messenger.send(placer, res);
                }
                break;
        }
    }

    @Nullable
    public static DyeColor getWoolColorAtPosition(Level worldIn, BlockPos pos)
    {
        BlockState state = worldIn.getBlockState(pos);
        return WOOL_BLOCK_TO_DYE.get(state.getBlock());
    }
}
