package com.yan.pillager_redux;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = PillagerRedux.MODID)
public class Debug {

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        Log("Config loaded.");
    }

    public static void Log(String message){
        System.out.println("[Debug] " + message);
    }

    public static void LogCoOrds(float x, float y, float z){
        Log("X: " + x + ", Y: " + y + ", Z: " + z);
    }
}