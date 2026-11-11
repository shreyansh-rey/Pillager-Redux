package com.yan.pillager_redux.main;

import com.yan.pillager_redux.devtools.Debug;
import com.yan.pillager_redux.setup.PillagerRedux;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public class Achievements {
    public static final String BE_GONE = "be_gone";
    public static final String STAY_GONE = "stay_gone";

    private static final String[] ALL = {"root", BE_GONE, STAY_GONE};

    private static final String CRITERION = "criterion";

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(PillagerRedux.MODID, name);
    }

    private static AdvancementHolder find(MinecraftServer server, String name) {
        return server.getAdvancements().get(id(name));
    }

    public static void verify(MinecraftServer server) {
        for (String name : ALL) {
            if (find(server, name) != null) {
                Debug.Log("Advancement loaded: " + id(name));
            } else {
                Debug.LogWarning("Advancement NOT loaded: " + id(name)
                        + " (expected data/" + PillagerRedux.MODID + "/advancement/" + name
                        + ".json in mod resources, or json has an error: check latest.log)");
            }
        }
    }

    private static String title(String name) {
        return switch (name) {
            case BE_GONE -> "Be Gone!";
            case STAY_GONE -> "Stay Gone!";
            default -> name;
        };
    }

    private static String description(String name) {
        return switch (name) {
            case BE_GONE -> "Destroyed a Pillager Outpost";
            case STAY_GONE -> "Defend against a reconquest raid";
            default -> "";
        };
    }

    public static int award(ServerLevel level, ServerPlayer player, String name) {
        AdvancementHolder holder = find(level.getServer(), name);

        if (holder == null) return awardFallback(level, player, name);

        if (player.getAdvancements().award(holder, CRITERION)) {
            Debug.Log("Awarded advancement " + name + " to " + player.getPlainTextName());
            return 1;
        }

        Debug.Log(player.getPlainTextName() + " already has advancement " + name);
        return 0;
    }

    private static int awardFallback(ServerLevel level, ServerPlayer player, String name) {
        Debug.LogWarning("Advancement " + id(name) + " not loaded, using chat fallback");

        String key = player.getUUID() + ":" + name;
        if (!SavedData.get(level).markAward(key)) return 0;

        Component msg = Component.literal(player.getPlainTextName() + " has made the advancement ["
                + title(name) + "] - " + description(name));

        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg, false);
        }

        Debug.Log("Fallback achievement " + name + " given to " + player.getPlainTextName());
        return 2;
    }

    public static boolean revoke(ServerLevel level, ServerPlayer player, String name) {
        AdvancementHolder holder = find(level.getServer(), name);

        boolean revoked;
        if (holder == null) {
            revoked = SavedData.get(level).unmarkAward(player.getUUID() + ":" + name);
        } else {
            revoked = player.getAdvancements().revoke(holder, CRITERION);
        }

        Debug.Log("Revoked advancement " + name + " from " + player.getPlainTextName() + ": " + revoked);
        return revoked;
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