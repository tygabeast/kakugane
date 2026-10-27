package com.tygabeast.kakugane.entity;

import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.ability.Alchemy;
import com.tygabeast.kakugane.entity.ai.ChargeGoal;
import com.tygabeast.kakugane.entity.ai.WebShotGoal;
import com.tygabeast.kakugane.registry.ModRegistry;
import com.tygabeast.kakugane.registry.ModTags;
import com.tygabeast.kakugane.world.LabTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Lab Leader: the human-type Homunculus at the heart of each lab. It looks like a person in a lab coat until a fight
 * starts, then shifts into a hybrid with its motif (Hound, Ram or Crawler) and that motif's attacks.
 *
 *  - Boss bar while transformed; regenerates quickly unless hit by Kakugane damage in the last 5 seconds.
 *  - When hurt, wakes every Homunculus within 24 blocks; at half health, calls two of its motif's beasts once.
 *  - Grows stronger with its lab's stage (see world/LabTracker). Tagged as a human-type (Kakugane milestone).
 */
public class LabLeader extends Monster implements ChargeGoal.Charger {
    public enum Motif {
        HOUND(BossEvent.BossBarColor.RED), RAM(BossEvent.BossBarColor.WHITE), CRAWLER(BossEvent.BossBarColor.PURPLE);

        final BossEvent.BossBarColor color;

        Motif(BossEvent.BossBarColor color) {
            this.color = color;
        }

        public static Motif byId(int id) {
            return values()[Math.floorMod(id, values().length)];
        }
    }

