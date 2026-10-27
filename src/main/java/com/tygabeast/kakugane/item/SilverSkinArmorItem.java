package com.tygabeast.kakugane.item;

import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.core.KakuganeAccess;
import com.tygabeast.kakugane.core.PlayerState;
import com.tygabeast.kakugane.data.Archetype;
import com.tygabeast.kakugane.data.KakuganeData;
import com.tygabeast.kakugane.data.Trait;
import com.tygabeast.kakugane.registry.ModRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.Level;

/**
 * One piece of the Silver Skin suit. These only exist while the form is active: a piece that is anywhere
 * other than its armor slot, or whose Kakugane is no longer active, removes itself.
 */
public class SilverSkinArmorItem extends ArmorItem {
    public SilverSkinArmorItem(Holder<ArmorMaterial> material, Type type, Properties props) {
        super(material, type, props);
    }

    /** Builds the piece for one slot, with stats scaled by tier and the accent tinted with the palette. */
    public static ItemStack create(EquipmentSlot slot, KakuganeData d, HolderLookup.Provider registries) {
        SilverSkinArmorItem item = switch (slot) {
            case HEAD -> ModRegistry.SILVER_SKIN_HELMET.get();
            case CHEST -> ModRegistry.SILVER_SKIN_CHESTPLATE.get();
            case LEGS -> ModRegistry.SILVER_SKIN_LEGGINGS.get();
            default -> ModRegistry.SILVER_SKIN_BOOTS.get();
        };
        ItemStack s = new ItemStack(item);
        int t = d.tier();
        int baseArmor = switch (slot) { case HEAD, FEET -> 3; case LEGS -> 6; default -> 8; };
        float scale = Trait.scale(t);
        Trait trait = d.traitValue().orElse(null);
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(slot);
        String n = slot.getName();

        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR, new AttributeModifier(Kakugane.id("silver_skin_armor_" + n), baseArmor + (slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS ? t : 0), AttributeModifier.Operation.ADD_VALUE), group)
                .add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(Kakugane.id("silver_skin_toughness_" + n), 3.0 + 0.5 * t, AttributeModifier.Operation.ADD_VALUE), group)
                .add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(Kakugane.id("silver_skin_knockback_" + n), 0.1, AttributeModifier.Operation.ADD_VALUE), group);
        if (slot == EquipmentSlot.CHEST && trait == Trait.STEADFAST) {
            b.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(Kakugane.id("trait_steadfast"), 0.3 * scale, AttributeModifier.Operation.ADD_VALUE), group);
        }
        if (slot == EquipmentSlot.CHEST && trait == Trait.SWIFT) {
            b.add(Attributes.MOVEMENT_SPEED, new AttributeModifier(Kakugane.id("trait_swift"), 0.10 * scale, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), group);
        }
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, b.build());
        s.set(DataComponents.UNBREAKABLE, new Unbreakable(false));
        s.set(DataComponents.DYED_COLOR, new DyedItemColor(d.palette().rgb(), false));
        s.set(ModRegistry.FORM_LINK.get(), d.serial());
        // Hidden Curses of Binding and Vanishing: the suit can't be taken off in survival or put in a chest,
        // and never drops. No glint, no tooltip line.
        var enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        ItemEnchantments.Mutable curses = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        curses.set(enchantments.getOrThrow(Enchantments.BINDING_CURSE), 1);
        curses.set(enchantments.getOrThrow(Enchantments.VANISHING_CURSE), 1);
        s.set(DataComponents.ENCHANTMENTS, curses.toImmutable().withTooltip(false));
        s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        return s;
    }

    public static int link(ItemStack s) {
        return s.getOrDefault(ModRegistry.FORM_LINK.get(), 0);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer p)) return;
        PlayerState st = KakuganeAccess.state(p);
        boolean worn = p.getItemBySlot(getEquipmentSlot()) == stack;
        if (!worn || st.activeForm != Archetype.SILVER_SKIN || st.activeSerial != link(stack)) {
            stack.setCount(0);
        }
    }
}
