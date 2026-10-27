package com.tygabeast.kakugane.ability;

import com.tygabeast.kakugane.core.KakuganeAccess;
import com.tygabeast.kakugane.core.PlayerState;
import com.tygabeast.kakugane.data.KakuganeData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Lance (inspired by Sunlight Heart): hold right-click to charge, release to dash along your view,
 * striking everything you pass through. Longer charge = faster, harder dash. Tiers add speed and damage.
 */
public final class Lance {
    public static final int FULL_CHARGE_TICKS = 30;
    private static final int DASH_TICKS = 10;

    public static void release(Player p, ItemStack stack, KakuganeData d, int usedTicks) {
        float charge = Math.min(1f, usedTicks / (float) FULL_CHARGE_TICKS);
        if (charge < 0.25f) return;
        Vec3 look = p.getLookAngle();
        double speed = 0.8 + 1.4 * charge + 0.15 * d.tier();
        // Mostly horizontal, with a small lift so the dash does not scrape the ground.
        p.setDeltaMovement(look.x * speed, Math.max(-0.2, look.y * speed * 0.5) + 0.15, look.z * speed);

        if (p instanceof ServerPlayer sp) {
            sp.hurtMarked = true; // sends the new velocity to the client
            PlayerState st = KakuganeAccess.state(sp);
            st.dashTicks = DASH_TICKS;
            st.dashDamage = 4f + 6f * charge + 1.5f * d.tier();
            st.dashSource = Alchemy.source(sp.serverLevel(), sp);
            st.dashHits.clear();
            sp.getCooldowns().addCooldown(stack.getItem(), 25);
            sp.level().playSound(null, sp.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 0.9f, 1.3f);
        }
    }

    /**
     * Runs each tick only while a dash is in progress (10 ticks), for that one player.
     * One small box search per tick; each target is hit at most once per dash.
     */
    public static void tickDash(ServerPlayer p, PlayerState st) {
        st.dashTicks--;
        p.fallDistance = 0;
        ServerLevel level = p.serverLevel();
        DamageSource src = st.dashSource != null ? st.dashSource : Alchemy.source(level, p);
        AABB box = p.getBoundingBox().inflate(0.9);
        Vec3 motion = p.getDeltaMovement();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive() && !e.isAlliedTo(p)
                && !(e instanceof TamableAnimal t && t.isOwnedBy(p)))) {
            if (st.dashHits.add(e.getId()) && e.hurt(src, st.dashDamage)) {
                e.knockback(0.6, -motion.x, -motion.z);
            }
        }
        if ((st.dashTicks & 1) == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.9, p.getZ(), 4, 0.2, 0.3, 0.2, 0.05);
        }
        if (st.dashTicks <= 0) st.dashHits.clear();
    }

    private Lance() {}
}
