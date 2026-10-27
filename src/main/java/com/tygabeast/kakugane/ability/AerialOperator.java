package com.tygabeast.kakugane.ability;

import com.tygabeast.kakugane.core.PlayerState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;

/**
 * Aerial Operator (canon): a huge gas mask that controls the air around the wearer.
 *
 *  - Always: filters the air (endless breath underwater through the mask's Oxygen Bonus, and immunity to Poison;
 *    see event/WornEvents). Zoom is client-side (hold Z).
 *  - Ability key: toggles night vision.
 *  - Tier II and up: enriches the air for allies within 5 blocks (Regeneration).
 *  - Tier III and up: thins the air around nearby enemies (they slow down and choke); Tier IV adds Weakness.
 *
 * Cost: once a second, a duration check; every 2 seconds at tier II+, one entity search in a radius of 5 to 7.
 */
public final class AerialOperator {

    public static void tickSecond(ServerPlayer p, PlayerState st, long now) {
        if (p.hasEffect(MobEffects.POISON)) p.removeEffect(MobEffects.POISON); // poisoned before the mask went on
        if (st.nightVision) {
            MobEffectInstance nv = p.getEffect(MobEffects.NIGHT_VISION);
            // Kept above 10 seconds so the screen never starts flickering (vanilla flickers below that).
            if (nv == null || nv.getDuration() < 240) p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, false));
        }
        int tier = st.activeTier;
        if (tier < 1 || (now / 20) % 2 != 0) return;
        double r = 5 + (tier >= 3 ? 2 : 0);
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), LivingEntity::isAlive)) {
            if (e == p) continue;
            if (isAlly(p, e)) {
                e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 50, 0, true, true), p);
            } else if (tier >= 2 && WornAbilities.isFoe(p, e)) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 0), p);
                if (tier >= 3) e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 50, 0), p);
                e.invulnerableTime = 0;
                e.hurt(Alchemy.source(p.serverLevel(), p), tier >= 3 ? 2f : 1f);
            }
        }
    }

    private static boolean isAlly(ServerPlayer p, LivingEntity e) {
        if (e instanceof Player other) return !other.isSpectator() && (p.isAlliedTo(other) || p.getTeam() == null);
        return e instanceof TamableAnimal t && t.isOwnedBy(p);
    }

    public static void toggleNightVision(ServerPlayer p, PlayerState st) {
        st.nightVision = !st.nightVision;
        if (!st.nightVision) removeNightVision(p);
        else p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, false));
        p.displayClientMessage(Component.translatable(st.nightVision ? "message.kakugane.night_vision_on" : "message.kakugane.night_vision_off"), true);
    }

    public static void end(ServerPlayer p) {
        removeNightVision(p);
    }

    /** Removes only the mask's own night vision (ambient, at most 20 seconds), never a potion's. */
    private static void removeNightVision(ServerPlayer p) {
        MobEffectInstance nv = p.getEffect(MobEffects.NIGHT_VISION);
        if (nv != null && nv.isAmbient() && nv.getDuration() <= 400) p.removeEffect(MobEffects.NIGHT_VISION);
    }

    private AerialOperator() {}
}
