package com.tygabeast.kakugane.world;

import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.entity.LabLeader;
import com.tygabeast.kakugane.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.function.Consumer;

/**
 * One piece of a Homunculus lab, built block by block in code (like vanilla's mineshafts and strongholds).
 * Coordinates are absolute (no rotation). Every decoration is decided from the piece's own seed, so each chunk the
 * piece crosses builds its share identically. Guards and the leader are placed at generation, once, by whichever
 * chunk contains their spawn point; cleared rooms stay cleared (no spawners).
 */
public class LabPiece extends StructurePiece {
    public enum Kind { ROOM, CORRIDOR_X, CORRIDOR_Z, SHAFT }

    public enum Room { HUB, VAT_HALL, CELLS, STORAGE, BARRACKS, LEADER }

    public static final int N = 1, E = 2, S = 4, W = 8;
    public static final ResourceKey<LootTable> STORAGE_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Kakugane.id("chests/lab_storage"));
    public static final ResourceKey<LootTable> CELL_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Kakugane.id("chests/lab_cell"));

    private final Kind kind;
    private final Room room;
    private final int doors, motif, seed, floorY, surfaceY;

    public LabPiece(Kind kind, Room room, BoundingBox box, int doors, int motif, int seed, int floorY, int surfaceY) {
        super(ModRegistry.LAB_PIECE.get(), 0, box);
        this.kind = kind;
        this.room = room;
        this.doors = doors;
        this.motif = motif;
        this.seed = seed;
        this.floorY = floorY;
        this.surfaceY = surfaceY;
        setOrientation(null);
    }

    public LabPiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super(ModRegistry.LAB_PIECE.get(), tag);
        this.kind = Kind.values()[tag.getInt("Kind")];
        this.room = Room.values()[tag.getInt("Room")];
        this.doors = tag.getInt("Doors");
        this.motif = tag.getInt("Motif");
        this.seed = tag.getInt("Seed");
        this.floorY = tag.getInt("FloorY");
        this.surfaceY = tag.getInt("SurfaceY");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putInt("Kind", kind.ordinal());
        tag.putInt("Room", room.ordinal());
        tag.putInt("Doors", doors);
        tag.putInt("Motif", motif);
        tag.putInt("Seed", seed);
        tag.putInt("FloorY", floorY);
        tag.putInt("SurfaceY", surfaceY);
    }

    public Kind kind() { return kind; }
    public Room room() { return room; }
    public int motif() { return motif; }
    public boolean isRoom() { return kind == Kind.ROOM; }

    // ---- materials ---------------------------------------------------------------------------------
    private static final BlockState FLOOR = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState FLOOR_TRIM = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState TILE = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState WALL = Blocks.DEEPSLATE_TILES.defaultBlockState();
    private static final BlockState WALL_CRACKED = Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
    private static final BlockState PILLAR = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState CORRIDOR = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState LIGHT = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState AIR = Blocks.CAVE_AIR.defaultBlockState();

    // ---- geometry helpers --------------------------------------------------------------------------
    private int cx() { return (boundingBox.minX() + boundingBox.maxX()) / 2; }
    private int cz() { return (boundingBox.minZ() + boundingBox.maxZ()) / 2; }
    private int half() { return (boundingBox.maxX() - boundingBox.minX()) / 2; }

    private void set(WorldGenLevel level, BoundingBox chunk, int x, int y, int z, BlockState s) {
        placeBlock(level, s, x, y, z, chunk);
    }

    /** Room-local position: u runs along the wall, v is the distance in from it. */
    private BlockPos wallPos(int wall, int u, int v, int y) {
        int h = half(), cx = cx(), cz = cz();
        return switch (wall) {
            case N -> new BlockPos(cx + u, y, cz - h + v);
            case S -> new BlockPos(cx + u, y, cz + h - v);
            case W -> new BlockPos(cx - h + v, y, cz + u);
            default -> new BlockPos(cx + h - v, y, cz + u);
        };
    }

    private static Direction inward(int wall) {
        return switch (wall) {
            case N -> Direction.SOUTH;
            case S -> Direction.NORTH;
            case W -> Direction.EAST;
            default -> Direction.WEST;
        };
    }

    /** The walls without a door, in a fixed order. */
    private int[] doorlessWalls() {
        int[] all = {N, E, S, W};
        int n = 0;
        for (int w : all) if ((doors & w) == 0) n++;
        int[] out = new int[n];
        int i = 0;
        for (int w : all) if ((doors & w) == 0) out[i++] = w;
        return out;
    }

    // ---- shared queries (used by LabTracker too, so they must match what postProcess builds) --------

    /** Every vat this piece places. */
    public void forEachVat(Consumer<BlockPos> out) {
        if (kind != Kind.ROOM) return;
        int cx = cx(), cz = cz(), y = floorY + 1;
        if (room == Room.VAT_HALL) {
            for (int dz : new int[]{-4, 4}) for (int dx : new int[]{-5, -3, 3, 5}) out.accept(new BlockPos(cx + dx, y, cz + dz));
        } else if (room == Room.LEADER) {
            for (int dz : new int[]{-5, 5}) for (int dx : new int[]{-5, 5}) out.accept(new BlockPos(cx + dx, y, cz + dz));
        }
    }

    /** A floor-level spot inside this room for a guard (clear of vats and furniture rows). */
    public BlockPos guardPoint(RandomSource r) {
        return new BlockPos(cx() + r.nextInt(5) - 2, floorY + 1, cz() + r.nextInt(5) - 2);
    }

    // ---- building ----------------------------------------------------------------------------------

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource unused,
                            BoundingBox chunk, ChunkPos chunkPos, BlockPos pivot) {
        RandomSource r = RandomSource.create(seed);
        switch (kind) {
            case ROOM -> buildRoom(level, chunk, r);
            case CORRIDOR_X, CORRIDOR_Z -> buildCorridor(level, chunk, r);
            case SHAFT -> buildShaft(level, chunk, r);
        }
    }

    private void shell(WorldGenLevel level, BoundingBox chunk, RandomSource r, boolean room) {
        BoundingBox b = boundingBox;
        int x0 = Math.max(b.minX(), chunk.minX()), x1 = Math.min(b.maxX(), chunk.maxX());
        int z0 = Math.max(b.minZ(), chunk.minZ()), z1 = Math.min(b.maxZ(), chunk.maxZ());
        int y0 = b.minY(), y1 = b.maxY();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edgeX = x == b.minX() || x == b.maxX(), edgeZ = z == b.minZ() || z == b.maxZ();
                // Deterministic per-block noise (independent of chunk order).
                int hsh = (x * 73856093) ^ (z * 19349663) ^ seed;
                for (int y = y0; y <= y1; y++) {
                    BlockState s;
                    int n = Math.floorMod(hsh ^ (y * 83492791), 16);
                    if (!room) {
                        boolean wallZ = kind == Kind.CORRIDOR_X ? edgeZ : edgeX;
                        if (y == y0) s = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                        else if (y == y1 || wallZ) s = n == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState() : CORRIDOR;
                        else s = AIR;
                    } else if (y == y0) {
                        boolean trim = x == b.minX() + 1 || x == b.maxX() - 1 || z == b.minZ() + 1 || z == b.maxZ() - 1;
                        s = edgeX || edgeZ ? PILLAR : trim ? FLOOR_TRIM : (n == 0 ? Blocks.ANDESITE.defaultBlockState() : FLOOR);
                    } else if (y == y1) {
                        s = WALL;
                    } else if (edgeX && edgeZ) {
                        s = PILLAR;
                    } else if (edgeX || edgeZ) {
                        s = y <= y0 + 3 ? TILE : (n == 0 ? WALL_CRACKED : WALL);
                    } else {
                        s = AIR;
                    }
                    set(level, chunk, x, y, z, s);
                }
            }
        }
    }

    private void buildCorridor(WorldGenLevel level, BoundingBox chunk, RandomSource r) {
        shell(level, chunk, r, false);
        BoundingBox b = boundingBox;
        set(level, chunk, (b.minX() + b.maxX()) / 2, b.maxY(), (b.minZ() + b.maxZ()) / 2, LIGHT);
    }

    private void buildRoom(WorldGenLevel level, BoundingBox chunk, RandomSource r) {
        shell(level, chunk, r, true);
        int cx = cx(), cz = cz(), h = half(), y = floorY, top = boundingBox.maxY();
        // Doors: 3 wide, 4 tall.
        for (int wall : new int[]{N, E, S, W}) {
            if ((doors & wall) == 0) continue;
            for (int u = -1; u <= 1; u++) for (int dy = 1; dy <= 4; dy++) {
                BlockPos p = wallPos(wall, u, 0, y + dy);
                set(level, chunk, p.getX(), p.getY(), p.getZ(), AIR);
            }
        }
        // Ceiling lights on a grid, a few dead.
        for (int dx = -h + 3; dx <= h - 3; dx += 4) for (int dz = -h + 3; dz <= h - 3; dz += 4) {
            if (r.nextInt(5) != 0) set(level, chunk, cx + dx, top, cz + dz, LIGHT);
        }
        switch (room) {
            case HUB -> hub(level, chunk, r);
            case VAT_HALL -> vatHall(level, chunk, r);
            case CELLS -> cells(level, chunk, r);
            case STORAGE -> storage(level, chunk, r);
            case BARRACKS -> barracks(level, chunk, r);
            case LEADER -> leader(level, chunk, r);
        }
        // Decay: cobwebs in some upper corners.
        for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            if (r.nextInt(3) == 0) set(level, chunk, cx + c[0] * (h - 1), top - 1, cz + c[1] * (h - 1), Blocks.COBWEB.defaultBlockState());
        }
        guards(level, chunk, r);
    }

    private void hub(WorldGenLevel level, BoundingBox chunk, RandomSource r) {
        int cx = cx(), cz = cz(), y = floorY, top = boundingBox.maxY();
        // Ladder column from the shaft down to the floor, on a pillar.
        for (int yy = y + 1; yy < top; yy++) {
            set(level, chunk, cx, yy, cz - 1, PILLAR);
            set(level, chunk, cx, yy, cz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (!(dx == 0 && dz == -1)) set(level, chunk, cx + dx, top, cz + dz, AIR);
        }
        set(level, chunk, cx, top, cz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        set(level, chunk, cx - 4, y + 1, cz + 4, Blocks.BREWING_STAND.defaultBlockState());
        set(level, chunk, cx + 4, y + 1, cz + 4, Blocks.CAULDRON.defaultBlockState());
        set(level, chunk, cx - 4, y + 1, cz - 4, Blocks.CRAFTING_TABLE.defaultBlockState());
        set(level, chunk, cx + 4, y + 1, cz - 4, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.WEST));
    }

    private void vatHall(WorldGenLevel level, BoundingBox chunk, RandomSource r) {
        int top = boundingBox.maxY();
        forEachVat(p -> {
            set(level, chunk, p.getX(), floorY, p.getZ(), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            set(level, chunk, p.getX(), p.getY(), p.getZ(), ModRegistry.EMBRYO_VAT.get().defaultBlockState());
            for (int yy = p.getY() + 1; yy < top; yy++) set(level, chunk, p.getX(), yy, p.getZ(), Blocks.CHAIN.defaultBlockState());
        });
        // Drains down the middle.
        for (int dx = -2; dx <= 2; dx += 2) set(level, chunk, cx() + dx, floorY, cz(), Blocks.IRON_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.HALF, Half.TOP));
    }

    private void cells(WorldGenLevel level, BoundingBox chunk, RandomSource r) {
        int[] walls = doorlessWalls();
        if (walls.length == 0) {
            barracks(level, chunk, r);
            return;
        }
        int wall = walls[0], y = floorY;
        int chestCell = r.nextInt(3);
        for (int c = 0; c < 3; c++) {
            int u0 = -5 + c * 4; // cells span u0..u0+2, dividers at u0-1 / u0+3
            for (int dy = 1; dy <= 3; dy++) {
                for (int v = 1; v <= 3; v++) {
                    if (c == 0) put(level, chunk, wallPos(wall, u0 - 1, v, y + dy), WALL);
                    put(level, chunk, wallPos(wall, u0 + 3, v, y + dy), WALL);
                }
                for (int u = u0; u <= u0 + 2; u++) {
                    boolean broken = c == (chestCell + 1) % 3 && u == u0 + 1 && dy <= 2;
                    put(level, chunk, wallPos(wall, u, 4, y + dy), broken ? AIR : Blocks.IRON_BARS.defaultBlockState());
                }
            }
            if (c == chestCell) {
                BlockPos p = wallPos(wall, u0 + 1, 1, y + 1);
                chest(level, chunk, p, inward(wall), CELL_LOOT);
                put(level, chunk, wallPos(wall, u0, 1, y + 1), Blocks.SKELETON_SKULL.defaultBlockState());
            } else {
                put(level, chunk, wallPos(wall, u0 + r.nextInt(3), 1 + r.nextInt(2), y + 1), Blocks.BONE_BLOCK.defaultBlockState());
                put(level, chunk, wallPos(wall, u0 + r.nextInt(3), 2, y + 3), Blocks.COBWEB.defaultBlockState());
            }
        }
    }

    private void storage(WorldGenLevel level, BoundingBox chunk, RandomSource r) {
        int[] walls = doorlessWalls();
        int y = floorY;
        int wall = walls.length > 0 ? walls[0] : N;
        for (int u = -5; u <= 5; u++) {
            if (walls.length == 0 && Math.abs(u) <= 1) continue; // keep the door clear
            for (int dy = 1; dy <= 2; dy++) {
                if (r.nextInt(4) == 0) continue;
                BlockPos p = wallPos(wall, u, 1, y + dy);
                boolean stocked = r.nextInt(3) == 0;
                put(level, chunk, p, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, inward(wall)));
                if (stocked && chunk.isInside(p)) net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, lootRandom(p), p, STORAGE_LOOT);
            }
        }
        int wall2 = walls.length > 1 ? walls[1] : wall;
        for (int u : new int[]{-3, 3}) {
            BlockPos p = wallPos(wall2, u, walls.length > 1 ? 1 : 3, y + 1);
            chest(level, chunk, p, inward(wall2), STORAGE_LOOT);
        }
    }

    private void barracks(WorldGenLevel level, BoundingBox chunk, RandomSource r) {
        int cx = cx(), cz = cz(), y = floorY, h = half();
        for (int i = 0; i < 14; i++) {
            int x = cx + r.nextInt(2 * h - 3) - (h - 2), z = cz + r.nextInt(2 * h - 3) - (h - 2);
            set(level, chunk, x, y, z, r.nextBoolean() ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState());
            if (r.nextInt(3) == 0) set(level, chunk, x, y + 1, z, r.nextBoolean() ? Blocks.BONE_BLOCK.defaultBlockState() : Blocks.COBWEB.defaultBlockState());
        }
        set(level, chunk, cx + h - 2, y + 1, cz - h + 2, Blocks.CAULDRON.defaultBlockState());
    }

    private void leader(WorldGenLevel level, BoundingBox chunk, RandomSource r) {
        int cx = cx(), cz = cz(), y = floorY, top = boundingBox.maxY();
        // Raised dais in the middle.
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            set(level, chunk, cx + dx, y + 1, cz + dz, Math.abs(dx) == 2 || Math.abs(dz) == 2
                    ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        }
        // Pillars.
        for (int[] c : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            for (int yy = y + 1; yy < top; yy++) set(level, chunk, cx + c[0], yy, cz + c[1], PILLAR);
        }
        forEachVat(p -> {
            set(level, chunk, p.getX(), y, p.getZ(), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            set(level, chunk, p.getX(), p.getY(), p.getZ(), ModRegistry.EMBRYO_VAT.get().defaultBlockState());
            for (int yy = p.getY() + 1; yy < top; yy++) set(level, chunk, p.getX(), yy, p.getZ(), Blocks.CHAIN.defaultBlockState());
        });
        chest(level, chunk, new BlockPos(cx, y + 2, cz + 1), Direction.NORTH, STORAGE_LOOT);
    }

    /** A loot chest. Uses its own positional random so building stays identical whichever chunk does it. */
    private void chest(WorldGenLevel level, BoundingBox chunk, BlockPos p, Direction facing, ResourceKey<LootTable> table) {
        if (!chunk.isInside(p)) return;
        level.setBlock(p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 2);
        net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, lootRandom(p), p, table);
    }

    private RandomSource lootRandom(BlockPos p) {
        return RandomSource.create(seed ^ p.asLong());
    }

    private void put(WorldGenLevel level, BoundingBox chunk, BlockPos p, BlockState s) {
        set(level, chunk, p.getX(), p.getY(), p.getZ(), s);
    }

    // ---- inhabitants -------------------------------------------------------------------------------

    private void guards(WorldGenLevel level, BoundingBox chunk, RandomSource r) {
        int count = switch (room) {
            case HUB, CELLS, STORAGE -> 1;
            case VAT_HALL -> 2;
            case BARRACKS -> 3;
            case LEADER -> 1;
        };
        for (int i = 0; i < count; i++) {
            BlockPos p = guardPoint(r);
            if (room == Room.LEADER) p = new BlockPos(p.getX(), p.getY(), cz() - 4); // the row in front of the dais
            spawn(level, chunk, guardType(motif, r), p);
        }
        if (room == Room.LEADER) {
            BlockPos p = new BlockPos(cx(), floorY + 2, cz());
            if (chunk.isInside(p)) {
                LabLeader leader = ModRegistry.LAB_LEADER.get().create(level.getLevel());
                if (leader != null) {
                    leader.setMotif(LabLeader.Motif.byId(motif));
                    leader.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 180f, 0f);
                    leader.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.STRUCTURE, null);
                    leader.setHome(p);
                    leader.setPersistenceRequired();
                    level.addFreshEntityWithPassengers(leader);
                }
            }
        }
    }

    /** Guards favour the lab's motif: half of them are its beast, the rest split between the other two. */
    public static EntityType<? extends Mob> guardType(int motif, RandomSource r) {
        int pick = r.nextInt(4) < 2 ? motif : Math.floorMod(motif + 1 + r.nextInt(2), 3);
        return switch (pick) {
            case 1 -> ModRegistry.RAM.get();
            case 2 -> ModRegistry.CRAWLER.get();
            default -> ModRegistry.HOUND.get();
        };
    }

    private static void spawn(WorldGenLevel level, BoundingBox chunk, EntityType<? extends Mob> type, BlockPos p) {
        if (!chunk.isInside(p)) return;
        // Skip spots taken by the ladder, furniture or bone piles, so nobody spawns inside a block and suffocates.
        if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()) return;
        Mob m = type.create(level.getLevel());
        if (m == null) return;
        m.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.STRUCTURE, null);
        m.setPersistenceRequired();
        level.addFreshEntityWithPassengers(m);
    }

    // ---- the way in --------------------------------------------------------------------------------

    /** A ladder shaft from the hub's ceiling to a ruined shed on the surface, its hatch shut. */
    private void buildShaft(WorldGenLevel level, BoundingBox chunk, RandomSource r) {
        int cx = cx(), cz = cz(), bottom = boundingBox.minY(), sy = surfaceY;
        // Shaft walls and air, from just above the hub ceiling to the surface.
        for (int y = bottom; y < sy; y++) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2 || (dx == 0 && dz == -1);
                set(level, chunk, cx + dx, y, cz + dz, wall ? CORRIDOR : AIR);
            }
            set(level, chunk, cx, y, cz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
        // Ruin floor, foundations, and shut hatches over the shaft.
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            int x = cx + dx, z = cz + dz;
            boolean opening = Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && !(dx == 0 && dz == -1);
            set(level, chunk, x, sy, z, opening ? Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP)
                    : (r.nextInt(3) == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState());
            if (!opening) {
                for (int y = sy - 1; y > sy - 6; y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!chunk.isInside(p)) break;
                    BlockState below = level.getBlockState(p);
                    if (!below.isAir() && !below.canBeReplaced() && below.getFluidState().isEmpty()) break;
                    level.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), 2);
                }
            }
            for (int y = sy + 1; y <= sy + 4; y++) set(level, chunk, x, y, z, Blocks.AIR.defaultBlockState());
        }
        // Broken walls and corner posts.
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
            if (!edge) continue;
            boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
            int height = corner ? 3 + r.nextInt(2) : r.nextInt(4);
            for (int y = sy + 1; y <= sy + height; y++) {
                BlockState s = corner ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState()
                        : switch (r.nextInt(3)) {
                            case 0 -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                            case 1 -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
                            default -> Blocks.COBBLESTONE.defaultBlockState();
                        };
                set(level, chunk, cx + dx, y, cz + dz, s);
            }
        }
        set(level, chunk, cx + 2, sy + 1, cz + 2, Blocks.COBWEB.defaultBlockState());
    }
}
