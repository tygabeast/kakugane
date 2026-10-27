package com.tygabeast.kakugane.world;

import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.KakuganeConfig;
import com.tygabeast.kakugane.entity.LabLeader;
import com.tygabeast.kakugane.event.WorldEvents;
import com.tygabeast.kakugane.registry.ModRegistry;
import com.tygabeast.kakugane.registry.ModTags;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Remembers each lab's state (saved in data/kakugane_labs.dat) and makes uncleared labs grow:
 *
 *  Dormant -> Active (vats regrow, guards return) -> Spreading (night outbreaks) -> Overrun (bigger outbreaks).
 *  A stage passes every N in-game days. Killing the leader stops the growth; destroying every vat as well clears
 *  the lab for good. When the clock runs depends on the "growth" config:
 *
 *  - PRESENCE: only time a player spends at the lab counts.
 *  - DISCOVERY (default): the clock starts when a player first comes within discoveryRange of the lab, and then runs
 *    whether or not anyone is there.
 *  - ALWAYS: the clock starts when the lab is first loaded, with one extra stage's wait before the first stage.
 *
 * Blocks and mobs can't change in unloaded chunks, so a lab growing on its own only updates its numbers (days,
 * stage, and how many vats and guards it is owed). The next time its chunks are loaded it catches up in one go.
 * Night outbreaks from a lab nobody is at go looking for players within outbreakRange instead.
 *
 * Cost: once every 10 seconds, one structure lookup per overworld player, plus (DISCOVERY) a look at the structure
 * references of the 7x7 chunks around them, which are map lookups in already-loaded chunks. Then a few arithmetic
 * operations per known lab. Placing vats and guards happens at most once per in-game day per lab.
 */
public final class LabTracker extends SavedData {
    public static final ResourceKey<Structure> LAB = ResourceKey.create(Registries.STRUCTURE, Kakugane.id("lab"));
    private static final String NAME = "kakugane_labs";

    public static final class Lab {
        public int stage, days;
        /** PRESENCE mode: ticks a player has spent at the lab since its last daily step (a step every 24000). */
        public int presence;
        public long lastOutbreakDay = -1;
        public boolean leaderDefeated, cleared;
        public final LongSet destroyed = new LongOpenHashSet();
        /** DISCOVERY / ALWAYS: whether the lab's clock is running, and the game time it counts days from. */
        public boolean tracked;
        public long growthStart;
        /** The lab's centre, so it can be found while its chunks are unloaded (BlockPos.asLong; 0 = not known yet). */
        public long center;
        /** Vats and guards the lab is owed from days that passed while nobody could see it. */
        public int regrowOwed, restockOwed;
        /** The last stage players were told about, and whether it grew while nobody was there. */
        public int announced;
        public boolean grewUnseen;
        /** The lab's beast (0 Hound, 1 Ram, 2 Crawler), kept here for outbreaks while it's unloaded. */
        public int motif;

        /**
         * Brings days and stage up to date with the clock (DISCOVERY / ALWAYS). Pure arithmetic, safe while unloaded.
         * Returns true if at least one day passed.
         */
        public boolean catchUp(long now, int daysPerStage, boolean watched) {
            if (!tracked || cleared || leaderDefeated || now < growthStart) return false;
            long due = (now - growthStart) / 24000L;
            boolean grew = false;
            // At most 30 steps at once, which keeps the loop bounded; any remaining days are skipped below.
            for (int n = 0; days < due && n < 30; n++) {
                stepDay(daysPerStage);
                grew = true;
            }
            if (days < due) { // a very long absence: skip the rest, and let the stage follow
                days = (int) Math.min(Integer.MAX_VALUE, due);
                stage = Math.max(stage, Math.min(3, days / daysPerStage));
            }
            if (grew && !watched) grewUnseen = true;
            return grew;
        }

        /** One day of growth: the stage follows the day count, and from Active on the lab is owed vats and guards. */
        void stepDay(int daysPerStage) {
            days++;
            stage = Math.max(stage, Math.min(3, days / daysPerStage));
            if (stage >= 1) {
                regrowOwed = Math.min(regrowOwed + 2, destroyed.size());
                restockOwed = Math.min(restockOwed + 3, 3 + 2 * stage);
            }
        }
    }

    private final Long2ObjectOpenHashMap<Lab> labs = new Long2ObjectOpenHashMap<>();

