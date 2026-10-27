package com.tygabeast.kakugane.client;

import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client game-bus events: turns key presses into the toggle packet. */
@EventBusSubscriber(modid = Kakugane.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        while (ClientSetup.ACTIVATE.consumeClick()) {
            PacketDistributor.sendToServer(ModNetwork.ToggleForm.INSTANCE);
        }
        ChargeEffects.tick(mc);
    }

    private ClientEvents() {}
}
