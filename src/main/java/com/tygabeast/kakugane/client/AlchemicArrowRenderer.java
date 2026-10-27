package com.tygabeast.kakugane.client;

import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.entity.AlchemicArrow;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Uses the vanilla arrow shape with a pale, glowing-looking texture. */
public class AlchemicArrowRenderer extends ArrowRenderer<AlchemicArrow> {
    private static final ResourceLocation TEXTURE = Kakugane.id("textures/entity/alchemic_arrow.png");

    public AlchemicArrowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(AlchemicArrow arrow) {
        return TEXTURE;
    }
}
