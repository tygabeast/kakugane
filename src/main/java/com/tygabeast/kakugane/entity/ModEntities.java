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
    }

    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(ModRegistry.HOUND.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Hound::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private ModEntities() {}
}
