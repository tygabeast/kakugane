package com.tygabeast.kakugane.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.entity.Hound;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** Draws the Hound with glowing eyes and chest mark (an emissive layer: one extra draw per Hound). */
public class HoundRenderer extends MobRenderer<Hound, HoundModel> {
    private static final ResourceLocation TEXTURE = Kakugane.id("textures/entity/hound.png");
    private static final RenderType GLOW = RenderType.eyes(Kakugane.id("textures/entity/hound_glow.png"));

    public HoundRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HoundModel(ctx.bakeLayer(HoundModel.LAYER)), 0.75f);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }
        });
    }

    @Override
    protected void scale(Hound hound, PoseStack pose, float partialTick) {
        pose.scale(1.25f, 1.25f, 1.25f);
    }

    @Override
    public ResourceLocation getTextureLocation(Hound hound) {
        return TEXTURE;
    }
}
