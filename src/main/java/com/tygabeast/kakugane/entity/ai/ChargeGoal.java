package com.tygabeast.kakugane.entity.ai;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Ram-style charge: plant the feet and glare (wind-up), then run straight at where the target was, stunning it on
 * impact. Dodging sideways during the wind-up makes it miss. Used by the Ram and the Ram-motif Lab Leader.
 * Only runs while the mob has a target in range; costs one bounding-box test per tick while charging.
 */
public class ChargeGoal extends Goal {
    /** Mobs using this goal show their state (0 idle, 1 wind-up, 2 charging) to the client for animation. */
    public interface Charger {
        void setChargeState(int state);
    }

    private final Mob mob;
    private final float damage;
    private final double speed;
    private int windup, charging;
    private long readyAt;
    private Vec3 dir = Vec3.ZERO;
    private boolean hit;

    public ChargeGoal(Mob mob, float damage, double speed) {
        this.mob = mob;
        this.damage = damage;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        // Goals are polled every other tick, so the cooldown is a game time rather than a countdown.
        if (mob.level().getGameTime() < readyAt) return false;
        LivingEntity t = mob.getTarget();
        if (t == null || !t.isAlive() || !mob.onGround()) return false;
        double d = mob.distanceToSqr(t);
        return d > 16 && d < 256 && mob.getSensing().hasLineOfSight(t);
    }

    @Override
    public boolean canContinueToUse() {
        return windup > 0 || charging > 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        windup = 18;
        charging = 0;
        hit = false;
        mob.getNavigation().stop();
        state(1);
        mob.playSound(SoundEvents.RAVAGER_ROAR, 0.6f, 1.4f);
    }

    @Override
    public void tick() {
        LivingEntity t = mob.getTarget();
        if (windup > 0) {
            windup--;
            if (t != null) mob.getLookControl().setLookAt(t, 30f, 30f);
            mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
            if (mob.level() instanceof ServerLevel sl && windup % 4 == 0) {
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, mob.getBlockStateOn()),
                        mob.getX(), mob.getY() + 0.1, mob.getZ(), 4, 0.3, 0.05, 0.3, 0.05);
            }
            if (windup == 0) {
                if (t == null) return;
                Vec3 to = t.position().subtract(mob.position());
                dir = new Vec3(to.x, 0, to.z).normalize();
                charging = 26;
                state(2);
            }
            return;
        }
        charging--;
        mob.setYRot((float) (Math.atan2(dir.z, dir.x) * (180 / Math.PI)) - 90f);
        mob.yBodyRot = mob.getYRot();
        mob.setDeltaMovement(dir.x * speed, mob.getDeltaMovement().y, dir.z * speed);
        if (t != null && !hit && mob.getBoundingBox().inflate(0.35).intersects(t.getBoundingBox())) {
            hit = true;
            if (t.hurt(mob.damageSources().mobAttack(mob), damage)) {
                t.knockback(1.6, -dir.x, -dir.z);
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3), mob);
                t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1), mob);
                mob.playSound(SoundEvents.RAVAGER_ATTACK, 1f, 0.9f);
            }
            charging = 0;
        } else if (mob.horizontalCollision) {
            mob.playSound(SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 0.8f, 0.7f);
            charging = 0;
        }
    }

    @Override
    public void stop() {
        windup = 0;
        charging = 0;
        readyAt = mob.level().getGameTime() + 80 + mob.getRandom().nextInt(40);
        state(0);
    }

    private void state(int s) {
        if (mob instanceof Charger c) c.setChargeState(s);
    }
}
