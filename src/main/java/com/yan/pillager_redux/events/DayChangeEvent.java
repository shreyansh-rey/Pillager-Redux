package com.yan.pillager_redux.events;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

public class DayChangeEvent extends Event {
    ServerLevel level;
    long day;

    public DayChangeEvent(ServerLevel level, long day) {
        this.level = level;
        this.day = day;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public long getDay() {
        return day;
    }
}