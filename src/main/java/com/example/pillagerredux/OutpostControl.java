package com.example.pillagerredux;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
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

public class OutpostControl {

    private static StructureStart getOutpost(ServerLevel level, BlockPos pos) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Structure outpost = registry.get(BuiltinStructures.PILLAGER_OUTPOST);

        if (outpost == null) return null;
        return level.structureManager().getStructureAt(pos, outpost);
    }

    public static boolean isInsideOutpost(ServerLevel level, BlockPos pos) {
        StructureStart structureStart = getOutpost(level, pos);
        if (structureStart == null || !structureStart.isValid()) {
            return false;
        }
        return level.structureManager()
                .getStructureWithPieceAt(pos, structureStart.getStructure())
                .isValid();
    }

    public static boolean isInsideOutpostArea(ServerLevel level, BlockPos pos) {
        StructureStart structureStart = getOutpost(level, pos);

        if (structureStart == null || !structureStart.isValid()) return false;

        BoundingBox box = structureStart.getBoundingBox();
        BlockPos center = box.getCenter();

        int dx = Math.abs(pos.getX() - center.getX());
        int dz = Math.abs(pos.getZ() - center.getZ());
        int dy = Math.abs(pos.getY() - center.getY());

        return dx <= 72 && dz <= 72 && dy <= 56;
    }

    public static ChunkPos getOutpostChunk(ServerLevel level, BlockPos pos) {
        StructureStart structureStart = getOutpost(level, pos);

        if (structureStart == null || !structureStart.isValid()) return null;

        return structureStart.getChunkPos();
    }

    public static boolean isOutpostBurning(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof FireBlock) {
            return OutpostControl.isInsideOutpost(level, pos);
        }
        return false;
    }

    public static void markOutpostBurnt(ServerLevel level, ChunkPos pos) {
        if (pos == null) return;
        BurntOutpostSavedData data = BurntOutpostSavedData.get(level);
        String id = getOutpostUniqueID(pos);
        data.markBurnt(id);
    }

    public static boolean isOutpustBurnt(ServerLevel level, ChunkPos pos) {
        if (pos == null) return false;
        BurntOutpostSavedData data = BurntOutpostSavedData.get(level);
        String id = getOutpostUniqueID(pos);
        return data.isBurnt(id);
    }

    public static String getOutpostUniqueID(ChunkPos pos) {
        return ("X" + pos.x + "Z" + pos.z + "OUTPOST");
    }
}