package com.tygabeast.kakugane.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tygabeast.kakugane.data.KakuganeData;
import com.tygabeast.kakugane.registry.ModRegistry;
import net.minecraft.world.item.ItemStack;

/**
 * The heart Kakugane lives here, on the player, not in their inventory: it cannot be dropped, stored or stolen.
 * When a held form is called up, a copy (the "projection") is placed in the hand and this copy stays the real one.
 */
public final class HeartData {
    public static final Codec<HeartData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("stack", ItemStack.EMPTY).forGetter(h -> h.stack),
            Codec.BOOL.optionalFieldOf("used", false).forGetter(h -> h.used)
    ).apply(i, HeartData::new));

    /** The heart Kakugane (empty if none). */
    public ItemStack stack;
    /** Whether a Kakugane has ever replaced this player's heart. It only happens once. */
    public boolean used;

    public HeartData() {
        this(ItemStack.EMPTY, false);
    }

    private HeartData(ItemStack stack, boolean used) {
        this.stack = stack;
        this.used = used;
    }

    public boolean has() { return !stack.isEmpty(); }

    public KakuganeData data() {
        return stack.getOrDefault(ModRegistry.KAKUGANE_DATA.get(), KakuganeData.EMPTY);
    }

    public boolean matches(int serial) { return has() && data().serial() == serial; }
}
