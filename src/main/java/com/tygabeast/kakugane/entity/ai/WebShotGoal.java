package com.tygabeast.kakugane.entity.ai;

import com.tygabeast.kakugane.entity.WebShot;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Spits a slowing web at a target 3 to 14 blocks away, then waits 3 to 5 seconds. One projectile per use.
 * Used by the Crawler and the Crawler-motif Lab Leader.
 */
public class WebShotGoal extends Goal {
    private final Mob mob;
    private final float damage;
    private long readyAt;

    public WebShotGoal(Mob mob, float damage) {
        this.mob = mob;
        this.damage = damage;
    }

    @Override
    public boolean canUse() {
        // Goals are polled every other tick, so the cooldown is a game time rather than a countdown.
        if (mob.level().getGameTime() < readyAt) return false;
        LivingEntity t = mob.getTarget();
        if (t == null || !t.isAlive()) return false;
        double d = mob.distanceToSqr(t);
        return d > 9 && d < 196 && mob.getSensing().hasLineOfSight(t);
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        LivingEntity t = mob.getTarget();
        if (t == null) return;
        WebShot web = new WebShot(mob.level(), mob, damage);
        double dx = t.getX() - mob.getX();
        double dy = t.getY(0.4) - web.getY();
        double dz = t.getZ() - mob.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        web.shoot(dx, dy + horiz * 0.15, dz, 1.3f, 3f);
        mob.level().addFreshEntity(web);
        mob.playSound(SoundEvents.LLAMA_SPIT, 1f, 0.6f);
        readyAt = mob.level().getGameTime() + 60 + mob.getRandom().nextInt(40);
    }
}
