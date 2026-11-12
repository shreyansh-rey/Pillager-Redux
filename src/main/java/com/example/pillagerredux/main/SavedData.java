package com.example.pillagerredux.main;

import com.example.pillagerredux.containers.Outpost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class SavedData extends net.minecraft.world.level.saveddata.SavedData {
    private static final String NAME = "pillager_redux_burnt_outposts";

    public record BlockEntry(BlockPos pos, BlockState state) {
        CompoundTag toTag() {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", pos.asLong());
            t.put("state", NbtUtils.writeBlockState(state));
            return t;
        }

        static BlockEntry fromTag(CompoundTag t, HolderGetter<Block> blocks) {
            return new BlockEntry(
                    BlockPos.of(t.getLong("pos")),
                    NbtUtils.readBlockState(blocks, t.getCompound("state")));
        }
    }

    public record OutpostEntry(int x, int z, long queueDate, int waveDelay,
                               boolean waveDue, boolean waveActive, long waveStartDay, int waveTotal, int escalation, boolean rebuilding,
                               List<UUID> wavePillagers, List<UUID> builders, List<UUID> escapees, Optional<UUID> captain, List<BlockEntry> blocks) {

        public String key() {
            return WorldData.Outpost.getOutpostUniqueID(new ChunkPos(x, z));
        }

        private static ListTag uuids(List<UUID> ids) {
            ListTag list = new ListTag();
            for (UUID id : ids) list.add(StringTag.valueOf(id.toString()));
            return list;
        }

        private static List<UUID> readUuids(CompoundTag t, String key) {
            ListTag list = t.getList(key, Tag.TAG_STRING);
            List<UUID> out = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                try {
                    out.add(UUID.fromString(list.getString(i)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return out;
        }

        CompoundTag toTag() {
            CompoundTag t = new CompoundTag();
            t.putInt("x", x);
            t.putInt("z", z);
            t.putLong("queue_date", queueDate);
            t.putInt("wave_delay", waveDelay);
            t.putBoolean("wave_due", waveDue);
            t.putBoolean("wave_active", waveActive);
            t.putLong("wave_start_day", waveStartDay);
            t.putInt("wave_total", waveTotal);
            t.putInt("escalation", escalation);
            t.putBoolean("rebuilding", rebuilding);
            t.put("wave_pillagers", uuids(wavePillagers));
            t.put("builders", uuids(builders));
            t.put("escapees", uuids(escapees));
            captain.ifPresent(c -> t.putString("captain", c.toString()));

            ListTag blockList = new ListTag();
            for (BlockEntry b : blocks) blockList.add(b.toTag());
            t.put("blocks", blockList);
            return t;
        }

        static OutpostEntry fromTag(CompoundTag t, HolderGetter<Block> blockLookup) {
            Optional<UUID> captain = Optional.empty();
            if (t.contains("captain", Tag.TAG_STRING)) {
                try {
                    captain = Optional.of(UUID.fromString(t.getString("captain")));
                } catch (IllegalArgumentException ignored) {
                }
            }

            ListTag blockList = t.getList("blocks", Tag.TAG_COMPOUND);
            List<BlockEntry> blocks = new ArrayList<>(blockList.size());
            for (int i = 0; i < blockList.size(); i++) {
                blocks.add(BlockEntry.fromTag(blockList.getCompound(i), blockLookup));
            }

            return new OutpostEntry(
                    t.getInt("x"),
                    t.getInt("z"),
                    t.getLong("queue_date"),
                    t.contains("wave_delay") ? t.getInt("wave_delay") : 3,
                    t.getBoolean("wave_due"),
                    t.getBoolean("wave_active"),
                    t.contains("wave_start_day") ? t.getLong("wave_start_day") : -1L,
                    t.getInt("wave_total"),
                    t.getInt("escalation"),
                    t.getBoolean("rebuilding"),
                    readUuids(t, "wave_pillagers"),
                    readUuids(t, "builders"),
                    readUuids(t, "escapees"),
                    captain,
                    blocks
            );
        }
    }

    private final Set<String> burntOutposts = new HashSet<>();
    private final Map<String, OutpostEntry> scheduledOutposts = new LinkedHashMap<>();
    private final Set<String> awards = new HashSet<>();
    private long lastDay = -1L;

    public SavedData() {}

    public static SavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SavedData data = new SavedData();
        HolderGetter<Block> blockLookup = registries.lookupOrThrow(Registries.BLOCK);

        ListTag burnt = tag.getList("burnt_outposts", Tag.TAG_STRING);
        for (int i = 0; i < burnt.size(); i++) data.burntOutposts.add(burnt.getString(i));

        ListTag awardList = tag.getList("awards", Tag.TAG_STRING);
        for (int i = 0; i < awardList.size(); i++) data.awards.add(awardList.getString(i));

        data.lastDay = tag.contains("last_day") ? tag.getLong("last_day") : -1L;

        ListTag scheduled = tag.getList("scheduled_outposts", Tag.TAG_COMPOUND);
        for (int i = 0; i < scheduled.size(); i++) {
            OutpostEntry entry = OutpostEntry.fromTag(scheduled.getCompound(i), blockLookup);
            data.scheduledOutposts.put(entry.key(), entry);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag burnt = new ListTag();
        for (String s : burntOutposts) burnt.add(StringTag.valueOf(s));
        tag.put("burnt_outposts", burnt);

        ListTag awardList = new ListTag();
        for (String s : awards) awardList.add(StringTag.valueOf(s));
        tag.put("awards", awardList);

        tag.putLong("last_day", lastDay);

        ListTag scheduled = new ListTag();
        for (OutpostEntry e : scheduledOutposts.values()) scheduled.add(e.toTag());
        tag.put("scheduled_outposts", scheduled);

        return tag;
    }

    public static SavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(new Factory<>(SavedData::new, SavedData::load, DataFixTypes.LEVEL), NAME);
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

    public boolean markAward(String key) {
        boolean added = awards.add(key);
        if (added) setDirty();
        return added;
    }

    public boolean unmarkAward(String key) {
        boolean removed = awards.remove(key);
        if (removed) setDirty();
        return removed;
    }

    public void removeOutpost(String key) {
        if (scheduledOutposts.remove(key) != null) setDirty();
    }
}
