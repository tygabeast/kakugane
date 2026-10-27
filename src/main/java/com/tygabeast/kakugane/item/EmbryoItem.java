package com.tygabeast.kakugane.item;

import com.tygabeast.kakugane.core.KakuganeAccess;
import com.tygabeast.kakugane.core.Milestones;
import com.tygabeast.kakugane.data.KakuganeData;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Dropped by Homunculi. Right-click while a Kakugane is active to destroy it; that counts toward the
 * active Kakugane's second milestone. Later waves also let the Alchemist Warriors trade for them.
 */
public class EmbryoItem extends Item {
    public EmbryoItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        if (!KakuganeAccess.state(sp).hasActive()) {
            sp.displayClientMessage(Component.translatable("message.kakugane.embryo_needs_active"), true);
            return InteractionResultHolder.fail(stack);
        }
        stack.consume(1, sp);
        Milestones.credit(sp, KakuganeData.Deeds::addEmbryo);
        sp.serverLevel().sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(this)),
                sp.getX(), sp.getEyeY() - 0.3, sp.getZ(), 16, 0.2, 0.2, 0.2, 0.08);
        level.playSound(null, sp.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0f, 0.6f);
        return InteractionResultHolder.consume(stack);
    }
}
