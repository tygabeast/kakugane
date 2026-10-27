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
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Alice in Wonderland: two wings made of small floating metal flakes, drawn full-bright so they shimmer.
 * Each wing is one model part holding about forty one-pixel cubes in three feather rows (built once, at bake
 * time); only the wings' angles change per frame. They half-fold on the ground and beat in the air.
 */
public class WingsRenderer extends WornRenderer {
    public static final ModelLayerLocation LAYER = layer("alice_wings");

    public WingsRenderer() {
        super(LAYER, "alice_wings");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        body.addOrReplaceChild("left_wing", wing(false), PartPose.offset(1.5f, 2.5f, 2.5f));
        body.addOrReplaceChild("right_wing", wing(true), PartPose.offset(-1.5f, 2.5f, 2.5f));
        return LayerDefinition.create(mesh, 16, 16);
    }

    /** Three rows of flakes: a bright leading edge (accent) and two trailing rows, swept back and drooping at the tip. */
    private static CubeListBuilder wing(boolean right) {
        CubeListBuilder b = CubeListBuilder.create();
        int[] lengths = {15, 12, 9};
        for (int row = 0; row < 3; row++) {
            int len = lengths[row];
            for (int i = 0; i < len; i += (row == 0 ? 1 : 1)) {
                float t = i / 14f;
                float x = i + 0.5f;
                float y = row * 2.4f - 4.5f * Mth.sin(Mth.PI * Math.min(1f, t * 1.15f)) + t * t * 5f;
                float z = i * 0.35f + row * 0.9f + ((i * 7 + row * 3) % 3) * 0.25f;
                if (right) x = -x - 1;
                b.texOffs(row == 0 ? 0 : 4, row == 0 ? 0 : 2 * row).addBox(x, y, z, 1, 1, 1);
            }
        }
        return b;
    }

    @Override
    protected void pose(HumanoidModel<?> model, LivingEntity entity, KakuganeData data, float limbSwingAmount, float ageInTicks, float partialTicks) {
        ModelPart body = root.getChild("body");
        body.copyFrom(model.body);
        boolean air = !entity.onGround() && !entity.isInWater();
        float beat = air ? Mth.sin(ageInTicks * 0.55f) * 0.45f : Mth.sin(ageInTicks * 0.1f) * 0.06f;
        float fold = air ? 0.25f : 0.75f;
        ModelPart l = body.getChild("left_wing"), r = body.getChild("right_wing");
        l.yRot = -fold;
        r.yRot = fold;
        l.zRot = -beat;
        r.zRot = beat;
    }

    @Override
    protected RenderType renderType(ResourceLocation tex) {
        return RenderType.entityTranslucentEmissive(tex);
    }

    @Override
    protected int light(int packedLight) {
        return LightTexture.FULL_BRIGHT;
    }
}
