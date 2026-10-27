package com.tygabeast.kakugane.core;

import com.tygabeast.kakugane.KakuganeConfig;
import com.tygabeast.kakugane.data.KakuganeData;
import com.tygabeast.kakugane.item.KakuganeItem;
import com.tygabeast.kakugane.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;

/**
 * The heart Kakugane. Once per player, ever: if they would die while carrying an unbonded Kakugane,
 * it takes the place of their heart and saves them. It then lives in HeartData, not the inventory.
 */
public final class Hearts {

    /** Called from the death event. Returns true if the player was saved (the death must then be cancelled). */
    public static boolean trySave(ServerPlayer p) {
        if (!KakuganeConfig.HEART_ENABLED.get()) return false;
        HeartData h = KakuganeAccess.heart(p);
        if (h.used) return false;

        Inventory inv = p.getInventory();
        ItemStack source = null;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof KakuganeItem) {
                KakuganeData d = KakuganeAccess.data(s);
                if (!d.bonded() && !d.heart()) {
                    source = s;
                    break;
                }
            }
        }
        if (source == null) return false;

        ItemStack heart = source.copyWithCount(1);
        source.shrink(1);
        KakuganeData d = ActiveForms.ensureSerial(p, heart, KakuganeAccess.data(heart));
        d = d.bondTo(p.getUUID(), p.getGameProfile().getName()).withHeart(true).withActive(false);
        heart.set(ModRegistry.KAKUGANE_DATA.get(), d);
        giveBackStored(p, heart);
        h.stack = heart;
        h.used = true;

        p.setHealth(Math.max(4.0f, p.getMaxHealth() * 0.3f));
        p.clearFire();
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 2));
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
        p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.1, p.getZ(), 40, 0.3, 0.5, 0.3, 0.12);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 0.6f);
        p.sendSystemMessage(Component.translatable("message.kakugane.heart_saved", KakuganeItem.serialText(d)).withStyle(ChatFormatting.RED));
        return true;
    }

    /**
     * Anything a Kakugane is still storing (armor a worn form displaced) goes back to the player, or is dropped if
     * the inventory is full. Normally the form has already reverted and this finds nothing; it is a safety net.
     */
    private static void giveBackStored(ServerPlayer p, ItemStack kakugane) {
        net.minecraft.world.item.component.ItemContainerContents stored = kakugane.get(ModRegistry.STORED_ARMOR.get());
        if (stored == null) return;
        kakugane.remove(ModRegistry.STORED_ARMOR.get());
        stored.nonEmptyItemsCopy().forEach(item -> p.getInventory().placeItemBackInInventory(item));
    }

    /** On a true death the heart Kakugane leaves the body as an unbonded Kakugane that keeps its form. */
    public static void dropOnTrueDeath(ServerPlayer p, Collection<ItemEntity> drops) {
        HeartData h = KakuganeAccess.heart(p);
        if (!h.has()) return;
        ItemStack s = h.stack;
        h.stack = ItemStack.EMPTY;
        s.set(ModRegistry.KAKUGANE_DATA.get(), KakuganeAccess.data(s).unbond().withHeart(false).withActive(false));
        // On death the stored items join the other drops rather than an inventory that has already been emptied.
        net.minecraft.world.item.component.ItemContainerContents stored = s.get(ModRegistry.STORED_ARMOR.get());
        if (stored != null) {
            s.remove(ModRegistry.STORED_ARMOR.get());
            stored.nonEmptyItemsCopy().forEach(item -> drops.add(new ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), item)));
        }
        ItemEntity e = new ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), s);
        e.setDefaultPickUpDelay();
        drops.add(e);
    }

    /** Operator command: give the heart back as an item and allow a new heart. */
    public static void reset(ServerPlayer p) {
        ActiveForms.deactivate(p);
        HeartData h = KakuganeAccess.heart(p);
        if (h.has()) {
            ItemStack s = h.stack;
            h.stack = ItemStack.EMPTY;
            s.set(ModRegistry.KAKUGANE_DATA.get(), KakuganeAccess.data(s).withHeart(false).withActive(false));
            p.getInventory().placeItemBackInInventory(s);
        }
        h.used = false;
    }

    private Hearts() {}
}
