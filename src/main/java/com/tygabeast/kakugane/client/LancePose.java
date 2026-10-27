package com.tygabeast.kakugane.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tygabeast.kakugane.ability.Lance;
import com.tygabeast.kakugane.data.Archetype;
import com.tygabeast.kakugane.data.KakuganeData;
import com.tygabeast.kakugane.item.KakuganeItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;
import org.joml.Matrix4f;

/**
 * How a charging Lance is held: couched at the hip and levelled forward, rather than raised like a trident.
 *
 * Third person: a custom arm pose (below) plus the form_lance_charging item model's display transform.
 * First person: a fixed camera-space placement, drawn back as the charge builds and trembling at full charge.
 * The numbers come from tools/pose_preview.py, which solves and previews them with the game's own transform chain.
 */
public final class LancePose {
    /** Added to HumanoidModel.ArmPose by META-INF/enumextensions.json (KAKUGANE_LANCE_CHARGE). */
    public static final EnumProxy<HumanoidModel.ArmPose> CHARGE = new EnumProxy<>(HumanoidModel.ArmPose.class,
            false, (IArmPoseTransformer) LancePose::poseArm);

    // First-person placement for each hand (camera space), applied before the item's own display transform.
    private static final Matrix4f RIGHT = new Matrix4f(
            -0.057442f, -1.043525f, -0.191473f, 0.000000f, -0.629842f, 0.187848f, -0.834817f, 0.000000f,
            0.853760f, 0.068371f, -0.628749f, 0.000000f, 0.395518f, -0.359226f, -0.822476f, 1.000000f);
    private static final Matrix4f LEFT = new Matrix4f(
            0.057442f, -1.043525f, -0.191473f, 0.000000f, 1.058873f, 0.068371f, -0.054960f, 0.000000f,
            0.066299f, -0.187848f, 1.043659f, 0.000000f, -0.132121f, -0.577715f, -0.385487f, 1.000000f);
    // How far the lance draws back toward the camera at full charge (right hand; x is mirrored for the left).
    private static final float PULL_X = 0.056443f, PULL_Y = -0.037629f, PULL_Z = 0.188144f;

    /** True while this entity is charging an active Lance held in this hand. Cheap: a few field reads. */
    public static boolean isCharging(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        if (!entity.isUsingItem() || entity.getUsedItemHand() != hand || entity.getUseItemRemainingTicks() <= 0) return false;
        KakuganeData d = KakuganeItem.data(stack);
        return d.active() && d.form().orElse(null) == Archetype.LANCE;
    }

    /** Third person: arm a little forward and turned in, so the levelled lance sits at the hip. */
    private static void poseArm(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
        part.xRot = -0.42f;
        part.yRot = 0.12f * side;
        part.zRot = 0f;
    }

    /** First person. Allocation-free: two translations and one constant matrix per frame. */
    public static void firstPerson(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partialTick, float equip) {
        boolean right = arm == HumanoidArm.RIGHT;
        float used = stack.getUseDuration(player) - (player.getUseItemRemainingTicks() - partialTick + 1f);
        float charge = Mth.clamp(used / Lance.FULL_CHARGE_TICKS, 0f, 1f);
        float side = right ? 1f : -1f;
        pose.translate(side * PULL_X * charge, PULL_Y * charge + equip * -0.6f, PULL_Z * charge);
        if (charge >= 1f) {
            // Straining at full charge.
            pose.translate(Mth.sin(used * 2.3f) * 0.004f, Mth.cos(used * 2.9f) * 0.004f, 0f);
        }
        pose.mulPose(right ? RIGHT : LEFT);
    }

    private LancePose() {}
}
