package carpet.helpers;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.CarpetBlockBehaviourAccess;
import carpet.fakes.RedstoneWireBlockInterface;

public class RedstoneWireTurbo
{

    private final RedStoneWireBlock wire;

    private List<UpdateNode> updateQueue0 = new ArrayList<>();
    private List<UpdateNode> updateQueue1 = new ArrayList<>();
    private List<UpdateNode> updateQueue2 = new ArrayList<>();

    public RedstoneWireTurbo(RedStoneWireBlock wire) {
        this.wire = wire;
    }

    public static BlockPos[] computeAllNeighbors(final BlockPos pos) {
        final int x = pos.getX();
        final int y = pos.getY();
        final int z = pos.getZ();
        final BlockPos[] n = new BlockPos[24];

        n[ 0] = new BlockPos(x-1, y  , z  );
        n[ 1] = new BlockPos(x+1, y  , z  );
        n[ 2] = new BlockPos(x  , y-1, z  );
        n[ 3] = new BlockPos(x  , y+1, z  );
        n[ 4] = new BlockPos(x  , y  , z-1);
        n[ 5] = new BlockPos(x  , y  , z+1);

        n[ 6] = new BlockPos(x-2, y  , z  );
        n[ 7] = new BlockPos(x-1, y-1, z  );
        n[ 8] = new BlockPos(x-1, y+1, z  );
        n[ 9] = new BlockPos(x-1, y  , z-1);
        n[10] = new BlockPos(x-1, y  , z+1);
        n[11] = new BlockPos(x+2, y  , z  );
        n[12] = new BlockPos(x+1, y-1, z  );
        n[13] = new BlockPos(x+1, y+1, z  );
        n[14] = new BlockPos(x+1, y  , z-1);
        n[15] = new BlockPos(x+1, y  , z+1);
        n[16] = new BlockPos(x  , y-2, z  );
        n[17] = new BlockPos(x  , y-1, z-1);
        n[18] = new BlockPos(x  , y-1, z+1);
        n[19] = new BlockPos(x  , y+2, z  );
        n[20] = new BlockPos(x  , y+1, z-1);
        n[21] = new BlockPos(x  , y+1, z+1);
        n[22] = new BlockPos(x  , y  , z-2);
        n[23] = new BlockPos(x  , y  , z+2);
        return n;
    }

    private static final boolean[] update_redstone = {
        true, true, false, false, true, true,
        false, true, true, false, false, false,
        true, true, false, false, false, true,
        true, false, true, true, false, false};

    private static final int North = 0;
    private static final int East = 1;
    private static final int South = 2;
    private static final int West = 3;

    private static final char[] dirname = {'N', 'E', 'S', 'W'};

    private static final int[] forward_is_north = {2, 3, 16, 19, 0, 4, 1, 5, 7, 8, 17, 20, 12, 13, 18, 21, 6, 9, 22, 14, 11, 10, 23, 15};
    private static final int[] forward_is_east  = {2, 3, 16, 19, 4, 1, 5, 0, 17, 20, 12, 13, 18, 21, 7, 8, 22, 14, 11, 15, 23, 9, 6, 10};
    private static final int[] forward_is_south = {2, 3, 16, 19, 1, 5, 0, 4, 12, 13, 18, 21, 7, 8, 17, 20, 11, 15, 23, 10, 6, 14, 22, 9};
    private static final int[] forward_is_west =  {2, 3, 16, 19, 5, 0, 4, 1, 18, 21, 7, 8, 17, 20, 12, 13, 23, 10, 6, 9, 22, 15, 11, 14};

    private static final int[][] reordering = {forward_is_north, forward_is_east, forward_is_south, forward_is_west};

    private static void orientNeighbors(final UpdateNode[] src, final UpdateNode[] dst, final int heading) {
        final int[] re = reordering[heading];
        for (int i=0; i<24; i++) {
            dst[i] = src[re[i]];
        }
    }

    private static class UpdateNode {
        public enum Type {
            UNKNOWN, REDSTONE, OTHER
        }

