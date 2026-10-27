package com.tygabeast.kakugane.client.worn;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.data.KakuganeData;
import com.tygabeast.kakugane.item.FormCurioItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Shared drawing for the worn forms that sit in Curios slots. Each form has one model and two textures: the metal,
 * and a grey accent layer tinted with the Kakugane's palette (the same idea as the held forms' item textures).
 *
 * Per frame and per wearer: two draw calls (metal and accent), no allocations. The model is baked once, the first
 * time it is drawn. Nothing is drawn for invisible wearers, so the Mantle's stealth hides the gear too.
 */
public abstract class WornRenderer implements ICurioRenderer {
    private final ModelLayerLocation layer;
    private final ResourceLocation texture, accent;
    protected ModelPart root;

    protected WornRenderer(ModelLayerLocation layer, String name) {
        this.layer = layer;
        this.texture = Kakugane.id("textures/entity/worn/" + name + ".png");
        this.accent = Kakugane.id("textures/entity/worn/" + name + "_accent.png");
    }

    public static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(Kakugane.id(name), "main");
    }

    /** Copy the wearer's pose onto this model and animate it. */
    protected abstract void pose(HumanoidModel<?> body, LivingEntity entity, KakuganeData data, float limbSwingAmount, float ageInTicks, float partialTicks);

    /** Render type for the model. Cut-out by default; the wings override this to glow. */
    protected RenderType renderType(ResourceLocation tex) {
        return RenderType.entityCutoutNoCull(tex);
    }

    protected int light(int packedLight) {
        return packedLight;
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext ctx, PoseStack pose,
            RenderLayerParent<T, M> parent, MultiBufferSource buffers, int packedLight, float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        LivingEntity entity = ctx.entity();
        if (entity.isInvisible() || !(parent.getModel() instanceof HumanoidModel<?> body)) return;
        if (root == null) root = Minecraft.getInstance().getEntityModels().bakeLayer(layer);
        KakuganeData data = FormCurioItem.data(stack);
        pose(body, entity, data, limbSwingAmount, ageInTicks, partialTicks);
        int light = light(packedLight);
        root.render(pose, buffers.getBuffer(renderType(texture)), light, OverlayTexture.NO_OVERLAY);
        root.render(pose, buffers.getBuffer(renderType(accent)), light, OverlayTexture.NO_OVERLAY, 0xFF000000 | data.palette().rgb());
    }
}
