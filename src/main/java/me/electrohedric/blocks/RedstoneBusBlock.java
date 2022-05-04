package me.electrohedric.blocks;

import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;
import net.minecraft.world.BlockView;

public class RedstoneBusBlock extends RedstoneWireBlock {
    
    public RedstoneBusBlock(Settings settings) {
        super(settings);
    }
    
    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView view, BlockPos pos, ShapeContext context) {
        // I don't like redstone being weird shapes
        return VoxelShapes.cuboid(0f, 0f, 0f, 1f, 0.0625f, 1f);
    }
    // FIXME: acts like redstone so far, except when power is removed, it holds it power
}
