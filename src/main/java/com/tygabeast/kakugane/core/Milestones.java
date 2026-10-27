package com.tygabeast.kakugane.core;

import com.tygabeast.kakugane.KakuganeConfig;
import com.tygabeast.kakugane.data.Archetype;
import com.tygabeast.kakugane.data.KakuganeData;
import com.tygabeast.kakugane.data.Trait;
import com.tygabeast.kakugane.item.KakuganeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Evolution. Each Kakugane has three milestones, earned by deeds done with it active (in any order):
 *   defeat a human-type Homunculus, destroy N Homunculus embryos, defeat an Elite
 * When one is reached the wielder chooses Deepen (tier +1) or Re-temper (reshape to their current affinity).
 * An unused milestone waits; milestones never stack (only one can be pending at a time).
 */
public final class Milestones {

    /** Credits a deed to the player's active Kakugane, then checks whether a milestone is now reached. */
    public static void credit(ServerPlayer p, UnaryOperator<KakuganeData.Deeds> deed) {
        PlayerState st = KakuganeAccess.state(p);
        if (!st.hasActive()) return;
        int serial = st.activeSerial;
        if (KakuganeAccess.edit(p, serial, d -> d.withDeeds(deed.apply(d.deeds()))) != null) check(p, serial);
    }

    public static void check(ServerPlayer p, int serial) {
        KakuganeData d = KakuganeAccess.find(p, serial);
        if (d == null || d.pending() || d.milestones() >= 3 || !d.isOwnedBy(p.getUUID()) || !met(d)) return;
        KakuganeData now = KakuganeAccess.edit(p, serial, x -> x.withPending(true));
        if (now != null) announce(p, now);
    }

    /**
     * The three deeds can be done in any order: a milestone is ready whenever more deeds are complete than
     * milestones have been used. (So embryos can be a first milestone while human-types don't exist yet.)
     */
    static boolean met(KakuganeData d) {
        int done = (d.deeds().human() ? 1 : 0)
                + (d.deeds().embryos() >= KakuganeConfig.EMBRYOS_FOR_MILESTONE.get() ? 1 : 0)
                + (d.deeds().elite() ? 1 : 0);
        return done > d.milestones();
    }

    /** Chat message with clickable choices; each click runs /kakugane choose ... for the player. */
    public static void announce(ServerPlayer p, KakuganeData d) {
        MutableComponent deepen = Component.translatable("message.kakugane.choice.deepen").withStyle(Style.EMPTY
                .withColor(ChatFormatting.GOLD).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/kakugane choose deepen"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.kakugane.choice.deepen.hover"))));
        MutableComponent retemper = Component.translatable("message.kakugane.choice.retemper").withStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/kakugane choose retemper"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.kakugane.choice.retemper.hover"))));
        p.sendSystemMessage(Component.translatable("message.kakugane.milestone", KakuganeItem.serialText(d), d.milestones() + 1)
                .withStyle(ChatFormatting.YELLOW));
        p.sendSystemMessage(Component.literal("  ").append(deepen).append(Component.literal("   ")).append(retemper));
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    /** /kakugane choose deepen|retemper. Returns 1 on success for the command system. */
    public static int choose(ServerPlayer p, boolean deepen) {
        int serial = targetSerial(p);
        KakuganeData d = serial == 0 ? null : KakuganeAccess.find(p, serial);
        if (d == null || !d.pending()) {
            p.sendSystemMessage(Component.translatable("message.kakugane.no_pending"));
            return 0;
        }
        Archetype newForm = null;
        if (!deepen) {
            Optional<Archetype> pick = Affinity.pick(p, serial);
            if (pick.isEmpty() || pick.get().id().equals(d.archetype())) {
                p.sendSystemMessage(Component.translatable("message.kakugane.retemper_same"));
                return 0;
            }
            newForm = pick.get();
        }

        PlayerState st = KakuganeAccess.state(p);
        boolean wasActive = st.hasActive() && st.activeSerial == serial;
        boolean wasHeart = wasActive && st.activeHeart;
        if (wasActive) ActiveForms.deactivate(p); // reactivated below so the new stats apply

        String name = p.getGameProfile().getName();
        Archetype form = newForm;
        KakuganeData after = KakuganeAccess.edit(p, serial, x -> deepen
                ? x.spendMilestone(true)
                : x.withForm(form, Trait.keepOrPick(x.trait(), serial, form.worn()), name).spendMilestone(false));
        if (after == null) return 0;

        if (wasActive) {
            if (wasHeart) {
                ActiveForms.activateHeart(p);
            } else {
                ItemStack s = KakuganeAccess.findInInventory(p, serial);
                if (s != null) ActiveForms.activateStack(p, s);
            }
        }
        p.sendSystemMessage(deepen
                ? Component.translatable("message.kakugane.deepened", KakuganeItem.serialText(after), after.tier())
                : Component.translatable("message.kakugane.retempered", KakuganeItem.serialText(after), form.displayName()));
        check(p, serial); // deeds may already satisfy the next milestone
        return 1;
    }

    /** Which Kakugane a command means: the active one, else the one in the main hand, else the heart. */
    static int targetSerial(ServerPlayer p) {
        PlayerState st = KakuganeAccess.state(p);
        if (st.hasActive()) return st.activeSerial;
        ItemStack main = p.getMainHandItem();
        if (main.getItem() instanceof KakuganeItem) {
            KakuganeData d = KakuganeAccess.data(main);
            if (d.isOwnedBy(p.getUUID())) return d.serial();
        }
        HeartData h = KakuganeAccess.heart(p);
        return h.has() ? h.data().serial() : 0;
    }

    private Milestones() {}
}
