package com.tygabeast.kakugane.network;

import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.core.ActiveForms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The only packet: "the activation key was pressed" (client -> server). It carries no data. */
public final class ModNetwork {

    public record ToggleForm() implements CustomPacketPayload {
        public static final ToggleForm INSTANCE = new ToggleForm();
        public static final Type<ToggleForm> TYPE = new Type<>(Kakugane.id("toggle_form"));
        public static final StreamCodec<ByteBuf, ToggleForm> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(ToggleForm.TYPE, ToggleForm.CODEC, ModNetwork::handleToggle);
    }

    private static void handleToggle(ToggleForm msg, IPayloadContext ctx) {
        // Packets arrive on the network thread; enqueueWork runs the change on the main server thread.
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p && p.isAlive()) ActiveForms.toggle(p);
        });
    }

    private ModNetwork() {}
}
