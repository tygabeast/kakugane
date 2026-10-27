package com.tygabeast.kakugane.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything a single Kakugane remembers, stored on the item stack as a data component.
 * Records are immutable: every "with..." method returns a changed copy, which is what item components require.
 *
 * @param serial     1..cap (0 = not assigned yet; assigned the first time it sits in a player's inventory)
 * @param owner      the bonded wielder, empty when unbonded
 * @param lastOwner  the most recent wielder, used to tell "rebonding yourself" from "a new wielder"
 * @param archetype  form id ("" = unformed)
 * @param tier       evolution tier 0..3 (power)
 * @param milestones milestones used so far 0..3 (progress; re-tempering uses a milestone without raising tier)
 * @param pending    a milestone is ready and waiting for the Deepen / Re-temper choice
 * @param shaper     name of the player whose fighting spirit gave it its current form
 * @param lineage    last few wielders' names, oldest first
 * @param heart      this Kakugane has replaced its owner's heart
 */
public record KakuganeData(int serial, Optional<UUID> owner, String ownerName, Optional<UUID> lastOwner,
                           String archetype, int tier, int milestones, boolean pending, Deeds deeds,
                           String trait, String shaper, List<String> lineage, boolean active, boolean heart) {

    public static final int MAX_LINEAGE = 5;
    public static final int MAX_TIER = 3;

    public static final KakuganeData EMPTY = new KakuganeData(0, Optional.empty(), "", Optional.empty(),
            "", 0, 0, false, Deeds.NONE, "", "", List.of(), false, false);

    public static final Codec<KakuganeData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("serial", 0).forGetter(KakuganeData::serial),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(KakuganeData::owner),
            Codec.STRING.optionalFieldOf("owner_name", "").forGetter(KakuganeData::ownerName),
            UUIDUtil.CODEC.optionalFieldOf("last_owner").forGetter(KakuganeData::lastOwner),
            Codec.STRING.optionalFieldOf("archetype", "").forGetter(KakuganeData::archetype),
            Codec.INT.optionalFieldOf("tier", 0).forGetter(KakuganeData::tier),
            Codec.INT.optionalFieldOf("milestones", 0).forGetter(KakuganeData::milestones),
            Codec.BOOL.optionalFieldOf("pending", false).forGetter(KakuganeData::pending),
            Deeds.CODEC.optionalFieldOf("deeds", Deeds.NONE).forGetter(KakuganeData::deeds),
            Codec.STRING.optionalFieldOf("trait", "").forGetter(KakuganeData::trait),
            Codec.STRING.optionalFieldOf("shaper", "").forGetter(KakuganeData::shaper),
            Codec.STRING.listOf().optionalFieldOf("lineage", List.of()).forGetter(KakuganeData::lineage),
            Codec.BOOL.optionalFieldOf("active", false).forGetter(KakuganeData::active),
            Codec.BOOL.optionalFieldOf("heart", false).forGetter(KakuganeData::heart)
    ).apply(i, KakuganeData::new));

    /** Sent to clients for tooltips and models. Small enough that reusing the save format is fine. */
    public static final StreamCodec<ByteBuf, KakuganeData> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public Optional<Archetype> form() { return Archetype.byId(archetype); }
    public Optional<Trait> traitValue() { return Trait.byId(trait); }
    public boolean formed() { return form().isPresent(); }
    public boolean bonded() { return owner.isPresent(); }
    public boolean isOwnedBy(UUID id) { return owner.isPresent() && owner.get().equals(id); }
    public Palette palette() { return Palette.forSerial(serial); }

    public KakuganeData withSerial(int s) {
        return new KakuganeData(s, owner, ownerName, lastOwner, archetype, tier, milestones, pending, deeds, trait, shaper, lineage, active, heart);
    }

    public KakuganeData withActive(boolean a) {
        return new KakuganeData(serial, owner, ownerName, lastOwner, archetype, tier, milestones, pending, deeds, trait, shaper, lineage, a, heart);
    }

    public KakuganeData withHeart(boolean h) {
        return new KakuganeData(serial, owner, ownerName, lastOwner, archetype, tier, milestones, pending, deeds, trait, shaper, lineage, active, h);
    }

    public KakuganeData withPending(boolean p) {
        return new KakuganeData(serial, owner, ownerName, lastOwner, archetype, tier, milestones, p, deeds, trait, shaper, lineage, active, heart);
    }

    public KakuganeData withDeeds(Deeds d) {
        return new KakuganeData(serial, owner, ownerName, lastOwner, archetype, tier, milestones, pending, d, trait, shaper, lineage, active, heart);
    }

    public KakuganeData withTier(int t) {
        return new KakuganeData(serial, owner, ownerName, lastOwner, archetype, Math.max(0, Math.min(MAX_TIER, t)), milestones, pending, deeds, trait, shaper, lineage, active, heart);
    }

    /** Sets a new form and trait, credited to a shaper. */
    public KakuganeData withForm(Archetype a, Trait t, String shaperName) {
        return new KakuganeData(serial, owner, ownerName, lastOwner, a.id(), tier, milestones, pending, deeds, t.id(), shaperName, lineage, active, heart);
    }

    /** A milestone was spent: progress moves on, the choice is no longer pending. */
    public KakuganeData spendMilestone(boolean raiseTier) {
        return new KakuganeData(serial, owner, ownerName, lastOwner, archetype,
                raiseTier ? Math.min(MAX_TIER, tier + 1) : tier, milestones + 1, false, deeds, trait, shaper, lineage, active, heart);
    }

    /**
     * Bond to a player. A new wielder (someone other than the last one) resets tier and milestone progress
     * (the evolution was the previous wielder's spirit) but keeps the form; the same wielder rebonding, or the
     * very first bond of a fresh Kakugane, keeps everything.
     */
    public KakuganeData bondTo(UUID id, String name) {
        boolean firstBond = lastOwner.isEmpty();
        boolean newWielder = !firstBond && !lastOwner.get().equals(id);
        List<String> newLineage = lineage;
        int newTier = tier, newMilestones = milestones;
        boolean newPending = pending;
        Deeds newDeeds = deeds;
        if (firstBond || newWielder) {
            List<String> l = new ArrayList<>(lineage);
            l.add(name);
            while (l.size() > MAX_LINEAGE) l.remove(0);
            newLineage = List.copyOf(l);
        }
        if (newWielder) {
            newTier = 0;
            newMilestones = 0;
            newPending = false;
            newDeeds = Deeds.NONE;
        }
        return new KakuganeData(serial, Optional.of(id), name, Optional.of(id), archetype, newTier, newMilestones,
                newPending, newDeeds, trait, shaper, newLineage, active, heart);
    }

    /** Release the bond; lastOwner is kept so the same player can rebond without losing progress. */
    public KakuganeData unbond() {
        return new KakuganeData(serial, Optional.empty(), "", lastOwner, archetype, tier, milestones, false, deeds, trait, shaper, lineage, false, heart);
    }

    /** Deeds done with this Kakugane by its current wielder. They drive the milestones. */
    public record Deeds(boolean human, int embryos, boolean elite) {
        public static final Deeds NONE = new Deeds(false, 0, false);
        public static final Codec<Deeds> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("human", false).forGetter(Deeds::human),
                Codec.INT.optionalFieldOf("embryos", 0).forGetter(Deeds::embryos),
                Codec.BOOL.optionalFieldOf("elite", false).forGetter(Deeds::elite)
        ).apply(i, Deeds::new));

        public Deeds withHuman() { return new Deeds(true, embryos, elite); }
        public Deeds addEmbryo() { return new Deeds(human, embryos + 1, elite); }
        public Deeds withElite() { return new Deeds(human, embryos, true); }
    }
}
