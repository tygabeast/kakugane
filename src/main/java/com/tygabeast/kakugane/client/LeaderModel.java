package com.tygabeast.kakugane.client;

import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.entity.LabLeader;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Lab Leader: a humanoid (64x64, the left limbs mirror the right) plus motif parts that only show once it has
 * transformed: Hound snout, ears, claws and tail; Ram horns and shoulder plates; Crawler back legs.
 */
public class LeaderModel extends HumanoidModel<LabLeader> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kakugane.id("lab_leader"), "main");

    private final ModelPart houndHead, houndClawsR, houndClawsL, houndTail, ramHorns, ramPlateR, ramPlateL;
    private final ModelPart[] crawlerLegs = new ModelPart[4];

    public LeaderModel(ModelPart root) {
        super(root);
        houndHead = head.getChild("hound_head");
        ramHorns = head.getChild("ram_horns");
        houndClawsR = rightArm.getChild("hound_claws");
        houndClawsL = leftArm.getChild("hound_claws");
        ramPlateR = rightArm.getChild("ram_plate");
        ramPlateL = leftArm.getChild("ram_plate");
        houndTail = body.getChild("hound_tail");
        for (int i = 0; i < 4; i++) crawlerLegs[i] = body.getChild("crawler_leg_" + i);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
        PartDefinition r = mesh.getRoot();
        PartDefinition head = r.getChild("head");
        head.addOrReplaceChild("hound_head", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-2f, -4f, -7.5f, 4f, 3f, 4f)
                .texOffs(16, 32).addBox(-4f, -10f, -1f, 2f, 3f, 1f)
                .texOffs(16, 32).mirror().addBox(2f, -10f, -1f, 2f, 3f, 1f), PartPose.ZERO);
        head.addOrReplaceChild("ram_horns", CubeListBuilder.create()
                .texOffs(0, 40).addBox(3.5f, -9f, -3f, 2f, 2f, 6f)
                .texOffs(0, 48).addBox(4.5f, -8f, 2f, 2f, 5f, 2f)
                .texOffs(0, 40).mirror().addBox(-5.5f, -9f, -3f, 2f, 2f, 6f)
                .texOffs(0, 48).mirror().addBox(-6.5f, -8f, 2f, 2f, 5f, 2f), PartPose.ZERO);
        PartDefinition rightArm = r.getChild("right_arm");
        PartDefinition leftArm = r.getChild("left_arm");
        CubeListBuilder claws = CubeListBuilder.create().texOffs(24, 32)
                .addBox(-3.5f, 9f, -4f, 1f, 1f, 3f).addBox(-2f, 9f, -4f, 1f, 1f, 3f).addBox(-0.5f, 9f, -4f, 1f, 1f, 3f);
        rightArm.addOrReplaceChild("hound_claws", claws, PartPose.ZERO);
        leftArm.addOrReplaceChild("hound_claws", CubeListBuilder.create().texOffs(24, 32).mirror()
                .addBox(-0.5f, 9f, -4f, 1f, 1f, 3f).addBox(1f, 9f, -4f, 1f, 1f, 3f).addBox(2.5f, 9f, -4f, 1f, 1f, 3f), PartPose.ZERO);
        rightArm.addOrReplaceChild("ram_plate", CubeListBuilder.create().texOffs(16, 40).addBox(-4f, -3f, -3f, 5f, 2f, 6f), PartPose.ZERO);
        leftArm.addOrReplaceChild("ram_plate", CubeListBuilder.create().texOffs(16, 40).mirror().addBox(-1f, -3f, -3f, 5f, 2f, 6f), PartPose.ZERO);
        PartDefinition body = r.getChild("body");
        body.addOrReplaceChild("hound_tail", CubeListBuilder.create().texOffs(32, 32).addBox(-1f, 0f, 0f, 2f, 2f, 7f),
                PartPose.offsetAndRotation(0f, 10f, 2f, -0.6f, 0f, 0f));
        // Four spider legs from the shoulder blades, fanned out sideways past the arms so the spider shape reads from
        // the front as well as the back. {x, y, sideways angle}: the lower pair spreads flatter than the upper pair.
        float[][] legs = {{-2.5f, 2f, -1.5f}, {2.5f, 2f, 1.5f}, {-2.5f, 6f, -1.6f}, {2.5f, 6f, 1.6f}};
        for (int i = 0; i < 4; i++) {
            // The lower (rear) pair is longer and thicker, so the spider silhouette reads from any angle.
            CubeListBuilder leg = i < 2 ? CubeListBuilder.create().texOffs(40, 48).addBox(-1f, -1f, 0f, 2f, 2f, 10f)
                    : CubeListBuilder.create().texOffs(8, 49).addBox(-1.5f, -1.5f, 0f, 3f, 3f, 12f);
            body.addOrReplaceChild("crawler_leg_" + i, leg, PartPose.offsetAndRotation(legs[i][0], legs[i][1], 2f, legLift(i), legs[i][2], 0f));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** How far each crawler leg tilts up from pointing straight back (radians): upper pair 0.7, lower pair 0.45. */
    private static float legLift(int i) {
        return i < 2 ? 0.7f : 0.45f;
    }

    @Override
    public void setupAnim(LabLeader e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        hat.visible = false;
        boolean t = e.transformed();
        LabLeader.Motif m = e.motif();
        houndHead.visible = houndClawsR.visible = houndClawsL.visible = houndTail.visible = t && m == LabLeader.Motif.HOUND;
        ramHorns.visible = ramPlateR.visible = ramPlateL.visible = t && m == LabLeader.Motif.RAM;
        for (int i = 0; i < 4; i++) {
            crawlerLegs[i].visible = t && m == LabLeader.Motif.CRAWLER;
            crawlerLegs[i].xRot = legLift(i) + 0.15f * Mth.sin(ageInTicks * 0.35f + i * 1.7f); // a slow, uneven twitch
        }
        if (!t) return;
        if (m == LabLeader.Motif.HOUND) {
            AnimationUtils.animateZombieArms(leftArm, rightArm, e.isAggressive(), attackTime, ageInTicks);
            houndTail.yRot = Mth.sin(ageInTicks * 0.3f) * 0.3f;
        } else if (m == LabLeader.Motif.RAM && e.chargeState() > 0) {
            body.xRot = 0.35f;
            head.xRot = 0.5f;
            rightArm.xRot = leftArm.xRot = 0.7f;
        }
    }
}
