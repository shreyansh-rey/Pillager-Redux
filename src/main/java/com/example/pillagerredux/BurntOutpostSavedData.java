package com.example.pillagerredux;

import net.minecraft.core.HolderLookup;
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

    public BurntOutpostSavedData() {}

    public static BurntOutpostSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        BurntOutpostSavedData data = new BurntOutpostSavedData();
        ListTag list = tag.getList("Outposts", Tag.TAG_STRING);
        for (Tag t : list) {
            data.burntOutposts.add(t.getAsString());
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (String s : burntOutposts) {
            list.add(StringTag.valueOf(s));
        }
        tag.put("Outposts", list);
        return tag;
    }

    public static final SavedData.Factory<BurntOutpostSavedData> FACTORY = new SavedData.Factory<>(
            BurntOutpostSavedData::new,
            BurntOutpostSavedData::load,
            null
    );

    public static BurntOutpostSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public boolean isBurnt(String key) {
        return burntOutposts.contains(key);
    }

    public void markBurnt(String key) {
        burntOutposts.add(key);
        setDirty();
    }
}