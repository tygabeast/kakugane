package com.tygabeast.kakugane.block;

import com.mojang.serialization.MapCodec;
import com.tygabeast.kakugane.world.LabTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A glass vat with a Homunculus embryo growing inside. Breaking one drops embryos and counts toward clearing its lab
 * (see world/LabTracker); labs past the Dormant stage regrow broken vats. No block entity, no ticking.
 */
public class EmbryoVatBlock extends Block {
    public static final MapCodec<EmbryoVatBlock> CODEC = simpleCodec(EmbryoVatBlock::new);
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

    public EmbryoVatBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /** Called for any removal (player, explosion, piston, command): tell the lab, if this vat belongs to one. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) LabTracker.vatDestroyed(sl, pos);
        super.onRemove(state, level, pos, newState, moving);
    }
}
