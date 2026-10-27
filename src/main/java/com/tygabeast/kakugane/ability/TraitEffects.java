package com.tygabeast.kakugane.ability;

import com.tygabeast.kakugane.core.PlayerState;
import com.tygabeast.kakugane.data.Trait;
import com.tygabeast.kakugane.registry.ModTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Trait effects that react to events. Stat traits (Swift, Steadfast) are attribute modifiers on the form
 * itself; damage-changing traits (Bane, Echo, Guarding, Featherfall) are in event/CombatEvents.
 */
public final class TraitEffects {

    /** After an alchemical hit lands. */
    public static void onHit(ServerPlayer attacker, PlayerState st, LivingEntity target, float dealt) {
        Trait t = st.activeTrait;
        if (t == null || target == attacker) return;
        float sc = st.traitScale();
        switch (t) {
            case VAMPIRIC -> attacker.heal(dealt * 0.08f * sc);
            case IMPACT -> target.knockback(0.35 * sc, attacker.getX() - target.getX(), attacker.getZ() - target.getZ());
            case SEARING -> target.igniteForSeconds(2f * sc);
            case FROST -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) (40 * sc), 1), attacker);
            case VENOM -> target.addEffect(new MobEffectInstance(MobEffects.POISON, (int) (50 * sc), 0), attacker);
            default -> { }
        }
    }

    /** Once a second, only for players with an active form. */
    public static void tickPassive(ServerPlayer p, PlayerState st, long now) {
        Trait t = st.activeTrait;
        if (t == Trait.MENDING) {
            // Heal 1 (scaled) every 3 seconds once 5 seconds have passed without taking damage.
            if (now - st.lastHurtTick > 100 && p.getHealth() < p.getMaxHealth() && (now / 20) % 3 == 0) {
                p.heal(1.0f * st.traitScale());
            }
        } else if (t == Trait.ATTUNED && (now / 20) % 2 == 0) {
            // Every 2 seconds: make Homunculi within 12 blocks glow briefly.
            for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(12),
                    e -> e.getType().is(ModTags.HOMUNCULI))) {
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50, 0, false, false));
            }
        }
    }

    private TraitEffects() {}
}