    private static final EntityDataAccessor<Integer> MOTIF = SynchedEntityData.defineId(LabLeader.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> TRANSFORMED = SynchedEntityData.defineId(LabLeader.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> CHARGE_STATE = SynchedEntityData.defineId(LabLeader.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private boolean motifGoalsAdded, summoned;
    /** True for leaders the lab generator placed (spawn-egg leaders aren't tied to a lab). */
    private boolean guardian;
    private int stage;
    private long healSuppressedUntil, nextAlert;

    public LabLeader(EntityType<? extends LabLeader> type, Level level) {
        super(type, level);
        this.xpReward = 40;
        bossEvent.setVisible(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 90.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MOTIF, 0);
        builder.define(TRANSFORMED, false);
        builder.define(CHARGE_STATE, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 1.0));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ---- motif, form, stage ------------------------------------------------------------------------

    public Motif motif() { return Motif.byId(entityData.get(MOTIF)); }
    public boolean transformed() { return entityData.get(TRANSFORMED); }
    public int chargeState() { return entityData.get(CHARGE_STATE); }

    @Override
    public void setChargeState(int state) { entityData.set(CHARGE_STATE, state); }

    public void setMotif(Motif m) {
        entityData.set(MOTIF, m.ordinal());
        bossEvent.setColor(m.color);
        bossEvent.setName(Component.translatable("entity.kakugane.lab_leader." + m.name().toLowerCase(java.util.Locale.ROOT)));
        if (motifGoalsAdded) return;
        motifGoalsAdded = true;
        switch (m) {
            case HOUND -> goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.5f));
            case RAM -> goalSelector.addGoal(2, new ChargeGoal(this, 10f, 0.9));
            case CRAWLER -> goalSelector.addGoal(2, new WebShotGoal(this, 3f));
        }
    }

    /** Lab stage 0..3 (Dormant, Active, Spreading, Overrun): +25% health and +20% damage per stage. */
    public void setStage(int s) {
        stage = Math.max(0, Math.min(3, s));
        float ratio = getHealth() / getMaxHealth();
        var hp = getAttribute(Attributes.MAX_HEALTH);
        var dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        hp.removeModifier(STAGE_HP);
        dmg.removeModifier(STAGE_DMG);
        if (stage > 0) {
            hp.addPermanentModifier(new AttributeModifier(STAGE_HP, 0.25 * stage, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            dmg.addPermanentModifier(new AttributeModifier(STAGE_DMG, 0.2 * stage, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        setHealth(getMaxHealth() * ratio);
    }

    private static final net.minecraft.resources.ResourceLocation STAGE_HP = Kakugane.id("lab_stage_health");
    private static final net.minecraft.resources.ResourceLocation STAGE_DMG = Kakugane.id("lab_stage_damage");

    /** Spawn eggs and commands: a random motif (the lab generator sets one before this runs). */
    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty, MobSpawnType reason, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.SpawnGroupData data) {
        if (!motifGoalsAdded) setMotif(Motif.byId(random.nextInt(3)));
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    /** Placed by the lab generator: keeps the leader in its chamber and ties it to that lab. */
    public void setHome(BlockPos home) {
        restrictTo(home, 7);
        guardian = true;
    }

    private void transform() {
        entityData.set(TRANSFORMED, true);
        bossEvent.setVisible(true);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0, getZ(), 30, 0.4, 0.8, 0.4, 0.04);
            sl.sendParticles(ParticleTypes.CRIMSON_SPORE, getX(), getY() + 1.0, getZ(), 40, 0.5, 0.9, 0.5, 0.1);
        }
        playSound(switch (motif()) {
            case HOUND -> SoundEvents.WOLF_GROWL;
            case RAM -> SoundEvents.RAVAGER_ROAR;
            case CRAWLER -> SoundEvents.SPIDER_AMBIENT;
        }, 1.5f, 0.5f);
    }

    // ---- per tick (server) -------------------------------------------------------------------------

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!transformed() && getTarget() != null) transform();
        long now = level().getGameTime();
        if ((now % 10) == 0 && getHealth() < getMaxHealth() && now >= healSuppressedUntil) heal(1.5f);
        bossEvent.setProgress(getHealth() / getMaxHealth());
        if ((now + getId()) % 20 == 0) updateBossBarViewers();
        // A leader that was unloaded when its lab grew catches up here (one structure lookup every 10 seconds).
        if (guardian && (now + getId()) % 200 == 0 && level() instanceof ServerLevel sl) {
            int labStage = LabTracker.stageAt(sl, getRestrictCenter());
            if (labStage >= 0 && labStage != stage) setStage(labStage);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide()) return super.hurt(source, amount);
        boolean alchemical = Alchemy.isAlchemical(source);
        if (alchemical) healSuppressedUntil = level().getGameTime() + 100;
        if (motif() == Motif.RAM && Ram.deflects(this, source)) {
            amount *= 0.5f;
            playSound(SoundEvents.SHIELD_BLOCK, 0.8f, 0.6f);
        }
        boolean hit = super.hurt(source, amount);
        if (hit && source.getEntity() instanceof LivingEntity attacker && attacker != this) {
            if (!transformed()) transform();
            alertGuards(attacker);
            if (!summoned && isAlive() && getHealth() <= getMaxHealth() * 0.5f) summonPack(attacker);
        }
        return hit;
    }

    /** Wakes idle Homunculi within 24 blocks (at most once every 2 seconds). */
    private void alertGuards(LivingEntity attacker) {
        long now = level().getGameTime();
        if (now < nextAlert) return;
        nextAlert = now + 40;
        for (Mob m : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(24),
                m -> m != this && m.getTarget() == null && m.getType().is(ModTags.HOMUNCULI))) {
            m.setTarget(attacker);
        }
    }

    private void summonPack(LivingEntity attacker) {
        summoned = true;
        if (!(level() instanceof ServerLevel sl)) return;
        EntityType<? extends Mob> type = switch (motif()) {
            case HOUND -> ModRegistry.HOUND.get();
            case RAM -> ModRegistry.RAM.get();
            case CRAWLER -> ModRegistry.CRAWLER.get();
        };
        for (int i = 0; i < 2; i++) {
            Mob m = type.create(sl);
            if (m == null) continue;
            double a = random.nextDouble() * Math.PI * 2;
            m.moveTo(getX() + Math.cos(a) * 2.5, getY(), getZ() + Math.sin(a) * 2.5, random.nextFloat() * 360f, 0f);
            m.finalizeSpawn(sl, sl.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            m.setTarget(attacker);
            sl.addFreshEntityWithPassengers(m);
            sl.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + 0.5, m.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
        }
        playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.2f, 0.7f);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && motif() == Motif.HOUND && target instanceof LivingEntity l && random.nextFloat() < 0.35f) {
            l.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2), this); // pinned
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        // "dead" stays false if another mod cancelled the death. Report against the chamber, not where it fell.
        if (dead && guardian && level() instanceof ServerLevel sl) LabTracker.leaderDefeated(sl, getRestrictCenter());
    }

    /** A lab's leader stays in Peaceful (it can't hurt anyone there), so the lab can still be cleared later. */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return !guardian;
    }

    // ---- boss bar ----------------------------------------------------------------------------------

    private static final double BAR_RANGE = 32.0;

    /** Shows the bar to players within 32 blocks while transformed (checked once a second). */
    private void updateBossBarViewers() {
        if (!(level() instanceof ServerLevel sl)) return;
        for (ServerPlayer p : new java.util.ArrayList<>(bossEvent.getPlayers())) {
            if (!transformed() || p.level() != sl || p.distanceToSqr(this) > BAR_RANGE * BAR_RANGE * 1.5) bossEvent.removePlayer(p);
        }
        if (!transformed()) return;
        for (ServerPlayer p : sl.players()) if (p.distanceToSqr(this) <= BAR_RANGE * BAR_RANGE) bossEvent.addPlayer(p);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        if (name != null) bossEvent.setName(name);
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    // ---- saving ------------------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Motif", motif().ordinal());
        tag.putBoolean("Transformed", transformed());
        tag.putBoolean("Summoned", summoned);
        tag.putInt("Stage", stage);
        if (hasRestriction()) tag.put("Home", NbtUtils.writeBlockPos(getRestrictCenter()));
        tag.putBoolean("Guardian", guardian);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setMotif(Motif.byId(tag.getInt("Motif")));
        entityData.set(TRANSFORMED, tag.getBoolean("Transformed"));
        bossEvent.setVisible(tag.getBoolean("Transformed"));
        summoned = tag.getBoolean("Summoned");
        stage = tag.getInt("Stage");
        NbtUtils.readBlockPos(tag, "Home").ifPresent(this::setHome);
        guardian = tag.getBoolean("Guardian");
    }

    @Override
    protected SoundEvent getAmbientSound() { return transformed() ? SoundEvents.WARDEN_AMBIENT : null; }
    @Override
    protected SoundEvent getHurtSound(DamageSource src) { return transformed() ? SoundEvents.RAVAGER_HURT : SoundEvents.PLAYER_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.RAVAGER_DEATH; }
    @Override
    public float getVoicePitch() { return 0.8f; }
}
