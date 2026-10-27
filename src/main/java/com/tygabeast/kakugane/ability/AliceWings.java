package com.tygabeast.kakugane.ability;

import com.tygabeast.kakugane.KakuganeConfig;
import com.tygabeast.kakugane.core.PlayerState;
import com.tygabeast.kakugane.data.Archetype;
import com.tygabeast.kakugane.network.ModNetwork;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Alice in Wonderland (canon): wings of metallic particles on the wearer's back.
 *
 * True flight (double-tap jump, as in creative) powered by a reserve that refills on the ground:
 *  - climbing drains fastest, hovering steadily, level flight less, descending almost nothing;
 *  - each hit taken in the air knocks a chunk off;
 *  - running dry: flight cuts out and the wings slow the fall to a glide; no flying again until you land and
 *    the reserve is back to a fifth.
 * Tier IV: the ability key scatters the wings into a blinding cloud (enemies lose you), then they reform.
 *
 * Flight is granted and revoked by the server (the "may fly" ability), so the server never kicks a player for
 * flying. Cost: per tick only while the wings are active, a few arithmetic operations; the HUD bar is synced at
 * most 4 times a second and only while the reserve changes.
 */
public final class AliceWings {
    private static final String FLIGHT_TAG = "kakugane_wing_flight";

    public static float max(int tier) {
        return KakuganeConfig.WING_RESERVE_SECONDS.get() * 20f * (1f + tier / 3f);
    }

    private static float refillPerTick(int tier) {
        return max(tier) / (KakuganeConfig.WING_REFILL_SECONDS.get() * 20f) * (1f + 0.25f * tier);
    }

