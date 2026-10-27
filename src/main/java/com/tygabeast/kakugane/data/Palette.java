package com.tygabeast.kakugane.data;

import net.minecraft.network.chat.Component;

/**
 * Accent colours. Textures are drawn with a grey accent layer that is tinted with one of these at render time,
 * so each form needs a single texture instead of one per palette. Black is reserved for the Black Kakugane.
 */
public enum Palette {
    STEEL("steel", 0x9AA7B4),
    GOLD("gold", 0xE8B931),
    CRIMSON("crimson", 0xC2272D),
    AZURE("azure", 0x2E8BEA),
    JADE("jade", 0x2FB67A),
    VIOLET("violet", 0x8E4FD8),
    IVORY("ivory", 0xEDE4CF),
    COPPER("copper", 0xC8703F);

    private final String id;
    private final int rgb;

    Palette(String id, int rgb) {
        this.id = id;
        this.rgb = rgb;
    }

    public int rgb() { return rgb; }

    public Component displayName() { return Component.translatable("palette.kakugane." + id); }

    public static Palette forSerial(int serial) {
        if (serial <= 0) return STEEL;
        return values()[Math.floorMod(Hashing.mix(serial * 17 + 11), values().length)];
    }
}
