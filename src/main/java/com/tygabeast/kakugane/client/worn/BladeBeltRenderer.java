package com.tygabeast.kakugane.client.worn;

import com.tygabeast.kakugane.client.BladeArms;
import com.tygabeast.kakugane.data.KakuganeData;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Blade Belt (Valkyrie Skirt): a cuff on each thigh, each carrying two long jointed arms that end in
 * hexagon-studded blades. At rest the arms spread out to the sides and down; when an arm strikes (see
 * client/BladeArms) it swings toward its target, straightens, and snaps back.
 *
 * Arm part references are looked up once, after the model is baked, not every frame.
 */
public class BladeBeltRenderer extends WornRenderer {
    public static final ModelLayerLocation LAYER = layer("blade_belt");

    // Rest angles: outward and a little to the front (arms 0 and 2) or back (1 and 3), tilted down, bent at the joints.
    private static final float SPLAY = 0.5f, TILT = -0.12f, BEND1 = -0.6f, BEND2 = 0.62f;
    private ModelPart[] armRoots, joint1, joint2;
    private final float[] restYaw = {-Mth.HALF_PI - SPLAY, -Mth.HALF_PI + SPLAY, Mth.HALF_PI + SPLAY, Mth.HALF_PI - SPLAY};

    public BladeBeltRenderer() {
        super(LAYER, "blade_belt");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // The cuffs ride on the legs; the arms hang from the hips (on the body), so a walking stride doesn't fling them.
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5f, 1f, -2.5f, 5, 2, 5), PartPose.ZERO);
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 0).mirror().addBox(-2.5f, 1f, -2.5f, 5, 2, 5), PartPose.ZERO);
        PartDefinition hips = root.addOrReplaceChild("hips", CubeListBuilder.create(), PartPose.ZERO);
        arm(hips, 0, -4.4f, -1.2f);
        arm(hips, 1, -4.4f, 1.2f);
        arm(hips, 2, 4.4f, -1.2f);
        arm(hips, 3, 4.4f, 1.2f);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** seg1 -> joint1 -> seg2 -> joint2 -> blade, each a child of the last so bending one moves the rest. */
    private static void arm(PartDefinition hips, int i, float x, float z) {
        PartDefinition seg1 = hips.addOrReplaceChild("arm" + i, CubeListBuilder.create().texOffs(0, 8).addBox(-0.5f, -0.5f, 0f, 1, 1, 7), PartPose.offset(x, 14f, z));
        PartDefinition j1 = seg1.addOrReplaceChild("j1", CubeListBuilder.create().texOffs(0, 17).addBox(-1f, -1f, -1f, 2, 2, 2), PartPose.offset(0, 0, 7f));
        j1.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 8).addBox(-0.5f, -0.5f, 0f, 1, 1, 8), PartPose.ZERO);
        PartDefinition j2 = j1.addOrReplaceChild("j2", CubeListBuilder.create().texOffs(0, 17).addBox(-1f, -1f, -1f, 2, 2, 2), PartPose.offset(0, 0, 8f));
        j2.addOrReplaceChild("blade", CubeListBuilder.create().texOffs(0, 22).addBox(-0.5f, -2f, 0f, 1, 4, 16), PartPose.ZERO);
    }

    @Override
    protected void pose(HumanoidModel<?> model, LivingEntity entity, KakuganeData data, float limbSwingAmount, float ageInTicks, float partialTicks) {
        ModelPart right = root.getChild("right_leg"), left = root.getChild("left_leg"), hips = root.getChild("hips");
        if (armRoots == null) {
            armRoots = new ModelPart[]{hips.getChild("arm0"), hips.getChild("arm1"), hips.getChild("arm2"), hips.getChild("arm3")};
            joint1 = new ModelPart[4];
            joint2 = new ModelPart[4];
            for (int i = 0; i < 4; i++) {
                joint1[i] = armRoots[i].getChild("j1");
                joint2[i] = joint1[i].getChild("j2");
            }
        }
        right.copyFrom(model.rightLeg);
        left.copyFrom(model.leftLeg);
        hips.copyFrom(model.body);
        BladeArms.State strikes = BladeArms.get(entity);
        float now = entity.level().getGameTime() + partialTicks;
        for (int i = 0; i < 4; i++) {
            float idle = Mth.sin(ageInTicks * 0.06f + i * 1.7f) * 0.06f;
            float yaw = restYaw[i] + idle, tilt = TILT, b1 = BEND1, b2 = BEND2;
            if (strikes != null) {
                float t = (now - strikes.start[i]) / BladeArms.STRIKE_TICKS;
                if (t >= 0 && t < 1) {
                    float e = Mth.sin(Mth.PI * t); // out and back
                    float target = Mth.PI - strikes.yaw[i] * Mth.DEG_TO_RAD;
                    yaw = yaw + Mth.wrapDegrees((target - yaw) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * e;
                    tilt = Mth.lerp(e, TILT, 0.05f);
                    b1 = Mth.lerp(e, BEND1, 0f);
                    b2 = Mth.lerp(e, BEND2, 0f);
                }
            }
            armRoots[i].xRot = tilt;
            armRoots[i].yRot = yaw;
            joint1[i].xRot = b1;
            joint2[i].xRot = b2;
        }
    }
}
