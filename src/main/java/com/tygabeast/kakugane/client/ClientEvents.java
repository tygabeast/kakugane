package com.tygabeast.kakugane.client;

import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.data.Archetype;
import com.tygabeast.kakugane.item.FormCurioItem;
import com.tygabeast.kakugane.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;

/** Client game-bus events: keys to packets, Greaves controls, zoom, the wing bar and small effects. */
@EventBusSubscriber(modid = Kakugane.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static final DustParticleOptions WING_FLAKE = new DustParticleOptions(new org.joml.Vector3f(0.86f, 0.9f, 0.95f), 0.6f);

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            WingHud.reset();
            return;
        }
        while (ClientSetup.ACTIVATE.consumeClick()) PacketDistributor.sendToServer(ModNetwork.ToggleForm.INSTANCE);
        while (ClientSetup.FORM_ABILITY.consumeClick()) PacketDistributor.sendToServer(ModNetwork.FormAbility.INSTANCE);
        GreavesControls.tick(mc);
        ChargeEffects.tick(mc);
        if (mc.level != null && (mc.level.getGameTime() % 20) == 0) BladeArms.cleanUp(mc.level);
        // A few flakes fall from your own wings while you fly (every 3 ticks, only while flying).
        if (mc.player.getAbilities().flying && !mc.player.isCreative() && (mc.player.tickCount % 3) == 0 && wears(mc, Archetype.ALICE)) {
            Vec3 back = Vec3.directionFromRotation(0, mc.player.yBodyRot).scale(-0.35);
            mc.level.addParticle(WING_FLAKE, mc.player.getX() + back.x + (mc.player.getRandom().nextDouble() - 0.5) * 1.2,
                    mc.player.getY() + 1.2, mc.player.getZ() + back.z + (mc.player.getRandom().nextDouble() - 0.5) * 1.2, 0, -0.02, 0);
        }
    }

    /** Aerial Operator zoom: hold the zoom key. The slot is only checked while the key is held. */
    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        if (!ClientSetup.ZOOM.isDown()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !wears(mc, Archetype.AERIAL_OPERATOR)) return;
        event.setNewFovModifier(event.getNewFovModifier() * 0.25f);
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        WingHud.render(event.getGuiGraphics());
    }

    /** True if the local player wears this Curios form. */
    private static boolean wears(Minecraft mc, Archetype form) {
        return CuriosApi.getCuriosInventory(mc.player).flatMap(inv -> inv.findFirstCurio(
                (ItemStack s) -> s.getItem() instanceof FormCurioItem f && f.form() == form)).isPresent();
    }

    private ClientEvents() {}
}
