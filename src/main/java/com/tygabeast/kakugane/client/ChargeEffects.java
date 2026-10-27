package com.tygabeast.kakugane.client;

import com.tygabeast.kakugane.ability.Lance;
import com.tygabeast.kakugane.data.KakuganeData;
import com.tygabeast.kakugane.item.KakuganeItem;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The Lance's charge indicator, purely client-side: sparks in the Kakugane's palette colour gather along the lance
 * as it charges, and at full charge it crackles at the tip with a chime. Shown for every player in view, so you can
 * see an opponent winding up. Runs every other tick and only does work for players who are charging a lance.
 */
public final class ChargeEffects {
    private static final IntSet FULL = new IntOpenHashSet(); // entity ids that have already chimed this charge
    private static ClientLevel lastLevel; // entity ids restart in each world, so forget them on a change

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            FULL.clear();
            lastLevel = level;
        }
        if (level == null || mc.isPaused()) return;
        if ((level.getGameTime() & 1) != 0) return;
        for (Player p : level.players()) {
            ItemStack stack = p.getUseItem();
            if (stack.isEmpty() || !(stack.getItem() instanceof KakuganeItem)
                    || !LancePose.isCharging(p, p.getUsedItemHand(), stack)) {
                if (!FULL.isEmpty()) FULL.remove(p.getId());
                continue;
            }
            float charge = Mth.clamp(p.getTicksUsingItem() / (float) Lance.FULL_CHARGE_TICKS, 0f, 1f);
            if (charge < 0.1f) continue;
            spawn(mc, level, p, stack, charge);
        }
    }

    private static void spawn(Minecraft mc, ClientLevel level, Player p, ItemStack stack, float charge) {
        KakuganeData d = KakuganeItem.data(stack);
        int rgb = d.palette().rgb();
        DustParticleOptions dust = new DustParticleOptions(
                new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f), 0.6f + 0.6f * charge);

        // Which side the lance is on.
        HumanoidArm arm = p.getUsedItemHand() == InteractionHand.MAIN_HAND ? p.getMainArm() : p.getMainArm().getOpposite();
        float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
        boolean firstPerson = p == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON;

        Vec3 base, dir;
        if (firstPerson) {
            // Matches the first-person charge placement: lower right, pointing ahead past the crosshair.
            Vec3 look = p.getViewVector(1f);
            Vec3 right = new Vec3(-look.z, 0, look.x).normalize().scale(side);
            base = p.getEyePosition().add(look.scale(0.9)).add(right.scale(0.3)).add(0, -0.32, 0);
            dir = look;
        } else {
            // Couched at the hip, levelled along the body's facing.
            float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
            Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(side);
            base = p.position().add(0, 0.8, 0).add(right.scale(0.38)).add(fwd.scale(0.2));
            dir = fwd;
        }
        double reach = firstPerson ? 0.9 : 1.5;
        int count = 1 + (int) (charge * 3);
        var rnd = p.getRandom();
        for (int i = 0; i < count; i++) {
            double t = rnd.nextDouble() * reach * charge;
            double jitter = 0.12 * (1.0 - charge * 0.5);
            level.addParticle(dust, base.x + dir.x * t + (rnd.nextDouble() - 0.5) * jitter,
                    base.y + dir.y * t + (rnd.nextDouble() - 0.5) * jitter,
                    base.z + dir.z * t + (rnd.nextDouble() - 0.5) * jitter, 0, 0, 0);
        }
        if (charge >= 1f) {
            Vec3 tip = base.add(dir.scale(reach));
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z,
                    (rnd.nextDouble() - 0.5) * 0.2, (rnd.nextDouble() - 0.5) * 0.2, (rnd.nextDouble() - 0.5) * 0.2);
            if (FULL.add(p.getId())) {
                level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                        1.2f, 1.6f, false);
            }
        }
    }

    private ChargeEffects() {}
}
