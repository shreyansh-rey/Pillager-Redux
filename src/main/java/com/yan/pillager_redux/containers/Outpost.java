package com.yan.pillager_redux.containers;

import com.yan.pillager_redux.main.Mod;
import com.yan.pillager_redux.main.SavedData;
import com.yan.pillager_redux.main.WorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class Outpost {
    String uuid;

    boolean built;

    boolean waveDue;

    boolean waveActive;
    long waveStartDay = -1L;
    int waveTotal;
    UUID captainId;
    final Set<UUID> wavePillagers = new HashSet<>();

    boolean rebuilding;
    Deque<BuilderBlock> rebuildQueue;
    BuilderBlock rebuildTarget;
    long rebuildTargetSince;
    long lastPlaceTick = -1000L;

    ChunkPos chunkPos;

    List<BuilderBlock> builderBlocks = new ArrayList<>();

    long constructionQueueDate;

    public Outpost(ChunkPos chunkPos) {
        this(chunkPos, (Mod.day == -1L) ? 0L : Mod.day);
    }

    public Outpost(ChunkPos chunkPos, long constructionQueueDate) {
        this.chunkPos = chunkPos;
        this.constructionQueueDate = constructionQueueDate;
        this.uuid = WorldData.Outpost.getOutpostUniqueID(chunkPos);
        this.built = false;
    }

    public void RegisterBuilderBlock(BuilderBlock builderBlock){
        if (builderBlock == null) return;

        builderBlocks.add(builderBlock);
    }

    public List<BuilderBlock> getBuilderBlocks() {
        return java.util.Collections.unmodifiableList(builderBlocks);
    }

    public int getBuilderBlockCount() {
        return builderBlocks.size();
    }

    public boolean isWaveDue() {
        return waveDue;
    }

    public void setWaveDue(boolean waveDue) {
        this.waveDue = waveDue;
    }

    public boolean isWaveActive() {
        return waveActive;
    }

    public long getWaveStartDay() {
        return waveStartDay;
    }

    public int getWaveTotal() {
        return waveTotal;
    }

    public void startWave(Collection<UUID> pillagerIds, long day) {
        waveActive = true;
        waveDue = false;
        waveStartDay = day;
        wavePillagers.clear();
        wavePillagers.addAll(pillagerIds);
        waveTotal = pillagerIds.size();
        captainId = null;
    }

    public void endWave() {
        waveActive = false;
        waveStartDay = -1L;
        waveTotal = 0;
        captainId = null;
        wavePillagers.clear();
    }

    public boolean isWavePillager(UUID id) {
        return wavePillagers.contains(id);
    }

    public void removeWavePillager(UUID id) {
        wavePillagers.remove(id);
    }

    public Set<UUID> getWavePillagerIds() {
        return Set.copyOf(wavePillagers);
    }

    public UUID getCaptainId() {
        return captainId;
    }

    public void setCaptainId(UUID captainId) {
        this.captainId = captainId;
    }

    public boolean isCaptain(UUID id) {
        return captainId != null && captainId.equals(id);
    }

    public int getWavePillagerCount() {
        return wavePillagers.size();
    }


    public boolean isRebuilding() {
        return rebuilding;
    }

    public void startRebuild() {
        rebuilding = true;
        rebuildQueue = null;
        rebuildTarget = null;
        lastPlaceTick = -1000L;

        waveActive = false;
        waveDue = false;
        waveStartDay = -1L;
        captainId = null;
    }

    public void stopRebuild() {
        rebuilding = false;
        rebuildQueue = null;
        rebuildTarget = null;
        wavePillagers.clear();
        waveTotal = 0;
        waveActive = false;
        waveStartDay = -1L;
    }

    public void setCrew(Collection<UUID> ids) {
        wavePillagers.clear();
        wavePillagers.addAll(ids);
        waveTotal = ids.size();
    }

    public BuilderBlock getRebuildTarget() {
        return rebuildTarget;
    }

    public long getRebuildTargetSince() {
        return rebuildTargetSince;
    }

    public void setRebuildTarget(BuilderBlock target, long tick) {
        this.rebuildTarget = target;
        this.rebuildTargetSince = tick;
    }

    public long getLastPlaceTick() {
        return lastPlaceTick;
    }

    public void setLastPlaceTick(long lastPlaceTick) {
        this.lastPlaceTick = lastPlaceTick;
    }

    public Deque<BuilderBlock> getRebuildQueue() {
        if (rebuildQueue == null) {
            List<BuilderBlock> sorted = new ArrayList<>(builderBlocks);
            sorted.sort(Comparator
                    .comparingInt((BuilderBlock b) -> b.getBlockPos().getY())
                    .thenComparingInt(b -> b.getBlockPos().getX())
                    .thenComparingInt(b -> b.getBlockPos().getZ()));
            rebuildQueue = new ArrayDeque<>(sorted);
        }
        return rebuildQueue;
    }

    public SavedData.OutpostEntry toEntry() {
        List<SavedData.BlockEntry> blocks = new ArrayList<>(builderBlocks.size());
        for (BuilderBlock b : builderBlocks) {
            blocks.add(new SavedData.BlockEntry(b.getBlockPos(), b.getBlockState()));
        }

        return new SavedData.OutpostEntry(
                chunkPos.x(), chunkPos.z(), constructionQueueDate,
                waveDue, waveActive, waveStartDay, waveTotal, rebuilding,
                new ArrayList<>(wavePillagers), Optional.ofNullable(captainId), blocks
        );
    }

    public static Outpost fromEntry(SavedData.OutpostEntry entry) {
        Outpost outpost = new Outpost(new ChunkPos(entry.x(), entry.z()), entry.queueDate());

        outpost.waveDue = entry.waveDue();
        outpost.waveActive = entry.waveActive();
        outpost.waveStartDay = entry.waveStartDay();
        outpost.waveTotal = entry.waveTotal();
        outpost.rebuilding = entry.rebuilding();
        outpost.wavePillagers.addAll(entry.wavePillagers());
        outpost.captainId = entry.captain().orElse(null);

        for (SavedData.BlockEntry b : entry.blocks()) {
            outpost.builderBlocks.add(new BuilderBlock(b.state().getBlock(), b.pos(), b.state()));
        }

        return outpost;
    }

    public long getConstructionQueueDate() {
        return constructionQueueDate;
    }

    public BlockPos getBlockPos() {
        return chunkPos.getWorldPosition();
    }

    public double getCenterX() {
        return chunkPos.getMiddleBlockX() + 0.5;
    }

    public double getCenterZ() {
        return chunkPos.getMiddleBlockZ() + 0.5;
    }

    public ChunkPos getChunkPos() {
        return chunkPos;
    }

    public String getUuid() {
        return uuid;
    }

    public void setConstructionQueueDate(long constructionQueueDate) {
        this.constructionQueueDate = constructionQueueDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Outpost other)) return false;
        return Objects.equals(uuid, other.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uuid);
    }
}