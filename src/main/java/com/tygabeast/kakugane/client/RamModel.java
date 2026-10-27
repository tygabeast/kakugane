package com.tygabeast.kakugane.client;

import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.entity.Ram;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Ram: goat proportions (vanilla's goat layout) with curled horns and a frontal plate. 128x64 texture: the goat's
 * UV layout on the left half, horns and plate on the right half. Head lowers during the wind-up and the charge.
 */
public class RamModel extends QuadrupedModel<Ram> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kakugane.id("ram"), "main");

    public RamModel(ModelPart root) {
        super(root, true, 19.0f, 1.0f, 2.5f, 2.0f, 24);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        PartDefinition head = r.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(2, 61).addBox("right ear", -6f, -11f, -10f, 3f, 2f, 1f)
                .texOffs(2, 61).mirror().addBox("left ear", 2f, -11f, -10f, 3f, 2f, 1f), PartPose.offset(1f, 14f, 0f));
        head.addOrReplaceChild("left_horn", CubeListBuilder.create()
                .texOffs(64, 0).addBox(2.5f, -15f, -9f, 3f, 3f, 6f)
                .texOffs(64, 10).addBox(3.5f, -13f, -4f, 3f, 7f, 3f), PartPose.ZERO);
        head.addOrReplaceChild("right_horn", CubeListBuilder.create()
                .texOffs(64, 0).mirror().addBox(-6.5f, -15f, -9f, 3f, 3f, 6f)
                .texOffs(64, 10).mirror().addBox(-7.5f, -13f, -4f, 3f, 7f, 3f), PartPose.ZERO);
        head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(34, 46).addBox(-3f, -4f, -8f, 5f, 7f, 10f),
                PartPose.offsetAndRotation(0f, -8f, -8f, 0.9599f, 0f, 0f));
        head.addOrReplaceChild("brow_plate", CubeListBuilder.create().texOffs(64, 24).addBox(-4f, -14f, -11f, 7f, 4f, 2f), PartPose.ZERO);
        r.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(1, 1).addBox(-4f, -17f, -7f, 9f, 11f, 16f)
                .texOffs(0, 28).addBox(-5f, -18f, -8f, 11f, 14f, 11f)
                .texOffs(64, 32).addBox(-4f, -13f, -9.5f, 9f, 8f, 2f), PartPose.offset(0f, 24f, 0f));
        r.addOrReplaceChild("left_hind_leg", CubeListBuilder.create().texOffs(36, 29).addBox(0f, 4f, 0f, 3f, 6f, 3f), PartPose.offset(1f, 14f, 4f));
        r.addOrReplaceChild("right_hind_leg", CubeListBuilder.create().texOffs(49, 29).addBox(0f, 4f, 0f, 3f, 6f, 3f), PartPose.offset(-3f, 14f, 4f));
        r.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(49, 2).addBox(0f, 0f, 0f, 3f, 10f, 3f), PartPose.offset(1f, 14f, -6f));
        r.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(35, 2).addBox(0f, 0f, 0f, 3f, 10f, 3f), PartPose.offset(-3f, 14f, -6f));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void setupAnim(Ram ram, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(ram, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        int state = ram.chargeState();
        if (state == 1) head.xRot = 0.55f + 0.08f * net.minecraft.util.Mth.sin(ageInTicks * 1.3f); // pawing, head down
        else if (state == 2) head.xRot = 0.75f;
    }
}
