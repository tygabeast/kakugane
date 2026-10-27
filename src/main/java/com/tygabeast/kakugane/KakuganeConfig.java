package com.tygabeast.kakugane;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Settings written to config/kakugane-common.toml.
 * Every value is read with .get() at the moment it is needed, so most changes apply without a restart
 * (spawn weights are the exception: they are read once when a world loads).
 */
public final class KakuganeConfig {
    public enum Rarity { RARE, STANDARD, COMMON, CUSTOM }
    public enum LabGrowth { PRESENCE, DISCOVERY, ALWAYS }
    public enum FlightMode { TRUE_FLIGHT, GLIDE, OFF }

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
    public static final ModConfigSpec.EnumValue<LabGrowth> LAB_GROWTH;
    public static final ModConfigSpec.IntValue LAB_DISCOVERY_RANGE;
    public static final ModConfigSpec.IntValue LAB_OUTBREAK_RANGE;
    // Worn forms
    public static final ModConfigSpec.EnumValue<FlightMode> FLIGHT_MODE;
    public static final ModConfigSpec.IntValue WING_RESERVE_SECONDS;
    public static final ModConfigSpec.IntValue WING_REFILL_SECONDS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> FLIGHT_BLACKLIST;
    public static final ModConfigSpec.BooleanValue BLADE_AUTO_STRIKES;
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
        LAB_DAYS_PER_STAGE = b.comment("In-game days for an uncleared lab to grow a stage",
                        "(Dormant, Active: vats regrow and guards return, Spreading: night outbreaks, Overrun: bigger outbreaks).")
                .defineInRange("daysPerStage", 3, 1, 100);
        LAB_OUTBREAKS = b.comment("Whether labs at the Spreading stage or later send Homunculi out at night.")
                .define("outbreaks", true);
        LAB_GROWTH = b.comment("When labs grow. PRESENCE: only while a player is at the lab.",
                        "DISCOVERY (default): from the moment a player first comes near it, whether or not anyone is there.",
                        "ALWAYS: from when the lab is first loaded, with a first stage that takes twice as long.")
                .defineEnum("growth", LabGrowth.DISCOVERY);
        LAB_DISCOVERY_RANGE = b.comment("How close (in blocks) a player must come to a lab to discover it.")
                .defineInRange("discoveryRange", 48, 8, 256);
        LAB_OUTBREAK_RANGE = b.comment("Labs growing while nobody is there send their night outbreaks toward players within this many blocks.")
                .defineInRange("outbreakRange", 192, 32, 1024);
        b.pop();

        b.push("worn_forms");
        FLIGHT_MODE = b.comment("Alice in Wonderland's wings. TRUE_FLIGHT: real flight on a reserve that refills on the ground.",
                        "GLIDE: the wings only slow your fall. OFF: no flight at all.")
                .defineEnum("flightMode", FlightMode.TRUE_FLIGHT);
        WING_RESERVE_SECONDS = b.comment("Seconds of level flight a tier 0 reserve holds (each tier adds a third more).")
                .defineInRange("wingReserveSeconds", 30, 5, 3600);
        WING_REFILL_SECONDS = b.comment("Seconds on the ground to refill an empty reserve at tier 0 (each tier is faster).")
                .defineInRange("wingRefillSeconds", 6, 1, 600);
        FLIGHT_BLACKLIST = b.comment("Dimensions where the wings can't fly (they still slow your fall), e.g. [\"minecraft:the_end\"].")
                .defineListAllowEmpty("flightDimensionBlacklist", java.util.List.of(), () -> "minecraft:the_end", o -> o instanceof String);
        BLADE_AUTO_STRIKES = b.comment("Whether the Blade Belt's arms also strike nearby enemies on their own during a fight.")
                .define("bladeBeltAutoStrikes", true);
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
