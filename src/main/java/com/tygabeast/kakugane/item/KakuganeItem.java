package com.tygabeast.kakugane.item;

import com.tygabeast.kakugane.ability.Breaker;
import com.tygabeast.kakugane.ability.BowForm;
import com.tygabeast.kakugane.ability.Lance;
import com.tygabeast.kakugane.core.ActiveForms;
import com.tygabeast.kakugane.core.ActiveFormsGuard;
import com.tygabeast.kakugane.data.Archetype;
import com.tygabeast.kakugane.data.Hashing;
import com.tygabeast.kakugane.data.KakuganeData;
import com.tygabeast.kakugane.data.Trait;
import com.tygabeast.kakugane.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * The Kakugane itself. Dormant, it is a hexagonal plate. Active, the same item stack becomes its form:
 * the item model switches picture (see client/ClientSetup), and held forms get weapon stats on the stack.
 *
 * Right-click a dormant Kakugane to activate it (sneak + right-click releases your bond).
 * Right-click an active held form to use its ability. The activation key (default R) toggles it too.
 */
public class KakuganeItem extends Item {
    public KakuganeItem(Properties props) {
        super(props);
    }

    public static KakuganeData data(ItemStack s) {
        return s.getOrDefault(ModRegistry.KAKUGANE_DATA.get(), KakuganeData.EMPTY);
    }

    /** "No. XLIV" */
    public static Component serialText(KakuganeData d) {
        return Component.translatable("item.kakugane.kakugane.serial", Hashing.roman(d.serial()));
    }

    // ---- Weapon stats for held forms ----------------------------------------------------------------

    /**
     * Stats put on a held form when it activates (and removed when it reverts). Using the vanilla base ids
     * makes the tooltip read like a normal weapon ("7 Attack Damage").
     */
    public static ItemAttributeModifiers heldModifiers(KakuganeData d) {
        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
        int t = d.tier();
        Archetype form = d.form().orElse(null);
        if (form == Archetype.LANCE) {
            b.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 6.0 + t, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
            b.add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.6, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
            b.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(com.tygabeast.kakugane.Kakugane.id("lance_reach"), 0.75, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        } else if (form == Archetype.BREAKER) {
            b.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 9.0 + 1.5 * t, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
            b.add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -3.2, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        }
        if (d.traitValue().orElse(null) == Trait.SWIFT) {
            b.add(Attributes.MOVEMENT_SPEED, new AttributeModifier(com.tygabeast.kakugane.Kakugane.id("trait_swift"),
                    0.10 * Trait.scale(t), AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.MAINHAND);
        }
        return b.build();
    }

    // ---- Using it ----------------------------------------------------------------------------------

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        KakuganeData d = data(stack);

        if (!d.active()) {
            if (d.heart()) return InteractionResultHolder.pass(stack);
            if (player.isShiftKeyDown() && d.isOwnedBy(player.getUUID())) {
                if (player instanceof ServerPlayer sp) {
                    stack.set(ModRegistry.KAKUGANE_DATA.get(), d.unbond());
                    sp.displayClientMessage(Component.translatable("message.kakugane.released", serialText(d)), true);
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
            }
            if (player instanceof ServerPlayer sp) ActiveForms.activateStack(sp, stack);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        Archetype form = d.form().orElse(null);
        if (form == null) return InteractionResultHolder.pass(stack);
        switch (form) {
            case LANCE, BOW -> {
                if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
                player.startUsingItem(hand);
                return InteractionResultHolder.consume(stack);
            }
            case BREAKER -> {
                if (!player.onGround() || player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
                if (player instanceof ServerPlayer sp) Breaker.slam(sp, stack, d);
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
            }
            default -> {
                // Worn forms held in the hand: right-click reverts them.
                if (player instanceof ServerPlayer sp) ActiveForms.deactivate(sp);
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
            }
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        KakuganeData d = data(stack);
        if (!d.active()) return 0;
        Archetype f = d.form().orElse(null);
        return f == Archetype.LANCE || f == Archetype.BOW ? 72000 : 0;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        KakuganeData d = data(stack);
        if (!d.active()) return UseAnim.NONE;
        Archetype f = d.form().orElse(null);
        if (f == Archetype.LANCE) return UseAnim.SPEAR;
        if (f == Archetype.BOW) return UseAnim.BOW;
        return UseAnim.NONE;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player p)) return;
        KakuganeData d = data(stack);
        int used = getUseDuration(stack, entity) - timeLeft;
        Archetype f = d.form().orElse(null);
        if (f == Archetype.LANCE) Lance.release(p, stack, d, used);
        else if (f == Archetype.BOW) BowForm.release(p, stack, d, used);
    }

    /** Creative players should not break blocks by swinging a melee form, just like swords. */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        KakuganeData d = data(player.getMainHandItem());
        return !(player.isCreative() && d.active());
    }

    /**
     * Runs every tick for each Kakugane in a player's inventory. Kept cheap: one component read, and
     * the real checks only for active stacks or projections (see ActiveFormsGuard).
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer p)) return;
        ActiveFormsGuard.tick(p, stack, selected);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return data(stack).pending();
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        // Only bob the hand when the form actually changes, not for every small data update.
        if (slotChanged || oldStack.getItem() != newStack.getItem()) return true;
        KakuganeData a = data(oldStack), b = data(newStack);
        return a.active() != b.active() || !a.archetype().equals(b.archetype());
    }

    // ---- Name and tooltip -------------------------------------------------------------------------

    @Override
    public Component getName(ItemStack stack) {
        KakuganeData d = data(stack);
        if (d.serial() <= 0) return super.getName(stack);
        return Component.translatable("item.kakugane.kakugane.numbered", Hashing.roman(d.serial()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        KakuganeData d = data(stack);
        ChatFormatting g = ChatFormatting.GRAY;
        if (d.heart()) lines.add(Component.translatable("tooltip.kakugane.heart").withStyle(ChatFormatting.RED));
        Component form = d.form().map(Archetype::displayName).orElse(Component.translatable("tooltip.kakugane.unformed"));
        lines.add(Component.translatable("tooltip.kakugane.form", form, Hashing.roman(d.tier() + 1)).withStyle(g));
        d.traitValue().ifPresent(t -> lines.add(Component.translatable("tooltip.kakugane.trait", t.displayName(), t.description()).withStyle(g)));
        if (d.serial() > 0) lines.add(Component.translatable("tooltip.kakugane.palette", d.palette().displayName()).withStyle(g));
        lines.add(d.bonded()
                ? Component.translatable("tooltip.kakugane.bonded", d.ownerName()).withStyle(g)
                : Component.translatable("tooltip.kakugane.unbonded").withStyle(ChatFormatting.DARK_GRAY));
        if (!d.shaper().isEmpty()) lines.add(Component.translatable("tooltip.kakugane.shaper", d.shaper()).withStyle(g));
        if (!d.lineage().isEmpty()) lines.add(Component.translatable("tooltip.kakugane.lineage", String.join(", ", d.lineage())).withStyle(ChatFormatting.DARK_GRAY));
        if (d.bonded() && d.milestones() < 3) {
            lines.add(Component.translatable("tooltip.kakugane.milestones", d.milestones(), d.deeds().embryos()).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (d.pending()) lines.add(Component.translatable("tooltip.kakugane.pending").withStyle(ChatFormatting.GOLD));
    }
}
