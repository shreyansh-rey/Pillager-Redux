package com.example.pillagerredux;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = PillagerRedux.MODID)
public class PillagerControl {

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getPlacedBlock();

        if (OutpostControl.isOutpostBurning(level, pos, state)) {
            ChunkPos chunkPos = OutpostControl.getOutpostChunk(level, pos);
            if (chunkPos != null) {
                OutpostControl.markOutpostBurnt(level, chunkPos);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Level current = event.getLevel();
        if (!(current instanceof ServerLevel level)) return;

        Entity mob = event.getEntity();
        BlockPos mobSpawnBlock = mob.blockPosition();

        if (mob.getType() != EntityType.PILLAGER) return;

        if (!OutpostControl.isInsideOutpostArea(level, mobSpawnBlock)) return;

        ChunkPos chunkPos = OutpostControl.getOutpostChunk(level, mobSpawnBlock);
        if (chunkPos == null) return;

        if (!OutpostControl.isOutpustBurnt(level, chunkPos)) return;

        if (!event.isCanceled()) {
            event.setCanceled(true);
        }
    }
}