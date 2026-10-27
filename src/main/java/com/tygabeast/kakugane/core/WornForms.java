package com.tygabeast.kakugane.core;

import com.tygabeast.kakugane.data.Archetype;
import com.tygabeast.kakugane.data.KakuganeData;
import com.tygabeast.kakugane.item.FormArmorItem;
import com.tygabeast.kakugane.item.FormCurioItem;
import com.tygabeast.kakugane.registry.ModRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Putting worn forms on and taking them off. A worn form takes over either vanilla armor slots (Silver Skin: all
 * four; Greaves: legs) or one Curios slot (head, back, hands or belt). Whatever was in those slots is stored inside
 * the Kakugane (the "stored_armor" component) and given back when the form reverts.
 *
 * Runs only on activation and deactivation, never per tick.
 */
public final class WornForms {
    private static final EquipmentSlot[] ALL_ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** The armor slots a form uses (empty for Curios forms). */
    public static EquipmentSlot[] armorSlots(Archetype form) {
        return switch (form) {
            case SILVER_SKIN -> ALL_ARMOR;
            case GREAVES -> new EquipmentSlot[]{EquipmentSlot.LEGS};
            default -> new EquipmentSlot[0];
        };
    }

    /** The Curios piece item for a form, or null for armor forms. */
    public static FormCurioItem curioPiece(Archetype form) {
        return switch (form) {
            case AERIAL_OPERATOR -> ModRegistry.AERIAL_OPERATOR.get();
            case ALICE -> ModRegistry.ALICE_WINGS.get();
            case GAUNTLETS -> ModRegistry.GAUNTLETS.get();
            case MANTLE -> ModRegistry.MANTLE.get();
            case BLADE_BELT -> ModRegistry.BLADE_BELT.get();
            default -> null;
        };
    }

    /**
     * False when the player has no slot for this form (a Curios slot disabled by a modpack, say), or a slot it
     * needs holds an item with Curse of Binding (outside creative), which can't be taken off to make room.
     */
    public static boolean canWear(ServerPlayer p, Archetype form) {
        FormCurioItem piece = curioPiece(form);
        if (piece == null) {
            if (p.isCreative()) return true;
            for (EquipmentSlot slot : armorSlots(form)) {
                ItemStack current = p.getItemBySlot(slot);
                if (!(current.getItem() instanceof FormArmorItem)
                        && EnchantmentHelper.has(current, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) return false;
            }
            return true;
        }
        return stacks(p, piece.slot()).map(h -> h.getSlots() > 0).orElse(false);
    }

    /** Puts the form on, storing what it displaces in the Kakugane holding it. */
    public static void equip(ServerPlayer p, ItemStack holder, KakuganeData d) {
        Archetype form = d.form().orElseThrow();
        // Never overwrite stored items: if this Kakugane still holds some (it shouldn't), give them back first.
        ItemContainerContents leftover = holder.get(ModRegistry.STORED_ARMOR.get());
        if (leftover != null) {
            leftover.nonEmptyItemsCopy().forEach(s -> p.getInventory().placeItemBackInInventory(s));
            holder.remove(ModRegistry.STORED_ARMOR.get());
        }
        List<ItemStack> stored = new ArrayList<>(4);
        FormCurioItem piece = curioPiece(form);
        if (piece == null) {
            for (EquipmentSlot slot : armorSlots(form)) {
                ItemStack current = p.getItemBySlot(slot);
                boolean ownPiece = current.getItem() instanceof FormArmorItem;
                stored.add(ownPiece ? ItemStack.EMPTY : current.copy());
                p.setItemSlot(slot, form == Archetype.GREAVES ? FormArmorItem.greaves(d, p.registryAccess())
                        : FormArmorItem.silverSkin(slot, d, p.registryAccess()));
            }
        } else {
            IDynamicStackHandler h = stacks(p, piece.slot()).map(ICurioStacksHandler::getStacks).orElse(null);
            if (h == null || h.getSlots() == 0) return;
            int index = 0;
            for (int i = 0; i < h.getSlots(); i++) {
                if (h.getStackInSlot(i).isEmpty()) {
                    index = i;
                    break;
                }
            }
            ItemStack current = h.getStackInSlot(index);
            stored.add(current.getItem() instanceof FormCurioItem ? ItemStack.EMPTY : current.copy());
            h.setStackInSlot(index, FormCurioItem.create(piece, d));
        }
        holder.set(ModRegistry.STORED_ARMOR.get(), ItemContainerContents.fromItems(stored));
    }

    /** Takes the form off and gives back what it displaced (into the slot if free, else the inventory). */
    public static void unequip(ServerPlayer p, ItemStack holder) {
        KakuganeData d = KakuganeAccess.data(holder);
        stripPieces(p, d.serial());
        ItemContainerContents contents = holder.get(ModRegistry.STORED_ARMOR.get());
        holder.remove(ModRegistry.STORED_ARMOR.get());
        if (contents == null) return;
        Archetype form = d.form().orElse(Archetype.SILVER_SKIN);
        FormCurioItem piece = curioPiece(form);
        if (piece == null) {
            EquipmentSlot[] slots = armorSlots(form);
            NonNullList<ItemStack> stored = NonNullList.withSize(slots.length, ItemStack.EMPTY);
            contents.copyInto(stored);
            for (int i = 0; i < slots.length; i++) {
                ItemStack s = stored.get(i);
                if (s.isEmpty()) continue;
                if (p.getItemBySlot(slots[i]).isEmpty()) p.setItemSlot(slots[i], s);
                else p.getInventory().placeItemBackInInventory(s);
            }
        } else {
            NonNullList<ItemStack> stored = NonNullList.withSize(1, ItemStack.EMPTY);
            contents.copyInto(stored);
            ItemStack s = stored.get(0);
            if (s.isEmpty()) return;
            IDynamicStackHandler h = stacks(p, piece.slot()).map(ICurioStacksHandler::getStacks).orElse(null);
            if (h != null) {
                for (int i = 0; i < h.getSlots(); i++) {
                    if (h.getStackInSlot(i).isEmpty()) {
                        h.setStackInSlot(i, s);
                        return;
                    }
                }
            }
            p.getInventory().placeItemBackInInventory(s);
        }
    }

    /** Removes every piece belonging to this serial, from the armor slots and the Curios slots. */
    public static void stripPieces(ServerPlayer p, int serial) {
        for (EquipmentSlot slot : ALL_ARMOR) {
            ItemStack s = p.getItemBySlot(slot);
            if (s.getItem() instanceof FormArmorItem && FormArmorItem.link(s) == serial) p.setItemSlot(slot, ItemStack.EMPTY);
        }
        CuriosApi.getCuriosInventory(p).ifPresent(inv -> {
            for (ICurioStacksHandler handler : inv.getCurios().values()) {
                IDynamicStackHandler h = handler.getStacks();
                for (int i = 0; i < h.getSlots(); i++) {
                    ItemStack s = h.getStackInSlot(i);
                    if (s.getItem() instanceof FormCurioItem && FormCurioItem.link(s) == serial) h.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        });
    }

    private static Optional<ICurioStacksHandler> stacks(ServerPlayer p, String slot) {
        return CuriosApi.getCuriosInventory(p).flatMap(inv -> inv.getStacksHandler(slot));
    }

    private WornForms() {}
}
