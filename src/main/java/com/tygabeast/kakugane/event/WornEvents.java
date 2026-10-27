package com.tygabeast.kakugane.event;

import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.ability.Greaves;
import com.tygabeast.kakugane.core.KakuganeAccess;
import com.tygabeast.kakugane.data.Archetype;
import com.tygabeast.kakugane.registry.ModRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/** Small worn-form rules that hang off other events: the mask filters poison, the stomp lands softly. */
@EventBusSubscriber(modid = Kakugane.MODID)
public final class WornEvents {

    /** Aerial Operator filters the air: Poison never takes hold. */
    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getEffectInstance().is(MobEffects.POISON)
                && p.hasData(ModRegistry.STATE.get()) && KakuganeAccess.state(p).activeForm == Archetype.AERIAL_OPERATOR) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.hasData(ModRegistry.STATE.get()) && Greaves.cancelsFall(KakuganeAccess.state(p))) {
            event.setDamageMultiplier(0f);
        }
    }

    private WornEvents() {}
}
