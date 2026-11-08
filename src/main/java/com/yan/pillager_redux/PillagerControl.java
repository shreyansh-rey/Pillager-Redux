package com.yan.pillager_redux;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.minecraft.world.entity.EntityType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

@Mod.EventBusSubscriber(modid = PillagerRedux.MODID)
public class PillagerControl {
    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event){
        ServerLevel level = (ServerLevel) event.getLevel();
        BlockPos pos = event.getPos();

        BlockState state = event.getPlacedBlock();

        if (OutpostControl.isOutpostBurning(level, pos, state)) {
            ChunkPos chunkPos = OutpostControl.getOutpostChunk(level, pos);
            OutpostControl.markOutpostBurnt(level, chunkPos);
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

        if (event.isCancelable())
            event.cancel();
    }
}