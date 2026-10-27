package com.tygabeast.kakugane.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.entity.LabLeader;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** Lab Leader: a person in a lab coat until it transforms, then a hybrid with glowing eyes. */
public class LeaderRenderer extends HumanoidMobRenderer<LabLeader, LeaderModel> {
    private static final ResourceLocation HUMAN = Kakugane.id("textures/entity/lab_leader.png");
    private static final ResourceLocation HYBRID = Kakugane.id("textures/entity/lab_leader_hybrid.png");
    private static final RenderType GLOW = RenderType.eyes(Kakugane.id("textures/entity/lab_leader_glow.png"));

    public LeaderRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new LeaderModel(ctx.bakeLayer(LeaderModel.LAYER)), 0.5f);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }

            @Override
            public void render(PoseStack pose, MultiBufferSource buffers, int light, LabLeader e, float limbSwing, float limbSwingAmount,
                               float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                if (e.transformed()) super.render(pose, buffers, light, e, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
            }
        });
    }

    @Override
    protected void scale(LabLeader e, PoseStack pose, float partialTick) {
        if (e.transformed()) pose.scale(1.12f, 1.12f, 1.12f);
    }

    @Override
    public ResourceLocation getTextureLocation(LabLeader e) {
        return e.transformed() ? HYBRID : HUMAN;
    }
}
