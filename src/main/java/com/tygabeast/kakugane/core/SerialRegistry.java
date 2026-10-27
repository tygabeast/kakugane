package com.tygabeast.kakugane.core;

import com.tygabeast.kakugane.KakuganeConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.BitSet;

/**
 * World-wide record of which Kakugane serials have been handed out (saved in data/kakugane_serials.dat).
 * New Kakugane get a random free serial between I and the cap; once all are taken, serials count up past it.
 */
public final class SerialRegistry extends SavedData {
    private static final String NAME = "kakugane_serials";
    private final BitSet claimed = new BitSet();
    private int overflow;

    public static SerialRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SerialRegistry::new, SerialRegistry::load, null), NAME);
    }

    public int claim(RandomSource random) {
        int cap = KakuganeConfig.SERIAL_CAP.get();
        int free = cap - claimed.get(1, cap + 1).cardinality();
        if (free > 0) {
            int pick = random.nextInt(free); // the pick-th free serial
            for (int s = claimed.nextClearBit(1); s <= cap; s = claimed.nextClearBit(s + 1)) {
                if (pick-- == 0) {
                    claimed.set(s);
                    setDirty();
                    return s;
                }
            }
        }
        overflow = Math.max(overflow, cap) + 1;
        setDirty();
        return overflow;
    }

    /** Marks a serial as taken (used when a Kakugane with a serial shows up, e.g. from another world). */
    public void mark(int serial) {
        if (serial > 0 && !claimed.get(serial)) {
            claimed.set(serial);
            setDirty();
        }
    }

    public int claimedCount() { return claimed.cardinality(); }

    private static SerialRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        SerialRegistry r = new SerialRegistry();
        for (int s : tag.getIntArray("claimed")) r.claimed.set(s);
        r.overflow = tag.getInt("overflow");
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putIntArray("claimed", claimed.stream().toArray());
        tag.putInt("overflow", overflow);
        return tag;
    }
}
