package com.tygabeast.kakugane.data;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The one secondary trait every Kakugane gets from its serial number.
 * Held forms draw from OFFENSIVE + UTILITY traits, worn forms from DEFENSIVE + UTILITY.
 * The actual effects live in ability/TraitEffects.
 */
public enum Trait {
    VAMPIRIC("vampiric", Category.OFFENSIVE),
    IMPACT("impact", Category.OFFENSIVE),
    SEARING("searing", Category.OFFENSIVE),
    FROST("frost", Category.OFFENSIVE),
    VENOM("venom", Category.OFFENSIVE),
    ECHO("echo", Category.OFFENSIVE),
    BANE("bane", Category.OFFENSIVE),
    GUARDING("guarding", Category.DEFENSIVE),
    THORNS("thorns", Category.DEFENSIVE),
    STEADFAST("steadfast", Category.DEFENSIVE),
    MENDING("mending", Category.DEFENSIVE),
    SWIFT("swift", Category.UTILITY),
    FEATHERFALL("featherfall", Category.UTILITY),
    ATTUNED("attuned", Category.UTILITY);

    public enum Category { OFFENSIVE, DEFENSIVE, UTILITY }

    private final String id;
    private final Category category;

    Trait(String id, Category category) {
        this.id = id;
        this.category = category;
    }

    public String id() { return id; }
    public Category category() { return category; }

    public Component displayName() { return Component.translatable("trait.kakugane." + id); }
    public Component description() { return Component.translatable("trait.kakugane." + id + ".desc"); }

    /** Traits get a little stronger with each evolution tier: 1.0, 1.25, 1.5, 1.75. */
    public static float scale(int tier) { return 1.0f + 0.25f * tier; }

    public boolean fits(boolean worn) {
        return category == Category.UTILITY || category == (worn ? Category.DEFENSIVE : Category.OFFENSIVE);
    }

    /** The serial always picks the same trait for the same kind of form (held or worn). */
    public static Trait pick(int serial, boolean worn) {
        List<Trait> pool = new ArrayList<>();
        for (Trait t : values()) if (t.fits(worn)) pool.add(t);
        int h = Hashing.mix(serial * 31 + (worn ? 7 : 3));
        return pool.get(Math.floorMod(h, pool.size()));
    }

    /** On re-tempering: keep the trait if it still suits the new form, otherwise let the serial pick again. */
    public static Trait keepOrPick(String current, int serial, boolean worn) {
        Optional<Trait> t = byId(current);
        if (t.isPresent() && t.get().fits(worn)) return t.get();
        return pick(serial, worn);
    }

    public static Optional<Trait> byId(String id) {
        for (Trait t : values()) if (t.id.equals(id)) return Optional.of(t);
        return Optional.empty();
    }
}
