package com.tygabeast.kakugane.core;

import com.tygabeast.kakugane.data.KakuganeData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Per-tick checks for Kakugane in an inventory (called from KakuganeItem.inventoryTick, server only).
 * Dormant plates cost one component read; only active stacks and heart projections do more.
 */
public final class ActiveFormsGuard {

    public static void tick(ServerPlayer p, ItemStack stack, boolean selected) {
        KakuganeData d = KakuganeAccess.data(stack);

        if (d.serial() <= 0) {
            ActiveForms.ensureSerial(p, stack, d); // first time a server sees this Kakugane
            return;
        }

        if (d.heart()) {
            guardProjection(p, stack, d, selected);
            return;
        }

        if (!d.active()) return;
        boolean worn = d.form().map(f -> f.worn()).orElse(false);
        PlayerState st = KakuganeAccess.state(p);
        boolean isOurs = st.hasActive() && !st.activeHeart && st.activeSerial == d.serial();

        if (!isOurs) {
            // Active but not this player's active form: it was moved here somehow. Revert it.
            ActiveForms.deactivateStack(p, stack);
            return;
        }
        if (!worn) {
            // Held forms only stay out while held. Switching away reverts them.
            boolean inHand = selected || p.getOffhandItem() == stack;
            if (!inHand) ActiveForms.deactivate(p);
        }
    }

    /**
     * A heart projection may only exist in its owner's selected hand while their heart form is active.
     * Anywhere else (another slot, another player) it simply vanishes; the real heart is safe in HeartData.
     */
    private static void guardProjection(ServerPlayer p, ItemStack stack, KakuganeData d, boolean selected) {
        HeartData h = KakuganeAccess.heart(p);
        PlayerState st = KakuganeAccess.state(p);
        boolean valid = selected && h.matches(d.serial()) && st.hasActive() && st.activeHeart && st.activeSerial == d.serial();
        if (valid) return;
        stack.setCount(0);
        if (h.matches(d.serial()) && st.hasActive() && st.activeHeart) ActiveForms.deactivate(p);
    }

    private ActiveFormsGuard() {}
}
