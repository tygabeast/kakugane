package com.tygabeast.kakugane.test;

import com.tygabeast.kakugane.core.ActiveForms;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dev-only worn-form screenshots (-Pshots=worn): each new worn form on the player, front and back, plus Alice's wings
 * in flight and a Blade Belt arm striking a target. Never part of the released mod.
 */
final class WornShots {
    static final List<ShotsHarness.Step> STEPS = new ArrayList<>();
    private static final String[] FORMS = {"aerial_operator", "alice", "gauntlets", "greaves", "mantle", "blade_belt"};

    private static void at(int t, Consumer<Minecraft> a) {
        STEPS.add(new ShotsHarness.Step(t, a));
    }

    static {
        int t = 120;
        at(t, mc -> ShotsHarness.server(mc, (s, p) -> {
            p.setGameMode(GameType.CREATIVE);
            for (String c : new String[]{"time set 6000", "weather clear", "gamerule doDaylightCycle false",
                    "gamerule doMobSpawning false", "fill -8 199 -8 8 199 8 minecraft:smooth_stone",
                    "fill -8 200 -8 8 206 8 minecraft:air"}) cmd(s, c);
            p.teleportTo(s.overworld(), 0.5, 200, 0.5, 0f, 5f);
        }));
        at(t + 10, mc -> {
            mc.options.bobView().set(false);
            ShotsHarness.thirdPerson(mc);
            mc.options.fov().set(55); // wider than the held-form shots, so legs, wings and blades fit
        });
        t += 30;
        for (String form : FORMS) {
            final String f = form;
            at(t, mc -> ShotsHarness.server(mc, (s, p) -> {
                String name = p.getGameProfile().getName();
                cmd(s, "clear " + name);
                cmd(s, "kill @e[type=!minecraft:player]");
                cmd(s, "kakugane give " + name + " " + f);
                p.getInventory().selected = 0;
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
                p.teleportTo(s.overworld(), 0.5, 200, 0.5, 0f, 5f);
                boolean ok = ActiveForms.activateStack(p, p.getInventory().getItem(0));
                StringBuilder curios = new StringBuilder(); // logged so a failed equip shows up in the run log
                top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).ifPresent(inv -> inv.getCurios().forEach((k, h) -> {
                    for (int i = 0; i < h.getStacks().getSlots(); i++)
                        if (!h.getStacks().getStackInSlot(i).isEmpty()) curios.append(k).append('=').append(h.getStacks().getStackInSlot(i)).append(' ');
                }));
                com.tygabeast.kakugane.Kakugane.LOGGER.info("[worn] {} activated={} held={} legs={} curios={} slots={}", f, ok,
                        p.getInventory().getItem(0), p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS), curios,
                        top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).map(inv -> inv.getCurios().keySet().toString()).orElse("none"));
            }));
            at(t + 2, mc -> mc.player.getInventory().selected = 0);
            at(t + 30, mc -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                ShotsHarness.bodyOffset = 40f;
            });
            at(t + 45, mc -> ShotsHarness.shot(mc, "worn_" + f + "_front"));
            at(t + 50, mc -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                ShotsHarness.bodyOffset = -30f;
            });
            at(t + 65, mc -> ShotsHarness.shot(mc, "worn_" + f + "_back"));
            t += 70;
            if (f.equals("alice")) {
                // Up in the air: the wings spread and beat while airborne.
                at(t, mc -> ShotsHarness.server(mc, (s, p) -> {
                    p.getAbilities().flying = true;
                    p.onUpdateAbilities();
                    p.teleportTo(s.overworld(), 0.5, 203, 0.5, 0f, 5f);
                }));
                at(t + 5, mc -> ShotsHarness.bodyOffset = 0f); // square on, to see both wings
                at(t + 25, mc -> ShotsHarness.shot(mc, "worn_alice_flying_back"));
                at(t + 30, mc -> {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    ShotsHarness.bodyOffset = 40f;
                });
                at(t + 45, mc -> ShotsHarness.shot(mc, "worn_alice_flying_front"));
                t += 50;
            }
            if (f.equals("blade_belt")) {
                // A target in front; swing at it and catch an arm mid-strike.
                at(t, mc -> ShotsHarness.server(mc, (s, p) -> {
                    Mob z = EntityType.ZOMBIE.create(s.overworld());
                    z.setNoAi(true);
                    z.moveTo(0.5, 200, 2.6, 180f, 0f);
                    z.setYHeadRot(180f);
                    s.overworld().addFreshEntity(z);
                }));
                at(t + 10, mc -> {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    ShotsHarness.bodyOffset = 0f;
                });
                at(t + 20, mc -> {
                    var target = mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class,
                            mc.player.getBoundingBox().inflate(5)).stream().findFirst().orElse(null);
                    if (target != null) {
                        mc.gameMode.attack(mc.player, target);
                        mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    }
                });
                at(t + 24, mc -> ShotsHarness.shot(mc, "worn_blade_belt_strike"));
                at(t + 26, mc -> ShotsHarness.shot(mc, "worn_blade_belt_strike2"));
                t += 30;
            }
        }
        at(t + 10, Minecraft::stop);
    }

    private static void cmd(MinecraftServer s, String c) {
        s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), c);
    }

    private WornShots() {}
}
