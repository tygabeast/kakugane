package com.tygabeast.kakugane.core;

import com.tygabeast.kakugane.KakuganeConfig;
import com.tygabeast.kakugane.data.Archetype;
import com.tygabeast.kakugane.data.Hashing;
import com.tygabeast.kakugane.registry.ModRegistry;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Records how a player fights and decides which form fits them.
 * Signals are recorded from combat events (see event/CombatEvents), never from a tick.
 */
public final class Affinity {
    /** Weight of an action: full when the player had a real alternative, small when they had no choice. */
    public static double weight(boolean hadChoice, double base) {
        return hadChoice ? base : base * KakuganeConfig.NO_CHOICE_WEIGHT.get();
    }

    public static void record(ServerPlayer p, Archetype a, double weight) {
        if (weight <= 0) return;
        AffinityData d = p.getData(ModRegistry.AFFINITY.get());
        d.decayTo(now(p), halfLife());
        d.add(a.id(), weight);
    }

    /** True once enough real choices have been recorded for a Kakugane to know this player. */
    public static boolean ready(ServerPlayer p) {
        return p.getData(ModRegistry.AFFINITY.get()).recorded() >= KakuganeConfig.IMPRINT_THRESHOLD.get();
    }

    /**
     * The archetype that fits the player best right now. Ties (rare) are broken by the serial,
     * so the same Kakugane always resolves a tie the same way.
     */
    public static Optional<Archetype> pick(ServerPlayer p, int serial) {
        if (!ready(p)) return Optional.empty();
        AffinityData d = p.getData(ModRegistry.AFFINITY.get());
        d.decayTo(now(p), halfLife());
        Archetype best = null;
        double bestScore = -1;
        Archetype[] all = Archetype.values();
        int offset = Math.floorMod(Hashing.mix(serial), all.length);
        for (int i = 0; i < all.length; i++) {
            Archetype a = all[(i + offset) % all.length];
            double s = d.score(a.id());
            if (s > bestScore) {
                bestScore = s;
                best = a;
            }
        }
        return Optional.ofNullable(best);
    }

    public static long now(ServerPlayer p) {
        return p.server.overworld().getGameTime();
    }

    private static long halfLife() {
        return KakuganeConfig.AFFINITY_HALF_LIFE_DAYS.get() * 24000L;
    }

    private Affinity() {}
}
