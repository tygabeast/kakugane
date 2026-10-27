package com.tygabeast.kakugane.item;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.core.KakuganeAccess;
import com.tygabeast.kakugane.core.PlayerState;
import com.tygabeast.kakugane.data.Archetype;
import com.tygabeast.kakugane.data.KakuganeData;
import com.tygabeast.kakugane.data.Trait;
import com.tygabeast.kakugane.registry.ModRegistry;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * A piece of a worn form that sits in a Curios slot: the Aerial Operator mask (head), Alice's wings and the
 * Mantle (back), the Gauntlets (hands) and the Blade Belt (belt).
 *
 * Like the armor pieces, these only exist while their form is active. They can't be taken out of the slot, never
 * drop, and delete themselves if they end up anywhere else (or their Kakugane stops being active). Each piece
 * carries a copy of its Kakugane's data so the client can tint it with the right palette and size it by tier.
 */
public class FormCurioItem extends Item implements ICurioItem {
    private final Archetype form;
    private final String slot;

    public FormCurioItem(Archetype form, String slot, Properties props) {
        super(props);
        this.form = form;
        this.slot = slot;
    }

    public Archetype form() { return form; }
    /** The Curios slot type this piece goes in ("head", "back", "hands" or "belt"). */
    public String slot() { return slot; }

    public static ItemStack create(FormCurioItem item, KakuganeData d) {
        ItemStack s = new ItemStack(item);
        s.set(ModRegistry.FORM_LINK.get(), d.serial());
        s.set(ModRegistry.KAKUGANE_DATA.get(), d);
        return s;
    }

    public static int link(ItemStack s) {
        return s.getOrDefault(ModRegistry.FORM_LINK.get(), 0);
    }

    /** Tier, trait and palette of the Kakugane this piece came from. */
    public static KakuganeData data(ItemStack s) {
        return s.getOrDefault(ModRegistry.KAKUGANE_DATA.get(), KakuganeData.EMPTY);
    }

    // ---- staying put ------------------------------------------------------------------------------

    /** Runs every tick while worn: one field compare, and the piece removes itself once its form has ended. */
    @Override
    public void curioTick(SlotContext ctx, ItemStack stack) {
        if (!(ctx.entity() instanceof ServerPlayer p)) return;
        PlayerState st = KakuganeAccess.state(p);
        if (st.activeForm != form || st.activeSerial != link(stack)) stack.setCount(0);
    }

    /**
     * In the ordinary inventory (dragged out in creative, say) it is no longer worn, so it vanishes. Curios also ticks
     * worn pieces through this method, but with slot index -1; vanilla inventory slots are always 0 or more.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotIndex, boolean selected) {
        if (!level.isClientSide() && slotIndex >= 0) stack.setCount(0);
    }

    @Override
    public boolean canUnequip(SlotContext ctx, ItemStack stack) {
        return ctx.entity() instanceof net.minecraft.world.entity.player.Player p && p.isCreative();
    }

    @Override
    public boolean canEquipFromUse(SlotContext ctx, ItemStack stack) {
        return false;
    }

    @Override
    public ICurio.DropRule getDropRule(SlotContext ctx, DamageSource source, boolean recentlyHit, ItemStack stack) {
        return ICurio.DropRule.DESTROY;
    }

    // ---- stats ------------------------------------------------------------------------------------

    /** Stat traits (Swift, Steadfast), plus each form's own small stat. Read when the piece is equipped. */
    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(SlotContext ctx, ResourceLocation id, ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> map = HashMultimap.create();
        KakuganeData d = data(stack);
        float scale = Trait.scale(d.tier());
        Trait trait = d.traitValue().orElse(null);
        if (trait == Trait.STEADFAST) {
            map.put(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(Kakugane.id("trait_steadfast"), 0.3 * scale, AttributeModifier.Operation.ADD_VALUE));
        }
        if (trait == Trait.SWIFT) {
            map.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(Kakugane.id("trait_swift"), 0.10 * scale, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        switch (form) {
            case GAUNTLETS -> map.put(Attributes.ARMOR, new AttributeModifier(Kakugane.id("gauntlets_armor"), 2 + d.tier(), AttributeModifier.Operation.ADD_VALUE));
            case AERIAL_OPERATOR -> map.put(Attributes.OXYGEN_BONUS, new AttributeModifier(Kakugane.id("aerial_operator_air"), 1000, AttributeModifier.Operation.ADD_VALUE));
            case MANTLE -> map.put(Attributes.SNEAKING_SPEED, new AttributeModifier(Kakugane.id("mantle_sneak"), 0.15 + 0.05 * d.tier(), AttributeModifier.Operation.ADD_VALUE));
            default -> { }
        }
        return map;
    }
}
