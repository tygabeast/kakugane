package com.tygabeast.kakugane;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Settings written to config/kakugane-common.toml.
 * Every value is read with .get() at the moment it is needed, so most changes apply without a restart
 * (spawn weights are the exception: they are read once when a world loads).
 */
public final class KakuganeConfig {
    public enum Rarity { RARE, STANDARD, COMMON, CUSTOM }

    public static final ModConfigSpec SPEC;

    // Forms
    public static final ModConfigSpec.DoubleValue IMPRINT_THRESHOLD;
    public static final ModConfigSpec.DoubleValue NO_CHOICE_WEIGHT;
    public static final ModConfigSpec.IntValue AFFINITY_HALF_LIFE_DAYS;
    public static final ModConfigSpec.IntValue SERIAL_CAP;
    // Milestones
    public static final ModConfigSpec.IntValue EMBRYOS_FOR_MILESTONE;
    // Heart
    public static final ModConfigSpec.BooleanValue HEART_ENABLED;
    // Homunculi
    public static final ModConfigSpec.DoubleValue HOMUNCULUS_DAMAGE_REDUCTION;
    public static final ModConfigSpec.EnumValue<Rarity> RARITY;
    public static final ModConfigSpec.IntValue CUSTOM_HOUND_WEIGHT;
    public static final ModConfigSpec.IntValue DENSITY_CAP;
    // Labs
    public static final ModConfigSpec.DoubleValue CUSTOM_LAB_CHANCE;
    public static final ModConfigSpec.IntValue LAB_DAYS_PER_STAGE;
    public static final ModConfigSpec.BooleanValue LAB_OUTBREAKS;
    // Debug
    public static final ModConfigSpec.BooleanValue DEBUG;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("forms");
        IMPRINT_THRESHOLD = b.comment("How much combat a player must record before a Kakugane can take its form.",
                        "A kill where you had a real choice of weapon counts 1.0; a hit counts about 0.25.")
                .defineInRange("imprintThreshold", 20.0, 0.0, 10000.0);
        NO_CHOICE_WEIGHT = b.comment("How much an action counts when you had no alternative (e.g. a bow kill with no melee weapon on your hotbar).")
                .defineInRange("noChoiceWeight", 0.15, 0.0, 1.0);
        AFFINITY_HALF_LIFE_DAYS = b.comment("Minecraft days for recorded combat to fade to half its weight, so recent play matters most.")
                .defineInRange("affinityHalfLifeDays", 3, 1, 1000);
        SERIAL_CAP = b.comment("Number of distinct serials (I to this number) handed out before serials overflow past the cap.")
                .defineInRange("serialCap", 100, 1, 10000);
        b.pop();

        b.push("milestones");
        EMBRYOS_FOR_MILESTONE = b.comment("Homunculus embryos to destroy with an active Kakugane for its second milestone.")
                .defineInRange("embryosForSecondMilestone", 10, 1, 1000);
        b.pop();

        b.push("heart");
        HEART_ENABLED = b.comment("If you would die while carrying an unbonded Kakugane, it replaces your heart once and saves you.")
                .define("enabled", true);
        b.pop();

        b.push("homunculi");
        HOMUNCULUS_DAMAGE_REDUCTION = b.comment("Damage reduction Homunculi take from anything that is not alchemical (0.75 = 75% less).")
                .defineInRange("damageReduction", 0.75, 0.0, 1.0);
        RARITY = b.comment("RARE (default): almost no ambient spawns. STANDARD: occasional night spawns. COMMON: a regular night threat.",
                        "CUSTOM uses customHoundWeight. Needs a world restart to change.")
                .defineEnum("rarity", Rarity.RARE);
        CUSTOM_HOUND_WEIGHT = b.comment("Hound spawn weight when rarity = CUSTOM (zombies are 95). 0 disables ambient Hounds.")
                .defineInRange("customHoundWeight", 8, 0, 200);
        DENSITY_CAP = b.comment("Most Homunculi allowed within 32 blocks of a natural spawn attempt.")
                .defineInRange("densityCap", 6, 1, 100);
        b.pop();

        b.push("labs");
        CUSTOM_LAB_CHANCE = b.comment("Chance a lab site actually gets a lab when rarity = CUSTOM (RARE 0.5, STANDARD 0.75, COMMON 1.0).",
                        "Labs exist at every preset. Only affects newly generated chunks.")
                .defineInRange("customLabChance", 0.75, 0.0, 1.0);
        LAB_DAYS_PER_STAGE = b.comment("In-game days, counted only while a player is at the lab, for an uncleared lab to grow a stage",
                        "(Dormant, Active: vats regrow and guards return, Spreading: night outbreaks, Overrun: bigger outbreaks).")
                .defineInRange("daysPerStage", 3, 1, 100);
        LAB_OUTBREAKS = b.comment("Whether labs at the Spreading stage or later send Homunculi out to the surface at night.")
                .define("outbreaks", true);
        b.pop();

        DEBUG = b.comment("Print extra information to the log.").define("debug", false);
        SPEC = b.build();
    }

    /** Ambient Hound spawn weight for the chosen rarity preset. */
    public static int houndWeight() {
        return switch (RARITY.get()) {
            case RARE -> 1;
            case STANDARD -> 8;
            case COMMON -> 25;
            case CUSTOM -> CUSTOM_HOUND_WEIGHT.get();
        };
    }

    /** Chance a lab site gets a lab, from the rarity preset. */
    public static double labChance() {
        return switch (RARITY.get()) {
            case RARE -> 0.5;
            case STANDARD -> 0.75;
            case COMMON -> 1.0;
            case CUSTOM -> CUSTOM_LAB_CHANCE.get();
        };
    }

    private KakuganeConfig() {}
}
