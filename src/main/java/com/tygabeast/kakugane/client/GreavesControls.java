package com.tygabeast.kakugane.client;

import com.tygabeast.kakugane.ability.Greaves;
import com.tygabeast.kakugane.data.Archetype;
import com.tygabeast.kakugane.data.KakuganeData;
import com.tygabeast.kakugane.item.FormArmorItem;
import com.tygabeast.kakugane.registry.ModRegistry;
import com.tygabeast.kakugane.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Greaves movement on the client (the client decides player movement in Minecraft, so dashes are applied here and
 * the server is told, see ability/Greaves): press jump in mid-air to dash, sneak in mid-air for the Tier IV stomp.
 * Cost per tick: one armor slot read, then nothing unless the Greaves are worn.
 */
public final class GreavesControls {
    private static boolean jumpWasDown, sneakWasDown;
    private static int dashesLeft;

    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        ItemStack legs = p.getItemBySlot(EquipmentSlot.LEGS);
        if (!(legs.getItem() instanceof FormArmorItem f) || f.form() != Archetype.GREAVES) return;
        int tier = legs.getOrDefault(ModRegistry.KAKUGANE_DATA.get(), KakuganeData.EMPTY).tier();
        boolean jump = mc.options.keyJump.isDown(), sneak = mc.options.keyShift.isDown();
        boolean free = !p.onGround() && !p.getAbilities().flying && !p.isInWater() && !p.isFallFlying()
                && !p.isPassenger() && !p.onClimbable() && mc.screen == null;
        if (p.onGround()) dashesLeft = Greaves.maxDashes(tier);
        if (jump && !jumpWasDown && free && dashesLeft > 0) {
            dashesLeft--;
            Vec3 look = p.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            flat = flat.lengthSqr() < 1e-4 ? Vec3.ZERO : flat.normalize();
            p.setDeltaMovement(flat.x * 1.1, 0.42, flat.z * 1.1);
            for (int i = 0; i < 6; i++) {
                p.level().addParticle(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(),
                        (p.getRandom().nextDouble() - 0.5) * 0.1, 0.02, (p.getRandom().nextDouble() - 0.5) * 0.1);
            }
            PacketDistributor.sendToServer(new ModNetwork.GreavesMove((byte) 0));
        }
        if (sneak && !sneakWasDown && free && tier >= 3) {
            p.setDeltaMovement(p.getDeltaMovement().x * 0.2, -1.8, p.getDeltaMovement().z * 0.2);
            PacketDistributor.sendToServer(new ModNetwork.GreavesMove((byte) 1));
        }
        jumpWasDown = jump;
        sneakWasDown = sneak;
    }

    private GreavesControls() {}
}
