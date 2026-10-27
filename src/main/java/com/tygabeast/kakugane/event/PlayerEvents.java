package com.tygabeast.kakugane.event;

import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.ability.Lance;
import com.tygabeast.kakugane.ability.TraitEffects;
import com.tygabeast.kakugane.ability.WornAbilities;
import com.tygabeast.kakugane.command.KakuganeCommand;
import com.tygabeast.kakugane.core.ActiveForms;
import com.tygabeast.kakugane.core.Affinity;
import com.tygabeast.kakugane.core.Hearts;
import com.tygabeast.kakugane.core.KakuganeAccess;
import com.tygabeast.kakugane.core.PlayerState;
import com.tygabeast.kakugane.data.KakuganeData;
import com.tygabeast.kakugane.item.KakuganeItem;
import com.tygabeast.kakugane.item.FormArmorItem;
import com.tygabeast.kakugane.item.FormCurioItem;
import com.tygabeast.kakugane.registry.ModRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Iterator;

/** Player lifecycle: ticking active forms, login/respawn rebuilds, throwing items and death drops. */
@EventBusSubscriber(modid = Kakugane.MODID)
public final class PlayerEvents {

    /**
     * Per-tick work, kept to the minimum: players with nothing active and no dash return after two field
     * reads. Dashes tick every tick (only for their 10 ticks); worn forms do their own small per-tick work (see
     * ability/WornAbilities); everything else runs once a second.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.hasData(ModRegistry.STATE.get())) return;
        PlayerState st = p.getData(ModRegistry.STATE.get());
        if (st.dashTicks > 0) Lance.tickDash(p, st);
        if (!st.hasActive()) return;
        long now = Affinity.now(p);
        if (st.activeForm.worn()) WornAbilities.tick(p, st, now);
        if (p.tickCount % 20 != 0) return;
        ActiveForms.validate(p, st);
        if (!st.hasActive()) return;
        TraitEffects.tickPassive(p, st, now);
        WornAbilities.tickSecond(p, st, now);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) ActiveForms.rebuild(p);
    }

    /** Revert on logout, before the player is saved, so armor stored in a Silver Skin can never be lost. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        ItemStack carried = p.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            p.containerMenu.setCarried(ItemStack.EMPTY);
            p.getInventory().placeItemBackInInventory(carried);
        }
        ActiveForms.deactivate(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) ActiveForms.rebuild(p);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) ActiveForms.rebuild(p);
    }

    /**
     * Throwing items: Silver Skin pieces and heart projections can't leave the body (the throw is cancelled,
     * which removes the thrown copy; the real heart stays safe). A thrown active Kakugane reverts first.
     */
    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer p)) return;
        ItemStack s = event.getEntity().getItem();
        if (s.getItem() instanceof FormArmorItem || s.getItem() instanceof FormCurioItem) {
            event.setCanceled(true);
            return;
        }
        if (s.getItem() instanceof KakuganeItem) {
            KakuganeData d = KakuganeAccess.data(s);
            if (d.heart()) {
                event.setCanceled(true);
                ActiveForms.deactivate(p);
            } else if (d.active()) {
                ActiveForms.deactivateStack(p, s);
            }
        }
    }

    /**
     * Death drops: Silver Skin pieces and projections vanish, the dead player's Kakugane lose their bond
     * (so someone else can take them up), and a heart Kakugane leaves the body on a true death.
     */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Iterator<ItemEntity> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack s = it.next().getItem();
            if (s.getItem() instanceof FormArmorItem || s.getItem() instanceof FormCurioItem) {
                it.remove();
            } else if (s.getItem() instanceof KakuganeItem) {
                KakuganeData d = KakuganeAccess.data(s);
                if (d.heart()) {
                    it.remove();
                } else if (d.isOwnedBy(p.getUUID())) {
                    s.set(ModRegistry.KAKUGANE_DATA.get(), d.unbond());
                }
            }
        }
        if (!p.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) Hearts.dropOnTrueDeath(p, event.getDrops());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        KakuganeCommand.register(event.getDispatcher());
    }

    private PlayerEvents() {}
}
