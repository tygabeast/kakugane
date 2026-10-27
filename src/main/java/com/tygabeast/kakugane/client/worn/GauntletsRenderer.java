package com.tygabeast.kakugane.client.worn;

import com.tygabeast.kakugane.data.KakuganeData;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;

/** Gauntlets: plated cuffs on the forearms, armoured backs of the hands and knuckle guards. Follows both arms. */
public class GauntletsRenderer extends WornRenderer {
    public static final ModelLayerLocation LAYER = layer("gauntlets");

    public GauntletsRenderer() {
        super(LAYER, "gauntlets");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot().addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO);
        r.addOrReplaceChild("cuff", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5f, 3f, -2.5f, 5, 5, 5), PartPose.ZERO);
        r.addOrReplaceChild("hand", CubeListBuilder.create().texOffs(0, 10).addBox(-3.5f, 8f, -2.5f, 5, 3, 5), PartPose.ZERO);
        r.addOrReplaceChild("knuckles", CubeListBuilder.create().texOffs(0, 18).addBox(-3.5f, 8.5f, -3.25f, 5, 2, 1), PartPose.ZERO);
        PartDefinition l = mesh.getRoot().addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO);
        l.addOrReplaceChild("cuff", CubeListBuilder.create().texOffs(0, 0).mirror().addBox(-1.5f, 3f, -2.5f, 5, 5, 5), PartPose.ZERO);
        l.addOrReplaceChild("hand", CubeListBuilder.create().texOffs(0, 10).mirror().addBox(-1.5f, 8f, -2.5f, 5, 3, 5), PartPose.ZERO);
        l.addOrReplaceChild("knuckles", CubeListBuilder.create().texOffs(0, 18).mirror().addBox(-1.5f, 8.5f, -3.25f, 5, 2, 1), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    protected void pose(HumanoidModel<?> body, LivingEntity entity, KakuganeData data, float limbSwingAmount, float ageInTicks, float partialTicks) {
        root.getChild("right_arm").copyFrom(body.rightArm);
        root.getChild("left_arm").copyFrom(body.leftArm);
    }
}
