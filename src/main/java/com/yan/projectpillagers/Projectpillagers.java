package com.yan.projectpillagers;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.*;

public class Projectpillagers implements ModInitializer {

    private DestroyedOutpostsData getData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(DestroyedOutpostsData.TYPE);
    }

    @Override
    public void onInitialize() {
        ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, isFromDisk) -> {
            //Reason -> How the mob was spawned
            //Type -> Mob type
            //isFromDisk -> Mob already existed or was it loaded with a new chunk
            if (entity.getType() == EntityType.PILLAGER && reason == EntitySpawnReason.NATURAL) {
                DestroyedOutpostsData data = getData(level);
                for (BoundingBox box : data.destroyedOutposts) {
                    if (box.isInside(entity.getOnPos())) {
                        Debug.Log("Stopping pillager spawn");
                        return false;
                    }
                }

                Debug.Log("Spawning pillager");
            }

            return true;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            ItemStack heldItem = player.getMainHandItem();

            if (heldItem.is(Items.FLINT_AND_STEEL)) {
                BlockPos pos = hitResult.getBlockPos();
                BlockState state = world.getBlockState(pos);

                if (world instanceof ServerLevel serverLevel) {
                    if (CheckOutpostInFire(serverLevel, pos) instanceof StructureStart outpost) {
                        Debug.Log("Outpost on fire");
                        StructurePiece tower = FindPillagerOutpostTower(outpost);
                        if (tower != null) {
                            BoundingBox box = outpost.getBoundingBox();
                            Debug.Log("Found outpost tower: " + box);
                            getData(serverLevel).markDestroyed(box);
                            return InteractionResult.PASS;
                        }
                    }
                }
            }

            return InteractionResult.PASS;
        });
    }

    private StructureStart FindPillagerOutpost(ServerLevel world, BlockPos pos, Holder<Structure> structure) {
        StructureStart start = world.structureManager().getStructureAt(pos, structure.value());

        if (start.isValid())
            return start;

        return null;
    }

    private StructurePiece FindPillagerOutpostTower(StructureStart start){
        StructurePiece towerPiece = null;
        long largestVolume = 0;

        for (StructurePiece piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            long volume = (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
            if (volume > largestVolume) {
                largestVolume = volume;
                towerPiece = piece;
            }
        }

        if (towerPiece != null) {
            return towerPiece;
        }

        return null;
    }

    private StructureStart CheckOutpostInFire(ServerLevel serverLevel, BlockPos pos) {
        Holder<Structure> structure = serverLevel.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE)
                .getOrThrow(BuiltinStructures.PILLAGER_OUTPOST);

        StructureStart outpost = FindPillagerOutpost(serverLevel, pos, structure);
        if (outpost != null)
            if (FindPillagerOutpostTower(outpost) != null)
                return outpost;

        return null;
    }
}