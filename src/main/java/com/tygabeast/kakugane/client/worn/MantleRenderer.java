package com.tygabeast.kakugane.client.worn;

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
 * Mantle: a high collar, a folded hood at the nape and a long cloak that lifts as you move. Follows the body.
 * The cloak swing is worked out from how fast you walk (no extra per-player state).
 */
public class MantleRenderer extends WornRenderer {
    public static final ModelLayerLocation LAYER = layer("mantle");

    public MantleRenderer() {
        super(LAYER, "mantle");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        body.addOrReplaceChild("collar", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5f, -1.5f, -2.5f, 9, 2, 5), PartPose.ZERO);
        body.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0, 28).addBox(-4f, -2f, 1.5f, 8, 3, 2), PartPose.ZERO);
        body.addOrReplaceChild("cloak", CubeListBuilder.create().texOffs(0, 8).addBox(-5f, 0f, 0f, 10, 18, 1), PartPose.offset(0, 0, 2.6f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    protected void pose(HumanoidModel<?> model, LivingEntity entity, KakuganeData data, float limbSwingAmount, float ageInTicks, float partialTicks) {
        ModelPart body = root.getChild("body");
        body.copyFrom(model.body);
        ModelPart cloak = body.getChild("cloak");
        float sway = Mth.sin(ageInTicks * 0.08f) * 0.03f;
        cloak.xRot = 0.08f + Math.min(1f, limbSwingAmount) * 0.55f + sway + (entity.isCrouching() ? 0.25f : 0f);
    }
}
