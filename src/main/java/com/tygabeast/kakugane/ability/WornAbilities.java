package com.tygabeast.kakugane.ability;

import com.tygabeast.kakugane.core.KakuganeAccess;
import com.tygabeast.kakugane.core.PlayerState;
import com.tygabeast.kakugane.data.Archetype;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Routes ticks, the form ability key and clean-up to the worn form that is active.
 *
 * Cost per player per tick: one field read when no worn form is active. Each form keeps its own work to the
 * minimum (see the class for each form); Aerial Operator only runs once a second.
 */
public final class WornAbilities {

    /** Every tick, for players with an active worn form. */
    public static void tick(ServerPlayer p, PlayerState st, long now) {
        switch (st.activeForm) {
            case ALICE -> AliceWings.tick(p, st, now);
            case GREAVES -> Greaves.tick(p, st);
            case MANTLE -> {
                if ((p.tickCount & 3) == 0) Mantle.tick(p, st, now);
            }
            case BLADE_BELT -> BladeBelt.tick(p, st, now);
            default -> { }
        }
    }

    /** Once a second, for players with an active form. */
    public static void tickSecond(ServerPlayer p, PlayerState st, long now) {
        if (st.activeForm == Archetype.AERIAL_OPERATOR) AerialOperator.tickSecond(p, st, now);
    }

    /** The form ability key (default V). */
    public static void ability(ServerPlayer p) {
        PlayerState st = KakuganeAccess.state(p);
        if (!st.hasActive()) return;
        switch (st.activeForm) {
            case AERIAL_OPERATOR -> AerialOperator.toggleNightVision(p, st);
            case ALICE -> AliceWings.scatter(p, st);
            default -> p.displayClientMessage(Component.translatable("message.kakugane.no_ability", st.activeForm.displayName()), true);
        }
    }

    /** A form is ending (deactivated, lost, death, logout): undo what it gave. Called before the state is cleared. */
    public static void end(ServerPlayer p, PlayerState st, Archetype form) {
        if (form == null) return;
        switch (form) {
            case AERIAL_OPERATOR -> AerialOperator.end(p);
            case ALICE -> AliceWings.end(p, st);
            case MANTLE -> Mantle.end(p, st);
            default -> { }
        }
    }

    /**
     * On login, respawn and dimension change: take back flight the wings granted if they aren't active any more
     * (after a crash, say). If they are, the player keeps flying, so a portal doesn't drop them.
     */
    public static void cleanUpAfterRejoin(ServerPlayer p, PlayerState st) {
        AliceWings.cleanUpAfterRejoin(p, st);
    }

    /** Hostile mobs the wielder may hit with form abilities (never players, pets or allies). */
    public static boolean isFoe(Player owner, LivingEntity e) {
        return e != owner && e.isAlive() && e instanceof Enemy && !e.isAlliedTo(owner)
                && !(e instanceof TamableAnimal t && t.isOwnedBy(owner));
    }

    private WornAbilities() {}
}
