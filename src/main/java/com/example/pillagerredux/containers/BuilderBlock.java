package com.example.pillagerredux.containers;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class BuilderBlock {
    final String name;

    final Block block;

    final BlockPos blockPos;
    final BlockState blockState;

    public BuilderBlock(Block block, BlockPos blockPos, BlockState blockState) {
        if (blockPos == null || blockState == null || block == null)
            throw new IllegalArgumentException("BuilderBlock args must be non-null");

        this.name = block.getName().getString();
        this.block = block;
        this.blockPos = blockPos.immutable();
        this.blockState = blockState;
    }

    public String getName() {
        return name;
    }

    public Block getBlock() {
        return block;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public BlockState getBlockState() {
        return blockState;
    }
}