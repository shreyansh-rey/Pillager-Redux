package com.yan.pillager_redux.main;

import com.yan.pillager_redux.containers.BuilderBlock;
import com.yan.pillager_redux.containers.Outpost;
import com.yan.pillager_redux.devtools.Debug;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class Mod {
    static List<Outpost> ScheduledOutposts = new ArrayList<>();
    public static long day = -1L;

    public static final int BUILDER_COUNT = 2;

    public static final int REBUILD_INTERVAL_TICKS = 20;

    private static final double BUILD_REACH = 5.0;

    private static final long BUILD_STUCK_TICKS = 200L;

    private static final double BUILD_STUCK_REACH_HORIZONTAL = 8.0;

    private static final double BUILD_WALK_SPEED = 1.0;

    private static final int REBUILD_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private record Retreat(Vec3 dir, long endTick, String outpostId) {}

    private static class RetreatGroup {
        int active;
        boolean resolved;
    }

    private static final Map<String, RetreatGroup> RETREAT_GROUPS = new HashMap<>();

    private static final Map<UUID, Retreat> RETREATING = new HashMap<>();

    public static final int RETREAT_DURATION_TICKS = 1800;

    public static final double RETREAT_ESCAPE_DISTANCE = 6 * 16.0;

    public static final float RETREAT_REATTACK_CHANCE = 0.5F;

    private static final double RETREAT_MESSAGE_RADIUS = 256.0;

    public static final int WAVE_DELAY_MIN_DAYS = 3;
    public static final int WAVE_DELAY_MAX_DAYS = 4;

    public static final int WAVE_ESCALATION_BONUS = 4;
    public static final int WAVE_ESCALATED_MAX = 15;

    private static final double RETREAT_STEP = 24.0;

    private static final double RETREAT_SPEED = 1.25;

    public static volatile boolean spawningWave = false;

    public static final int WAVE_MIN_SIZE = 3;
    public static final int WAVE_MAX_SIZE = 7;

    public static final int WAVE_SPAWN_CHUNK_DISTANCE = 3;

    private static final int WAVE_SPAWN_ATTEMPTS = 12;

    private static final int WAVE_CLUSTER_RADIUS = 2;

    private static BlockPos findWaveSpawnPoint(ServerLevel world, ChunkPos outpostChunk, RandomSource random) {
        return findSpawnPointAround(world, outpostChunk, random, WAVE_SPAWN_CHUNK_DISTANCE);
    }

    private static BlockPos findSpawnPointAround(ServerLevel world, ChunkPos outpostChunk, RandomSource random,
                                                 int chunkDistance) {
        double dist = chunkDistance * 16.0;
        int cx = outpostChunk.getMiddleBlockX();
        int cz = outpostChunk.getMiddleBlockZ();

        for (int attempt = 0; attempt < WAVE_SPAWN_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int x = cx + (int) Math.round(Math.cos(angle) * dist);
            int z = cz + (int) Math.round(Math.sin(angle) * dist);

            if (!world.hasChunk(x >> 4, z >> 4)) continue;

            int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

            if (!world.getFluidState(new BlockPos(x, y - 1, z)).isEmpty()) continue;

            return new BlockPos(x, y, z);
        }

        return null;
    }

    private static void equipCaptain(ServerLevel world, Pillager pillager) {
        ItemStack banner = Raid.getOminousBannerInstance(world.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN));

        pillager.setItemSlot(EquipmentSlot.HEAD, banner);
        pillager.setDropChance(EquipmentSlot.HEAD, 2.0F);
        pillager.setPatrolLeader(true);
    }

    public static List<Pillager> spawnWave(ServerLevel world, ChunkPos outpostChunk, int count){
        BlockPos origin = findWaveSpawnPoint(world, outpostChunk, world.getRandom());

        if (origin == null) {
            Debug.LogWarning("No valid wave spawn point " + WAVE_SPAWN_CHUNK_DISTANCE
                    + " chunks out from " + outpostChunk + " (chunks unloaded or water)");
            return new ArrayList<>();
        }

        return spawnGroup(world, outpostChunk, origin, count, false);
    }

    public static List<Pillager> spawnBuilders(ServerLevel world, ChunkPos outpostChunk, BlockPos hint, int count) {
        BlockPos origin = (hint != null) ? hint : findWaveSpawnPoint(world, outpostChunk, world.getRandom());

        if (origin == null) {
            Debug.LogWarning("No valid builder spawn point near " + outpostChunk);
            return new ArrayList<>();
        }

        return spawnGroup(world, outpostChunk, origin, count, true);
    }

    private static List<Pillager> spawnGroup(ServerLevel world, ChunkPos outpostChunk, BlockPos origin,
                                             int count, boolean builder) {
        RandomSource random = world.getRandom();
        List<Pillager> spawn = new ArrayList<>();

        int tx = outpostChunk.getMiddleBlockX();
        int tz = outpostChunk.getMiddleBlockZ();
        BlockPos patrolTarget = new BlockPos(tx, world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz), tz);

        String kind = builder ? "builders" : "pillagers";
        Debug.Log("Spawning " + count + " " + kind + " at " + origin.toShortString()
                + " (outpost chunk " + outpostChunk + ")");

        spawningWave = true;
        try {
            for (int i = 0; i < count; i++) {
                int x = origin.getX() + random.nextInt(WAVE_CLUSTER_RADIUS * 2 + 1) - WAVE_CLUSTER_RADIUS;
                int z = origin.getZ() + random.nextInt(WAVE_CLUSTER_RADIUS * 2 + 1) - WAVE_CLUSTER_RADIUS;

                if (!world.hasChunk(x >> 4, z >> 4)) {
                    x = origin.getX();
                    z = origin.getZ();
                }

                int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

                Pillager pillager = EntityTypes.PILLAGER.create(world, EntitySpawnReason.MOB_SUMMONED);
                if (pillager == null) continue;

                Vec3 spawnPos = new Vec3(x + 0.5f, y, z + 0.5f);

                pillager.setPos(spawnPos);
                pillager.setXRot(0);
                pillager.setYRot(random.nextFloat() * 360);

                pillager.finalizeSpawn(
                        world,
                        world.getCurrentDifficultyAt(BlockPos.containing(spawnPos)),
                        EntitySpawnReason.MOB_SUMMONED,
                        null
                );

                if (builder) {
                    pillager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    pillager.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                    pillager.setCustomName(Component.literal("Builder"));
                } else {
                    pillager.setPatrolTarget(patrolTarget);
                }

                pillager.setPersistenceRequired();

                if (world.addFreshEntity(pillager)) {
                    spawn.add(pillager);
                    Debug.Log((builder ? "Builder" : "Pillager") + " spawned at: "
                            + spawnPos.x + ", " + spawnPos.y + ", " + spawnPos.z);
                } else {
                    Debug.LogWarning("Pillager rejected at: " + spawnPos.x + ", " + spawnPos.y + ", " + spawnPos.z);
                }
            }
        } finally {
            spawningWave = false;
        }

        return spawn;
    }

    public static void rollWaveDelay(ServerLevel level, Outpost outpost) {
        int days = WAVE_DELAY_MIN_DAYS + level.getRandom().nextInt(WAVE_DELAY_MAX_DAYS - WAVE_DELAY_MIN_DAYS + 1);
        outpost.setWaveDelayDays(days);
        Debug.Log("Outpost " + outpost.getUuid() + ": next raid due " + days + " days after day "
                + outpost.getConstructionQueueDate());
    }

    public static boolean beginWave(ServerLevel level, Outpost outpost) {
        int count = WAVE_MIN_SIZE + level.getRandom().nextInt(WAVE_MAX_SIZE - WAVE_MIN_SIZE + 1);

        int escalation = outpost.getEscalation();
        if (escalation > 0) {
            int escalated = Math.min(count + escalation * WAVE_ESCALATION_BONUS, WAVE_ESCALATED_MAX);
            Debug.Log("Escalated raid at Outpost " + outpost.getUuid() + ": " + count + " -> " + escalated + " pillagers");
            count = escalated;
        }

        List<Pillager> wave = spawnWave(level, outpost.getChunkPos(), count);

        if (wave.isEmpty()) {
            Debug.LogWarning("Wave failed, no pillagers spawned at Outpost: " + outpost.getUuid());
            return false;
        }

        List<UUID> ids = new ArrayList<>(wave.size());
        for (Pillager p : wave) ids.add(p.getUUID());

        outpost.startWave(ids, day);
        outpost.setConstructionQueueDate(day);
        outpost.clearEscalation();
        outpost.clearEscapees();
        rollWaveDelay(level, outpost);
        promoteCaptain(level, outpost, wave.get(0));
        save(level, outpost);
        WaveBar.update(level, outpost);

        Debug.Log("Wave ACTIVE at Outpost: " + outpost.getUuid() + " (" + ids.size() + " pillagers, day " + day + ")");
        return true;
    }

    public static void promoteCaptain(ServerLevel level, Outpost outpost, Pillager pillager) {
        equipCaptain(level, pillager);
        outpost.setCaptainId(pillager.getUUID());

        Debug.Log("New captain " + pillager.getUUID() + " at Outpost " + outpost.getUuid());
    }

    public static Pillager pickReplacementCaptain(ServerLevel level, Outpost outpost) {
        List<Pillager> candidates = new ArrayList<>();

        for (UUID id : outpost.getWavePillagerIds()) {
            if (level.getEntity(id) instanceof Pillager p && p.isAlive()) candidates.add(p);
        }

        if (candidates.isEmpty()) return null;

        return candidates.get(level.getRandom().nextInt(candidates.size()));
    }

    public static void winWave(ServerLevel level, Outpost outpost, String reason) {
        outpost.setConcluding(true);
        outpost.setWaveDue(false);

        List<Pillager> fleeing = new ArrayList<>();
        for (UUID id : outpost.getWavePillagerIds()) {
            if (level.getEntity(id) instanceof Pillager p && p.isAlive()) fleeing.add(p);
        }

        List<UUID> fleeingIds = new ArrayList<>();
        for (Pillager p : fleeing) fleeingIds.add(p.getUUID());
        outpost.setEscapees(fleeingIds);

        outpost.endWave();
        WaveBar.update(level, outpost); // wave inactive -> removes bar
        save(level, outpost);

        Debug.Log("Wave DEFEATED at Outpost " + outpost.getUuid() + ": " + reason
                + " (" + fleeing.size() + " pillagers retreating)");

        List<ServerPlayer> winners = Achievements.awardNear(
                level, outpost.getCenterX(), outpost.getCenterZ(), WaveBar.VIEW_RADIUS, Achievements.STAY_GONE);

        for (ServerPlayer p : winners) {
            p.sendSystemMessage(Component.literal("Reconquest repelled!"), true);
        }

        if (fleeing.isEmpty()) {
            concludeRaids(level, outpost, "no pillagers left to retreat");
            return;
        }

        RetreatGroup group = new RetreatGroup();
        group.active = fleeing.size();
        RETREAT_GROUPS.put(outpost.getUuid(), group);

        for (Pillager p : fleeing) startRetreat(level, p, outpost.getUuid());
    }

    private static void startRetreat(ServerLevel level, Pillager pillager, String outpostId) {
        RandomSource random = level.getRandom();

        double angle;
        Player nearest = level.getNearestPlayer(pillager, WaveBar.VIEW_RADIUS);
        if (nearest != null) {
            double away = Math.atan2(pillager.getZ() - nearest.getZ(), pillager.getX() - nearest.getX());
            angle = away + (random.nextDouble() - 0.5) * Math.PI;
        } else {
            angle = random.nextDouble() * Math.PI * 2.0;
        }

        Vec3 dir = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));

        pillager.setTarget(null);
        pillager.setAggressive(false);
        pillager.setPatrolLeader(false);

        RETREATING.put(pillager.getUUID(), new Retreat(dir, level.getGameTime() + RETREAT_DURATION_TICKS, outpostId));

        Debug.Log("Pillager " + pillager.getUUID() + " retreating, heading "
                + (int) Math.toDegrees(angle) + " deg");
    }

    private static boolean isOutpaced(ServerLevel level, Pillager pillager) {
        double minSq = Double.MAX_VALUE;

        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) continue;

            double dx = player.getX() - pillager.getX();
            double dz = player.getZ() - pillager.getZ();
            minSq = Math.min(minSq, dx * dx + dz * dz);
        }

        return minSq > RETREAT_ESCAPE_DISTANCE * RETREAT_ESCAPE_DISTANCE;
    }

    public static void tickRetreats(ServerLevel level) {
        if (RETREATING.isEmpty()) return;

        long now = level.getGameTime();

        for (Map.Entry<UUID, Retreat> entry : new ArrayList<>(RETREATING.entrySet())) {
            UUID id = entry.getKey();
            Retreat retreat = entry.getValue();

            Entity found = level.getEntity(id);

            if (found == null) {
                RETREATING.remove(id);
                Debug.Log("Retreating pillager " + id + " unloaded, far from every player");

                RetreatGroup g = RETREAT_GROUPS.get(retreat.outpostId());
                if (g != null && !g.resolved) {
                    g.resolved = true;
                    onRetreatSuccess(level, retreat.outpostId());
                }

                endRetreatMember(level, retreat.outpostId());
                continue;
            }

            if (!(found instanceof Pillager p) || !p.isAlive()) {
                RETREATING.remove(id);
                Debug.Log("Retreating pillager " + id + " no longer alive");
                endRetreatMember(level, retreat.outpostId());
                continue;
            }

            if (now >= retreat.endTick()) {
                RETREATING.remove(id);
                Debug.Log("Pillager " + id + " retreat finished at " + p.blockPosition().toShortString());
                endRetreatMember(level, retreat.outpostId());
                continue;
            }

            RetreatGroup group = RETREAT_GROUPS.get(retreat.outpostId());
            if (group != null && !group.resolved && isOutpaced(level, p)) {
                group.resolved = true;
                onRetreatSuccess(level, retreat.outpostId());
            }

            p.setTarget(null);

            if (now % 10L != 0L && !p.getNavigation().isDone()) continue;

            double tx = p.getX() + retreat.dir().x * RETREAT_STEP;
            double tz = p.getZ() + retreat.dir().z * RETREAT_STEP;
            double ty = p.getY();

            if (level.hasChunk((int) Math.floor(tx) >> 4, (int) Math.floor(tz) >> 4)) {
                ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(tx), (int) Math.floor(tz));
            }

            p.setPatrolTarget(BlockPos.containing(tx, ty, tz));
            p.getNavigation().moveTo(tx, ty, tz, RETREAT_SPEED);
        }
    }

    public static boolean onRetreaterKilled(ServerLevel level, UUID id) {
        Retreat retreat = RETREATING.remove(id);
        if (retreat == null) return false;

        Debug.Log("Retreating pillager " + id + " killed");
        endRetreatMember(level, retreat.outpostId());
        return true;
    }

    public static void onEscapeeKilled(ServerLevel level, UUID id) {
        for (Outpost outpost : new ArrayList<>(ScheduledOutposts)) {
            if (!outpost.isEscapee(id)) continue;

            outpost.removeEscapee(id);
            int left = outpost.getEscapeeCount();

            Debug.Log("Raider killed at Outpost " + outpost.getUuid() + ", " + left + " raiders left");

            if (left == 0) {
                concludeRaids(level, outpost, "all attacking pillagers killed, next raid cancelled");
            } else {
                save(level, outpost);
            }
            return;
        }
    }

    private static void endRetreatMember(ServerLevel level, String outpostId) {
        RetreatGroup group = RETREAT_GROUPS.get(outpostId);
        if (group == null) return;

        group.active--;
        if (group.active > 0) return;

        RETREAT_GROUPS.remove(outpostId);

        if (!group.resolved) {
            Outpost outpost = findOutpost(outpostId);
            if (outpost != null) {
                concludeRaids(level, outpost, "retreat failed, no pillager got clear");
            }
        }
    }

    private static void onRetreatSuccess(ServerLevel level, String outpostId) {
        Outpost outpost = findOutpost(outpostId);
        if (outpost == null) return;

        Debug.Log("Pillagers ESCAPED from Outpost " + outpostId + " (" + (int) RETREAT_ESCAPE_DISTANCE
                + "+ blocks clear of every player)");

        double maxSq = RETREAT_MESSAGE_RADIUS * RETREAT_MESSAGE_RADIUS;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - outpost.getCenterX();
            double dz = player.getZ() - outpost.getCenterZ();
            if (dx * dx + dz * dz > maxSq) continue;

            player.sendSystemMessage(
                    Component.literal("The pillagers escaped... they might be back again."), false);
        }

        if (level.getRandom().nextFloat() < RETREAT_REATTACK_CHANCE) {
            outpost.addEscalation();
            outpost.setConcluding(false);
            outpost.setConstructionQueueDate(day);
            rollWaveDelay(level, outpost);
            save(level, outpost);

            Debug.Log("Escape roll WON: Outpost " + outpostId + " will be attacked again, larger (escalation "
                    + outpost.getEscalation() + ")");
        } else {
            Debug.Log("Escape roll lost: no further raids on Outpost " + outpostId);
            concludeRaids(level, outpost, "pillagers escaped for good");
        }
    }

    private static void concludeRaids(ServerLevel level, Outpost outpost, String reason) {
        if (!ScheduledOutposts.contains(outpost)) return;

        WaveBar.remove(outpost);
        ScheduledOutposts.remove(outpost);
        SavedData.get(level).removeOutpost(outpost.getUuid());

        Debug.Log("Raids CONCLUDED at Outpost " + outpost.getUuid() + ": " + reason);
    }

    public static final int OUTPOST_MAX_PILLAGERS = 10;

    public static final int DISPATCH_GROUP_SIZE = 3;

    public static final int DISPATCH_CHUNK_DISTANCE = 4;

    private static final double OUTPOST_AREA_MARGIN = 16.0;

    private static class WaitList {
        final ChunkPos chunk;
        BlockPos anchor;
        int count;
        final Set<UUID> enRoute = new HashSet<>();

        WaitList(ChunkPos chunk, BlockPos anchor) {
            this.chunk = chunk;
            this.anchor = anchor;
        }
    }

    private static final Map<String, WaitList> WAIT_LISTS = new HashMap<>();

    private static AABB outpostArea(StructureStart start) {
        BoundingBox bb = start.getBoundingBox();
        return new AABB(bb.minX(), bb.minY(), bb.minZ(), bb.maxX() + 1, bb.maxY() + 1, bb.maxZ() + 1)
                .inflate(OUTPOST_AREA_MARGIN);
    }

    public static int countPillagersAt(ServerLevel level, StructureStart start) {
        return level.getEntitiesOfClass(Pillager.class, outpostArea(start),
                p -> p.isAlive() && !isTrackedPillager(p.getUUID())).size();
    }

    public static void enqueuePillager(ChunkPos chunk, BlockPos anchor) {
        String id = WorldData.Outpost.getOutpostUniqueID(chunk);
        WaitList w = WAIT_LISTS.computeIfAbsent(id, k -> new WaitList(chunk, anchor));

        w.anchor = anchor;
        w.count++;

        Debug.Log("Pillager spawn held at Outpost " + id + " (cap " + OUTPOST_MAX_PILLAGERS
                + " reached), wait list: " + w.count);
    }

    public static void processWaitLists(ServerLevel level) {
        for (Map.Entry<String, WaitList> entry : new ArrayList<>(WAIT_LISTS.entrySet())) {
            String id = entry.getKey();
            WaitList w = entry.getValue();

            if (WorldData.Outpost.isOutpostBurnt(level, w.chunk)) {
                WAIT_LISTS.remove(id);
                Debug.Log("Outpost " + id + " destroyed, wait list dropped (" + w.count + " pillagers)");
                continue;
            }

            if (!level.hasChunk(w.chunk.x(), w.chunk.z())) continue;

            StructureStart start = WorldData.Outpost.getOutpostAt(level, w.anchor);
            if (!start.isValid()) continue;

            AABB area = outpostArea(start);

            w.enRoute.removeIf(uid ->
                    !(level.getEntity(uid) instanceof Pillager p) || !p.isAlive() || area.contains(p.position()));

            if (w.count <= 0 && w.enRoute.isEmpty()) {
                WAIT_LISTS.remove(id);
                continue;
            }

            if (w.count < DISPATCH_GROUP_SIZE) continue;

            int present = countPillagersAt(level, start);
            if (present + w.enRoute.size() + DISPATCH_GROUP_SIZE > OUTPOST_MAX_PILLAGERS) continue;

            BlockPos origin = findSpawnPointAround(level, w.chunk, level.getRandom(), DISPATCH_CHUNK_DISTANCE);
            if (origin == null) {
                Debug.Log("Outpost " + id + ": no loaded dry point " + DISPATCH_CHUNK_DISTANCE + " chunks out, retry later");
                continue;
            }

            List<Pillager> group = spawnGroup(level, w.chunk, origin, DISPATCH_GROUP_SIZE, false);
            if (group.isEmpty()) continue;

            group.get(0).setPatrolLeader(true);
            for (Pillager p : group) w.enRoute.add(p.getUUID());
            w.count -= group.size();

            Debug.Log("Dispatched " + group.size() + " pillagers toward Outpost " + id + " from "
                    + origin.toShortString() + ", " + w.count + " still waiting");
        }
    }

    public static boolean testRebuild(ServerLevel level, BlockPos pos, int wreckPercent, Consumer<String> feedback) {
        StructureStart start = WorldData.Outpost.getOutpostAt(level, pos);

        if (!start.isValid()) {
            feedback.accept("No pillager outpost at your position. Stand inside one.");
            Debug.LogWarning("RebuildTest: no outpost at " + pos.toShortString());
            return false;
        }

        ChunkPos chunk = start.getChunkPos();
        String id = WorldData.Outpost.getOutpostUniqueID(chunk);

        Outpost outpost = findOutpost(id);

        if (outpost != null && outpost.isRebuilding()) {
            feedback.accept("Outpost already rebuilding.");
            return false;
        }

        if (outpost != null) {
            Debug.Log("RebuildTest: dropping old record of outpost " + id + " and rescanning");
            ScheduledOutposts.remove(outpost);
            SavedData.get(level).removeOutpost(id);
            outpost = null;
        }

        Debug.Log("RebuildTest: registering outpost " + id + " as burnt");
        markOutpostBurnt(level, chunk);
        scheduleOutpostForConstruction(level, start);
        outpost = findOutpost(id);

        if (outpost == null || outpost.getBuilderBlockCount() == 0) {
            feedback.accept("Outpost has no recorded blocks, cannot rebuild.");
            Debug.LogWarning("RebuildTest: no blocks recorded for " + id);
            return false;
        }

        int soldierCount = WAVE_MIN_SIZE + level.getRandom().nextInt(WAVE_MAX_SIZE - WAVE_MIN_SIZE + 1);
        List<Pillager> soldiers = spawnWave(level, chunk, soldierCount);

        BlockPos hint = soldiers.isEmpty() ? null : soldiers.get(0).blockPosition();
        List<Pillager> builders = spawnBuilders(level, chunk, hint, BUILDER_COUNT);

        if (builders.isEmpty()) {
            for (Pillager p : soldiers) p.discard();
            feedback.accept("Could not spawn builders (no loaded spawn point).");
            return false;
        }

        List<UUID> soldierIds = new ArrayList<>();
        for (Pillager p : soldiers) soldierIds.add(p.getUUID());
        outpost.trackSoldiers(soldierIds);

        List<UUID> builderIds = new ArrayList<>();
        for (Pillager p : builders) builderIds.add(p.getUUID());
        outpost.setBuilders(builderIds);

        Debug.Log("RebuildTest: spawned " + soldiers.size() + " soldiers and " + builders.size()
                + " builders for outpost " + id);

        int wrecked = 0;
        if (wreckPercent > 0) {
            RandomSource random = level.getRandom();

            for (BuilderBlock b : outpost.getBuilderBlocks()) {
                if (random.nextInt(100) >= wreckPercent) continue;

                BlockPos bp = b.getBlockPos();
                if (!level.hasChunk(bp.getX() >> 4, bp.getZ() >> 4)) continue;
                if (level.getBlockState(bp).isAir()) continue;

                level.setBlock(bp, Blocks.AIR.defaultBlockState(), REBUILD_FLAGS);
                wrecked++;
            }

            Debug.Log("RebuildTest: wrecked " + wrecked + " blocks (" + wreckPercent + "%) of outpost " + id);
        }

        beginRebuild(level, outpost, "RebuildTest command");

        feedback.accept("Rebuilding outpost " + id + ": " + wrecked + " blocks wrecked (" + wreckPercent + "%), "
                + outpost.getBuilderBlockCount() + " recorded, " + soldiers.size() + " soldiers and "
                + builders.size() + " builders spawned.");
        return true;
    }

    public static boolean spawnPillagerOutpost(ServerLevel level, BlockPos pos) {
        Identifier id = Identifier.fromNamespaceAndPath("minecraft", "pillager_outpost");

        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, id);

        Holder<Structure> structureHolder = level.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE)
                .getOrThrow(key);

        ChunkGenerator chunkGenerator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        StructureTemplateManager templateManager = level.getStructureManager();
        ChunkPos chunkPos = new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);

        StructureStart start = structureHolder.value().generate(
                structureHolder,
                level.dimension(),
                level.registryAccess(),
                chunkGenerator,
                chunkGenerator.getBiomeSource(),
                randomState,
                templateManager,
                level.getSeed(),
                chunkPos,
                0,                       // references
                level,                   // LevelHeightAccessor
                _ -> true            // skip biome validity check
        );

        if (!start.isValid())
            return false;

        BoundingBox box = start.getBoundingBox();
        RandomSource random = level.getRandom();

        int minChunkX = box.minX() >> 4;
        int maxChunkX = box.maxX() >> 4;
        int minChunkZ = box.minZ() >> 4;
        int maxChunkZ = box.maxZ() >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                start.placeInChunk(
                        level,
                        level.structureManager(),
                        chunkGenerator,
                        random,
                        box,
                        new ChunkPos(cx, cz)
                );
            }
        }

        return true;
    }


    public static void scheduleOutpostForConstruction(ServerLevel level, StructureStart outpost){
        String uuid = WorldData.Outpost.getOutpostUniqueID(outpost.getChunkPos());
        if (findOutpost(uuid) != null) {
            Debug.Log("Outpost already scheduled: " + uuid);
            return;
        }

        Outpost scheduled = new Outpost(outpost.getChunkPos());
        rollWaveDelay(level, scheduled);
        Debug.Log("Scheduling outpost for construction: " + uuid);

        StructureTemplateManager templateManager = level.getStructureManager();

        for (StructurePiece piece : outpost.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece poolPiece)) {
                Debug.Log("Skipping non-pool piece: " + piece.getClass().getSimpleName());
                continue;
            }

            List<SinglePoolElement> singles = new ArrayList<>();
            collectSingles(poolPiece.getElement(), singles);

            for (SinglePoolElement single : singles) {
                int n = recordTemplateBlocks(scheduled, templateManager, poolPiece, single);
                Debug.Log("Recorded " + n + " blocks from " + poolPiece.getElement().getClass().getSimpleName()
                        + " piece at " + poolPiece.getPosition().toShortString());
            }
        }

        ScheduledOutposts.add(scheduled);
        save(level, scheduled);
        Debug.Log("Scheduled outpost for construction: " + uuid + " (" + scheduled.getBuilderBlockCount() + " blocks recorded)");
    }

    private static void collectSingles(StructurePoolElement element, List<SinglePoolElement> out) {
        if (element instanceof SinglePoolElement single) {
            out.add(single);
        } else if (element instanceof ListPoolElement list) {
            for (StructurePoolElement inner : unwrapList(list)) collectSingles(inner, out);
        } else {
            Debug.Log("Skipping unsupported pool element: " + element.getClass().getSimpleName());
        }
    }

    private static List<StructurePoolElement> unwrapList(ListPoolElement list) {
        for (Field f : ListPoolElement.class.getDeclaredFields()) {
            if (!List.class.isAssignableFrom(f.getType())) continue;

            try {
                f.setAccessible(true);
                Object value = f.get(list);

                if (value instanceof List<?> raw) {
                    List<StructurePoolElement> out = new ArrayList<>();
                    for (Object o : raw) {
                        if (o instanceof StructurePoolElement e) out.add(e);
                    }
                    return out;
                }
            } catch (ReflectiveOperationException | RuntimeException ex) {
                Debug.LogWarning("Failed to read ListPoolElement elements: " + ex);
            }
        }

        Debug.LogWarning("ListPoolElement has no readable list field, its blocks are not recorded");
        return List.of();
    }

    private static int recordTemplateBlocks(Outpost scheduled, StructureTemplateManager templateManager, PoolElementStructurePiece poolPiece, SinglePoolElement single) {
        StructureTemplate template = single.getTemplate(templateManager);

        List<StructureTemplate.Palette> palettes = template.palettes;
        if (palettes.isEmpty()) return 0;

        List<StructureTemplate.StructureBlockInfo> blocks = palettes.getFirst().blocks();

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(poolPiece.getRotation());

        int recorded = 0;

        for (StructureTemplate.StructureBlockInfo info : blocks) {
            BlockPos worldPos = StructureTemplate.calculateRelativePosition(settings, info.pos())
                    .offset(poolPiece.getPosition());

            BlockState state = info.state();

            if (state.getBlock() instanceof AirBlock) continue;
            if (state.is(Blocks.JIGSAW) || state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.STRUCTURE_VOID)) continue;

            scheduled.RegisterBuilderBlock(new BuilderBlock(state.getBlock(), worldPos, state));
            recorded++;
        }

        return recorded;
    }

    public static void save(ServerLevel level, Outpost outpost) {
        SavedData.get(level).putOutpost(outpost);
    }

    public static void restoreScheduledOutposts(ServerLevel level) {
        ScheduledOutposts.clear();

        for (SavedData.OutpostEntry entry : SavedData.get(level).getScheduledOutposts()) {
            Outpost outpost = Outpost.fromEntry(entry);
            ScheduledOutposts.add(outpost);

            Debug.Log("Restored Outpost " + outpost.getUuid()
                    + " blocks=" + outpost.getBuilderBlockCount()
                    + " waveActive=" + outpost.isWaveActive()
                    + " rebuilding=" + outpost.isRebuilding());
        }

        Debug.Log("Restored " + ScheduledOutposts.size() + " scheduled outposts");
    }

    public static void clearScheduledOutposts() {
        ScheduledOutposts.clear();
        RETREATING.clear();
        RETREAT_GROUPS.clear();
        WAIT_LISTS.clear();
        day = -1L;
    }

    public static Outpost findOutpost(String uuid) {
        for (Outpost o : ScheduledOutposts) {
            if (o.getUuid().equals(uuid)) return o;
        }
        return null;
    }

    public static void markOutpostBurnt(ServerLevel level, ChunkPos pos) {
        if (pos == null) return;
        SavedData data = SavedData.get(level);
        String id = WorldData.Outpost.getOutpostUniqueID(pos);
        data.markBurnt(id);
        Debug.Log("Outpost marked burnt: " + id);
    }

    public static List<Outpost> getBurntOutposts(){
        return Collections.unmodifiableList(ScheduledOutposts);
    }

    public static void beginRebuild(ServerLevel level, Outpost outpost, String reason) {
        if (outpost.isRebuilding()) return;

        if (outpost.getBuilderBlockCount() == 0) {
            Debug.LogWarning("Cannot rebuild Outpost " + outpost.getUuid() + ": no blocks recorded");
            return;
        }

        if (outpost.getBuilderCount() == 0) {
            Debug.LogWarning("Cannot rebuild Outpost " + outpost.getUuid() + ": no builder pillagers");
            return;
        }

        outpost.startRebuild();
        WaveBar.remove(outpost);
        save(level, outpost);

        Debug.Log("Outpost REBUILD STARTED: " + outpost.getUuid()
                + " (" + outpost.getBuilderBlockCount() + " blocks, "
                + outpost.getBuilderCount() + " builders, " + outpost.getWavePillagerCount()
                + " soldiers) reason: " + reason);
    }

    public static boolean sendBuilders(ServerLevel level, Outpost outpost, String reason) {
        if (outpost.isRebuilding()) return false;

        if (outpost.getBuilderBlockCount() == 0) {
            Debug.LogWarning("Pillagers won at Outpost " + outpost.getUuid() + " but no blocks recorded, no rebuild");
            return false;
        }

        BlockPos hint = null;
        for (UUID id : outpost.getWavePillagerIds()) {
            if (level.getEntity(id) instanceof Pillager p && p.isAlive()) {
                hint = p.blockPosition();
                break;
            }
        }

        List<Pillager> builders = spawnBuilders(level, outpost.getChunkPos(), hint, BUILDER_COUNT);

        if (builders.isEmpty()) {
            Debug.LogWarning("Pillagers won at Outpost " + outpost.getUuid() + " but builders could not spawn");
            return false;
        }

        List<UUID> ids = new ArrayList<>();
        for (Pillager p : builders) ids.add(p.getUUID());
        outpost.setBuilders(ids);

        Debug.Log("Pillagers WON at Outpost " + outpost.getUuid() + ": " + builders.size() + " builders arrive");

        beginRebuild(level, outpost, reason);
        return true;
    }

    private static BuilderBlock nextTarget(ServerLevel level, Deque<BuilderBlock> queue) {
        Iterator<BuilderBlock> it = queue.iterator();

        while (it.hasNext()) {
            BuilderBlock b = it.next();
            BlockPos pos = b.getBlockPos();

            if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;

            if (level.getBlockState(pos).equals(b.getBlockState())) {
                it.remove();
                continue;
            }

            return b;
        }

        return null;
    }

    public static void tickRebuild(ServerLevel level, Outpost outpost) {
        long now = level.getGameTime();

        if (now % 10L != 0L) return;

        Deque<BuilderBlock> queue = outpost.getRebuildQueue();

        if (now % 100L == 0L) {
            Debug.Log("Outpost " + outpost.getUuid() + " rebuild progress: " + queue.size()
                    + " blocks left, " + outpost.getBuilderCount() + " builders");
        }

        if (outpost.getBuilderCount() == 0) {
            haltRebuild(level, outpost, "no builders left");
            return;
        }

        List<Pillager> builders = new ArrayList<>();
        for (UUID id : outpost.getBuilderIds()) {
            if (level.getEntity(id) instanceof Pillager p && p.isAlive()) builders.add(p);
        }

        BuilderBlock target = nextTarget(level, queue);

        if (queue.isEmpty()) {
            finishRebuild(level, outpost);
            return;
        }

        if (target == null) {
            if (now % 200L == 0L) {
                Debug.Log("Outpost " + outpost.getUuid() + " rebuild waiting on unloaded chunks ("
                        + queue.size() + " blocks left)");
            }
            return;
        }

        if (outpost.getRebuildTarget() != target) {
            outpost.setRebuildTarget(target, now);
        }

        BlockPos tp = target.getBlockPos();
        double cx = tp.getX() + 0.5;
        double cy = tp.getY() + 0.5;
        double cz = tp.getZ() + 0.5;

        for (Pillager p : builders) {
            if (p.getTarget() != null) continue;

            double dx = p.getX() - cx;
            double dy = p.getY() - cy;
            double dz = p.getZ() - cz;
            if (dx * dx + dy * dy + dz * dz <= 3.0 * 3.0) continue;

            p.setPatrolTarget(tp);
            p.getNavigation().moveTo(cx, tp.getY(), cz, BUILD_WALK_SPEED);
        }

        if (now - outpost.getLastPlaceTick() < REBUILD_INTERVAL_TICKS) return;

        boolean stuck = now - outpost.getRebuildTargetSince() >= BUILD_STUCK_TICKS;

        Pillager worker = null;
        boolean fromAfar = false;

        for (Pillager p : builders) {
            if (p.getTarget() != null) continue;

            double dx = p.getX() - cx;
            double dy = p.getY() - cy;
            double dz = p.getZ() - cz;

            if (dx * dx + dy * dy + dz * dz <= BUILD_REACH * BUILD_REACH) {
                worker = p;
                break;
            }

            if (stuck && dx * dx + dz * dz <= BUILD_STUCK_REACH_HORIZONTAL * BUILD_STUCK_REACH_HORIZONTAL) {
                worker = p;
                fromAfar = true;
                break;
            }
        }

        if (worker == null) return;

        level.setBlock(tp, target.getBlockState(), REBUILD_FLAGS);
        worker.swing(InteractionHand.MAIN_HAND);

        queue.remove(target);
        outpost.setRebuildTarget(null, now);
        outpost.setLastPlaceTick(now);

        if (fromAfar) {
            Debug.Log("Builder " + worker.getUUID() + " could not reach " + tp.toShortString()
                    + ", placed block from afar");
        }
    }

    public static void haltRebuild(ServerLevel level, Outpost outpost, String reason) {
        int left = outpost.getRebuildQueue().size();

        outpost.stopRebuild();
        outpost.setConstructionQueueDate(day);
        rollWaveDelay(level, outpost);
        save(level, outpost);

        Debug.Log("Outpost REBUILD HALTED: " + outpost.getUuid() + " - " + reason
                + " (" + left + " blocks never rebuilt)");

        List<ServerPlayer> defenders = Achievements.awardNear(
                level, outpost.getCenterX(), outpost.getCenterZ(), WaveBar.VIEW_RADIUS, Achievements.STAY_GONE);

        for (ServerPlayer p : defenders) {
            p.sendSystemMessage(Component.literal("The pillagers abandon the reconstruction!"), true);
        }
    }

    public static boolean isTrackedPillager(UUID id) {
        for (Outpost o : ScheduledOutposts) {
            if (o.isWavePillager(id) || o.isBuilder(id)) return true;
        }
        return false;
    }

    private static void finishRebuild(ServerLevel level, Outpost outpost) {
        String id = outpost.getUuid();

        SavedData data = SavedData.get(level);
        data.unmarkBurnt(id);
        data.removeOutpost(id);
        ScheduledOutposts.remove(outpost);

        Debug.Log("Outpost REBUILD FINISHED: " + id + " (burnt flag cleared, unscheduled)");
    }
}