package com.tygabeast.kakugane.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.data.KakuganeData;
import com.tygabeast.kakugane.item.KakuganeItem;
import com.tygabeast.kakugane.registry.ModRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Client-only setup on the mod bus: renderers, model switching, colour tints and the activation key. */
@EventBusSubscriber(modid = Kakugane.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    public static final KeyMapping ACTIVATE = new KeyMapping("key.kakugane.activate",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.kakugane");

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            KakuganeItem item = ModRegistry.KAKUGANE.get();
            // "form": 0 = dormant plate, otherwise the active archetype's model index. The item model JSON
            // picks a picture from this number, so changing form costs nothing beyond a model lookup.
            ItemProperties.register(item, Kakugane.id("form"), (stack, level, entity, seed) -> {
                KakuganeData d = KakuganeItem.data(stack);
                return d.active() ? d.form().map(f -> f.modelIndex()).orElse(0) : 0;
            });
            // Bow drawing animation, same maths as the vanilla bow.
            ItemProperties.register(item, Kakugane.id("pulling"), (stack, level, entity, seed) ->
                    entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1f : 0f);
            ItemProperties.register(item, Kakugane.id("pull"), (stack, level, entity, seed) -> {
                if (entity == null || entity.getUseItem() != stack) return 0f;
                return (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20f;
            });
        });
    }

    /** Layer 1 of each texture is a grey accent, tinted here with the Kakugane's palette. */
    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 1 ? 0xFF000000 | KakuganeItem.data(stack).palette().rgb() : -1,
                ModRegistry.KAKUGANE.get());
        event.register((stack, tint) -> tint == 1 ? 0xFF000000 | DyedItemColor.getOrDefault(stack, 0x9AA7B4) : -1,
                ModRegistry.SILVER_SKIN_HELMET.get(), ModRegistry.SILVER_SKIN_CHESTPLATE.get(),
                ModRegistry.SILVER_SKIN_LEGGINGS.get(), ModRegistry.SILVER_SKIN_BOOTS.get());
    }

    /** Tints the Silver Skin accent layer on the body with the piece's palette colour. */
    @SubscribeEvent
    public static void onClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public int getDefaultDyeColor(ItemStack stack) {
                return 0xFF000000 | DyedItemColor.getOrDefault(stack, 0x9AA7B4);
            }
        }, ModRegistry.SILVER_SKIN_HELMET.get(), ModRegistry.SILVER_SKIN_CHESTPLATE.get(),
                ModRegistry.SILVER_SKIN_LEGGINGS.get(), ModRegistry.SILVER_SKIN_BOOTS.get());
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistry.HOUND.get(), HoundRenderer::new);
        event.registerEntityRenderer(ModRegistry.ALCHEMIC_ARROW.get(), AlchemicArrowRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HoundModel.LAYER, HoundModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(ACTIVATE);
    }

    private ClientSetup() {}
}
