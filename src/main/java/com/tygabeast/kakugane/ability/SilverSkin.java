package com.tygabeast.kakugane.ability;

import com.tygabeast.kakugane.core.PlayerState;

/**
 * Silver Skin (canon): a full suit of hexagonal plating over all four armor slots (put on and taken off by
 * core/WornForms). Each hit taken hardens the plating a little more; stacks reset after 6 seconds without a hit.
 */
public final class SilverSkin {

    /** Called when a Silver Skin wearer is about to take damage. Returns the reduced amount. */
    public static float harden(PlayerState st, float amount, long now) {
        if (now - st.lastHardenTick > 120) st.hardenStacks = 0;
        float reduced = amount * (1f - 0.05f * st.hardenStacks);
        int max = 4 + st.activeTier;
        if (st.hardenStacks < max) st.hardenStacks++;
        st.lastHardenTick = now;
        return reduced;
    }

    private SilverSkin() {}
}
