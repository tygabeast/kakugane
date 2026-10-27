package com.tygabeast.kakugane.entity;

import com.tygabeast.kakugane.KakuganeConfig;
import com.tygabeast.kakugane.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
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
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Hound: an animal-type Homunculus built on vanilla goals (no custom pathfinding).
 * Hunts in packs, leaps at its prey, and its bite can pin (heavy slowness). Hunts villagers too.
 * Takes heavily reduced damage from anything that is not alchemical (it is in the kakugane:homunculi tag).
 */
public class Hound extends Monster {
    public Hound(EntityType<? extends Hound> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.33)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.45f));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.25, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.9));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers()); // hit one, the pack comes
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living && random.nextFloat() < 0.3f) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2), this); // pinned
        }
        return hit;
    }

    // Sounds: a deeper, wrong-sounding wolf.
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.WOLF_GROWL; }
    @Override protected SoundEvent getHurtSound(DamageSource src) { return SoundEvents.WOLF_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.WOLF_DEATH; }
    @Override public float getVoicePitch() { return 0.6f + random.nextFloat() * 0.1f; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.WOLF_STEP, 0.15f, 0.8f); }

    /**
     * Natural spawn rule: normal monster rules (dark, not peaceful) plus a local cap on Homunculi so they
     * never crowd out regular mobs. The box search only runs for an actual Hound spawn attempt.
     */
    public static boolean checkSpawnRules(EntityType<Hound> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) return false;
        if (!Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) return false;
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) return true;
        int cap = KakuganeConfig.DENSITY_CAP.get();
        int count = level.getEntitiesOfClass(Monster.class, new AABB(pos).inflate(32), e -> e.getType().is(ModTags.HOMUNCULI)).size();
        return count < cap;
    }
}
