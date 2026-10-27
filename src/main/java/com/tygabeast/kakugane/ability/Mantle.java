package com.tygabeast.kakugane.ability;

import com.tygabeast.kakugane.core.PlayerState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;

/**
 * Mantle (original): a cloak that fades the wearer from view.
 *
 *  - Stand still for 2 seconds and you vanish (Invisibility; mobs further than 3 blocks lose you).
 *    From Tier III, sneaking keeps you hidden while you move.
 *  - Your first hit from stealth deals 50% more (+25% per tier) and reveals you.
 *  - Tier IV: a kill straight after a stealth hit makes nearby enemies lose track of you and you fade again at once.
 *
 * Cost: every 4 ticks, a position compare; one entity search when you vanish (and after a Tier IV kill).
 */
public final class Mantle {
    private static final int STILL_CHECKS = 10; // 10 checks x 4 ticks = 2 seconds

    public static void tick(ServerPlayer p, PlayerState st, long now) {
        double dx = p.getX() - st.lastX, dy = p.getY() - st.lastY, dz = p.getZ() - st.lastZ;
        st.lastX = p.getX();
        st.lastY = p.getY();
        st.lastZ = p.getZ();
        double moved = dx * dx + dy * dy + dz * dz;
        boolean sneakingAllowed = st.activeTier >= 2 && p.isShiftKeyDown() && moved < 0.5;
        if (moved < 0.0004 || sneakingAllowed) {
            if (st.stillChecks < STILL_CHECKS) st.stillChecks++;
        } else {
            st.stillChecks = 0;
            if (st.stealthed) reveal(p, st);
        }
        if (!st.stealthed && st.stillChecks >= STILL_CHECKS) vanish(p, st);
        if (st.stealthed) {
            MobEffectInstance inv = p.getEffect(MobEffects.INVISIBILITY);
            if (inv == null || inv.getDuration() < 10) p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, true, false, false));
        }
    }

    private static void vanish(ServerPlayer p, PlayerState st) {
        st.stealthed = true;
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, true, false, false));
        loseTrack(p, 3);
        p.serverLevel().sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1, p.getZ(), 12, 0.3, 0.5, 0.3, 0.01);
        p.level().playSound(null, p.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.4f, 1.4f);
    }

    /** Mobs within 16 blocks that are hunting the wearer and further than minDistance away lose the trail. */
    private static void loseTrack(ServerPlayer p, double minDistance) {
        double min2 = minDistance * minDistance;
        for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), m -> m.getTarget() == p)) {
            if (m.distanceToSqr(p) > min2) m.setTarget(null);
        }
    }

    public static void reveal(ServerPlayer p, PlayerState st) {
        st.stealthed = false;
        st.stillChecks = 0;
        removeOwnInvisibility(p);
    }

    /** Called when the wearer hits something: the stealth bonus, and the reveal. Returns the new damage. */
    public static float onAttack(ServerPlayer p, PlayerState st, float amount, long now) {
        if (!st.stealthed) return amount;
        reveal(p, st);
        st.lastStealthStrike = now;
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1f, 0.7f);
        return amount * (1.5f + 0.25f * st.activeTier);
    }

    /** Tier IV: a kill within half a second of a stealth hit sends you straight back into hiding. */
    public static void onKill(ServerPlayer p, PlayerState st, long now) {
        if (st.activeTier < 3 || now - st.lastStealthStrike > 10) return;
        loseTrack(p, 0);
        st.stillChecks = STILL_CHECKS;
        vanish(p, st);
    }

    public static void end(ServerPlayer p, PlayerState st) {
        if (st.stealthed) removeOwnInvisibility(p);
    }

    /** Only the cloak's own invisibility (ambient, short); a potion's is left alone. */
    private static void removeOwnInvisibility(ServerPlayer p) {
        MobEffectInstance inv = p.getEffect(MobEffects.INVISIBILITY);
        if (inv != null && inv.isAmbient() && inv.getDuration() <= 30) p.removeEffect(MobEffects.INVISIBILITY);
    }

    private Mantle() {}
}
