package com.tygabeast.kakugane.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.entity.Crawler;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.resources.ResourceLocation;

/** Crawler: the vanilla spider model (and its glowing eyes) with our own texture, 1.15x size. */
public class CrawlerRenderer extends SpiderRenderer<Crawler> {
    private static final ResourceLocation TEXTURE = Kakugane.id("textures/entity/crawler.png");

    public CrawlerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.0f;
    }

    @Override
    protected void scale(Crawler crawler, PoseStack pose, float partialTick) {
        pose.scale(1.15f, 1.15f, 1.15f);
    }

    @Override
    public ResourceLocation getTextureLocation(Crawler crawler) {
        return TEXTURE;
    }
}
