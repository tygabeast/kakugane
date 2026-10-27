package com.tygabeast.kakugane.test;

import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.entity.LabLeader;
import com.tygabeast.kakugane.registry.ModRegistry;
import com.tygabeast.kakugane.world.LabPiece;
import com.tygabeast.kakugane.world.LabTracker;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dev-only lab check for the screenshot client (-Dkakugane.shots=labs): places a lab, photographs its rooms and the
 * new Homunculi, then drives growth and clearing through the real code paths and logs the results.
 */
final class LabShots {
    static final List<ShotsHarness.Step> STEPS = new ArrayList<>();
    static StructureStart lab;
    static Vec3 camera;

    private static void at(int t, Consumer<Minecraft> a) {
        STEPS.add(new ShotsHarness.Step(t, a));
    }

    static {
        build();
    }

    // A method rather than the static block itself, so the quick mode can return early.
    private static void build() {
        int t = 120;
        at(t, mc -> ShotsHarness.server(mc, (s, p) -> {
            p.setGameMode(GameType.CREATIVE);
            cmd(s, "time set 6000");
            cmd(s, "weather clear");
            cmd(s, "gamerule doDaylightCycle false");
            cmd(s, "gamerule doMobSpawning false");
            cmd(s, "effect give " + p.getGameProfile().getName() + " minecraft:night_vision infinite 0 true");
            cmd(s, "locate structure kakugane:lab");
            ServerLevel level = s.overworld();
            var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(LabTracker.LAB);
            // The shots world is a normal (not superflat) world, so find the nearest naturally generated lab.
            var holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(LabTracker.LAB);
            var found = level.getChunkSource().getGenerator().findNearestMapStructure(level,
                    net.minecraft.core.HolderSet.direct(holder), p.blockPosition(), 100, false);
            if (found != null) {
                BlockPos at = found.getFirst();
                lab = level.getChunk(at.getX() >> 4, at.getZ() >> 4).getStartForStructure(structure);
            }
            if (lab == null || !lab.isValid()) {
                Kakugane.LOGGER.error("[labs] place failed");
                return;
            }
            Kakugane.LOGGER.info("[labs] lab at {} with {} pieces", lab.getBoundingBox(), lab.getPieces().size());
            for (StructurePiece piece : lab.getPieces()) {
                if (piece instanceof LabPiece lp) Kakugane.LOGGER.info("[labs]   {} {} {}", lp.kind(), lp.room(), lp.getBoundingBox());
            }
            // Hover (creative flight, so we don't fall) a little above the ground, south-east of the ruin.
            BlockPos ruin = shaft().getBoundingBox().getCenter();
            int cx = ruin.getX() + 6, cz = ruin.getZ() + 6;
            int y = Math.max(level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, cx, cz),
                    shaft().getBoundingBox().maxY() - 2) + 6;
            fly(p);
            p.teleportTo(level, cx + 0.5, y, cz + 0.5, 135f, 45f);
            camera = new Vec3(cx + 0.5, y, cz + 0.5);
        }));
        if (Boolean.getBoolean("kakugane.quick")) { at(t + 20, Minecraft::stop); return; }
        at(t + 1, mc -> ShotsHarness.settle());
        // Flight set while the far chunks were still loading doesn't always stick, so set it again once there.
        at(t + 10, mc -> ShotsHarness.server(mc, (s, p) -> {
            if (camera == null) return;
            fly(p);
            p.teleportTo(s.overworld(), camera.x, camera.y, camera.z, 135f, 45f);
        }));
        at(t + 11, mc -> ShotsHarness.settle());
        at(t + 60, mc -> view(mc, false));
        at(t + 100, mc -> ShotsHarness.shot(mc, "lab_surface_ruin"));
        // Room tour.
        String[] rooms = {"HUB", "VAT_HALL", "CELLS", "STORAGE", "BARRACKS", "LEADER"};
        int tt = t + 110;
        for (String r : rooms) {
            final String room = r;
            at(tt, mc -> ShotsHarness.server(mc, (s, p) -> {
                LabPiece lp = room(LabPiece.Room.valueOf(room));
                if (lp == null) return;
                var b = lp.getBoundingBox();
                p.teleportTo(s.overworld(), b.minX() + 1.5, b.minY() + 1, b.minZ() + 1.5, -45f, 12f);
            }));
            at(tt + 1, mc -> ShotsHarness.settle());
            at(tt + 50, mc -> ShotsHarness.shot(mc, "lab_room_" + room.toLowerCase()));
            tt += 60;
        }
        // The leader, transformed, from the chamber door side.
        at(tt, mc -> ShotsHarness.server(mc, (s, p) -> {
            LabPiece lp = room(LabPiece.Room.LEADER);
            var b = lp.getBoundingBox();
            // Clear the guards off the dais so the leader is in full view.
            for (Mob m : s.overworld().getEntitiesOfClass(Mob.class, net.minecraft.world.phys.AABB.of(b), m -> !(m instanceof LabLeader))) m.discard();
            for (LabLeader l : s.overworld().getEntitiesOfClass(LabLeader.class, net.minecraft.world.phys.AABB.of(b))) {
                l.setNoAi(true);
                BlockPos d = b.getCenter();
                l.moveTo(d.getX() + 0.5, b.minY() + 2, d.getZ() + 0.5, 0f, 0f);
                l.setYRot(0f);
                l.yBodyRot = 0f;
                l.yHeadRot = 0f;
                l.hurt(p.damageSources().playerAttack(p), 1f);
                Kakugane.LOGGER.info("[labs] leader motif {} transformed {}", l.motif(), l.transformed());
            }
            BlockPos c = b.getCenter();
            fly(p);
            p.teleportTo(s.overworld(), c.getX() + 0.5, b.minY() + 2, c.getZ() + 5.5, 180f, 4f);
        }));
        at(tt + 1, mc -> ShotsHarness.settle());
        at(tt + 45, mc -> mc.options.fov().set(50));
        at(tt + 50, mc -> ShotsHarness.shot(mc, "lab_leader_hybrid"));
        at(tt + 55, mc -> mc.options.fov().set(80));
        tt += 60;
        // Line-up of every Homunculus on the surface, in daylight.
        at(tt, mc -> ShotsHarness.server(mc, (s, p) -> {
            ServerLevel level = s.overworld();
            // A platform high above the lab, so its chunks are already loaded and it's in daylight.
            BlockPos c = lab.getBoundingBox().getCenter();
            int bx = c.getX() - 10, bz = c.getZ() - 4;
            cmd(s, "fill " + bx + " 199 " + bz + " " + (bx + 20) + " 199 " + (bz + 8) + " minecraft:smooth_stone");
            double x = bx + 2;
            for (var type : List.of(ModRegistry.HOUND.get(), ModRegistry.RAM.get(), ModRegistry.CRAWLER.get())) {
                Mob m = type.create(level);
                m.setNoAi(true);
                m.moveTo(x, 200, bz + 4.5, 0f, 0f);
                m.yHeadRot = m.yBodyRot = 0f;
                level.addFreshEntity(m);
                x += 2.8;
            }
            for (LabLeader.Motif motif : LabLeader.Motif.values()) {
                LabLeader l = ModRegistry.LAB_LEADER.get().create(level);
                l.setMotif(motif);
                l.setNoAi(true);
                l.moveTo(x, 200, bz + 4.5, 0f, 0f);
                l.yHeadRot = l.yBodyRot = 0f;
                level.addFreshEntity(l);
                l.hurt(p.damageSources().playerAttack(p), 1f);
                x += 2.2;
            }
            fly(p);
            p.teleportTo(level, bx + 8.9, 201.2, bz + 13.5, 180f, 8f);
        }));
        at(tt + 1, mc -> ShotsHarness.settle());
        at(tt + 55, mc -> mc.options.fov().set(50));
        at(tt + 60, mc -> ShotsHarness.shot(mc, "homunculi_lineup"));
        tt += 70;
        // Growth and clearing through the real code paths.
        at(tt, mc -> ShotsHarness.server(mc, (s, p) -> {
            ServerLevel level = s.overworld();
            LabPiece hub = room(LabPiece.Room.HUB);
            Vec3 in = Vec3.atCenterOf(hub.getBoundingBox().getCenter());
            p.teleportTo(level, in.x, hub.getBoundingBox().minY() + 1, in.z, 0f, 0f);
            labCmd(s, in, "kakugane lab");
            labCmd(s, in, "kakugane lab stage 1");
            List<BlockPos> vats = new ArrayList<>();
            for (StructurePiece piece : lab.getPieces()) if (piece instanceof LabPiece lp) lp.forEachVat(vats::add);
            level.setBlock(vats.get(0), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(vats.get(1), Blocks.AIR.defaultBlockState(), 3);
            labCmd(s, in, "kakugane lab");
            labCmd(s, in, "kakugane lab advance");
            Kakugane.LOGGER.info("[labs] after advance: vat0 regrown={} vat1 regrown={}",
                    level.getBlockState(vats.get(0)).is(ModRegistry.EMBRYO_VAT.get()), level.getBlockState(vats.get(1)).is(ModRegistry.EMBRYO_VAT.get()));
        }));
        // Away: wind the lab's clock six days on, walk 150 blocks off at night (in survival, so outbreaks can find us),
        // and check the lab grew without anyone there and sent a pack after us.
        tt += 40;
        at(tt, mc -> ShotsHarness.server(mc, (s, p) -> {
            ServerLevel level = s.overworld();
            LabTracker.Lab rec = LabTracker.get(level).record(lab);
            Kakugane.LOGGER.info("[labs] before away: tracked={} stage={} days={}", rec.tracked, rec.stage, rec.days);
            rec.growthStart -= 6 * 24000L;
            BlockPos c = lab.getBoundingBox().getCenter();
            level.getChunk((c.getX() + 150) >> 4, c.getZ() >> 4); // load it first, or the height map reads as empty
            BlockPos away = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.offset(150, 0, 0));
            p.setGameMode(GameType.SURVIVAL);
            cmd(s, "effect give " + p.getGameProfile().getName() + " minecraft:resistance infinite 4 true");
            cmd(s, "time set 18000");
            p.teleportTo(level, away.getX() + 0.5, away.getY() + 1, away.getZ() + 0.5, 270f, 0f);
        }));
        at(tt + 1, mc -> ShotsHarness.settle());
        at(tt + 230, mc -> ShotsHarness.server(mc, (s, p) -> {
            ServerLevel level = s.overworld();
            LabTracker.Lab rec = LabTracker.get(level).record(lab);
            int pack = level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(48), m -> m.getType().is(com.tygabeast.kakugane.registry.ModTags.HOMUNCULI)).size();
            Kakugane.LOGGER.info("[labs] away: stage={} days={} grewUnseen={} homunculi near player={} player at {}", rec.stage, rec.days, rec.grewUnseen, pack, p.blockPosition());
            for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(160), m -> m.getType().is(com.tygabeast.kakugane.registry.ModTags.HOMUNCULI)))
                Kakugane.LOGGER.info("[labs]   {} at {} removed={} target={}", m.getType().getDescriptionId(), m.blockPosition(), m.isRemoved(), m.getTarget() == p);
            cmd(s, "time set 6000");
            LabPiece hub = room(LabPiece.Room.HUB);
            Vec3 in = Vec3.atCenterOf(hub.getBoundingBox().getCenter());
            p.setGameMode(GameType.CREATIVE);
            p.teleportTo(level, in.x, hub.getBoundingBox().minY() + 1, in.z, 0f, 0f);
        }));
        at(tt + 231, mc -> ShotsHarness.settle());
        // Back at the lab: the next tracker pass should announce the growth and pay out the vats and guards owed.
        at(tt + 470, mc -> ShotsHarness.server(mc, (s, p) -> {
            ServerLevel level = s.overworld();
            LabTracker.Lab rec = LabTracker.get(level).record(lab);
            Kakugane.LOGGER.info("[labs] back: stage={} announced={} regrowOwed={} restockOwed={}", rec.stage, rec.announced, rec.regrowOwed, rec.restockOwed);
            LabPiece hub = room(LabPiece.Room.HUB);
            Vec3 in = Vec3.atCenterOf(hub.getBoundingBox().getCenter());
            List<BlockPos> vats = new ArrayList<>();
            for (StructurePiece piece : lab.getPieces()) if (piece instanceof LabPiece lp) lp.forEachVat(vats::add);
            for (BlockPos v : vats) level.setBlock(v, Blocks.AIR.defaultBlockState(), 3);
            labCmd(s, in, "kakugane lab");
            for (LabLeader l : level.getEntitiesOfClass(LabLeader.class, net.minecraft.world.phys.AABB.of(lab.getBoundingBox()))) {
                l.hurt(p.damageSources().playerAttack(p), 10000f);
            }
            labCmd(s, in, "kakugane lab");
        }));
        tt += 470;
        at(tt + 40, Minecraft::stop);
    }

    /** Creative players fall unless flying, so hold the camera in the air. */
    private static void fly(ServerPlayer p) {
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
    }

    private static void view(Minecraft mc, boolean hud) {
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.hideGui = !hud;
        mc.options.fov().set(80);
    }

    static LabPiece room(LabPiece.Room r) {
        for (StructurePiece p : lab.getPieces()) if (p instanceof LabPiece lp && lp.isRoom() && lp.room() == r) return lp;
        return null;
    }

    static LabPiece shaft() {
        for (StructurePiece p : lab.getPieces()) if (p instanceof LabPiece lp && lp.kind() == LabPiece.Kind.SHAFT) return lp;
        return null;
    }

    static void cmd(MinecraftServer s, String c) {
        s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), c);
    }

    static void labCmd(MinecraftServer s, Vec3 at, String c) {
        s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withPosition(at), c);
    }

    private LabShots() {}
}
