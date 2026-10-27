package com.tygabeast.kakugane.core;

import com.tygabeast.kakugane.data.Archetype;
import com.tygabeast.kakugane.data.Trait;

import java.util.HashSet;
import java.util.Set;

/**
 * Server-side, per-player state that does not need saving. It is attached to the player entity, so it
 * disappears with the entity on logout and never leaks memory. ActiveForms rebuilds it on login and respawn.
 */
public final class PlayerState {
    // Cache of the currently active Kakugane, so hot paths (damage events, ticks) never search the inventory.
    public int activeSerial;
    public Archetype activeForm;   // null = nothing active
    public Trait activeTrait;      // may be null
    public int activeTier;
    public boolean activeHeart;

    // Lance dash in progress
    public int dashTicks;
    public float dashDamage;
    public net.minecraft.world.damagesource.DamageSource dashSource; // built once per dash, not per tick
    public final Set<Integer> dashHits = new HashSet<>();

    // Silver Skin hardening
    public int hardenStacks;
    public long lastHardenTick;

    // Last time this player took damage (for the "tank" affinity signal and the Mending trait)
    public long lastHurtTick = -100000;

    public boolean hasActive() { return activeForm != null; }

    public void clearActive() {
        activeSerial = 0;
        activeForm = null;
        activeTrait = null;
        activeTier = 0;
        activeHeart = false;
        hardenStacks = 0;
    }

    public float traitScale() { return Trait.scale(activeTier); }
}
