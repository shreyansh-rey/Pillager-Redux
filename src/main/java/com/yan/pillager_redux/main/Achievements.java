package com.yan.pillager_redux.main;

import com.yan.pillager_redux.devtools.Debug;
import com.yan.pillager_redux.setup.PillagerRedux;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public class Achievements {
    public static final String BE_GONE = "be_gone";
    public static final String STAY_GONE = "stay_gone";

    private static final String CRITERION = "criterion";

    public static void award(ServerLevel level, ServerPlayer player, String name) {
        AdvancementHolder holder = level.getServer().getAdvancements()
                .get(Identifier.fromNamespaceAndPath(PillagerRedux.MODID, name));

        if (holder == null) {
            Debug.LogWarning("Advancement not found: " + PillagerRedux.MODID + ":" + name
                    + " (missing json in data/" + PillagerRedux.MODID + "/advancement/ ?)");
            return;
        }

        if (player.getAdvancements().award(holder, CRITERION)) {
            Debug.Log("Awarded advancement " + name + " to " + player.getPlainTextName());
        }
    }

    public static List<ServerPlayer> awardNear(ServerLevel level, double x, double z, double radius, String name) {
        List<ServerPlayer> hit = new ArrayList<>();
        double maxSq = radius * radius;

        for (ServerPlayer p : level.players()) {
            if (!p.isAlive() || p.isSpectator()) continue;

            double dx = p.getX() - x;
            double dz = p.getZ() - z;
            if (dx * dx + dz * dz > maxSq) continue;

            hit.add(p);
            award(level, p, name);
        }

        return hit;
    }
}