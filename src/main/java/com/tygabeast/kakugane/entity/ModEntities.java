package com.tygabeast.kakugane.entity;

import com.tygabeast.kakugane.registry.ModRegistry;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/** Mod-bus setup for entities: attributes and natural spawn placement rules. */
public final class ModEntities {
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModRegistry.HOUND.get(), Hound.createAttributes().build());
        event.put(ModRegistry.RAM.get(), Ram.createAttributes().build());
        event.put(ModRegistry.CRAWLER.get(), Crawler.createAttributes().build());
        event.put(ModRegistry.LAB_LEADER.get(), LabLeader.createAttributes().build());
    }

    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(ModRegistry.HOUND.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Hound::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        // Rams and Crawlers come from labs and outbreaks only; these rules apply if a pack maker adds them to biomes.
        event.register(ModRegistry.RAM.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.entity.monster.Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModRegistry.CRAWLER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.entity.monster.Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private ModEntities() {}
}
