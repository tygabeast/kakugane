package com.tygabeast.kakugane.event;

import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.KakuganeConfig;
import com.tygabeast.kakugane.ability.Alchemy;
import com.tygabeast.kakugane.ability.SilverSkin;
import com.tygabeast.kakugane.ability.TraitEffects;
import com.tygabeast.kakugane.core.ActiveForms;
import com.tygabeast.kakugane.core.Affinity;
import com.tygabeast.kakugane.core.Hearts;
import com.tygabeast.kakugane.core.KakuganeAccess;
import com.tygabeast.kakugane.core.Loadout;
import com.tygabeast.kakugane.core.Milestones;
import com.tygabeast.kakugane.core.PlayerState;
import com.tygabeast.kakugane.data.Archetype;
import com.tygabeast.kakugane.data.KakuganeData;
import com.tygabeast.kakugane.data.Trait;
import com.tygabeast.kakugane.entity.AlchemicArrow;
import com.tygabeast.kakugane.item.KakuganeItem;
import com.tygabeast.kakugane.registry.ModTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.AxeItem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

/**
 * All combat hooks: damage rules, trait effects, affinity signals, deeds and the heart.
 * Everything here runs only when something is hit or dies, never per tick.
 */
@EventBusSubscriber(modid = Kakugane.MODID)
public final class CombatEvents {

    /** Before damage is applied: the Homunculus damage rule and damage-changing traits. */
    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        DamageSource src = event.getSource();
        float amount = event.getAmount();
        boolean alchemical = Alchemy.isAlchemical(src);
        boolean homunculus = target.getType().is(ModTags.HOMUNCULI);

        if (homunculus && !alchemical) {
            amount *= (float) (1.0 - KakuganeConfig.HOMUNCULUS_DAMAGE_REDUCTION.get());
        }

        if (alchemical && src.getEntity() instanceof ServerPlayer attacker) {
            PlayerState st = KakuganeAccess.state(attacker);
            if (st.activeTrait == Trait.BANE && homunculus) amount *= 1f + 0.25f * st.traitScale();
            if (st.activeTrait == Trait.ECHO && attacker.getRandom().nextFloat() < 0.15f * st.traitScale()) amount *= 1.4f;
        }

        if (target instanceof ServerPlayer p) {
            PlayerState st = KakuganeAccess.state(p);
            long now = Affinity.now(p);
            st.lastHurtTick = now;
            if (st.hasActive()) {
                if (st.activeTrait == Trait.GUARDING) amount *= 1f - 0.08f * st.traitScale();
                if (st.activeTrait == Trait.FEATHERFALL && src.is(DamageTypeTags.IS_FALL)) amount *= Math.max(0f, 1f - 0.4f * st.traitScale());
                if (st.activeForm == Archetype.SILVER_SKIN && !src.is(DamageTypeTags.BYPASSES_ARMOR)) amount = SilverSkin.harden(st, amount, now);
            }
        }
        event.setAmount(amount);
    }

    /** After damage lands: on-hit traits, Thorns, and hit-based affinity signals. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        float dealt = event.getNewDamage();
        if (dealt <= 0) return;
        DamageSource src = event.getSource();
        Entity direct = src.getDirectEntity();

        if (src.getEntity() instanceof ServerPlayer attacker && attacker != target) {
            PlayerState st = KakuganeAccess.state(attacker);
            if (st.hasActive() && Alchemy.isAlchemical(src)) TraitEffects.onHit(attacker, st, target, dealt);

            // Affinity: behaviour signals from melee hits on hostile mobs.
            // Skipped while a form is active, so a form never reinforces itself.
            if (direct == attacker && target instanceof Enemy && !st.hasActive()) {
                if (attacker.isSprinting() || looksLikeCrit(attacker)) Affinity.record(attacker, Archetype.LANCE, 0.25);
                if (Affinity.now(attacker) - st.lastHurtTick < 60) Affinity.record(attacker, Archetype.SILVER_SKIN, 0.2);
            }
        }

        // Thorns trait: reflect part of melee damage (never reflect a reflection).
        if (target instanceof ServerPlayer p && direct instanceof LivingEntity atk && atk != p && !src.is(DamageTypes.THORNS)) {
            PlayerState st = KakuganeAccess.state(p);
            if (st.activeTrait == Trait.THORNS) atk.hurt(p.damageSources().thorns(p), dealt * 0.15f * st.traitScale());
        }
    }

    /** Blocking with a shield is a "tank" signal. */
    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (event.getBlocked() && event.getEntity() instanceof ServerPlayer p && event.getDamageSource().getEntity() instanceof Enemy) {
            Affinity.record(p, Archetype.SILVER_SKIN, 0.4);
        }
    }

    /** Deaths: the heart, reverting forms, kill-based affinity signals and deeds. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;

        if (victim instanceof ServerPlayer p) {
            if (Hearts.trySave(p)) {
                event.setCanceled(true);
                return;
            }
            ActiveForms.deactivate(p); // restore stored armor before the inventory drops
        }

        DamageSource src = event.getSource();
        if (!(src.getEntity() instanceof ServerPlayer killer) || killer == victim) return;
        Entity direct = src.getDirectEntity();

        // Affinity: tool choices on hostile kills. Kills with a Kakugane form don't count, so a form never feeds itself.
        if (victim instanceof Enemy) {
            if (direct instanceof Projectile proj && !(direct instanceof AlchemicArrow) && proj.getOwner() == killer) {
                Affinity.record(killer, Archetype.BOW, Affinity.weight(Loadout.hasMelee(killer), 1.0));
            } else if (direct == killer && killer.getMainHandItem().getItem() instanceof AxeItem) {
                Affinity.record(killer, Archetype.BREAKER, Affinity.weight(Loadout.hasNonAxeMelee(killer) || Loadout.hasRanged(killer), 1.0));
            } else if (direct == killer && !(killer.getMainHandItem().getItem() instanceof KakuganeItem)
                    && killer.getMainHandItem().getItem() instanceof net.minecraft.world.item.TridentItem) {
                Affinity.record(killer, Archetype.LANCE, Affinity.weight(true, 0.5));
            }
        }

        // Deeds for milestones: defeats done with the active Kakugane.
        PlayerState st = KakuganeAccess.state(killer);
        if (st.hasActive() && Alchemy.isAlchemical(src)) {
            if (victim.getType().is(ModTags.HUMAN_HOMUNCULI)) Milestones.credit(killer, KakuganeData.Deeds::withHuman);
            if (victim.getType().is(ModTags.ELITE_HOMUNCULI)) Milestones.credit(killer, KakuganeData.Deeds::withElite);
        }
    }

    /** Vanilla's critical-hit condition, checked after the fact. */
    private static boolean looksLikeCrit(ServerPlayer p) {
        return p.fallDistance > 0 && !p.onGround() && !p.onClimbable() && !p.isInWater() && !p.isPassenger();
    }

    private CombatEvents() {}
}
