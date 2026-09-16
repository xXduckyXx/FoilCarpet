package carpet.fakes;

import carpet.mixins.PistonStructureResolver_customStickyMixin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;

public interface BlockPistonBehaviourInterface {

    boolean isSticky(BlockState state);

    boolean isStickyToNeighbor(Level level, BlockPos pos, BlockState state, BlockPos neighborPos, BlockState neighborState, Direction dir, Direction moveDir);
}
