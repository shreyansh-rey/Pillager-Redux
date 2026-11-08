package com.yan.pillager_redux.main;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yan.pillager_redux.containers.Outpost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class SavedData extends net.minecraft.world.level.saveddata.SavedData {

    public record BlockEntry(BlockPos pos, BlockState state) {
        public static final Codec<BlockEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(BlockEntry::pos),
                BlockState.CODEC.fieldOf("state").forGetter(BlockEntry::state)
        ).apply(instance, BlockEntry::new));
    }

    public record OutpostEntry(int x, int z, long queueDate,
                               boolean waveDue, boolean waveActive, long waveStartDay, int waveTotal, boolean rebuilding,
                               List<UUID> wavePillagers, Optional<UUID> captain, List<BlockEntry> blocks) {
        public static final Codec<OutpostEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("x").forGetter(OutpostEntry::x),
                Codec.INT.fieldOf("z").forGetter(OutpostEntry::z),
                Codec.LONG.fieldOf("queue_date").forGetter(OutpostEntry::queueDate),
                Codec.BOOL.optionalFieldOf("wave_due", false).forGetter(OutpostEntry::waveDue),
                Codec.BOOL.optionalFieldOf("wave_active", false).forGetter(OutpostEntry::waveActive),
                Codec.LONG.optionalFieldOf("wave_start_day", -1L).forGetter(OutpostEntry::waveStartDay),
                Codec.INT.optionalFieldOf("wave_total", 0).forGetter(OutpostEntry::waveTotal),
                Codec.BOOL.optionalFieldOf("rebuilding", false).forGetter(OutpostEntry::rebuilding),
                UUIDUtil.CODEC.listOf().optionalFieldOf("wave_pillagers", List.of()).forGetter(OutpostEntry::wavePillagers),
                UUIDUtil.CODEC.optionalFieldOf("captain").forGetter(OutpostEntry::captain),
                BlockEntry.CODEC.listOf().optionalFieldOf("blocks", List.of()).forGetter(OutpostEntry::blocks)
        ).apply(instance, OutpostEntry::new));

        public String key() {
            return WorldData.Outpost.getOutpostUniqueID(new ChunkPos(x, z));
        }
    }

    private final Set<String> burntOutposts = new HashSet<>();
    private final Map<String, OutpostEntry> scheduledOutposts = new LinkedHashMap<>();
    private long lastDay = -1L;

    public SavedData() {}

    private SavedData(List<String> outposts, long lastDay, List<OutpostEntry> scheduled) {
        this.burntOutposts.addAll(outposts);
        this.lastDay = lastDay;
        for (OutpostEntry entry : scheduled) {
            this.scheduledOutposts.put(entry.key(), entry);
        }
    }

    public static final SavedDataType<SavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("pillager_redux", "burnt_outposts"),
            SavedData::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.listOf().fieldOf("burnt_outposts")
                            .forGetter(data -> new ArrayList<>(data.burntOutposts)),
                    Codec.LONG.optionalFieldOf("last_day", -1L)
                            .forGetter(data -> data.lastDay),
                    OutpostEntry.CODEC.listOf().optionalFieldOf("scheduled_outposts", List.of())
                            .forGetter(data -> new ArrayList<>(data.scheduledOutposts.values()))
            ).apply(instance, SavedData::new))
    );

    public static SavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isBurnt(String key) {
        return burntOutposts.contains(key);
    }

    public void markBurnt(String key) {
        burntOutposts.add(key);
        setDirty();
    }

    public void unmarkBurnt(String key) {
        if (burntOutposts.remove(key)) setDirty();
    }

    public long getLastDay() {
        return lastDay;
    }

    public void setLastDay(long lastDay) {
        this.lastDay = lastDay;
        setDirty();
    }

    public List<OutpostEntry> getScheduledOutposts() {
        return new ArrayList<>(scheduledOutposts.values());
    }

    public void putOutpost(Outpost outpost) {
        scheduledOutposts.put(outpost.getUuid(), outpost.toEntry());
        setDirty();
    }

    public void removeOutpost(String key) {
        if (scheduledOutposts.remove(key) != null) setDirty();
    }
}