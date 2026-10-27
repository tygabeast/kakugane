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
 *  A stage passes every N in-game days, counted only while a player is at the lab. Killing the leader stops the
 *  growth; destroying every vat as well clears the lab for good.
 *
 * Cost: once every 10 seconds, one structure lookup per player in the overworld. Everything else happens at most
 * once per in-game day (or night) per lab, and only for labs a player is at.
 */
public final class LabTracker extends SavedData {
    public static final ResourceKey<Structure> LAB = ResourceKey.create(Registries.STRUCTURE, Kakugane.id("lab"));
    private static final String NAME = "kakugane_labs";

    public static final class Lab {
        public int stage, days;
        /** Ticks a player has spent at the lab since its last daily step (a step every 24000). */
        public int presence;
        public long lastOutbreakDay = -1;
        public boolean leaderDefeated, cleared;
        public final LongSet destroyed = new LongOpenHashSet();
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
        return labs.computeIfAbsent(st.getChunkPos().toLong(), k -> new Lab());
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
        if (s == null || level.players().isEmpty()) return;
        LongSet seen = new LongOpenHashSet();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            StructureStart st = level.structureManager().getStructureAt(p.blockPosition(), s);
            if (!st.isValid()) st = level.structureManager().getStructureAt(p.blockPosition().below(12), s);
            if (st.isValid() && seen.add(st.getChunkPos().toLong())) update(level, st, false);
        }
    }

    /**
     * Called every 10 seconds while a player is at the lab: adds those 200 ticks to its time with company, and takes a
     * daily step once that reaches a full day (or right away when forced, for commands). A lab that only sees a
     * visitor now and then grows only by the time they actually spend there.
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
            if (!lab.leaderDefeated) {
                lab.days++;
                int stage = Math.min(3, lab.days / KakuganeConfig.LAB_DAYS_PER_STAGE.get());
                if (stage > lab.stage) setStage(level, st, lab, stage);
                if (lab.stage >= 1) {
                    regrowVats(level, st, lab, 2);
                    restock(level, st, lab);
                }
            }
        }
        if (KakuganeConfig.LAB_OUTBREAKS.get() && lab.stage >= 2 && !lab.leaderDefeated && level.isNight()) {
            long day = level.getDayTime() / 24000L;
            if (lab.lastOutbreakDay != day) {
                lab.lastOutbreakDay = day;
                t.setDirty();
                outbreak(level, st, lab);
            }
        }
    }

    public static void setStage(ServerLevel level, StructureStart st, Lab lab, int stage) {
        lab.stage = Math.max(0, Math.min(3, stage));
        get(level).setDirty();
        for (LabLeader leader : level.getEntitiesOfClass(LabLeader.class, AABB.of(st.getBoundingBox()))) leader.setStage(lab.stage);
        tell(level, st, Component.translatable("message.kakugane.lab_stage_" + lab.stage).withStyle(ChatFormatting.DARK_RED));
    }

    private static void regrowVats(ServerLevel level, StructureStart st, Lab lab, int max) {
        int n = 0;
        for (long l : lab.destroyed.toLongArray()) {
            if (n >= max) break;
            BlockPos p = BlockPos.of(l);
            if (!level.isLoaded(p) || !level.getBlockState(p).isAir()) continue;
            level.setBlock(p, ModRegistry.EMBRYO_VAT.get().defaultBlockState(), 3);
            lab.destroyed.remove(l);
            n++;
        }
    }

    /**
     * Brings the guard count back up to 3 + 2 per stage, at most 3 per day. Counts Homunculi a good way around the
     * lab too, since guards wander up the shaft and chase players out. Restocked guards aren't persistent: they
     * despawn like other monsters once nobody is near, and the lab refills on the next visit, so they can't pile up.
     */
    private static void restock(ServerLevel level, StructureStart st, Lab lab) {
        AABB box = AABB.of(st.getBoundingBox()).inflate(32);
        int have = level.getEntitiesOfClass(Mob.class, box, m -> m.getType().is(ModTags.HOMUNCULI)).size();
        int want = Math.min(3, 3 + 2 * lab.stage - have);
        List<LabPiece> rooms = new ArrayList<>();
        for (StructurePiece p : st.getPieces()) {
            if (p instanceof LabPiece lp && lp.isRoom() && lp.room() != LabPiece.Room.LEADER) rooms.add(lp);
        }
        if (rooms.isEmpty()) return;
        RandomSource r = level.random;
        int motif = motif(st);
        for (int i = 0; i < want; i++) {
            BlockPos p = rooms.get(r.nextInt(rooms.size())).guardPoint(r);
            if (!level.isLoaded(p) || !level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()) continue;
            spawn(level, LabPiece.guardType(motif, r), p, MobSpawnType.STRUCTURE, false);
        }
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

    private static void spawn(ServerLevel level, EntityType<? extends Mob> type, BlockPos p, MobSpawnType reason, boolean persistent) {
        Mob m = type.create(level);
        if (m == null) return;
        m.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(p), reason, null);
        if (persistent) m.setPersistenceRequired();
        level.addFreshEntityWithPassengers(m);
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
            list.add(c);
        }
        tag.put("Labs", list);
        return tag;
    }
}
