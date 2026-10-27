package com.tygabeast.kakugane.data;

import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * The forms a Kakugane can take. Each one is either HELD (it becomes the item in your hand)
 * or WORN (it takes over equipment slots and leaves your hands free).
 *
 * modelIndex is the number the item model uses to pick the right picture (see the "kakugane:form" property).
 * Saved items store the id string, so adding entries is safe. The order here is also the tie-break order when
 * two forms score exactly the same (offset by the serial, see core/Affinity).
 */
public enum Archetype {
    LANCE("lance", false, 1),
    BOW("bow", false, 2),
    BREAKER("breaker", false, 3),
    SILVER_SKIN("silver_skin", true, 4),
    AERIAL_OPERATOR("aerial_operator", true, 5),
    ALICE("alice", true, 6),
    GAUNTLETS("gauntlets", true, 7),
    GREAVES("greaves", true, 8),
    MANTLE("mantle", true, 9),
    BLADE_BELT("blade_belt", true, 10);

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
