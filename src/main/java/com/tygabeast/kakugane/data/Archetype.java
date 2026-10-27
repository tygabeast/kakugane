package com.tygabeast.kakugane.data;

import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * The forms a Kakugane can take. Each one is either HELD (it becomes the item in your hand)
 * or WORN (it takes over equipment slots and leaves your hands free).
 *
 * modelIndex is the number the item model uses to pick the right picture (see the "kakugane:form" property).
 * Later waves add the remaining archetypes here; saved items store the id string, so adding entries is safe.
 */
public enum Archetype {
    LANCE("lance", false, 1),
    BOW("bow", false, 2),
    BREAKER("breaker", false, 3),
    SILVER_SKIN("silver_skin", true, 4);

    private final String id;
    private final boolean worn;
    private final int modelIndex;

    Archetype(String id, boolean worn, int modelIndex) {
        this.id = id;
        this.worn = worn;
        this.modelIndex = modelIndex;
    }

    public String id() { return id; }
    public boolean worn() { return worn; }
    public int modelIndex() { return modelIndex; }

    public Component displayName() {
        return Component.translatable("archetype.kakugane." + id);
    }

    public static Optional<Archetype> byId(String id) {
        for (Archetype a : values()) {
            if (a.id.equals(id)) return Optional.of(a);
        }
        return Optional.empty();
    }
}
