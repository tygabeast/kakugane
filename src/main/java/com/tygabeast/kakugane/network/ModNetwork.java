package com.tygabeast.kakugane.network;

import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.ability.Greaves;
import com.tygabeast.kakugane.ability.WornAbilities;
import com.tygabeast.kakugane.core.ActiveForms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The mod's packets. All are tiny records; the client -> server ones carry no more than one byte.
 *
 *  client -> server: ToggleForm (activation key), FormAbility (form ability key), GreavesMove (air dash / stomp)
 *  server -> client: WingReserve (flight reserve for the HUD bar), BladeStrike (animate a Blade Belt arm)
 */
public final class ModNetwork {

    public record ToggleForm() implements CustomPacketPayload {
        public static final ToggleForm INSTANCE = new ToggleForm();
        public static final Type<ToggleForm> TYPE = new Type<>(Kakugane.id("toggle_form"));
        public static final StreamCodec<ByteBuf, ToggleForm> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record FormAbility() implements CustomPacketPayload {
        public static final FormAbility INSTANCE = new FormAbility();
        public static final Type<FormAbility> TYPE = new Type<>(Kakugane.id("form_ability"));
        public static final StreamCodec<ByteBuf, FormAbility> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** kind 0 = air dash, 1 = diving stomp. The client moves the player; the server handles the damage. */
    public record GreavesMove(byte kind) implements CustomPacketPayload {
        public static final Type<GreavesMove> TYPE = new Type<>(Kakugane.id("greaves_move"));
        public static final StreamCodec<ByteBuf, GreavesMove> CODEC = ByteBufCodecs.BYTE.map(GreavesMove::new, GreavesMove::kind);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** reserve < 0 hides the bar (the wings are gone). */
    public record WingReserve(float reserve, float max) implements CustomPacketPayload {
        public static final Type<WingReserve> TYPE = new Type<>(Kakugane.id("wing_reserve"));
        public static final StreamCodec<ByteBuf, WingReserve> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, WingReserve::reserve, ByteBufCodecs.FLOAT, WingReserve::max, WingReserve::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** A Blade Belt arm struck: which player, and toward which entity (so the client can swing the right arm). */
    public record BladeStrike(int player, int target) implements CustomPacketPayload {
        public static final Type<BladeStrike> TYPE = new Type<>(Kakugane.id("blade_strike"));
        public static final StreamCodec<ByteBuf, BladeStrike> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, BladeStrike::player, ByteBufCodecs.VAR_INT, BladeStrike::target, BladeStrike::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("2");
        r.playToServer(ToggleForm.TYPE, ToggleForm.CODEC, (msg, ctx) -> onServer(ctx, ActiveForms::toggle));
        r.playToServer(FormAbility.TYPE, FormAbility.CODEC, (msg, ctx) -> onServer(ctx, WornAbilities::ability));
        r.playToServer(GreavesMove.TYPE, GreavesMove.CODEC, (msg, ctx) -> onServer(ctx, p -> Greaves.onMove(p, msg.kind())));
        // Client handlers live in client code; the server side only needs to know these packets exist.
        r.playToClient(WingReserve.TYPE, WingReserve.CODEC, (msg, ctx) -> {
            if (FMLEnvironment.dist.isClient()) ctx.enqueueWork(() -> com.tygabeast.kakugane.client.ClientPackets.wingReserve(msg));
        });
        r.playToClient(BladeStrike.TYPE, BladeStrike.CODEC, (msg, ctx) -> {
            if (FMLEnvironment.dist.isClient()) ctx.enqueueWork(() -> com.tygabeast.kakugane.client.ClientPackets.bladeStrike(msg));
        });
    }

    /** Packets arrive on the network thread; enqueueWork runs the change on the main server thread. */
    private static void onServer(IPayloadContext ctx, java.util.function.Consumer<ServerPlayer> action) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p && p.isAlive()) action.accept(p);
        });
    }

    private ModNetwork() {}
}
