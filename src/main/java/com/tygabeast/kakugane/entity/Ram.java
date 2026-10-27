package com.tygabeast.kakugane.entity;

import com.tygabeast.kakugane.data.Archetype;
import com.tygabeast.kakugane.entity.ai.ChargeGoal;
import com.tygabeast.kakugane.item.KakuganeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Ram: an animal-type Homunculus with heavy frontal plating. It winds up and charges in a straight line, stunning
 * what it hits. Hits from the front are mostly deflected; hit it from the side or behind, or crack the plating
 * with a Breaker.
 */
public class Ram extends Monster implements ChargeGoal.Charger {
    private static final EntityDataAccessor<Integer> CHARGE_STATE = SynchedEntityData.defineId(Ram.class, EntityDataSerializers.INT);

    public Ram(EntityType<? extends Ram> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CHARGE_STATE, 0);
    }

    @Override
    public void setChargeState(int state) {
        entityData.set(CHARGE_STATE, state);
    }

    public int chargeState() {
        return entityData.get(CHARGE_STATE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new ChargeGoal(this, 8f, 0.85));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
    }

    /** Frontal plating: 60% less damage from anything in front, unless it's a Breaker. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide() && deflects(this, source)) {
            amount *= 0.4f;
            playSound(SoundEvents.SHIELD_BLOCK, 0.8f, 0.7f);
        }
        return super.hurt(source, amount);
    }

    /** True if the hit comes from within 60 degrees of where this mob faces and isn't from a Breaker form. */
    public static boolean deflects(Monster self, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_ARMOR) || source.getSourcePosition() == null) return false;
        if (source.getEntity() instanceof Player p) {
            var d = KakuganeItem.data(p.getMainHandItem());
            if (d.active() && d.form().orElse(null) == Archetype.BREAKER) return false;
        }
        Vec3 to = source.getSourcePosition().subtract(self.position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        if (flat.lengthSqr() < 1.0e-4) return false;
        Vec3 facing = Vec3.directionFromRotation(0, self.yBodyRot);
        return flat.normalize().dot(facing) > 0.5;
    }

    @Override protected SoundEvent getAmbientSound() { return SoundEvents.GOAT_SCREAMING_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource src) { return SoundEvents.GOAT_SCREAMING_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.GOAT_SCREAMING_DEATH; }
    @Override public float getVoicePitch() { return 0.55f + random.nextFloat() * 0.1f; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.GOAT_STEP, 0.2f, 0.7f); }
}
