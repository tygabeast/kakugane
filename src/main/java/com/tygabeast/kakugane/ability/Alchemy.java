package com.tygabeast.kakugane.ability;

import com.tygabeast.kakugane.entity.AlchemicArrow;
import com.tygabeast.kakugane.item.KakuganeItem;
import com.tygabeast.kakugane.registry.ModRegistry;
import com.tygabeast.kakugane.registry.ModTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** What counts as "alchemical" damage (full damage against Homunculi) and how to make it. */
public final class Alchemy {

    /**
     * True for: Kakugane abilities, conjured arrows, melee hits with an active held form, melee hits by
     * someone wearing an active worn form, and weapons in the kakugane:alchemical_weapons tag.
     * Called from damage events only.
     */
    public static boolean isAlchemical(DamageSource src) {
        if (src.is(ModTags.BUSO_RENKIN)) return true;
        Entity direct = src.getDirectEntity();
        if (direct instanceof AlchemicArrow) return true;
        if (src.getEntity() instanceof Player p && direct == p) {
            ItemStack main = p.getMainHandItem();
            if (main.getItem() instanceof KakuganeItem && KakuganeItem.data(main).active()) return true;
            if (main.is(ModTags.ALCHEMICAL_WEAPONS)) return true;
            if (!p.level().isClientSide() && p.hasData(ModRegistry.STATE.get())) {
                var st = p.getData(ModRegistry.STATE.get());
                return st.hasActive() && st.activeForm.worn();
            }
        }
        return false;
    }

    /** Damage source for Kakugane abilities (Lance dash, Breaker slam), credited to the wielder. */
    public static DamageSource source(ServerLevel level, Entity attacker) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ModTags.BUSO_RENKIN), attacker);
    }

    private Alchemy() {}
}
