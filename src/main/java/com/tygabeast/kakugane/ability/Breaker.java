package com.tygabeast.kakugane.ability;

import com.tygabeast.kakugane.data.KakuganeData;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Breaker (original): a great hammer. Slow, heavy swings, and right-click on the ground for a slam that
 * damages and throws back everything nearby. Tiers widen the slam, hit harder and shorten the cooldown.
 */
public final class Breaker {
    public static void slam(ServerPlayer p, ItemStack stack, KakuganeData d) {
        ServerLevel level = p.serverLevel();
        double r = 3.5 + 0.5 * d.tier();
        float damage = 5f + 2f * d.tier();
        DamageSource src = Alchemy.source(level, p);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r, 1.5, r),
                e -> e != p && e.isAlive() && !e.isAlliedTo(p) && !(e instanceof TamableAnimal t && t.isOwnedBy(p))
                        && e.distanceToSqr(p) <= r * r)) {
            if (e.hurt(src, damage)) {
                e.knockback(0.9, p.getX() - e.getX(), p.getZ() - e.getZ());
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.35, 0));
                e.hurtMarked = true;
            }
        }
        BlockState below = level.getBlockState(p.blockPosition().below());
        if (!below.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), p.getX(), p.getY() + 0.1, p.getZ(),
                    40, r * 0.4, 0.05, r * 0.4, 0.15);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.2, p.getZ(), 3, r * 0.3, 0.05, r * 0.3, 0);
        level.playSound(null, p.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0f, 0.8f);
        p.getCooldowns().addCooldown(stack.getItem(), 80 - 10 * d.tier());
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }

    private Breaker() {}
}