    public static void tick(ServerPlayer p, PlayerState st, long now) {
        float max = max(st.activeTier);
        if (st.wingsFresh) {
            st.wingsFresh = false;
            if (st.wingReserve < 0) st.wingReserve = max;
            st.wingReserve = Math.min(st.wingReserve, max); // a lower-tier Kakugane holds less
            st.lastX = p.getX();
            st.lastY = p.getY();
            st.lastZ = p.getZ();
        }
        double dy = p.getY() - st.lastY, dx = p.getX() - st.lastX, dz = p.getZ() - st.lastZ;
        st.lastX = p.getX();
        st.lastY = p.getY();
        st.lastZ = p.getZ();
        if (p.isCreative() || p.isSpectator()) return; // their own flight; the wings don't interfere

        KakuganeConfig.FlightMode mode = KakuganeConfig.FLIGHT_MODE.get();
        float before = st.wingReserve;
        if (p.getAbilities().flying) {
            float cost = dy > 0.05 ? 3f : dy < -0.05 ? 0.2f : (dx * dx + dz * dz < 0.0025 ? 1.5f : 1f);
            st.wingReserve -= cost;
            if (st.wingReserve <= 0) {
                st.wingReserve = 0;
                st.wingsDry = true;
                p.displayClientMessage(Component.translatable("message.kakugane.wings_dry"), true);
            }
        } else if (p.onGround() && st.wingReserve < max) {
            st.wingReserve = Math.min(max, st.wingReserve + refillPerTick(st.activeTier));
        }
        if (st.wingsDry && p.onGround() && st.wingReserve >= 0.2f * max) st.wingsDry = false;

        boolean canFly = mode == KakuganeConfig.FlightMode.TRUE_FLIGHT && !st.wingsDry && st.wingReserve > 0
                && now >= st.wingsScatteredUntil && !blacklisted(p);
        // Also re-grant if something else switched it off (a game-mode change resets "may fly").
        if (canFly && (!st.flightGranted || !p.getAbilities().mayfly)) grant(p, st);
        else if (!canFly && st.flightGranted) revoke(p, st);

        // Gliding: when not flying, the wings slow a fall (re-applied every half second while airborne).
        if (mode != KakuganeConfig.FlightMode.OFF && !p.getAbilities().flying && !p.onGround() && p.fallDistance > 1.5f
                && !p.isShiftKeyDown() && !p.isInWater() && (p.tickCount % 10) == 0) {
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 15, 0, true, false, false));
        }

        // HUD sync: at most every 5 ticks, and only when the reserve differs from what the client was last told.
        if (st.wingSyncCooldown > 0) st.wingSyncCooldown--;
        if (st.wingSyncCooldown <= 0 && Math.abs(st.wingReserve - st.wingReserveSent) > 0.01f) {
            st.wingSyncCooldown = 5;
            st.wingReserveSent = st.wingReserve;
            PacketDistributor.sendToPlayer(p, new ModNetwork.WingReserve(st.wingReserve, max));
        }
    }

    /** A hit taken while flying costs an eighth of the reserve (called from the damage event). */
    public static void onHurt(ServerPlayer p, PlayerState st) {
        if (!p.getAbilities().flying || st.wingReserve <= 0 || p.isCreative()) return;
        st.wingReserve = Math.max(0, st.wingReserve - max(st.activeTier) / 8f);
        st.wingSyncCooldown = 0; // show the hit on the bar straight away
    }

    /** Tier IV ability: scatter into a blinding cloud. Nearby enemies lose track of you; no flight for 3 seconds. */
    public static void scatter(ServerPlayer p, PlayerState st) {
        if (st.activeTier < 3) {
            p.displayClientMessage(Component.translatable("message.kakugane.needs_tier", 4), true);
            return;
        }
        long now = com.tygabeast.kakugane.core.Affinity.now(p);
        if (now < st.scatterReadyAt) {
            p.displayClientMessage(Component.translatable("message.kakugane.cooldown", (st.scatterReadyAt - now + 19) / 20), true);
            return;
        }
        st.scatterReadyAt = now + 600;
        st.wingsScatteredUntil = now + 60;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(6), e -> e != p && e.isAlive())) {
            if (e instanceof Mob m && WornAbilities.isFoe(p, m)) {
                if (m.getTarget() == p) m.setTarget(null);
                m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), p);
                m.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), p);
            } else if (e instanceof Player other && !p.isAlliedTo(other) && p.getTeam() != null) {
                other.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), p);
            }
        }
        p.serverLevel().sendParticles(ParticleTypes.WHITE_ASH, p.getX(), p.getY() + 1.2, p.getZ(), 120, 2.5, 1.5, 2.5, 0.05);
        p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.2, p.getZ(), 40, 2.0, 1.2, 2.0, 0.08);
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0f, 1.4f);
    }

    public static void end(ServerPlayer p, PlayerState st) {
        if (st.flightGranted || p.getPersistentData().getBoolean(FLIGHT_TAG)) revoke(p, st);
        st.wingReserveSent = -1;
        PacketDistributor.sendToPlayer(p, new ModNetwork.WingReserve(-1, 1));
    }

    public static void cleanUpAfterRejoin(ServerPlayer p, PlayerState st) {
        if (st.activeForm == Archetype.ALICE && p.getPersistentData().getBoolean(FLIGHT_TAG)) {
            st.flightGranted = true; // still ours; the tick below keeps or takes it as the reserve allows
            return;
        }
        if (p.getPersistentData().getBoolean(FLIGHT_TAG)) {
            p.getPersistentData().remove(FLIGHT_TAG);
            if (!p.isCreative() && !p.isSpectator()) {
                p.getAbilities().mayfly = false;
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
            }
        }
    }

    private static void grant(ServerPlayer p, PlayerState st) {
        st.flightGranted = true;
        p.getPersistentData().putBoolean(FLIGHT_TAG, true);
        p.getAbilities().mayfly = true;
        p.onUpdateAbilities();
    }

    private static void revoke(ServerPlayer p, PlayerState st) {
        st.flightGranted = false;
        p.getPersistentData().remove(FLIGHT_TAG);
        if (p.isCreative() || p.isSpectator()) return;
        p.getAbilities().mayfly = false;
        p.getAbilities().flying = false;
        p.onUpdateAbilities();
    }

    private static boolean blacklisted(ServerPlayer p) {
        var list = KakuganeConfig.FLIGHT_BLACKLIST.get();
        return !list.isEmpty() && list.contains(p.level().dimension().location().toString());
    }

    private AliceWings() {}
}
