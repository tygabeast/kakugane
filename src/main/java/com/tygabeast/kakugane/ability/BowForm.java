package com.tygabeast.kakugane.ability;

import com.tygabeast.kakugane.data.KakuganeData;
import com.tygabeast.kakugane.entity.AlchemicArrow;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

/**
 * Bow (inspired by Angel Gozen): draws like a normal bow but needs no ammo; it fires conjured arrows
 * that cannot be picked up and fade shortly after landing. Tiers add damage.
 */
public final class BowForm {
    public static void release(Player p, ItemStack stack, KakuganeData d, int usedTicks) {
        float power = BowItem.getPowerForTime(usedTicks);
        if (power < 0.1f || p.level().isClientSide()) return;
        AlchemicArrow arrow = new AlchemicArrow(p.level(), p);
        arrow.shootFromRotation(p, p.getXRot(), p.getYRot(), 0.0f, power * 3.0f, 1.0f);
        arrow.setCritArrow(power >= 1.0f);
        arrow.setBaseDamage(2.0 + 0.5 * d.tier());
        p.level().addFreshEntity(arrow);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS,
                1.0f, 1.2f / (p.getRandom().nextFloat() * 0.4f + 1.2f) + power * 0.5f);
    }

    private BowForm() {}
}
