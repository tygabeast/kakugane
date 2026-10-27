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

    // Seconds the active Kakugane has been missing from the inventory (see ActiveForms.validate)
    public int missingChecks;

    // Last time this player landed a melee hit (Gauntlets combo, Blade Belt "in combat")
    public long lastMeleeTick = -100000;

    // ---- worn forms (see ability/WornAbilities and the class for each form) ----
    // Aerial Operator
    public boolean nightVision;
    // Alice in Wonderland: flight reserve (-1 = not set up yet), whether we granted flight, and the cooldowns
    /**
     * Alice's flight reserve. Kept when the wings are taken off, so switching them off and on in mid-air can't
     * refill it; it only refills on the ground while worn. -1 = never used (starts full).
     */
    public float wingReserve = -1;
    /** The reserve value the client's HUD last received, and whether the wings were just put on. */
    public float wingReserveSent = -1;
    public boolean wingsFresh = true;
    public boolean flightGranted, wingsDry;
    public long wingsScatteredUntil, scatterReadyAt;
    public int wingSyncCooldown;
    // Gauntlets: consecutive hits
    public int combo;
    // Greaves: air dashes left before landing, ticks of an active kick, diving stomp
    public int airDashes;
    public int kickTicks;
    public boolean stomping;
    public long stompReadyAt;
    public final Set<Integer> kickHits = new HashSet<>();
    // Mantle: standing still, stealth
    public int stillChecks;
    public boolean stealthed;
    public long lastStealthStrike = -100000;
    public double lastX, lastY, lastZ;
    // Blade Belt
    public long bladeReadyAt, nextAutoStrike;

    public boolean hasActive() { return activeForm != null; }

    public void clearActive() {
        activeSerial = 0;
        activeForm = null;
        activeTrait = null;
        activeTier = 0;
        activeHeart = false;
        hardenStacks = 0;
        missingChecks = 0;
        nightVision = false;
        flightGranted = false; // wingReserve and wingsDry deliberately carry over (see wingReserve)
        wingsFresh = true;
        combo = 0;
        airDashes = 0;
        kickTicks = 0;
        stomping = false;
        stompReadyAt = 0;
        stillChecks = 0;
        stealthed = false;
    }

    public float traitScale() { return Trait.scale(activeTier); }
}
