/*
 * Copyright (C)
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General
 * Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package me.electrohedric.blocks;

import net.minecraft.block.*;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.*;

public class RedstoneBusBlock extends AbstractRedstoneGateBlock {
    private static final VoxelShape SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 0.25, 16.0);
    public static int MAX_BUS_CHAIN = 512; // any longer and the chunks literally won't load

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
            // do a chain of updates until we hit something we don't know what to do with,
            // then let minecraft figure it out
            Direction myDirection = state.get(FACING);
            Direction pointing = myDirection.getOpposite();
            BlockPos curPos = pos;
            int i = MAX_BUS_CHAIN;
            while (i-- > 0) {
                BlockPos lastPos = curPos;
                curPos = curPos.offset(pointing);
                BlockState nextBlock = world.getBlockState(curPos);
                // can we skip a neighbor update?
                if (nextBlock.isOf(this) && nextBlock.get(FACING) == myDirection) {
                    world.setBlockState(lastPos, state.with(POWERED, !powered), Block.NOTIFY_LISTENERS);
                } else {
                    world.setBlockState(lastPos, state.with(POWERED, !powered), Block.NOTIFY_ALL);
                    break;
                }
            }
        }
    }

    protected void updateTarget(World world, BlockPos pos, BlockState state) {
        Direction direction = state.get(FACING);
        BlockPos blockPos = pos.offset(direction.getOpposite());
        world.updateNeighborsExcept(blockPos, this, direction);
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
