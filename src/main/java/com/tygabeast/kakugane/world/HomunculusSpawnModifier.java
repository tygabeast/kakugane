package com.tygabeast.kakugane.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tygabeast.kakugane.KakuganeConfig;
import com.tygabeast.kakugane.registry.ModRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/**
 * Adds ambient Homunculus spawns to the biomes listed in data/kakugane/neoforge/biome_modifier/hound_spawns.json.
 * The weight comes from the config's rarity preset and is read once when the world loads, so spawning
 * itself costs nothing extra. Weight 0 adds nothing.
 */
public record HomunculusSpawnModifier(HolderSet<Biome> biomes) implements BiomeModifier {
    public static final MapCodec<HomunculusSpawnModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biomes").forGetter(HomunculusSpawnModifier::biomes)
    ).apply(i, HomunculusSpawnModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.ADD || !biomes.contains(biome)) return;
        int weight = KakuganeConfig.houndWeight();
        if (weight <= 0) return;
        builder.getMobSpawnSettings().addSpawn(MobCategory.MONSTER,
                new MobSpawnSettings.SpawnerData(ModRegistry.HOUND.get(), weight, 2, 3));
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return ModRegistry.HOMUNCULUS_SPAWNS.get();
    }
}
