package com.tygabeast.kakugane.core;

import com.tygabeast.kakugane.data.KakuganeData;
import com.tygabeast.kakugane.item.KakuganeItem;
import com.tygabeast.kakugane.registry.ModRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

/**
 * Finds and edits a player's Kakugane by serial, wherever it is: in the inventory or in their chest as a heart.
 * Called from commands, milestones and activation, never from a per-tick path.
 */
public final class KakuganeAccess {
    public static KakuganeData data(ItemStack s) {
        return s.getOrDefault(ModRegistry.KAKUGANE_DATA.get(), KakuganeData.EMPTY);
    }

    public static PlayerState state(ServerPlayer p) {
        return p.getData(ModRegistry.STATE.get());
    }

    public static HeartData heart(ServerPlayer p) {
        return p.getData(ModRegistry.HEART.get());
    }

    /** A Kakugane item in the inventory with this serial (heart projections are skipped). */
    @Nullable
    public static ItemStack findInInventory(Player p, int serial) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof KakuganeItem) {
                KakuganeData d = data(s);
                if (d.serial() == serial && !d.heart()) return s;
            }
        }
        // A Kakugane being dragged in an open inventory screen sits on the cursor, not in a slot.
        ItemStack carried = p.containerMenu.getCarried();
        if (carried.getItem() instanceof KakuganeItem && data(carried).serial() == serial && !data(carried).heart()) return carried;
        return null;
    }

    /** Current data of the player's Kakugane with this serial (inventory or heart). */
    @Nullable
    public static KakuganeData find(ServerPlayer p, int serial) {
        ItemStack s = findInInventory(p, serial);
        if (s != null) return data(s);
        HeartData h = heart(p);
        return h.matches(serial) ? h.data() : null;
    }

    /**
     * Applies a change to the player's Kakugane with this serial. For a heart, the real copy and the
     * projection in the hand (if any) are both changed so they never disagree. Returns the new data.
     */
    @Nullable
    public static KakuganeData edit(ServerPlayer p, int serial, UnaryOperator<KakuganeData> change) {
        KakuganeData result = null;
        ItemStack s = findInInventory(p, serial);
        if (s != null) {
            result = change.apply(data(s));
            s.set(ModRegistry.KAKUGANE_DATA.get(), result);
        }
        HeartData h = heart(p);
        if (h.matches(serial)) {
            result = change.apply(h.data());
            h.stack.set(ModRegistry.KAKUGANE_DATA.get(), result);
            Inventory inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack proj = inv.getItem(i);
                if (proj.getItem() instanceof KakuganeItem && data(proj).heart() && data(proj).serial() == serial) {
                    proj.set(ModRegistry.KAKUGANE_DATA.get(), result);
                }
            }
        }
        return result;
    }

    /** True if a heart projection with this serial is in the inventory or on the cursor. */
    public static boolean hasProjection(Player p, int serial) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof KakuganeItem && data(s).heart() && data(s).serial() == serial) return true;
        }
        ItemStack carried = p.containerMenu.getCarried();
        return carried.getItem() instanceof KakuganeItem && data(carried).heart() && data(carried).serial() == serial;
    }

    /** Removes any heart projection with this serial from the inventory. */
    public static void removeProjections(ServerPlayer p, int serial) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof KakuganeItem && data(s).heart() && data(s).serial() == serial) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private KakuganeAccess() {}
}
