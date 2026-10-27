package com.tygabeast.kakugane.ability;

import com.tygabeast.kakugane.KakuganeConfig;
import com.tygabeast.kakugane.core.PlayerState;
import com.tygabeast.kakugane.network.ModNetwork;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Blade Belt (Valkyrie Skirt): cuffs on the thighs carrying four long blade-arms.
 *
 *  - Follow-ups: each melee hit you land (at most every 8 ticks) brings an arm strike on the same target. From
 *    Tier II, spare arms (one per tier) strike other enemies within reach at the same time.
 *  - On their own (config, on by default): while you're in a fight, an arm lashes at the nearest enemy in reach
 *    every 15 ticks.
 *  - Tier IV: a 20% chance to parry a melee hit from the front or sides, with an arm striking back.
 *
 * Arms are drawn on the player model (client/BladeBeltRenderer), never entities; each strike sends one small
 * packet so nearby clients can swing the arm. Cost: the follow-up searches one box per hit (only from Tier II);
 * the arms' own strikes search one box every 5 ticks, only during a fight.
 */
public final class BladeBelt {

    public static double reach(int tier) {
        return 3.0 + 0.5 * tier;
    }

    public static float armDamage(int tier) {
        return 2.0f + 0.5f * tier;
    }

    /** After the wearer's melee hit lands. */
    public static void onMelee(ServerPlayer p, PlayerState st, LivingEntity target, long now) {
        if (now < st.bladeReadyAt) return;
        st.bladeReadyAt = now + 8;
        strike(p, st, target);
        int spare = st.activeTier; // tier 0: none; tier III (2): two more
        if (spare <= 0) return;
        List<LivingEntity> others = p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(reach(st.activeTier)),
                e -> e != target && WornAbilities.isFoe(p, e));
        others.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
        for (int i = 0; i < Math.min(spare, others.size()); i++) strike(p, st, others.get(i));
    }

    /** Every tick for wearers: cheap checks first; the search runs at most every 5 ticks, only in a fight. */
    public static void tick(ServerPlayer p, PlayerState st, long now) {
        if (now < st.nextAutoStrike || (p.tickCount % 5) != 0 || !KakuganeConfig.BLADE_AUTO_STRIKES.get()) return;
        if (now - st.lastHurtTick > 100 && now - st.lastMeleeTick > 100) return;
        double r = reach(st.activeTier);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), e -> WornAbilities.isFoe(p, e))) {
            double d = e.distanceToSqr(p);
            if (d < bestD && p.hasLineOfSight(e)) {
                best = e;
                bestD = d;
            }
        }
        if (best == null) return;
        st.nextAutoStrike = now + 15;
        strike(p, st, best);
    }

    /**
     * Tier IV parry: true if the hit is blocked (the event is then cancelled). Only melee hits, only from in front
     * or the sides (not from behind), 20% of the time.
     */
    public static boolean tryParry(ServerPlayer p, PlayerState st, DamageSource src) {
        if (st.activeTier < 3 || !(src.getDirectEntity() instanceof LivingEntity attacker) || attacker == p) return false;
        Vec3 look = p.getViewVector(1f);
        Vec3 to = attacker.position().subtract(p.position());
        Vec3 flatLook = new Vec3(look.x, 0, look.z), flatTo = new Vec3(to.x, 0, to.z);
        if (flatLook.lengthSqr() < 1e-4 || flatTo.lengthSqr() < 1e-4) return false;
        if (flatLook.normalize().dot(flatTo.normalize()) < -0.25) return false; // from behind
        if (p.getRandom().nextFloat() >= 0.2f) return false;
        p.level().playSound(null, p.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1f, 1.3f);
        p.serverLevel().sendParticles(ParticleTypes.CRIT, attacker.getX(), attacker.getY(0.6), attacker.getZ(), 8, 0.2, 0.2, 0.2, 0.2);
        if (WornAbilities.isFoe(p, attacker)) strike(p, st, attacker);
        return true;
    }

    private static void strike(ServerPlayer p, PlayerState st, LivingEntity target) {
        target.invulnerableTime = 0; // an arm is its own blade: don't let the wearer's own hit block it
        if (target.hurt(Alchemy.source(p.serverLevel(), p), armDamage(st.activeTier))) {
            target.knockback(0.25, p.getX() - target.getX(), p.getZ() - target.getZ());
        }
        p.level().playSound(null, target.blockPosition(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.5f, 1.5f);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new ModNetwork.BladeStrike(p.getId(), target.getId()));
    }

    private BladeBelt() {}
}
