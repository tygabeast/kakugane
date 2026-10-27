package com.tygabeast.kakugane.core;

import com.tygabeast.kakugane.data.KakuganeData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Per-tick checks for Kakugane in an inventory (called from KakuganeItem.inventoryTick, server only).
 * Dormant plates cost one component read; only active stacks and heart projections do more.
 */
public final class ActiveFormsGuard {

    public static void tick(ServerPlayer p, ItemStack stack) {
        KakuganeData d = KakuganeAccess.data(stack);

        if (d.serial() <= 0) {
            ActiveForms.ensureSerial(p, stack, d); // first time a server sees this Kakugane
            return;
        }

        if (d.heart()) {
            guardProjection(p, stack, d);
            return;
        }

        if (!d.active()) return;
        PlayerState st = KakuganeAccess.state(p);
        boolean isOurs = st.hasActive() && !st.activeHeart && st.activeSerial == d.serial();
        // An active form may sit anywhere in its wielder's inventory (switch to an embryo, move the plate around).
        // One that is active but not this player's active form was moved here somehow: revert it.
        if (!isOurs) ActiveForms.deactivateStack(p, stack);
    }

    /**
     * A heart projection may only exist in its owner's inventory while their heart form is active.
     * Anywhere else (another player, after reverting) it simply vanishes; the real heart is safe in HeartData.
     */
    private static void guardProjection(ServerPlayer p, ItemStack stack, KakuganeData d) {
        HeartData h = KakuganeAccess.heart(p);
        PlayerState st = KakuganeAccess.state(p);
        boolean valid = h.matches(d.serial()) && st.hasActive() && st.activeHeart && st.activeSerial == d.serial();
        if (valid) return;
        stack.setCount(0);
        if (h.matches(d.serial()) && st.hasActive() && st.activeHeart) ActiveForms.deactivate(p);
    }

    private ActiveFormsGuard() {}
}
