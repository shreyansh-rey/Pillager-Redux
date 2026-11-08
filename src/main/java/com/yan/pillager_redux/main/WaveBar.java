package com.yan.pillager_redux.main;

import com.yan.pillager_redux.containers.Outpost;
import com.yan.pillager_redux.devtools.Debug;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WaveBar {
    public static final Component TITLE = Component.literal("Reconquest");

    public static final double VIEW_RADIUS = 128.0;

    private static final Map<String, ServerBossEvent> BARS = new HashMap<>();

    public static void update(ServerLevel level, Outpost outpost) {
        if (!outpost.isWaveActive()) {
            remove(outpost);
            return;
        }

        ServerBossEvent bar = BARS.get(outpost.getUuid());
        if (bar == null) {
            bar = new ServerBossEvent(UUID.randomUUID(), TITLE, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
            BARS.put(outpost.getUuid(), bar);
            Debug.Log("Reconquest bar created for Outpost " + outpost.getUuid());
        }

        int alive = outpost.getWavePillagerCount();
        float total = Math.max(1, Math.max(outpost.getWaveTotal(), alive));
        bar.setProgress(Mth.clamp(alive / total, 0.0F, 1.0F));

        double maxSq = VIEW_RADIUS * VIEW_RADIUS;
        List<ServerPlayer> online = level.players();

        for (ServerPlayer player : online) {
            double dx = player.getX() - outpost.getCenterX();
            double dz = player.getZ() - outpost.getCenterZ();

            if (player.isAlive() && dx * dx + dz * dz <= maxSq) {
                bar.addPlayer(player);
            } else {
                bar.removePlayer(player);
            }
        }

        for (ServerPlayer old : new ArrayList<>(bar.getPlayers())) {
            if (!online.contains(old)) bar.removePlayer(old);
        }
    }

    public static void remove(Outpost outpost) {
        ServerBossEvent bar = BARS.remove(outpost.getUuid());
        if (bar == null) return;

        bar.removeAllPlayers();
        bar.setVisible(false);
        Debug.Log("Reconquest bar removed for Outpost " + outpost.getUuid());
    }

    public static void clearAll() {
        for (ServerBossEvent bar : BARS.values()) {
            bar.removeAllPlayers();
            bar.setVisible(false);
        }
        BARS.clear();
    }
}