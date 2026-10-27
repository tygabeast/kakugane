package com.tygabeast.kakugane.client.worn;

import com.tygabeast.kakugane.data.KakuganeData;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;

/**
 * Aerial Operator: a huge gas mask. A face plate with two round goggle lenses, a big filter snout and a canister
 * on each cheek, held on by a strap. Follows the head.
 */
public class MaskRenderer extends WornRenderer {
    public static final ModelLayerLocation LAYER = layer("aerial_operator");

    public MaskRenderer() {
        super(LAYER, "aerial_operator");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition head = mesh.getRoot().addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        head.addOrReplaceChild("face", CubeListBuilder.create().texOffs(0, 0).addBox(-5f, -7f, -5.5f, 10, 8, 1), PartPose.ZERO);
        head.addOrReplaceChild("lens_r", CubeListBuilder.create().texOffs(0, 10).addBox(-4f, -6.5f, -6.5f, 3, 3, 1), PartPose.ZERO);
        head.addOrReplaceChild("lens_l", CubeListBuilder.create().texOffs(0, 10).mirror().addBox(1f, -6.5f, -6.5f, 3, 3, 1), PartPose.ZERO);
        head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(24, 0).addBox(-2.5f, -3.5f, -7.5f, 5, 4, 2), PartPose.ZERO);
        head.addOrReplaceChild("filter", CubeListBuilder.create().texOffs(24, 8).addBox(-3f, -4f, -10.5f, 6, 5, 3), PartPose.ZERO);
        head.addOrReplaceChild("can_r", CubeListBuilder.create().texOffs(0, 16).addBox(-7.5f, -4.5f, -4f, 3, 4, 4), PartPose.ZERO);
        head.addOrReplaceChild("can_l", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(4.5f, -4.5f, -4f, 3, 4, 4), PartPose.ZERO);
        head.addOrReplaceChild("strap", CubeListBuilder.create().texOffs(0, 26).addBox(-4f, -5.5f, -4f, 8, 1, 8, new CubeDeformation(0.35f)), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    protected void pose(HumanoidModel<?> body, LivingEntity entity, KakuganeData data, float limbSwingAmount, float ageInTicks, float partialTicks) {
        root.getChild("head").copyFrom(body.head);
    }
}
