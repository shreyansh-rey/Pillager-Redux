package com.example.pillagerredux;

import net.minecraft.world.level.ChunkPos;

public class Outpost {
    private boolean isDestroyed;
    private ChunkPos chunkPos;
    private String UID;
    private int garrisonStrength;

    public Outpost(boolean destroyed, ChunkPos pos, String id, int strength){
        this.isDestroyed = destroyed;
        this.chunkPos = pos;
        this.UID = id;
        this.garrisonStrength = strength;
    }

    public void setDestroyed(boolean destroyed){
        this.isDestroyed = destroyed;
    }
    public void setGarrisonStrength(int strength){
        this.garrisonStrength = strength;
    }

    public String getUID() {
        return UID;
    }
    public int getGarrisonStrength(){
        return garrisonStrength;
    }
    public ChunkPos getChunkPos(){
        return chunkPos;
    }
    public boolean isDestroyed(){
        return isDestroyed;
    }
}