        BlockState currentState;
        UpdateNode[] neighbor_nodes;
        BlockPos self;
        BlockPos parent;
        Type type = Type.UNKNOWN;
        int layer;
        boolean visited;
        int xbias, zbias;
    }

    private final Map<BlockPos, UpdateNode> nodeCache = new HashMap<>();

    private void identifyNode(final Level worldIn, final UpdateNode upd1) {
        final BlockPos pos = upd1.self;
        final BlockState oldState = worldIn.getBlockState(pos);
        upd1.currentState = oldState;

        final Block block = oldState.getBlock();
        if (block != wire) {

            upd1.type = UpdateNode.Type.OTHER;

            return;
        }

        if (!oldState.canSurvive(worldIn, pos)) {

            Block.dropResources(oldState, worldIn, pos);
            worldIn.removeBlock(pos, false);

            upd1.type = UpdateNode.Type.OTHER;

            return;
        }

        upd1.type = UpdateNode.Type.REDSTONE;
    }

    static private int computeHeading(final int rx, final int rz) {

        final int code = (rx + 1) + 3*(rz + 1);
        switch (code) {
            case 0: {

                final int j = ThreadLocalRandom.current().nextInt(0, 1);
                return (j==0) ? North : West;
            }
            case 1: {

                return North;
            }
            case 2: {

                final int j = ThreadLocalRandom.current().nextInt(0, 1);
                return (j==0) ? North : East;
            }
            case 3: {

                return West;
            }
            case 4: {

                return ThreadLocalRandom.current().nextInt(0, 4);
            }
            case 5: {

                return East;
            }
            case 6: {

                final int j = ThreadLocalRandom.current().nextInt(0, 1);
                return (j==0) ? South : West;
            }
            case 7: {

                return South;
            }
            case 8: {

                final int j = ThreadLocalRandom.current().nextInt(0, 1);
                return (j==0) ? South : East;
            }
        }

        return ThreadLocalRandom.current().nextInt(0, 4);
    }

    private static final boolean old_current_change = false;

    private void updateNode(final Level worldIn, final UpdateNode upd1, final int layer) {
        final BlockPos pos = upd1.self;

        upd1.visited = true;

        final BlockState oldState = upd1.currentState;

        BlockState newState;
        if (old_current_change) {
            newState = ((RedstoneWireBlockInterface)wire).updateLogicPublic(worldIn, pos, oldState);
        } else {

            newState = this.calculateCurrentChanges(worldIn, upd1);
        }

        if (newState != oldState) {

            upd1.currentState = newState;

            propagateChanges(worldIn, upd1, layer);
        }
    }

    private void findNeighbors(final Level worldIn, final UpdateNode upd1) {
        final BlockPos pos = upd1.self;

        final BlockPos[] neighbors = computeAllNeighbors(pos);

        final UpdateNode[] neighbor_nodes = new UpdateNode[24];

        upd1.neighbor_nodes = new UpdateNode[24];

        for (int i=0; i<24; i++) {

            final BlockPos pos2 = neighbors[i];
            UpdateNode upd2 = nodeCache.get(pos2);
            if (upd2 == null) {

                upd2 = new UpdateNode();
                upd2.self = pos2;
                upd2.parent = pos;
                nodeCache.put(pos2, upd2);
                identifyNode(worldIn, upd2);
            }

            if (update_redstone[i] || upd2.type != UpdateNode.Type.REDSTONE) {
                neighbor_nodes[i] = upd2;
            }
        }

        final boolean fromWest = (neighbor_nodes[0].visited || neighbor_nodes[7].visited || neighbor_nodes[8].visited);
        final boolean fromEast = (neighbor_nodes[1].visited || neighbor_nodes[12].visited || neighbor_nodes[13].visited);
        final boolean fromNorth = (neighbor_nodes[4].visited || neighbor_nodes[17].visited || neighbor_nodes[20].visited);
        final boolean fromSouth = (neighbor_nodes[5].visited || neighbor_nodes[18].visited || neighbor_nodes[21].visited);

        int cx = 0, cz = 0;
        if (fromWest) cx += 1;
        if (fromEast) cx -= 1;
        if (fromNorth) cz += 1;
        if (fromSouth) cz -= 1;

        int heading;
        if (cx==0 && cz==0) {

            heading = computeHeading(upd1.xbias, upd1.zbias);

            for (int i=0; i<24; i++) {
                final UpdateNode nn = neighbor_nodes[i];
                if (nn != null) {
                    nn.xbias = upd1.xbias;
                    nn.zbias = upd1.zbias;
                }
            }
        } else {
            if (cx != 0 && cz != 0) {

                if (upd1.xbias != 0) cz = 0;
                if (upd1.zbias != 0) cx = 0;
            }
            heading = computeHeading(cx, cz);

            for (int i=0; i<24; i++) {
                final UpdateNode nn = neighbor_nodes[i];
                if (nn != null) {
                    nn.xbias = cx;
                    nn.zbias = cz;
                }
            }
        }

        orientNeighbors(neighbor_nodes, upd1.neighbor_nodes, heading);
    }

