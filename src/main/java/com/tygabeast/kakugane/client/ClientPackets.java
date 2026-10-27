package com.tygabeast.kakugane.client;

import com.tygabeast.kakugane.network.ModNetwork;
import net.minecraft.client.Minecraft;

/** Client-side handling of the server's packets. Only ever loaded on the client (see network/ModNetwork). */
public final class ClientPackets {

    public static void wingReserve(ModNetwork.WingReserve msg) {
        WingHud.update(msg.reserve(), msg.max());
    }

    public static void bladeStrike(ModNetwork.BladeStrike msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BladeArms.strike(mc.level, msg.player(), msg.target());
    }

    private ClientPackets() {}
}
