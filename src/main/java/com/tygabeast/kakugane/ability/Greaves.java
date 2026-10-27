package com.tygabeast.kakugane.ability;

import com.tygabeast.kakugane.core.KakuganeAccess;
import com.tygabeast.kakugane.core.Affinity;
import com.tygabeast.kakugane.core.PlayerState;
import com.tygabeast.kakugane.data.Archetype;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Greaves (original): an air dash with a damaging kick.
 *
 *  - Press jump in mid-air to dash toward where you look; anything you hit in the next 8 ticks is kicked.
 *    One dash per jump, two from Tier III.
 *  - Tier IV: sneak in mid-air for a diving stomp; landing sends out a shockwave and takes no fall damage.
 *
 * Player movement is decided by the client (client/GreavesControls moves the player and sends GreavesMove);
 * the server only counts the dashes and deals the damage. Cost: per tick, a field check; a small box search
 * only during the 8 kick ticks; one search on a stomp landing.
 */
public final class Greaves {

    public static int maxDashes(int tier) {
        return tier >= 2 ? 2 : 1;
    }

    public static float kickDamage(int tier) {
        return 4f + 1.5f * tier;
    }

    /** From the client: kind 0 = dash, 1 = stomp. Ignored unless the Greaves are active and the move is allowed. */
    public static void onMove(ServerPlayer p, byte kind) {
        PlayerState st = KakuganeAccess.state(p);
        if (st.activeForm != Archetype.GREAVES || p.onGround()) return;
        if (kind == 0 && st.airDashes > 0) {
            st.airDashes--;
            st.kickTicks = 8;
            st.kickHits.clear();
            p.fallDistance = 0;
            p.level().playSound(null, p.blockPosition(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.7f, 1.3f);
            p.serverLevel().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 6, 0.2, 0.1, 0.2, 0.02);
        } else if (kind == 1 && st.activeTier >= 3 && !st.stomping && !p.isInWater() && !p.onClimbable()
                && !p.getAbilities().flying && !p.isFallFlying() && Affinity.now(p) >= st.stompReadyAt) {
            st.stomping = true;
            p.level().playSound(null, p.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.8f, 0.6f);
        }
    }

    public static void tick(ServerPlayer p, PlayerState st) {
        // A stomp only counts if it ends on the ground: water, a ladder or flight cancel it (and its fall immunity).
        if (st.stomping && (p.isInWater() || p.onClimbable() || p.getAbilities().flying)) st.stomping = false;
        if (p.onGround()) {
            if (st.airDashes != maxDashes(st.activeTier)) {
                st.airDashes = maxDashes(st.activeTier);
            }
            if (st.stomping) {
                st.stomping = false;
                st.stompReadyAt = Affinity.now(p) + 40; // two seconds between shockwaves
                shockwave(p, st);
            }
        }
        if (st.kickTicks <= 0) return;
        st.kickTicks--;
        p.fallDistance = 0;
        Vec3 dir = p.getLookAngle();
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(0.8),
                e -> e != p && WornAbilities.isFoe(p, e))) {
            if (!st.kickHits.add(e.getId())) continue;
            e.invulnerableTime = 0;
            if (e.hurt(Alchemy.source(p.serverLevel(), p), kickDamage(st.activeTier))) {
                e.knockback(1.0, -dir.x, -dir.z);
                p.level().playSound(null, e.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1f, 0.8f);
            }
        }
        if (st.kickTicks == 0) st.kickHits.clear();
    }

    private static void shockwave(ServerPlayer p, PlayerState st) {
        ServerLevel level = p.serverLevel();
        double r = 4 + 0.5 * st.activeTier;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r, 1.5, r),
                e -> e != p && WornAbilities.isFoe(p, e))) {
            e.invulnerableTime = 0;
            if (e.hurt(Alchemy.source(level, p), 6f + st.activeTier)) {
                e.push(0, 0.5, 0);
                e.knockback(0.8, p.getX() - e.getX(), p.getZ() - e.getZ());
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), p);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.1, p.getZ(), 3, r / 3, 0.1, r / 3, 0);
        level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 30, r / 2, 0.1, r / 2, 0.08);
        level.playSound(null, p.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1f, 0.8f);
    }

    /** The stomp's own landing takes no fall damage (called from the fall event). */
    public static boolean cancelsFall(PlayerState st) {
        return st.activeForm == Archetype.GREAVES && st.stomping;
    }

    private Greaves() {}
}
