package com.tygabeast.kakugane.entity;

import com.tygabeast.kakugane.registry.ModRegistry;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** A glob of web spat by Crawlers: light damage and heavy slowness. Never places cobwebs (no griefing). */
public class WebShot extends ThrowableItemProjectile {
    private float damage = 2f;

    public WebShot(EntityType<? extends WebShot> type, Level level) {
        super(type, level);
    }

    public WebShot(Level level, LivingEntity owner, float damage) {
        super(ModRegistry.WEB_SHOT.get(), owner, level);
        this.damage = damage;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.COBWEB;
    }

    /** Flies through other Homunculi, so a stray glob doesn't set the lab's guards fighting each other. */
    @Override
    protected boolean canHitEntity(net.minecraft.world.entity.Entity target) {
        return super.canHitEntity(target) && !target.getType().is(com.tygabeast.kakugane.registry.ModTags.HOMUNCULI);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (hit.getEntity() instanceof LivingEntity target && target != getOwner()) {
            target.hurt(damageSources().thrown(this, getOwner()), damage);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2), getOwner());
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.COBWEB)), getX(), getY(), getZ(), 8, 0.2, 0.2, 0.2, 0.05);
            discard();
        }
    }
}
