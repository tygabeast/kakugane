package com.tygabeast.kakugane.entity;

import com.tygabeast.kakugane.registry.ModRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Arrow conjured by the Bow form. Counts as alchemical, cannot be picked up, and fades soon after landing. */
public class AlchemicArrow extends AbstractArrow {
    public AlchemicArrow(EntityType<? extends AlchemicArrow> type, Level level) {
        super(type, level);
    }

    public AlchemicArrow(Level level, LivingEntity owner) {
        super(ModRegistry.ALCHEMIC_ARROW.get(), owner, level, new ItemStack(Items.ARROW), null);
        this.pickup = Pickup.DISALLOWED;
    }

    @Override
    public void tick() {
        super.tick();
        // Fade one second after sticking in something, or after 10 seconds of flight.
        if (!level().isClientSide() && (inGroundTime > 20 || tickCount > 200)) discard();
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.ARROW);
    }
}
