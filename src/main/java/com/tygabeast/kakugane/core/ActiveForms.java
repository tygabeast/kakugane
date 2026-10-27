package com.tygabeast.kakugane.core;

import com.tygabeast.kakugane.ability.WornAbilities;
import com.tygabeast.kakugane.data.Archetype;
import com.tygabeast.kakugane.data.KakuganeData;
import com.tygabeast.kakugane.data.Trait;
import com.tygabeast.kakugane.item.KakuganeItem;
import com.tygabeast.kakugane.registry.ModRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Turning Kakugane on and off ("Buso Renkin!"), and keeping the PlayerState cache honest.
 *
 * Rules enforced here:
 *  - a Kakugane bonds to the first player who activates it; it refuses anyone else while bonded
 *  - an unformed Kakugane takes its form from the player's affinity once there is enough combat recorded
 *  - only one Kakugane is active at a time (activating a second reverts the first)
 *  - held forms become the item itself; worn forms take over armor or Curios slots and give back what was there
 */
public final class ActiveForms {

    /** The activation keybind: turn off what is active, otherwise activate the held Kakugane or the heart. */
    public static void toggle(ServerPlayer p) {
        PlayerState st = KakuganeAccess.state(p);
        if (st.hasActive()) {
            deactivate(p);
            return;
        }
        ItemStack main = p.getMainHandItem();
        if (main.getItem() instanceof KakuganeItem && !KakuganeAccess.data(main).heart()) {
            activateStack(p, main);
            return;
        }
        if (KakuganeAccess.heart(p).has()) {
            activateHeart(p);
            return;
        }
        tell(p, Component.translatable("message.kakugane.hold_one"));
    }

