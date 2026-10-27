package com.tygabeast.kakugane.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tygabeast.kakugane.KakuganeConfig;
import com.tygabeast.kakugane.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A Homunculus lab: a ruined shed on the surface hides a ladder shaft down to a complex of 7 to 9 rooms on a
 * 4 x 3 grid (about 65 x 50 blocks), joined by corridors. The hub sits under the shaft; the leader's chamber is
 * the room farthest from it. Layout, room kinds and the lab's motif all come from the world seed.
 *
 * Whether a candidate spot gets a lab depends on the rarity preset (labs exist at every preset), and labs only
 * generate on dry land.
 */
public class LabStructure extends Structure {
    public static final MapCodec<LabStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(settingsCodec(i)).apply(i, LabStructure::new));

    static final int SPACING = 17, COLS = 4, ROWS = 3, ROOM_HALF = 6, LEADER_HALF = 7, ROOM_H = 6, LEADER_H = 8;

    public LabStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public StructureType<?> type() {
        return ModRegistry.LAB.get();
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        WorldgenRandom r = ctx.random();
        if (r.nextDouble() >= KakuganeConfig.labChance()) return Optional.empty();
        ChunkPos cp = ctx.chunkPos();
        ChunkGenerator gen = ctx.chunkGenerator();
        int ox = cp.getMiddleBlockX() - (int) (1.5 * SPACING), oz = cp.getMiddleBlockZ() - SPACING;

        // Grow the room graph from a random hub cell.
        int hub = r.nextInt(COLS * ROWS);
        int target = 7 + r.nextInt(3);
        List<Integer> rooms = new ArrayList<>(List.of(hub));
        boolean[][] edge = new boolean[COLS * ROWS][COLS * ROWS];
        int guard = 0;
        while (rooms.size() < target && guard++ < 400) {
            int from = rooms.get(r.nextInt(rooms.size()));
            int to = neighbour(from, r.nextInt(4));
            if (to < 0 || rooms.contains(to)) continue;
            rooms.add(to);
            edge[from][to] = edge[to][from] = true;
        }
        // One or two extra links for loops.
        for (int k = 0, extra = 1 + r.nextInt(2); k < 20 && extra > 0; k++) {
            int a = rooms.get(r.nextInt(rooms.size()));
            int b = neighbour(a, r.nextInt(4));
            if (b >= 0 && rooms.contains(b) && !edge[a][b]) {
                edge[a][b] = edge[b][a] = true;
                extra--;
            }
        }
        int leader = farthest(hub, rooms, edge);

        // Depth: well below the lowest ground over any room; the shaft comes up at the hub.
        int hubX = ox + (hub % COLS) * SPACING, hubZ = oz + (hub / COLS) * SPACING;
        int surface = gen.getFirstOccupiedHeight(hubX, hubZ, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
        // Check the biome before sampling more terrain (the game would only check it after we return).
        var biome = ctx.biomeSource().getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(hubX), net.minecraft.core.QuartPos.fromBlock(surface),
                net.minecraft.core.QuartPos.fromBlock(hubZ), ctx.randomState().sampler());
        if (!ctx.validBiome().test(biome)) return Optional.empty();
        int ground = gen.getFirstOccupiedHeight(hubX, hubZ, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
        if (surface != ground || surface <= gen.getSeaLevel() - 4) { // water, or a deep hole
            debug("rejected at {} {}: surface {} ground {} sea {}", hubX, hubZ, surface, ground, gen.getSeaLevel());
            return Optional.empty();
        }
        int lowest = surface;
        for (int cell : rooms) {
            int x = ox + (cell % COLS) * SPACING, z = oz + (cell / COLS) * SPACING;
            lowest = Math.min(lowest, gen.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState()));
        }
        int floorY = Math.max(gen.getMinY() + 10, lowest - 24 - r.nextInt(7));
        if (floorY + LEADER_H + 6 > lowest) {
            debug("rejected at {} {}: too shallow (floor {}, lowest ground {})", hubX, hubZ, floorY, lowest);
            return Optional.empty();
        }
        debug("lab at {} {}: surface {}, floor {}, {} rooms", hubX, hubZ, surface, floorY, rooms.size());

        // Room kinds: hub, leader, at least two vat halls, a cell block and a store; the rest mixed.
        List<LabPiece.Room> kinds = new ArrayList<>(List.of(LabPiece.Room.VAT_HALL, LabPiece.Room.VAT_HALL,
                LabPiece.Room.CELLS, LabPiece.Room.STORAGE));
        LabPiece.Room[] filler = {LabPiece.Room.VAT_HALL, LabPiece.Room.BARRACKS, LabPiece.Room.CELLS, LabPiece.Room.BARRACKS};
        while (kinds.size() < rooms.size() - 2) kinds.add(filler[r.nextInt(filler.length)]);
        Collections.shuffle(kinds, new java.util.Random(r.nextLong()));
        int motif = r.nextInt(3);
        long pieceSeed = r.nextLong();

        List<LabPiece> pieces = new ArrayList<>();
        int k = 0;
        for (int cell : rooms) {
            LabPiece.Room kind = cell == hub ? LabPiece.Room.HUB : cell == leader ? LabPiece.Room.LEADER : kinds.get(k++);
            int half = kind == LabPiece.Room.LEADER ? LEADER_HALF : ROOM_HALF;
            int h = kind == LabPiece.Room.LEADER ? LEADER_H : ROOM_H;
            int cx = ox + (cell % COLS) * SPACING, cz = oz + (cell / COLS) * SPACING;
            int doors = 0;
            for (int d = 0; d < 4; d++) {
                int n = neighbour(cell, d);
                if (n >= 0 && edge[cell][n]) doors |= DOOR[d];
            }
            BoundingBox box = new BoundingBox(cx - half, floorY, cz - half, cx + half, floorY + h + 1, cz + half);
            pieces.add(new LabPiece(LabPiece.Kind.ROOM, kind, box, doors, motif, (int) (pieceSeed ^ cell * 0x9E3779B9L), floorY, 0));
        }
        // Corridors (each edge once, from the lower-numbered cell).
        for (int a : rooms) for (int d = 1; d <= 2; d++) { // east and south only
            int b = neighbour(a, d);
            if (b < 0 || !edge[a][b]) continue;
            int ha = a == leader ? LEADER_HALF : ROOM_HALF, hb = b == leader ? LEADER_HALF : ROOM_HALF;
            int ax = ox + (a % COLS) * SPACING, az = oz + (a / COLS) * SPACING;
            BoundingBox box = d == 1
                    ? new BoundingBox(ax + ha + 1, floorY, az - 2, ax + SPACING - hb - 1, floorY + 5, az + 2)
                    : new BoundingBox(ax - 2, floorY, az + ha + 1, ax + 2, floorY + 5, az + SPACING - hb - 1);
            pieces.add(new LabPiece(d == 1 ? LabPiece.Kind.CORRIDOR_X : LabPiece.Kind.CORRIDOR_Z, LabPiece.Room.HUB, box, 0, motif,
                    (int) (pieceSeed ^ (a * 31L + b)), floorY, 0));
        }
        // The shaft and surface ruin above the hub.
        BoundingBox shaft = new BoundingBox(hubX - 3, floorY + ROOM_H + 2, hubZ - 3, hubX + 3, surface + 5, hubZ + 3);
        pieces.add(new LabPiece(LabPiece.Kind.SHAFT, LabPiece.Room.HUB, shaft, 0, motif, (int) pieceSeed, floorY, surface));

        return Optional.of(new GenerationStub(new BlockPos(hubX, surface, hubZ), builder -> pieces.forEach(builder::addPiece)));
    }

    private static void debug(String msg, Object... args) {
        if (KakuganeConfig.DEBUG.get()) com.tygabeast.kakugane.Kakugane.LOGGER.info("[lab] " + msg, args);
    }

    // Directions: 0 north, 1 east, 2 south, 3 west (grid cell = col + row * COLS).
    private static final int[] DOOR = {LabPiece.N, LabPiece.E, LabPiece.S, LabPiece.W};

    private static int neighbour(int cell, int dir) {
        int c = cell % COLS, row = cell / COLS;
        switch (dir) {
            case 0 -> row--;
            case 1 -> c++;
            case 2 -> row++;
            default -> c--;
        }
        return c < 0 || c >= COLS || row < 0 || row >= ROWS ? -1 : c + row * COLS;
    }

    private static int farthest(int hub, List<Integer> rooms, boolean[][] edge) {
        int[] dist = new int[COLS * ROWS];
        java.util.Arrays.fill(dist, -1);
        dist[hub] = 0;
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>(List.of(hub));
        int best = hub;
        while (!q.isEmpty()) {
            int a = q.poll();
            if (dist[a] > dist[best]) best = a;
            for (int b : rooms) if (edge[a][b] && dist[b] < 0) {
                dist[b] = dist[a] + 1;
                q.add(b);
            }
        }
        return best;
    }
}
