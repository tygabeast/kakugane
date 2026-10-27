package com.tygabeast.kakugane.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Client memory of Blade Belt strikes, so the renderer can swing the right arm toward the target.
 * One small record per wearer seen striking; entries are dropped a few seconds after their last strike and the
 * whole map is cleared when the world changes, so it can't grow.
 */
public final class BladeArms {
    /** How long a strike animation lasts, in ticks. */
    public static final float STRIKE_TICKS = 7f;

    /** Per wearer: when each of the four arms last struck, and the direction it struck in (body-relative yaw). */
    public static final class State {
        public final long[] start = {-1000, -1000, -1000, -1000};
        public final float[] yaw = new float[4];
        int next;
        long last;
    }

    private static final Int2ObjectMap<State> STATES = new Int2ObjectOpenHashMap<>();
    private static ClientLevel level;

    public static void strike(ClientLevel lvl, int playerId, int targetId) {
        check(lvl);
        Entity p = lvl.getEntity(playerId), t = lvl.getEntity(targetId);
        if (!(p instanceof LivingEntity wearer)) return;
        State s = STATES.computeIfAbsent(playerId, k -> new State());
        float rel = 0;
        if (t != null) {
            double dx = t.getX() - wearer.getX(), dz = t.getZ() - wearer.getZ();
            float worldYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            rel = Mth.wrapDegrees(worldYaw - wearer.yBodyRot);
        }
        // Right-side arms (0, 1) strike to the right, left-side (2, 3) to the left; alternate front and back.
        boolean right = rel < 0;
        int arm = (right ? 0 : 2) + (s.next++ & 1);
        long now = lvl.getGameTime();
        s.start[arm] = now;
        s.yaw[arm] = rel;
        s.last = now;
    }

    /** The wearer's state, or null if they haven't struck lately. */
    public static State get(LivingEntity e) {
        if (e.level() != level) return null;
        return STATES.get(e.getId());
    }

    /** Called once a second from the client tick. */
    public static void cleanUp(ClientLevel lvl) {
        check(lvl);
        if (lvl == null || STATES.isEmpty()) return;
        long now = lvl.getGameTime();
        STATES.values().removeIf(s -> now - s.last > 100);
    }

    private static void check(ClientLevel lvl) {
        if (lvl != level) {
            STATES.clear();
            level = lvl;
        }
    }

    private BladeArms() {}
}