    private void propagateChanges(final Level worldIn, final UpdateNode upd1, final int layer) {
        if (upd1.neighbor_nodes == null) {

            findNeighbors(worldIn, upd1);
        }

        final BlockPos pos = upd1.self;
        final int x = pos.getX();
        final int y = pos.getY();
        final int z = pos.getZ();

        final int layer1 = layer + 1;

        for (int i=0; i<24; i++) {
            final UpdateNode upd2 = upd1.neighbor_nodes[i];

            if (upd2 != null && layer1 > upd2.layer) {
                upd2.layer = layer1;
                updateQueue1.add(upd2);

                upd2.parent = pos;
            }
        }

        final int layer2 = layer + 2;

        for (int i=0; i<4; i++) {
            final UpdateNode upd2 = upd1.neighbor_nodes[i];
            if (upd2 != null && layer2 > upd2.layer) {
                upd2.layer = layer2;
                updateQueue2.add(upd2);
                upd2.parent = pos;
            }
        }
    }

    private int currentWalkLayer = 0;

    private void shiftQueue() {
        final List<UpdateNode> t = updateQueue0;
        t.clear();
        updateQueue0 = updateQueue1;
        updateQueue1 = updateQueue2;
        updateQueue2 = t;
    }

    private void breadthFirstWalk(final Level worldIn) {
        shiftQueue();
        currentWalkLayer = 1;

        while (updateQueue0.size()>0 || updateQueue1.size()>0) {

            final List<UpdateNode> thisLayer = updateQueue0;

            for (UpdateNode upd : thisLayer) {
                if (upd.type == UpdateNode.Type.REDSTONE) {

                    updateNode(worldIn, upd, currentWalkLayer);
                } else {

                    worldIn.getBlockState(upd.self).handleNeighborChanged(worldIn, upd.self, wire, null, false);
                }
            }

            shiftQueue();
            currentWalkLayer++;
        }

        currentWalkLayer = 0;
    }

    private BlockState scheduleReentrantNeighborChanged(final Level worldIn, final BlockPos pos, final BlockState newState, final BlockPos source)
    {
        if (source != null) {

            UpdateNode src = nodeCache.get(source);
            if (src == null) {
                src = new UpdateNode();
                src.self = source;
                src.parent = source;
                src.visited = true;
                identifyNode(worldIn, src);
                nodeCache.put(source, src);
            }
        }

        UpdateNode upd = nodeCache.get(pos);
        if (upd == null) {
            upd = new UpdateNode();
            upd.self = pos;
            upd.parent = pos;
            upd.visited = true;
            identifyNode(worldIn, upd);
            nodeCache.put(pos, upd);
        }
        upd.currentState = newState;

        if (upd.neighbor_nodes != null) {
            for (int i=0; i<24; i++) {
                final UpdateNode upd2 = upd.neighbor_nodes[i];
                if (upd2 == null) continue;
                upd2.type = UpdateNode.Type.UNKNOWN;
                upd2.currentState = null;
                identifyNode(worldIn, upd2);
            }
        }

        propagateChanges(worldIn, upd, currentWalkLayer);

        return newState;
    }

