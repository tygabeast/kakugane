package com.tygabeast.kakugane.client;

import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.entity.Hound;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Hound model: wolf proportions with a heavier mane, a split jaw and a ridge of spines (64x32 texture).
 * The renderer draws it 1.25x larger than a wolf.
 */
public class HoundModel extends HierarchicalModel<Hound> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kakugane.id("hound"), "main");

    private final ModelPart root, head, rightHind, leftHind, rightFront, leftFront, tail;

    public HoundModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.rightHind = root.getChild("right_hind_leg");
        this.leftHind = root.getChild("left_hind_leg");
        this.rightFront = root.getChild("right_front_leg");
        this.leftFront = root.getChild("left_front_leg");
        this.tail = root.getChild("tail");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        PartDefinition head = r.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(-1.0f, 13.5f, -7.0f));
        head.addOrReplaceChild("skull", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.0f, -3.0f, -2.0f, 6, 6, 4)        // skull
                .texOffs(16, 14).addBox(-2.0f, -5.0f, 0.0f, 2, 2, 1)       // left ear
                .texOffs(16, 14).addBox(2.0f, -5.0f, 0.0f, 2, 2, 1)        // right ear
                .texOffs(0, 10).addBox(-0.5f, -0.5f, -5.0f, 3, 2, 4)       // upper jaw
                .texOffs(50, 22).addBox(-0.5f, 1.6f, -4.6f, 3, 1, 3),      // lower jaw, slightly open
                PartPose.ZERO);
        r.addOrReplaceChild("body", CubeListBuilder.create().texOffs(18, 14).addBox(-3.0f, -2.0f, -3.0f, 6, 9, 6),
                PartPose.offsetAndRotation(0.0f, 14.0f, 2.0f, Mth.HALF_PI, 0.0f, 0.0f));
        PartDefinition mane = r.addOrReplaceChild("mane", CubeListBuilder.create().texOffs(21, 0).addBox(-3.0f, -3.0f, -3.0f, 8, 6, 7),
                PartPose.offsetAndRotation(-1.0f, 14.0f, -3.0f, Mth.HALF_PI, 0.0f, 0.0f));
        mane.addOrReplaceChild("spines", CubeListBuilder.create()
                        .texOffs(52, 0).addBox(-0.5f, -2.0f, -5.5f, 1, 5, 2)
                        .texOffs(52, 0).addBox(2.5f, -2.0f, -5.5f, 1, 5, 2),
                PartPose.offset(0.5f, 0.0f, 0.0f));
        CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 18).addBox(0.0f, 0.0f, -1.0f, 2, 8, 2);
        r.addOrReplaceChild("right_hind_leg", leg, PartPose.offset(-2.5f, 16.0f, 7.0f));
        r.addOrReplaceChild("left_hind_leg", leg, PartPose.offset(0.5f, 16.0f, 7.0f));
        r.addOrReplaceChild("right_front_leg", leg, PartPose.offset(-2.5f, 16.0f, -4.0f));
        r.addOrReplaceChild("left_front_leg", leg, PartPose.offset(0.5f, 16.0f, -4.0f));
        PartDefinition tail = r.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.0f, 12.0f, 8.0f, 0.9f, 0.0f, 0.0f));
        tail.addOrReplaceChild("tail_bone", CubeListBuilder.create().texOffs(9, 18).addBox(0.0f, 0.0f, -1.0f, 2, 8, 2), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Hound hound, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        float swing = Mth.cos(limbSwing * 0.6662f) * 1.4f * limbSwingAmount;
        rightHind.xRot = swing;
        leftHind.xRot = -swing;
        rightFront.xRot = -swing;
        leftFront.xRot = swing;
        tail.yRot = Mth.cos(ageInTicks * 0.15f) * 0.15f + swing * 0.2f;
    }
}
