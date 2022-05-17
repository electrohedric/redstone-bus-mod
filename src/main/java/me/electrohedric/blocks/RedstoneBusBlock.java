package me.electrohedric.blocks;

import net.minecraft.block.*;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.*;

import java.util.Random;

public class RedstoneBusBlock extends AbstractRedstoneGateBlock {
    private static final VoxelShape SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 0.25, 16.0);

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    public RedstoneBusBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH).with(POWERED, false));
    }

    @Override
    protected void updatePowered(World world, BlockPos pos, BlockState state) {
        boolean powered = state.get(POWERED);
        if (powered != this.hasPower(world, pos, state)) {
            // calls neighbor updates on surrounding wires which eventually calls updatePowered()
            // FIXME: this can result in stackoverflow (which is probably a bad sign and calls for a better design)
            //        I'd like to have this just go down the wire and set all the block states myself
            // sorry, I literally don't know what the max update depth does.
            // I thought I did, but setting it to 0 does like the same thing...
            // but the default (512) definitely breaks at 1000+ blocks
            world.setBlockState(pos, state.with(POWERED, !powered), Block.NOTIFY_ALL, 256);
        }
    }

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        boolean powered = state.get(POWERED);
        updatePowered(world, pos, state);
        if (!powered && !this.hasPower(world, pos, state)) {
            world.createAndScheduleBlockTick(pos, this, 2, TickPriority.VERY_HIGH);
        }
    }

    @Override
    protected int getUpdateDelayInternal(BlockState state) { return 0; }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getPlayerFacing().getOpposite());
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

}
