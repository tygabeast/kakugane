package com.tygabeast.kakugane.core;

import com.tygabeast.kakugane.item.KakuganeItem;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/**
 * Looks at what a player had ready (hotbar + offhand) at the moment of a kill, to tell a real choice
 * ("I had a sword but used the bow") from no choice ("the bow was all I had").
 * Only called from kill events, so scanning 10 slots is cheap.
 */
public final class Loadout {
    public static boolean hasMelee(Player p) {
        return any(p, s -> s.getItem() instanceof SwordItem || s.getItem() instanceof AxeItem
                || s.getItem() instanceof TridentItem || s.getItem() instanceof MaceItem);
    }

    /** A melee weapon other than an axe (sword, trident, mace). */
    public static boolean hasNonAxeMelee(Player p) {
        return any(p, s -> s.getItem() instanceof SwordItem || s.getItem() instanceof TridentItem || s.getItem() instanceof MaceItem);
    }

    /** A bow or crossbow that can actually fire (has ammo, or the player is in creative / has Infinity). */
    public static boolean hasRanged(Player p) {
        return any(p, s -> s.getItem() instanceof ProjectileWeaponItem && !p.getProjectile(s).isEmpty());
    }

    private static boolean any(Player p, java.util.function.Predicate<ItemStack> test) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && !(s.getItem() instanceof KakuganeItem) && test.test(s)) return true;
        }
        ItemStack off = p.getOffhandItem();
        return !off.isEmpty() && !(off.getItem() instanceof KakuganeItem) && test.test(off);
    }

    private Loadout() {}
}