    public static LabTracker get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(LabTracker::new, LabTracker::load, null), NAME);
    }

    // ---- lookups -----------------------------------------------------------------------------------

    private static Structure structure(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(LAB);
    }

    /** The lab whose bounds contain this position, or null. */
    public static StructureStart labAt(ServerLevel level, BlockPos pos) {
        Structure s = structure(level);
        if (s == null) return null;
        StructureStart st = level.structureManager().getStructureAt(pos, s);
        return st.isValid() ? st : null;
    }

    /** The stage of the lab at this position, or -1 if there's no lab there. */
    public static int stageAt(ServerLevel level, BlockPos pos) {
        StructureStart st = labAt(level, pos);
        return st == null ? -1 : get(level).record(st).stage;
    }

    public Lab record(StructureStart st) {
        Lab lab = labs.computeIfAbsent(st.getChunkPos().toLong(), k -> new Lab());
        if (lab.center == 0) {
            lab.center = st.getBoundingBox().getCenter().asLong();
            lab.motif = motif(st);
            setDirty();
        }
        return lab;
    }

    /** Starts a lab's clock (DISCOVERY and ALWAYS modes). delayDays pushes the first day back. */
    public void track(Lab lab, long now, int delayDays) {
        if (lab.tracked) return;
        lab.tracked = true;
        // Keep any days already earned (e.g. a world upgraded from presence mode) so the stage doesn't jump back.
        lab.growthStart = now + delayDays * 24000L - lab.days * 24000L;
        setDirty();
    }

    private static List<BlockPos> vats(StructureStart st) {
        List<BlockPos> out = new ArrayList<>();
        for (StructurePiece p : st.getPieces()) if (p instanceof LabPiece lp) lp.forEachVat(out::add);
        return out;
    }

    private static int motif(StructureStart st) {
        for (StructurePiece p : st.getPieces()) if (p instanceof LabPiece lp) return lp.motif();
        return 0;
    }

    // ---- events from blocks and mobs ---------------------------------------------------------------

    public static void vatDestroyed(ServerLevel level, BlockPos pos) {
        StructureStart st = labAt(level, pos);
        if (st == null || !vats(st).contains(pos)) return;
        LabTracker t = get(level);
        Lab lab = t.record(st);
        if (lab.destroyed.add(pos.asLong())) t.setDirty();
        t.checkCleared(level, st, lab);
    }

    public static void leaderDefeated(ServerLevel level, BlockPos pos) {
        StructureStart st = labAt(level, pos);
        if (st == null) return;
        LabTracker t = get(level);
        Lab lab = t.record(st);
        lab.leaderDefeated = true;
        t.setDirty();
        tell(level, st, Component.translatable("message.kakugane.lab_leader_down").withStyle(ChatFormatting.GOLD));
        t.checkCleared(level, st, lab);
    }

    private void checkCleared(ServerLevel level, StructureStart st, Lab lab) {
        if (lab.cleared || !lab.leaderDefeated || lab.destroyed.size() < vats(st).size()) return;
        lab.cleared = true;
        setDirty();
        tell(level, st, Component.translatable("message.kakugane.lab_cleared").withStyle(ChatFormatting.GREEN));
    }

    // ---- growth ------------------------------------------------------------------------------------

    /** Every 10 seconds (see event/WorldEvents). */
    public static void tick(ServerLevel level) {
        Structure s = structure(level);
        if (s == null) return;
        LabTracker t = get(level);
        KakuganeConfig.LabGrowth mode = KakuganeConfig.LAB_GROWTH.get();
        long now = level.getGameTime();

        // Which labs have a player at them right now.
        Long2ObjectOpenHashMap<StructureStart> watched = new Long2ObjectOpenHashMap<>();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            StructureStart st = loadedLabAt(level, s, p.blockPosition());
            if (st == null) st = loadedLabAt(level, s, p.blockPosition().below(12));
            if (st != null) watched.putIfAbsent(st.getChunkPos().toLong(), st);
            if (mode == KakuganeConfig.LabGrowth.DISCOVERY) t.discoverNear(level, s, p, now);
        }

        if (mode == KakuganeConfig.LabGrowth.PRESENCE) {
            for (StructureStart st : watched.values()) update(level, st, false);
            return;
        }
        // Being inside a lab always counts as finding it (also covers ALWAYS-mode labs loaded before this setting).
        for (StructureStart st : watched.values()) {
            Lab lab = t.record(st);
            if (!lab.tracked) {
                t.track(lab, now, 0);
                debug("lab at {} found from inside", st.getBoundingBox().getCenter());
                if (mode == KakuganeConfig.LabGrowth.DISCOVERY) tell(level, st, discoveredMessage());
            }
        }
        int perStage = KakuganeConfig.LAB_DAYS_PER_STAGE.get();
        for (var e : t.labs.long2ObjectEntrySet()) {
            Lab lab = e.getValue();
            if (!lab.tracked || lab.cleared) continue;
            long key = e.getLongKey();
            boolean isWatched = watched.containsKey(key);
            if (lab.catchUp(now, perStage, isWatched)) t.setDirty();
            StructureStart st = isWatched ? watched.get(key) : loadedStart(level, s, key);
            if (st != null) t.applyOwed(level, st, lab);
            t.nightOutbreak(level, st, lab, isWatched);
        }
    }

    /**
     * DISCOVERY: starts the clock of any lab within discoveryRange of this player. Looks only at structure
     * references in loaded chunks (never loads one); each lab is checked once and then skipped.
     */
    private void discoverNear(ServerLevel level, Structure s, ServerPlayer p, long now) {
        int range = KakuganeConfig.LAB_DISCOVERY_RANGE.get();
        int r = (range >> 4) + 1;
        ChunkPos pc = p.chunkPosition();
        LongSet checked = null;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            LevelChunk c = level.getChunkSource().getChunkNow(pc.x + dx, pc.z + dz);
            if (c == null) continue;
            LongSet refs = c.getReferencesForStructure(s);
            if (refs.isEmpty()) continue;
            for (long key : refs) {
                Lab known = labs.get(key);
                if (known != null && known.tracked) continue;
                if (checked == null) checked = new LongOpenHashSet();
                if (!checked.add(key)) continue;
                StructureStart st = loadedStart(level, s, key);
                if (st == null || !AABB.of(st.getBoundingBox()).inflate(range).contains(p.position())) continue;
                track(record(st), now, 0);
                p.displayClientMessage(discoveredMessage(), false);
                debug("{} discovered the lab at {}", p.getGameProfile().getName(), st.getBoundingBox().getCenter());
            }
        }
    }

    private static Component discoveredMessage() {
        return Component.translatable("message.kakugane.lab_discovered").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC);
    }

    /**
     * The lab containing this position, using only loaded chunks (the periodic check must never load one).
     * Looks at the structure references of the position's chunk, then each referenced lab's start chunk.
     */
    private static StructureStart loadedLabAt(ServerLevel level, Structure s, BlockPos pos) {
        LevelChunk c = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (c == null) return null;
        for (long key : c.getReferencesForStructure(s)) {
            StructureStart st = loadedStart(level, s, key);
            if (st != null && st.getBoundingBox().isInside(pos)) return st;
        }
        return null;
    }

    /** The lab whose start is in this chunk, if that chunk is loaded; never loads it. */
    private static StructureStart loadedStart(ServerLevel level, Structure s, long key) {
        LevelChunk c = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
        if (c == null) return null;
        StructureStart st = c.getStartForStructure(s);
        return st != null && st.isValid() ? st : null;
    }

    /** ALWAYS mode: called when a chunk loads (see event/WorldEvents); starts the clock of a lab whose start it holds. */
    public static void onChunkLoaded(ServerLevel level, LevelChunk chunk) {
        Structure s = structure(level);
        if (s == null) return;
        StructureStart st = chunk.getStartForStructure(s);
        if (st == null || !st.isValid()) return;
        LabTracker t = get(level);
        Lab lab = t.record(st);
        if (!lab.tracked) t.track(lab, level.getGameTime(), KakuganeConfig.LAB_DAYS_PER_STAGE.get());
    }

    /**
     * With the lab loaded: tells nearby players about stage changes, and places the vats and guards it is owed.
     * Only parts that are loaded are touched; whatever can't be placed stays owed for next time.
     */
    private void applyOwed(ServerLevel level, StructureStart st, Lab lab) {
        // Announce only when someone is close enough to read it; otherwise keep it for when they return.
        if (lab.stage != lab.announced && anyoneNear(level, st)) {
            for (LabLeader leader : level.getEntitiesOfClass(LabLeader.class, AABB.of(st.getBoundingBox()))) leader.setStage(lab.stage);
            if (lab.grewUnseen) tell(level, st, Component.translatable("message.kakugane.lab_grew_away").withStyle(ChatFormatting.DARK_RED));
            tell(level, st, Component.translatable("message.kakugane.lab_stage_" + lab.stage).withStyle(ChatFormatting.DARK_RED));
            debug("announced stage {} at {} (grew while away: {})", lab.stage, st.getBoundingBox().getCenter(), lab.grewUnseen);
            lab.announced = lab.stage;
            lab.grewUnseen = false;
            setDirty();
        }
        if (lab.regrowOwed > 0) {
            int placed = regrowVats(level, st, lab, lab.regrowOwed);
            if (placed > 0) {
                lab.regrowOwed -= placed;
                setDirty();
            }
        }
        if (lab.restockOwed > 0) {
            int settled = restock(level, st, lab, lab.restockOwed);
            if (settled > 0) {
                lab.restockOwed = Math.max(0, lab.restockOwed - settled);
                setDirty();
            }
        }
    }

    /**
     * Once per night for a lab at Spreading or later. A pack climbs out around the lab if a player is there;
     * otherwise it heads for the nearest player within outbreakRange, appearing in a loaded spot between them.
     */
    private void nightOutbreak(ServerLevel level, StructureStart st, Lab lab, boolean watched) {
        if (!KakuganeConfig.LAB_OUTBREAKS.get() || lab.stage < 2 || lab.leaderDefeated || !level.isNight()) return;
        long day = level.getDayTime() / 24000L;
        if (lab.lastOutbreakDay == day) return;
        if (watched && st != null) {
            lab.lastOutbreakDay = day;
            setDirty();
            outbreak(level, st, lab);
            return;
        }
        BlockPos c = BlockPos.of(lab.center);
        double range = KakuganeConfig.LAB_OUTBREAK_RANGE.get();
        ServerPlayer target = null;
        double best = range * range;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.isCreative()) continue;
            double d = p.distanceToSqr(c.getX() + 0.5, p.getY(), c.getZ() + 0.5);
            if (d <= best) {
                best = d;
                target = p;
            }
        }
        if (target == null) return; // nobody in range tonight; try again later in the night
        debug("outbreak from the lab at {} hunts {}", c, target.getGameProfile().getName());
        lab.lastOutbreakDay = day;
        setDirty();
        huntPlayer(level, lab, target);
    }

    /** Spawns a pack 24 to 32 blocks from the player, on the lab's side of them, and sets it on the player. */
    private static void huntPlayer(ServerLevel level, Lab lab, ServerPlayer player) {
        BlockPos c = BlockPos.of(lab.center);
        RandomSource r = level.random;
        double dx = c.getX() - player.getX(), dz = c.getZ() - player.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1) { dx = 1; dz = 0; len = 1; }
        double dist = 24 + r.nextInt(9);
        BlockPos base = BlockPos.containing(player.getX() + dx / len * dist, 0, player.getZ() + dz / len * dist);
        if (!level.isLoaded(base)) {
            debug("hunting pack spot {} not loaded", base);
            return;
        }
        base = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base);
        int nearby = level.getEntitiesOfClass(Mob.class, new AABB(base).inflate(32), m -> m.getType().is(ModTags.HOMUNCULI)).size();
        int count = Math.min(1 + lab.stage, KakuganeConfig.DENSITY_CAP.get() * 2 - nearby);
        debug("hunting pack of {} at {}", count, base);
        int motif = lab.motif;
        for (int i = 0; i < count; i++) {
            BlockPos spot = base.offset(r.nextInt(5) - 2, 0, r.nextInt(5) - 2);
            if (!level.isLoaded(spot)) continue;
            Mob m = spawn(level, LabPiece.guardType(motif, r), level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot),
                    MobSpawnType.EVENT, false);
            if (m != null) m.setTarget(player);
            debug("  {} at {} alive={}", m == null ? "nothing" : m.getType().getDescriptionId(), m == null ? spot : m.blockPosition(), m != null && m.isAlive());
        }
    }

    /**
     * PRESENCE mode: called every 10 seconds while a player is at the lab. Adds those 200 ticks to its time with
     * company, and takes a daily step once that reaches a full day.
     */
    public static void update(ServerLevel level, StructureStart st, boolean force) {
        LabTracker t = get(level);
        Lab lab = t.record(st);
        if (lab.cleared) return;
        if (!force) {
            lab.presence += WorldEvents.LAB_TICK_INTERVAL;
            t.setDirty();
        }
        if (force || lab.presence >= 24000) {
            lab.presence = 0;
            t.setDirty();
            if (!lab.leaderDefeated) lab.stepDay(KakuganeConfig.LAB_DAYS_PER_STAGE.get());
        }
        t.applyOwed(level, st, lab);
        t.nightOutbreak(level, st, lab, true);
    }

    /** /kakugane lab advance: one day of growth now, in any mode. */
    public static void advance(ServerLevel level, StructureStart st) {
        LabTracker t = get(level);
        Lab lab = t.record(st);
        if (lab.cleared) return;
        if (lab.tracked) {
            lab.growthStart -= 24000L; // move the clock a day on, then let it catch up
            lab.catchUp(level.getGameTime(), KakuganeConfig.LAB_DAYS_PER_STAGE.get(), true);
            t.setDirty();
            t.applyOwed(level, st, lab);
        } else {
            update(level, st, true);
        }
    }

    /** /kakugane lab stage: jumps to the start of a stage, keeping the clock consistent with it. */
    public static void forceStage(ServerLevel level, StructureStart st, int stage) {
        LabTracker t = get(level);
        Lab lab = t.record(st);
        int perStage = KakuganeConfig.LAB_DAYS_PER_STAGE.get();
        lab.stage = Math.max(0, Math.min(3, stage));
        lab.days = lab.stage * perStage;
        if (lab.tracked) lab.growthStart = level.getGameTime() - lab.days * 24000L;
        t.setDirty();
        t.applyOwed(level, st, lab);
    }

    /** Puts back up to max broken vats (in loaded chunks); returns how many. */
    private static int regrowVats(ServerLevel level, StructureStart st, Lab lab, int max) {
        int n = 0;
        for (long l : lab.destroyed.toLongArray()) {
            if (n >= max) break;
            BlockPos p = BlockPos.of(l);
            if (!level.isLoaded(p) || !level.getBlockState(p).isAir()) continue;
            level.setBlock(p, ModRegistry.EMBRYO_VAT.get().defaultBlockState(), 3);
            lab.destroyed.remove(l);
            n++;
        }
        return n;
    }

    /**
     * Brings the guard count back up to 3 + 2 per stage, spawning at most the number owed; returns how much of the
     * debt is settled (spawned guards, or all of it when the lab is already full). Counts Homunculi a good way around the
     * lab too, since guards wander up the shaft and chase players out. Restocked guards aren't persistent: they
     * despawn like other monsters once nobody is near, and the lab refills on the next visit, so they can't pile up.
     */
    private static int restock(ServerLevel level, StructureStart st, Lab lab, int owed) {
        AABB box = AABB.of(st.getBoundingBox()).inflate(32);
        int have = level.getEntitiesOfClass(Mob.class, box, m -> m.getType().is(ModTags.HOMUNCULI)).size();
        int want = Math.min(owed, 3 + 2 * lab.stage - have);
        if (want <= 0) return owed; // already full: the debt is settled
        List<LabPiece> rooms = new ArrayList<>();
        for (StructurePiece p : st.getPieces()) {
            if (p instanceof LabPiece lp && lp.isRoom() && lp.room() != LabPiece.Room.LEADER) rooms.add(lp);
        }
        if (rooms.isEmpty()) return 0;
        RandomSource r = level.random;
        int motif = motif(st);
        int placed = 0;
        for (int i = 0; i < want; i++) {
            BlockPos p = rooms.get(r.nextInt(rooms.size())).guardPoint(r);
            if (!level.isLoaded(p) || !level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()) continue;
            if (spawn(level, LabPiece.guardType(motif, r), p, MobSpawnType.STRUCTURE, false) != null) placed++;
        }
        return placed;
    }

    /** At night, a pack climbs out and roams the surface around the lab (2 to 4 beasts by stage). */
    private static void outbreak(ServerLevel level, StructureStart st, Lab lab) {
        BlockPos c = st.getBoundingBox().getCenter();
        RandomSource r = level.random;
        int motif = motif(st);
        double a = r.nextDouble() * Math.PI * 2;
        double dist = 16 + r.nextInt(24);
        int x = c.getX() + (int) (Math.cos(a) * dist), z = c.getZ() + (int) (Math.sin(a) * dist);
        BlockPos base = new BlockPos(x, 0, z);
        if (!level.isLoaded(base)) return;
        base = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base);
        int nearby = level.getEntitiesOfClass(Mob.class, new AABB(base).inflate(32), m -> m.getType().is(ModTags.HOMUNCULI)).size();
        int count = Math.min(1 + lab.stage, KakuganeConfig.DENSITY_CAP.get() * 2 - nearby);
        for (int i = 0; i < count; i++) {
            BlockPos spot = base.offset(r.nextInt(5) - 2, 0, r.nextInt(5) - 2);
            if (!level.isLoaded(spot)) continue; // never load a chunk just for this
            BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot);
            spawn(level, LabPiece.guardType(motif, r), p, MobSpawnType.EVENT, false);
        }
    }

    private static Mob spawn(ServerLevel level, EntityType<? extends Mob> type, BlockPos p, MobSpawnType reason, boolean persistent) {
        Mob m = type.create(level);
        if (m == null) return null;
        m.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(p), reason, null);
        if (persistent) m.setPersistenceRequired();
        level.addFreshEntityWithPassengers(m);
        return m;
    }

    private static boolean anyoneNear(ServerLevel level, StructureStart st) {
        AABB box = AABB.of(st.getBoundingBox()).inflate(16);
        for (ServerPlayer p : level.players()) if (box.contains(p.position())) return true;
        return false;
    }

    private static void debug(String msg, Object... args) {
        if (KakuganeConfig.DEBUG.get()) Kakugane.LOGGER.info("[lab] " + msg, args);
    }

    private static void tell(ServerLevel level, StructureStart st, Component msg) {
        AABB box = AABB.of(st.getBoundingBox()).inflate(16);
        for (ServerPlayer p : level.players()) if (box.contains(p.position())) p.displayClientMessage(msg, false);
    }

    // ---- info for commands -------------------------------------------------------------------------

    public static Component describe(ServerLevel level, StructureStart st) {
        Lab lab = get(level).record(st);
        int total = vats(st).size();
        String motif = LabLeader.Motif.byId(motif(st)).name().toLowerCase(java.util.Locale.ROOT);
        return Component.translatable("command.kakugane.lab.info", Component.translatable("message.kakugane.stage_name_" + lab.stage),
                lab.days, total - lab.destroyed.size(), total,
                Component.translatable(lab.leaderDefeated ? "command.kakugane.lab.leader_dead" : "command.kakugane.lab.leader_alive"),
                Component.translatable("entity.kakugane.lab_leader." + motif),
                Component.translatable(lab.cleared ? "command.kakugane.lab.cleared" : "command.kakugane.lab.active"));
    }

    // ---- saving ------------------------------------------------------------------------------------

    private static LabTracker load(CompoundTag tag, HolderLookup.Provider registries) {
        LabTracker t = new LabTracker();
        ListTag list = tag.getList("Labs", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            Lab lab = new Lab();
            lab.stage = c.getInt("Stage");
            lab.days = c.getInt("Days");
            lab.presence = c.getInt("Presence");
            lab.lastOutbreakDay = c.getLong("LastOutbreak");
            lab.leaderDefeated = c.getBoolean("LeaderDefeated");
            lab.cleared = c.getBoolean("Cleared");
            for (long l : c.getLongArray("Destroyed")) lab.destroyed.add(l);
            lab.tracked = c.getBoolean("Tracked");
            lab.growthStart = c.getLong("GrowthStart");
            lab.center = c.getLong("Center");
            lab.motif = c.getInt("Motif");
            lab.regrowOwed = c.getInt("RegrowOwed");
            lab.restockOwed = c.getInt("RestockOwed");
            // Labs saved before 0.3.0 have no "Announced": treat their current stage as already announced.
            lab.announced = c.contains("Announced") ? c.getInt("Announced") : lab.stage;
            lab.grewUnseen = c.getBoolean("GrewUnseen");
            t.labs.put(c.getLong("Key"), lab);
        }
        return t;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var e : labs.long2ObjectEntrySet()) {
            Lab lab = e.getValue();
            CompoundTag c = new CompoundTag();
            c.putLong("Key", e.getLongKey());
            c.putInt("Stage", lab.stage);
            c.putInt("Days", lab.days);
            c.putInt("Presence", lab.presence);
            c.putLong("LastOutbreak", lab.lastOutbreakDay);
            c.putBoolean("LeaderDefeated", lab.leaderDefeated);
            c.putBoolean("Cleared", lab.cleared);
            c.put("Destroyed", new LongArrayTag(lab.destroyed.toLongArray()));
            c.putBoolean("Tracked", lab.tracked);
            c.putLong("GrowthStart", lab.growthStart);
            c.putLong("Center", lab.center);
            c.putInt("Motif", lab.motif);
            c.putInt("RegrowOwed", lab.regrowOwed);
            c.putInt("RestockOwed", lab.restockOwed);
            c.putInt("Announced", lab.announced);
            c.putBoolean("GrewUnseen", lab.grewUnseen);
            list.add(c);
        }
        tag.put("Labs", list);
        return tag;
    }
}
