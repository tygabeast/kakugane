package com.tygabeast.kakugane.registry;

import com.tygabeast.kakugane.Kakugane;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

/** Tags and keys defined in data files (data/kakugane/...), so pack makers can change them without code. */
public final class ModTags {
    /** Every Homunculus: takes reduced damage from anything that is not alchemical. */
    public static final TagKey<EntityType<?>> HOMUNCULI = TagKey.create(Registries.ENTITY_TYPE, Kakugane.id("homunculi"));
    /** Human-type Homunculi: defeating one with a Kakugane is milestone 1 (filled in a later wave). */
    public static final TagKey<EntityType<?>> HUMAN_HOMUNCULI = TagKey.create(Registries.ENTITY_TYPE, Kakugane.id("human_homunculi"));
    /** Elite Homunculi: defeating one with a Kakugane is milestone 3 (filled in a later wave). */
    public static final TagKey<EntityType<?>> ELITE_HOMUNCULI = TagKey.create(Registries.ENTITY_TYPE, Kakugane.id("elite_homunculi"));
    /** Other mods' weapons that should count as alchemical against Homunculi. Empty by default. */
    public static final TagKey<Item> ALCHEMICAL_WEAPONS = TagKey.create(Registries.ITEM, Kakugane.id("alchemical_weapons"));

    /** Damage dealt by Kakugane abilities (dash, slam). */
    public static final ResourceKey<DamageType> BUSO_RENKIN = ResourceKey.create(Registries.DAMAGE_TYPE, Kakugane.id("buso_renkin"));

    private ModTags() {}
}
