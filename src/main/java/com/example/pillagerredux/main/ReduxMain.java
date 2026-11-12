package com.example.pillagerredux.main;

import com.example.pillagerredux.containers.Outpost;
import com.example.pillagerredux.devtools.Debug;
import com.example.pillagerredux.events.DayChangeEvent;
import com.example.pillagerredux.setup.PillagerRedux;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.UUID;

@EventBusSubscriber(modid= PillagerRedux.MODID)
public class ReduxMain {
    private static final double pillagerWaveSpawnRadius = 64.0;
    private static final int waveCheckIntervalTicks = 20;

    private static final double rebuildTriggerRadius = 128.0;

    private static final double captainSuccessionMinStrength = 0.4;

    private static final float captainSuccessionChance = 0.5F;

    private static final long waveExpireDays = 2L;

    private static String nameOf(Player p) {
        return p.getName().getString();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        Achievements.verify(event.getServer());
        Mod.restoreScheduledOutposts(event.getServer().overworld());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        Debug.Log("Server stopped, clearing outpost state");
        WaveBar.clearAll();
        Mod.clearScheduledOutposts();
    }

    @SubscribeEvent
    public static void OnBlockPlaced(@NotNull EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getPlacedBlock();

        if (WorldData.Outpost.isOutpostBurning(level, pos, state)) {
            ChunkPos outpostChunk = WorldData.Outpost.getOutpostChunk(level, pos);

            if (outpostChunk == null) return;
            awardBeGone(level, event, pos);

            if (WorldData.Outpost.isOutpostBurnt(level, outpostChunk)) return;

            Debug.Log("Outpost set on fire at " + pos.toShortString() + " chunk " + outpostChunk);

            StructureStart outpost = WorldData.Outpost.getOutpostAt(level, pos);

            Mod.markOutpostBurnt(level, outpostChunk);
            Mod.scheduleOutpostForConstruction(level, outpost);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        Entity source = event.getExplosion().getDirectSourceEntity();
        if (!(source instanceof PrimedTnt tnt)) return;

        BlockPos pos = tnt.blockPosition();
        if (!WorldData.Outpost.isInsideOutpostTower(level, pos)) return;

        ChunkPos outpostChunk = WorldData.Outpost.getOutpostChunk(level, pos);
        if (outpostChunk == null) return;

        Achievements.awardNear(level, pos.getX(), pos.getZ(), 64.0, Achievements.BE_GONE);

        if (WorldData.Outpost.isOutpostBurnt(level, outpostChunk)) return;

        Debug.Log("Outpost blown up by TNT at " + pos.toShortString() + " chunk " + outpostChunk);

        StructureStart outpost = WorldData.Outpost.getOutpostAt(level, pos);

        Mod.markOutpostBurnt(level, outpostChunk);
        Mod.scheduleOutpostForConstruction(level, outpost);
    }

    private static void awardBeGone(ServerLevel level, EntityPlaceEvent event, BlockPos pos) {
        if (event.getEntity() instanceof ServerPlayer placer) {
            Achievements.award(level, placer, Achievements.BE_GONE);
        } else {
            Achievements.awardNear(level, pos.getX(), pos.getZ(), 64.0, Achievements.BE_GONE);
        }
    }

    @SubscribeEvent
    public static void OnEntityJoin(@NotNull EntityJoinLevelEvent event){
        if (!(event.getLevel() instanceof  ServerLevel level)) return;

        Entity mob = event.getEntity();

        if (mob.getType() != EntityType.PILLAGER) return;

        if (Mod.spawningWave) return;

        if (Mod.isTrackedPillager(mob.getUUID())) return;

        BlockPos mobSpawnPos = mob.blockPosition();
        ChunkPos chunkPos = WorldData.Outpost.getOutpostChunk(level, mobSpawnPos);

        if (chunkPos == null) return;

        if (WorldData.Outpost.isOutpostBurnt(level, chunkPos)) {
            if (!event.isCanceled())
                event.setCanceled(true);
            return;
        }

        if (event.loadedFromDisk()) return;

        StructureStart start = WorldData.Outpost.getOutpostAt(level, mobSpawnPos);
        if (!start.isValid()) return;
        if (Mod.countPillagersAt(level, start) < Mod.OUTPOST_MAX_PILLAGERS) return;

        event.setCanceled(true);
        Mod.enqueuePillager(chunkPos, mobSpawnPos);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();

        if (!(dead.level() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;

        if (dead instanceof ServerPlayer player) {
            onPlayerDeath(level, player);
        } else if (dead.getType() == EntityType.PILLAGER) {
            Mod.onRetreaterKilled(level, dead.getUUID());
            Mod.onEscapeeKilled(level, dead.getUUID());
            onPillagerDeath(level, dead);
        }
    }

    private static void onPlayerDeath(ServerLevel level, ServerPlayer player) {
        Debug.Log(nameOf(player) + " died at " + player.blockPosition().toShortString());

        for (Outpost outpost : new ArrayList<>(Mod.getBurntOutposts())) {
            if (outpost.isRebuilding()) continue;

            if (!outpost.isWaveActive()) continue;

            double distSq = horizontalDistSq(player.getX(), player.getZ(), outpost);
            if (distSq > rebuildTriggerRadius * rebuildTriggerRadius) {
                Debug.Log("Player death too far from Outpost " + outpost.getUuid()
                        + " (" + (int) Math.sqrt(distSq) + " blocks), no rebuild");
                continue;
            }

            Mod.sendBuilders(level, outpost,
                    nameOf(player) + " died during active wave ("
                            + outpost.getWavePillagerCount() + " soldiers still alive)");
        }
    }

    private static void onPillagerDeath(ServerLevel level, LivingEntity pillager) {
        UUID id = pillager.getUUID();

        for (Outpost outpost : Mod.getBurntOutposts()) {
            if (!outpost.isWavePillager(id) && !outpost.isBuilder(id)) continue;

            if (outpost.isRebuilding()) {
                if (outpost.isBuilder(id)) {
                    outpost.removeBuilder(id);
                    int builders = outpost.getBuilderCount();

                    Debug.Log("Builder killed at Outpost " + outpost.getUuid() + ", " + builders + " builders left");

                    if (builders == 0) {
                        Mod.haltRebuild(level, outpost, "all builders killed");
                    } else {
                        Mod.save(level, outpost);
                    }
                } else {
                    outpost.removeWavePillager(id);
                    Debug.Log("Soldier killed during rebuild at Outpost " + outpost.getUuid()
                            + ", " + outpost.getWavePillagerCount() + " soldiers left");
                    Mod.save(level, outpost);
                }
                return;
            }

            boolean wasCaptain = outpost.isCaptain(id);
            outpost.removeWavePillager(id);

            int alive = outpost.getWavePillagerCount();
            int total = Math.max(1, Math.max(outpost.getWaveTotal(), alive));

            Debug.Log((wasCaptain ? "Wave CAPTAIN" : "Wave pillager") + " killed at Outpost "
                    + outpost.getUuid() + ", " + alive + "/" + total + " remaining");

            if (alive == 0) {
                Debug.Log("Wave CLEARED at Outpost " + outpost.getUuid());
                Mod.winWave(level, outpost, "all pillagers killed");
                return;
            }

            if (wasCaptain) {
                outpost.setCaptainId(null);

                double strength = (double) alive / total;
                Debug.Log("Captain down. Wave strength " + (int) (strength * 100) + "%");

                if (strength > captainSuccessionMinStrength) {
                    if (level.getRandom().nextFloat() < captainSuccessionChance) {
                        Pillager next = Mod.pickReplacementCaptain(level, outpost);

                        if (next != null) {
                            Mod.promoteCaptain(level, outpost, next);
                            WaveBar.update(level, outpost);
                            Mod.save(level, outpost);
                            return;
                        }

                        Debug.Log("Succession roll won but no loaded pillager available");
                    } else {
                        Debug.Log("Succession roll failed");
                    }
                } else {
                    Debug.Log("Wave too weak for succession");
                }

                Mod.winWave(level, outpost, "captain fell, wave retreats");
                return;
            }

            WaveBar.update(level, outpost);
            Mod.save(level, outpost);
            return;
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;

        long currentDay = level.getDayTime() / 24000L;

        SavedData data = SavedData.get(level);
        long lastDay = data.getLastDay();

        if (lastDay == -1L) {
            lastDay = currentDay;
            data.setLastDay(currentDay);
        }

        Mod.day = lastDay;

        if (currentDay != lastDay) {
            Mod.day = currentDay;
            data.setLastDay(currentDay);
            NeoForge.EVENT_BUS.post(new DayChangeEvent(level, currentDay));
        }

        if (level.getGameTime() % waveCheckIntervalTicks == 0) {
            processWaves(level);
        }

        if (level.getGameTime() % 100L == 0L) {
            Mod.processWaitLists(level);
        }

        processRebuilds(level);
        Mod.tickRetreats(level);
    }

    @SubscribeEvent
    public static void onDayChange(DayChangeEvent event) {
        ServerLevel level = event.getLevel();

        long day = event.getDay();

        for (Outpost outpost : Mod.getBurntOutposts()) {
            if (outpost.isRebuilding()) continue;
            if (outpost.isWaveActive()) continue;
            if (outpost.isWaveDue()) continue;
            if (outpost.isConcluding()) continue;
            if (day - outpost.getConstructionQueueDate() < outpost.getWaveDelayDays()) continue;

            outpost.setWaveDue(true);
            Mod.save(level, outpost);
            Debug.Log("Wave due at Outpost: " + outpost.getUuid());
        }
    }

    private static void processWaves(ServerLevel level) {
        for (Outpost outpost : new ArrayList<>(Mod.getBurntOutposts())) {
            if (outpost.isRebuilding()) continue;

            if (outpost.isWaveActive()) {
                if (Mod.day - outpost.getWaveStartDay() >= waveExpireDays) {
                    Debug.Log("Wave EXPIRED at Outpost " + outpost.getUuid() + " ("
                            + outpost.getWavePillagerCount() + " pillagers unaccounted for)");
                    outpost.endWave();
                    Mod.save(level, outpost);
                }

                WaveBar.update(level, outpost);
                continue;
            }

            if (!outpost.isWaveDue()) continue;
            if (outpost.isConcluding()) continue;

            ChunkPos chunk = outpost.getChunkPos();
            if (!level.hasChunk(chunk.x, chunk.z)) continue;

            Player player = findPlayerInRange(level, outpost);
            if (player == null) continue;

            Debug.Log(nameOf(player) + " in range of Outpost: " + outpost.getUuid());

            Mod.beginWave(level, outpost);
        }
    }

    private static void processRebuilds(ServerLevel level) {
        for (Outpost outpost : new ArrayList<>(Mod.getBurntOutposts())) {
            if (!outpost.isRebuilding()) continue;

            Mod.tickRebuild(level, outpost);
        }
    }

    private static double horizontalDistSq(double x, double z, Outpost outpost) {
        double dx = x - outpost.getCenterX();
        double dz = z - outpost.getCenterZ();
        return dx * dx + dz * dz;
    }

    private static Player findPlayerInRange(ServerLevel level, Outpost outpost) {
        double maxSq = pillagerWaveSpawnRadius * pillagerWaveSpawnRadius;

        for (Player p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;

            if (horizontalDistSq(p.getX(), p.getZ(), outpost) <= maxSq) return p;
        }

        return null;
    }
}