    public BlockState updateSurroundingRedstone(final Level worldIn, final BlockPos pos, final BlockState state, final BlockPos source)
    {

        final BlockState newState = ((RedstoneWireBlockInterface)wire).updateLogicPublic(worldIn, pos, state);

        if (newState == state) {
            return state;
        }

        if (currentWalkLayer>0 || nodeCache.size()>0) {

            return scheduleReentrantNeighborChanged(worldIn, pos, newState, source);
        }

        if (source != null) {
            final UpdateNode src = new UpdateNode();
            src.self = source;
            src.parent = source;
            src.visited = true;
            nodeCache.put(source, src);
            identifyNode(worldIn, src);
        }

        final UpdateNode upd = new UpdateNode();
        upd.self = pos;
        upd.parent = source!=null ? source : pos;
        upd.currentState = newState;
        upd.type = UpdateNode.Type.REDSTONE;
        upd.visited = true;
        nodeCache.put(pos, upd);
        propagateChanges(worldIn, upd, 0);

        breadthFirstWalk(worldIn);

        nodeCache.clear();

        return newState;
    }

    private static final int[] rs_neighbors =    {4, 5, 6, 7};
    private static final int[] rs_neighbors_up = {9, 11, 13, 15};
    private static final int[] rs_neighbors_dn = {8, 10, 12, 14};

    private BlockState calculateCurrentChanges(final Level worldIn, final UpdateNode upd)
    {
        BlockState state = upd.currentState;
        final int i = state.getValue(RedStoneWireBlock.POWER);
        int j = 0;
        j = getMaxCurrentStrength(upd, j);
        int l = 0;

        ((RedstoneWireBlockInterface)wire).setWiresGivePower(false);

        final int k = worldIn.getBestNeighborSignal(upd.self);
        ((RedstoneWireBlockInterface)wire).setWiresGivePower(true);

        if (k<15) {
            if (upd.neighbor_nodes == null) {

                findNeighbors(worldIn, upd);
            }

            UpdateNode center_up = upd.neighbor_nodes[1];
            boolean center_up_is_cube = center_up.currentState.isRedstoneConductor(worldIn, center_up.self);

            for (int m=0; m<4; m++) {

                int n = rs_neighbors[m];

                UpdateNode neighbor = upd.neighbor_nodes[n];
                l = getMaxCurrentStrength(neighbor, l);

                boolean neighbor_is_cube = neighbor.currentState.isRedstoneConductor(worldIn, neighbor.self);
                if (!neighbor_is_cube) {
                    UpdateNode neighbor_down = upd.neighbor_nodes[rs_neighbors_dn[m]];
                    l = getMaxCurrentStrength(neighbor_down, l);
                } else
                if (!center_up_is_cube) {
                    UpdateNode neighbor_up = upd.neighbor_nodes[rs_neighbors_up[m]];
                    l = getMaxCurrentStrength(neighbor_up, l);
                }
            }
        }

        j = l-1;

        if (k>j) j=k;

        if (i != j) {

            state = state.setValue(RedStoneWireBlock.POWER, j);

            if (worldIn.getBlockState(upd.self).getBlock() == Blocks.REDSTONE_WIRE)

                if (worldIn.setBlock(upd.self, state, Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS))
                    updateNeighborShapes(worldIn, upd.self, state);
        }

        return state;
    }

    public void updateNeighborShapes(Level level, BlockPos pos, BlockState state) {

        state.updateIndirectNeighbourShapes(level, pos, Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS);

        for (Direction dir : CarpetBlockBehaviourAccess.UPDATE_SHAPE_ORDER) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);

            BlockState newState = neighborState.updateShape(level, level, neighborPos, dir.getOpposite(), pos, state, level.getRandom());
            Block.updateOrDestroy(neighborState, newState, level, neighborPos, Block.UPDATE_CLIENTS);
        }
    }

    private static int getMaxCurrentStrength(final UpdateNode upd, final int strength) {
        if (upd.type != UpdateNode.Type.REDSTONE) return strength;
        final int i = upd.currentState.getValue(RedStoneWireBlock.POWER);
        return i > strength ? i : strength;
    }
}
