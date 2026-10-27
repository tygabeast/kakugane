package com.tygabeast.kakugane.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.entity.Ram;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** Ram, drawn 1.15x a goat, with glowing eyes (one extra draw). */
public class RamRenderer extends MobRenderer<Ram, RamModel> {
    private static final ResourceLocation TEXTURE = Kakugane.id("textures/entity/ram.png");
    private static final RenderType GLOW = RenderType.eyes(Kakugane.id("textures/entity/ram_glow.png"));

    public RamRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new RamModel(ctx.bakeLayer(RamModel.LAYER)), 0.8f);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }
        });
    }

    @Override
    protected void scale(Ram ram, PoseStack pose, float partialTick) {
        pose.scale(1.15f, 1.15f, 1.15f);
    }

    @Override
    public ResourceLocation getTextureLocation(Ram ram) {
        return TEXTURE;
    }
}
