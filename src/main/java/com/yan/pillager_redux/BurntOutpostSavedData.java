package com.yan.pillager_redux;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

public class BurntOutpostSavedData extends SavedData {

    private static final String NAME = "burnt_outposts";

    private final Set<String> burntOutposts = new HashSet<>();

    // Called when loading from disk
    public static BurntOutpostSavedData load(CompoundTag tag) {
        BurntOutpostSavedData data = new BurntOutpostSavedData();

        ListTag list = tag.getList("Outposts", Tag.TAG_STRING);
        for (Tag t : list) {
            data.burntOutposts.add(t.getAsString());
        }

        return data;
    }

    // Called when saving to disk
    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (String s : burntOutposts) {
            list.add(StringTag.valueOf(s));
        }
        tag.put("Outposts", list);
        return tag;
    }

    // Getter for world instance
    public static BurntOutpostSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                BurntOutpostSavedData::load,
                BurntOutpostSavedData::new,
                NAME
        );
    }

    public boolean isBurnt(String key) {
        return burntOutposts.contains(key);
    }

    public void markBurnt(String key) {
        burntOutposts.add(key);
        setDirty(); // REQUIRED or it won't save
    }
}