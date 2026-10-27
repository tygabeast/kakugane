package com.tygabeast.kakugane.ability;

import com.tygabeast.kakugane.core.PlayerState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Gauntlets (original): each hit in a quick chain hits harder, and a full chain ends in a finisher.
 *
 *  - Bare fists hit hard: +3 damage, +1 per tier.
 *  - Combo: melee hits less than 1.5 seconds apart each add 6% damage, up to 4 hits (+1 per tier).
 *    Hits closer together than 6 ticks don't build the chain, so spam-clicking doesn't help.
 *  - At a full chain the next hit is a finisher: 50% more damage and heavy knockback. Tier III and up, the
 *    finisher also bursts into enemies within 3 blocks of the target (40% damage, staggered).
 *
 * Cost: arithmetic on each melee hit; the burst searches one small box, only on a finisher.
 */
public final class Gauntlets {

    /** Changes a melee hit's damage (called from the incoming-damage event). */
    public static float onMelee(ServerPlayer p, PlayerState st, LivingEntity target, float amount, long now) {
        if (p.getMainHandItem().isEmpty()) amount += 3 + st.activeTier;
        long since = now - st.lastMeleeTick;
        if (since > 30) st.combo = 0;
        int max = 4 + st.activeTier;
        if (st.combo >= max && since >= 6) {
            amount *= 1.5f;
            st.combo = 0;
            target.knockback(1.2, p.getX() - target.getX(), p.getZ() - target.getZ());
            p.level().playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 1.6f);
            p.serverLevel().sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY(0.5), target.getZ(), 1, 0, 0, 0, 0);
            if (st.activeTier >= 2) burst(p, target, amount * 0.4f);
            return amount;
        }
        amount *= 1f + 0.06f * st.combo;
        if (since >= 6 && st.combo < max) {
            st.combo++;
            p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.4f, 0.8f + 0.12f * st.combo);
        }
        return amount;
    }

    private static void burst(ServerPlayer p, LivingEntity center, float damage) {
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(3),
                e -> e != center && WornAbilities.isFoe(p, e))) {
            e.invulnerableTime = 0;
            if (e.hurt(Alchemy.source(p.serverLevel(), p), damage)) {
                e.knockback(0.6, center.getX() - e.getX(), center.getZ() - e.getZ());
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1), p);
            }
        }
    }

    private Gauntlets() {}
}
