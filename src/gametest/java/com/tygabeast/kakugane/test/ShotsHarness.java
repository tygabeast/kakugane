package com.tygabeast.kakugane.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.core.ActiveForms;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dev-only screenshot run (gradle runShotsClient with -Dkakugane.shots=true). Joins a world, poses the player with
 * each form and saves screenshots to run/screenshots, then quits. Never part of the released mod.
 */
@EventBusSubscriber(modid = Kakugane.MODID, value = Dist.CLIENT)
public final class ShotsHarness {
    private static final String MODE = System.getProperty("kakugane.shots", "");
    private static final boolean ON = !MODE.isEmpty();
    private static final float YAW = 0f;
    private static int tick = -1;
    static Float bodyOffset;
    private static final List<Step> STEPS = new ArrayList<>();

    record Step(int at, Consumer<Minecraft> action) {}

    private static void at(int t, Consumer<Minecraft> a) {
        STEPS.add(new Step(t, a));
    }

    static {
        int t = 120; // let chunks load
        at(t, mc -> server(mc, (s, p) -> {
            p.setGameMode(GameType.CREATIVE);
            for (String c : new String[]{"time set 6000", "weather clear", "gamerule doDaylightCycle false",
                    "gamerule doMobSpawning false", "fill -6 199 -6 6 199 6 minecraft:smooth_stone",
                    "fill -6 200 -6 6 204 6 minecraft:air", "clear " + p.getGameProfile().getName(),
                    "kakugane give " + p.getGameProfile().getName() + " lance",
                    "kakugane give " + p.getGameProfile().getName() + " bow"}) {
                s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), c);
            }
            p.teleportTo(s.overworld(), 0.5, 200, 0.5, YAW, 5f);
        }));
        at(t + 10, mc -> {
            mc.options.bobView().set(false);
            mc.player.getInventory().selected = 0;
        });
        at(t + 20, mc -> server(mc, (s, p) -> ActiveForms.activateStack(p, p.getInventory().getItem(0))));
        // Lance: idle and charging, third person (body turned so the camera sees it three-quarters on) and first person.
        at(t + 40, mc -> thirdPerson(mc));
        at(t + 70, mc -> shot(mc, "lance_idle_3rd"));
        at(t + 75, mc -> holdUse(mc, true));
        at(t + 92, mc -> shot(mc, "lance_charge_half_3rd")); // part-way: dust only
        at(t + 115, mc -> shot(mc, "lance_charge_3rd")); // full: dust plus the tip spark
        at(t + 120, mc -> firstPerson(mc));
        at(t + 130, mc -> shot(mc, "lance_charge_1st"));
        at(t + 135, mc -> mc.player.getInventory().selected = 4); // stop using without releasing (no dash)
        at(t + 137, mc -> holdUse(mc, false));
        at(t + 140, mc -> mc.player.getInventory().selected = 0);
        at(t + 160, mc -> shot(mc, "lance_idle_1st"));
        // Bow: idle and drawing.
        at(t + 170, mc -> mc.player.getInventory().selected = 1);
        at(t + 180, mc -> server(mc, (s, p) -> ActiveForms.activateStack(p, p.getInventory().getItem(1))));
        at(t + 200, mc -> shot(mc, "bow_idle_1st"));
        at(t + 205, mc -> holdUse(mc, true));
        at(t + 235, mc -> shot(mc, "bow_draw_1st"));
        at(t + 160, mc -> firstPerson(mc));
        at(t + 240, mc -> thirdPerson(mc));
        at(t + 260, mc -> shot(mc, "bow_draw_3rd"));
        at(t + 265, mc -> mc.player.getInventory().selected = 4);
        at(t + 267, mc -> holdUse(mc, false));
        at(t + 270, mc -> mc.player.getInventory().selected = 1);
        at(t + 300, mc -> shot(mc, "bow_idle_3rd"));
        // Vanilla bow in the same scene, for comparison.
        at(t + 310, mc -> server(mc, (s, p) -> s.getCommands().performPrefixedCommand(s.createCommandSourceStack(),
                "item replace entity " + p.getGameProfile().getName() + " hotbar.2 with minecraft:bow")));
        at(t + 315, mc -> server(mc, (s, p) -> s.getCommands().performPrefixedCommand(s.createCommandSourceStack(),
                "give " + p.getGameProfile().getName() + " minecraft:arrow 4")));
        at(t + 320, mc -> mc.player.getInventory().selected = 2);
        at(t + 340, mc -> shot(mc, "vanilla_bow_idle_3rd"));
        at(t + 345, mc -> holdUse(mc, true));
        at(t + 375, mc -> shot(mc, "vanilla_bow_draw_3rd"));
        at(t + 380, mc -> firstPerson(mc));
        at(t + 390, mc -> shot(mc, "vanilla_bow_draw_1st"));
        at(t + 395, mc -> mc.player.getInventory().selected = 4);
        at(t + 397, mc -> holdUse(mc, false));
        at(t + 400, mc -> mc.player.getInventory().selected = 2);
        at(t + 420, mc -> shot(mc, "vanilla_bow_idle_1st"));
        at(t + 440, Minecraft::stop);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ON) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        tick++;
        if (tick > 125 && !MODE.equals("labs")) {
            mc.player.setYRot(YAW);
            mc.player.yRotO = YAW;
            mc.player.setXRot(5f);
            mc.player.xRotO = 5f;
            mc.player.yHeadRot = mc.player.yHeadRotO = YAW;
            if (bodyOffset != null) mc.player.yBodyRot = mc.player.yBodyRotO = YAW + bodyOffset;
        }
        if (MODE.equals("labs")) {
            // The lab scenario teleports far away, so its clock pauses until the world around the player is drawn.
            if (settling(mc)) {
                tick--;
                return;
            }
            for (Step s : LabShots.STEPS) if (s.at == tick) s.action.accept(mc);
            return;
        }
        if (MODE.equals("worn")) {
            for (Step s : WornShots.STEPS) if (s.at == tick) s.action.accept(mc);
            return;
        }
        for (Step s : STEPS) if (s.at == tick) s.action.accept(mc);
    }

    /** Third person from the front with the body turned 40 degrees, zoomed in (FOV 30) and without the HUD. */
    static void thirdPerson(Minecraft mc) {
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        mc.options.fov().set(30);
        mc.options.hideGui = true;
        bodyOffset = 40f;
    }

    /** First person; the HUD stays on because hiding it also hides the hand. */
    private static void firstPerson(Minecraft mc) {
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.fov().set(70);
        mc.options.hideGui = false;
        bodyOffset = null;
    }

    private static void holdUse(Minecraft mc, boolean down) {
        InputConstants.Key key = mc.options.keyUse.getKey();
        if (down) KeyMapping.click(key);
        KeyMapping.set(key, down);
    }

    private static int settleTicks = -1, steadyTicks;
    /** Server actions queued but not yet run; settling waits for these (finding a lab can take seconds). */
    private static final java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger();

    /** Pause the step clock until the chunks around the player are loaded and drawn (at most a minute). */
    static void settle() {
        settleTicks = 0;
        steadyTicks = 0;
    }

    private static boolean settling(Minecraft mc) {
        if (settleTicks < 0) return false;
        settleTicks++;
        net.minecraft.core.BlockPos pos = mc.player.blockPosition();
        boolean ready = pending.get() == 0 && mc.levelRenderer.hasRenderedAllSections() && mc.levelRenderer.isSectionCompiled(pos);
        for (int dx = -2; dx <= 2 && ready; dx++)
            for (int dz = -2; dz <= 2 && ready; dz++)
                ready = mc.level.hasChunk((pos.getX() >> 4) + dx, (pos.getZ() >> 4) + dz);
        steadyTicks = ready ? steadyTicks + 1 : 0;
        // At least 3 seconds (for the server to move us), then 2 steady seconds; give up after a minute.
        if ((settleTicks > 60 && steadyTicks > 40) || settleTicks > 1200) {
            Kakugane.LOGGER.info("[shots] settled after {} ticks (ready={})", settleTicks, ready);
            settleTicks = -1;
            return false;
        }
        return true;
    }

    static void shot(Minecraft mc, String name) {
        Kakugane.LOGGER.info("[shots] {}: at {} flying={} using={} useTicks={}", name, mc.player.position(), mc.player.getAbilities().flying,
                mc.player.isUsingItem(), mc.player.getTicksUsingItem());
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> Kakugane.LOGGER.info("[shots] {}", msg.getString()));
    }

    static void server(Minecraft mc, java.util.function.BiConsumer<MinecraftServer, ServerPlayer> action) {
        MinecraftServer s = mc.getSingleplayerServer();
        java.util.UUID id = mc.player.getUUID();
        pending.incrementAndGet();
        s.execute(() -> {
            try {
                ServerPlayer p = s.getPlayerList().getPlayer(id);
                if (p != null) action.accept(s, p);
            } finally {
                pending.decrementAndGet();
            }
        });
    }

    private ShotsHarness() {}
}
