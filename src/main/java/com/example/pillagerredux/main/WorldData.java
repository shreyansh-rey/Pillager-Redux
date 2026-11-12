package com.example.pillagerredux.main;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WorldData {

    public static class World{
        public static ChunkPos getChunkPosFromBlockPos(BlockPos blockPos) {
            return new ChunkPos(blockPos.getX() >> 4, blockPos.getZ() >> 4);
        }
    }

    public static class Outpost{
        public static @NotNull StructureStart getOutpostAt(@NotNull ServerLevel world, BlockPos pos) {
            Registry<Structure> registry = world.registryAccess().registryOrThrow(Registries.STRUCTURE);
            Structure outpost = registry.get(BuiltinStructures.PILLAGER_OUTPOST);

            if (outpost == null) return StructureStart.INVALID_START;

            return world.structureManager().getStructureAt(pos, outpost);
        }

        public static boolean isInsideOutpostChunk(@NotNull ServerLevel world, BlockPos pos) {
            StructureStart outpost = getOutpostAt(world, pos);

            if (!outpost.isValid()) return false;

            return world.structureManager().getStructureWithPieceAt(pos, outpost.getStructure()).isValid();
        }

        public static boolean isInsideOutpostTower(@NotNull ServerLevel world, BlockPos pos) {
            StructureStart outpost = getOutpostAt(world, pos);

            if (!outpost.isValid()) return false;

            BoundingBox box = outpost.getBoundingBox();
            BlockPos center = box.getCenter();

            int dx = Math.abs(pos.getX() - center.getX());
            int dz = Math.abs(pos.getZ() - center.getZ());
            int dy = Math.abs(pos.getY() - center.getY());

            return dx <= 72 && dz <= 72 && dy <= 56;
        }

        public static @Nullable ChunkPos getOutpostChunk(@NotNull ServerLevel world, BlockPos pos) {
            StructureStart outpost = getOutpostAt(world, pos);

            if (!outpost.isValid()) return null;

            return outpost.getChunkPos();
        }

        public static boolean isOutpostBurning(@NotNull ServerLevel world, BlockPos pos, @NotNull BlockState state) {
            if (state.getBlock() instanceof FireBlock){
                return isInsideOutpostTower(world, pos);
            }

            return false;
        }

        public static boolean isOutpostBurnt(@NotNull ServerLevel level, ChunkPos pos) {
            if (pos == null) return false;
            SavedData data = SavedData.get(level);
            String id = getOutpostUniqueID(pos);
            return data.isBurnt(id);
        }

        public static @NotNull String getOutpostUniqueID(@NotNull ChunkPos pos) {
            return ("X" + pos.x + "Z" + pos.z + "OUTPOST");
        }
    }
}