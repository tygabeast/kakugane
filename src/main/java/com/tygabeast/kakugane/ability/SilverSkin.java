package com.tygabeast.kakugane.ability;

import com.tygabeast.kakugane.core.KakuganeAccess;
import com.tygabeast.kakugane.core.PlayerState;
import com.tygabeast.kakugane.data.KakuganeData;
import com.tygabeast.kakugane.item.SilverSkinArmorItem;
import com.tygabeast.kakugane.registry.ModRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Silver Skin (canon): a full suit of hexagonal plating over all four armor slots.
 * Whatever armor was worn is stored inside the Kakugane and given back when the form reverts.
 * Each hit taken hardens the plating a little more (stacks reset after 6 seconds without a hit).
 */
public final class SilverSkin {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Puts the suit on, storing the old armor in the Kakugane holding the form. Armor with Curse of Binding stays. */
    public static void equip(ServerPlayer p, ItemStack holder, KakuganeData d) {
        List<ItemStack> stored = new ArrayList<>(4);
        for (EquipmentSlot slot : SLOTS) {
            ItemStack current = p.getItemBySlot(slot);
            boolean ownPiece = current.getItem() instanceof SilverSkinArmorItem;
            if (!ownPiece && EnchantmentHelper.has(current, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE) && !p.isCreative()) {
                stored.add(ItemStack.EMPTY);
                continue;
            }
            stored.add(ownPiece ? ItemStack.EMPTY : current.copy());
            p.setItemSlot(slot, SilverSkinArmorItem.create(slot, d, p.registryAccess()));
        }
        holder.set(ModRegistry.STORED_ARMOR.get(), ItemContainerContents.fromItems(stored));
    }

    /** Takes the suit off and gives back the stored armor (into the slot if free, else the inventory or the ground). */
    public static void unequip(ServerPlayer p, ItemStack holder) {
        stripPieces(p, KakuganeAccess.data(holder).serial());
        ItemContainerContents contents = holder.get(ModRegistry.STORED_ARMOR.get());
        if (contents == null) return;
        NonNullList<ItemStack> stored = NonNullList.withSize(SLOTS.length, ItemStack.EMPTY);
        contents.copyInto(stored);
        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack s = stored.get(i);
            if (s.isEmpty()) continue;
            if (p.getItemBySlot(SLOTS[i]).isEmpty()) p.setItemSlot(SLOTS[i], s);
            else p.getInventory().placeItemBackInInventory(s);
        }
        holder.remove(ModRegistry.STORED_ARMOR.get());
    }

    /** Removes the Silver Skin pieces belonging to this serial from the armor slots. */
    public static void stripPieces(ServerPlayer p, int serial) {
        for (EquipmentSlot slot : SLOTS) {
            ItemStack s = p.getItemBySlot(slot);
            if (s.getItem() instanceof SilverSkinArmorItem && SilverSkinArmorItem.link(s) == serial) {
                p.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

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