    /** Activates a Kakugane item the player is carrying. Returns true if it is now active. */
    public static boolean activateStack(ServerPlayer p, ItemStack stack) {
        KakuganeData d = ensureSerial(p, stack, KakuganeAccess.data(stack));
        if (d.heart()) return false;
        // Still marked active (it came back from a chest, say): revert it properly first, which hands back
        // whatever it was storing, so equipping again can't overwrite that.
        if (d.active()) {
            deactivateStack(p, stack);
            d = KakuganeAccess.data(stack);
        }
        SerialRegistry.get(p.server).mark(d.serial()); // serials from other worlds count as taken here
        Prepared pr = prepare(p, d);
        stack.set(ModRegistry.KAKUGANE_DATA.get(), pr.data);
        if (!pr.proceed) return false;
        if (!canWear(p, pr.data)) return false;

        deactivate(p); // one active at a time
        d = pr.data.withActive(true);
        stack.set(ModRegistry.KAKUGANE_DATA.get(), d);
        Archetype form = d.form().orElseThrow();
        if (form.worn()) {
            WornForms.equip(p, stack, d);
        } else {
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, KakuganeItem.heldModifiers(d));
        }
        begin(p, d, false);
        return true;
    }

    /** Calls up the heart Kakugane. Held forms appear in the (empty) main hand; worn forms go straight on. */
    public static void activateHeart(ServerPlayer p) {
        HeartData h = KakuganeAccess.heart(p);
        if (!h.has()) return;
        Prepared pr = prepare(p, h.data());
        h.stack.set(ModRegistry.KAKUGANE_DATA.get(), pr.data);
        if (!pr.proceed || !canWear(p, pr.data)) return;

        Archetype form = pr.data.form().orElseThrow();
        if (!form.worn() && !p.getMainHandItem().isEmpty()) {
            tell(p, Component.translatable("message.kakugane.heart_hand_full"));
            return;
        }
        deactivate(p);
        KakuganeData d = pr.data.withActive(true);
        h.stack.set(ModRegistry.KAKUGANE_DATA.get(), d);
        if (form.worn()) {
            WornForms.equip(p, h.stack, d);
        } else {
            ItemStack projection = h.stack.copy();
            projection.set(DataComponents.ATTRIBUTE_MODIFIERS, KakuganeItem.heldModifiers(d));
            p.setItemInHand(InteractionHand.MAIN_HAND, projection);
        }
        begin(p, d, true);
    }

    /** Reverts whatever Kakugane is active. Safe to call when nothing is. */
    public static void deactivate(ServerPlayer p) {
        PlayerState st = KakuganeAccess.state(p);
        if (!st.hasActive()) return;
        int serial = st.activeSerial;
        boolean heart = st.activeHeart;
        Archetype form = st.activeForm;
        endActive(p, st);
        st.dashTicks = 0;

        if (heart) {
            HeartData h = KakuganeAccess.heart(p);
            if (h.matches(serial)) {
                if (form.worn()) WornForms.unequip(p, h.stack);
                h.stack.set(ModRegistry.KAKUGANE_DATA.get(), h.data().withActive(false));
            }
            KakuganeAccess.removeProjections(p, serial);
        } else {
            ItemStack s = KakuganeAccess.findInInventory(p, serial);
            if (s != null) {
                revertStack(p, s);
            } else if (form.worn()) {
                WornForms.stripPieces(p, serial); // the plate is gone; at least take the pieces off
            }
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 0.8f, 0.7f);
    }

    /**
     * Reverts one specific stack (used when an active Kakugane is thrown, ends up in the wrong inventory, etc.).
     * Clears the player's cache too if this was their active one.
     */
    public static void deactivateStack(ServerPlayer p, ItemStack stack) {
        KakuganeData d = KakuganeAccess.data(stack);
        if (!d.active()) return;
        PlayerState st = KakuganeAccess.state(p);
        if (st.hasActive() && !st.activeHeart && st.activeSerial == d.serial()) endActive(p, st);
        revertStack(p, stack);
    }

    private static void revertStack(ServerPlayer p, ItemStack s) {
        KakuganeData d = KakuganeAccess.data(s);
        if (d.form().map(Archetype::worn).orElse(false)) {
            WornForms.unequip(p, s);
        } else {
            s.remove(DataComponents.ATTRIBUTE_MODIFIERS);
        }
        s.set(ModRegistry.KAKUGANE_DATA.get(), d.withActive(false));
    }

    /** Rebuilds the cache from the inventory and heart (login, respawn, dimension change). */
    public static void rebuild(ServerPlayer p) {
        PlayerState st = KakuganeAccess.state(p);
        st.clearActive();
        HeartData h = KakuganeAccess.heart(p);
        if (h.has() && h.data().active()) {
            KakuganeData d = h.data();
            boolean ok = d.form().isPresent();
            if (ok && !d.form().get().worn()) ok = KakuganeAccess.hasProjection(p, d.serial());
            if (ok) {
                fill(st, d, true);
            } else {
                h.stack.set(ModRegistry.KAKUGANE_DATA.get(), d.withActive(false));
                KakuganeAccess.removeProjections(p, d.serial());
            }
        }
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!(s.getItem() instanceof KakuganeItem)) continue;
            KakuganeData d = KakuganeAccess.data(s);
            if (!d.active() || d.heart()) continue;
            if (!st.hasActive() && d.form().isPresent() && d.isOwnedBy(p.getUUID())) {
                fill(st, d, false);
            } else {
                revertStack(p, s); // a second active one, or one that is not ours
            }
        }
        // Flight the wings granted before a relog or crash goes, unless the wings are still on (portals, rejoin).
        WornAbilities.cleanUpAfterRejoin(p, st);
    }

    /**
     * Cheap consistency check, once a second for players with an active form: the active Kakugane (or a held
     * heart form's copy) must still be in the inventory. If it has gone (into a chest, dropped, deleted) the
     * form reverts after a grace period. Creative-mode inventory moves are only reported to the server when the
     * item is put down, so creative players get a minute before anything happens.
     */
    public static void validate(ServerPlayer p, PlayerState st) {
        int serial = st.activeSerial;
        boolean present;
        if (st.activeHeart) {
            HeartData h = KakuganeAccess.heart(p);
            if (!h.matches(serial) || !h.data().active()) {
                endActive(p, st);
                return;
            }
            present = st.activeForm.worn() || KakuganeAccess.hasProjection(p, serial);
        } else {
            present = KakuganeAccess.findInInventory(p, serial) != null;
        }
        if (present) {
            st.missingChecks = 0;
            return;
        }
        if (++st.missingChecks < (p.isCreative() ? 60 : 3)) return;
        if (st.activeHeart) {
            deactivate(p);
            return;
        }
        Archetype form = st.activeForm;
        endActive(p, st);
        if (form.worn()) WornForms.stripPieces(p, serial);
    }

    /**
     * Ends the running form's abilities (flight, night vision, stealth) and then forgets it. Every path that stops
     * a live form goes through here, so nothing an ability granted can outlive the form.
     */
    private static void endActive(ServerPlayer p, PlayerState st) {
        WornAbilities.end(p, st, st.activeForm);
        st.clearActive();
    }

    // ---- internals ----------------------------------------------------------------------------------

    /** A worn form needs its slot to exist (a modpack can disable Curios slots). */
    private static boolean canWear(ServerPlayer p, KakuganeData d) {
        Archetype form = d.form().orElse(null);
        if (form == null || !form.worn() || WornForms.canWear(p, form)) return true;
        tell(p, Component.translatable("message.kakugane.no_slot", form.displayName()));
        return false;
    }

    private record Prepared(KakuganeData data, boolean proceed) {}

    /** Bonding and imprinting. Returns the (possibly changed) data and whether activation may continue. */
    private static Prepared prepare(ServerPlayer p, KakuganeData d) {
        String name = p.getGameProfile().getName();
        if (d.bonded() && !d.isOwnedBy(p.getUUID())) {
            tell(p, Component.translatable("message.kakugane.bonded_other", d.ownerName()));
            return new Prepared(d, false);
        }
        if (!d.bonded()) {
            d = d.bondTo(p.getUUID(), name);
            p.sendSystemMessage(Component.translatable("message.kakugane.bonded", KakuganeItem.serialText(d)));
        }
        if (!d.formed()) {
            Optional<Archetype> pick = Affinity.pick(p, d.serial());
            if (pick.isEmpty()) {
                AffinityData a = p.getData(ModRegistry.AFFINITY.get());
                int pct = (int) Math.min(99, 100 * a.recorded() / Math.max(0.01, com.tygabeast.kakugane.KakuganeConfig.IMPRINT_THRESHOLD.get()));
                tell(p, Component.translatable("message.kakugane.unformed", pct));
                return new Prepared(d, false);
            }
            Archetype a = pick.get();
            d = d.withForm(a, Trait.pick(d.serial(), a.worn()), name);
            p.sendSystemMessage(Component.translatable("message.kakugane.imprinted", a.displayName()));
        }
        return new Prepared(d, true);
    }

    private static void begin(ServerPlayer p, KakuganeData d, boolean heart) {
        fill(KakuganeAccess.state(p), d, heart);
        var level = p.serverLevel();
        level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7f, 1.8f);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.4, 0.6, 0.4, 0.15);
        tell(p, Component.translatable("message.kakugane.activated", d.form().orElseThrow().displayName()));
        Milestones.check(p, d.serial());
    }

    private static void fill(PlayerState st, KakuganeData d, boolean heart) {
        st.activeSerial = d.serial();
        st.activeForm = d.form().orElse(null);
        st.activeTrait = d.traitValue().orElse(null);
        st.activeTier = d.tier();
        st.activeHeart = heart;
        // Greaves put on in mid-air start with their dashes, matching the client (which counts its own).
        if (st.activeForm == Archetype.GREAVES) st.airDashes = com.tygabeast.kakugane.ability.Greaves.maxDashes(st.activeTier);
    }

    /** Gives a fresh Kakugane its serial the first time the server sees it. */
    public static KakuganeData ensureSerial(ServerPlayer p, ItemStack stack, KakuganeData d) {
        if (d.serial() > 0) return d;
        KakuganeData out = d.withSerial(SerialRegistry.get(p.server).claim(p.getRandom()));
        stack.set(ModRegistry.KAKUGANE_DATA.get(), out);
        return out;
    }

    static void tell(ServerPlayer p, Component c) {
        p.displayClientMessage(c, true);
    }

    private ActiveForms() {}
}
