package com.tygabeast.kakugane;

import com.mojang.logging.LogUtils;
import com.tygabeast.kakugane.entity.ModEntities;
import com.tygabeast.kakugane.network.ModNetwork;
import com.tygabeast.kakugane.registry.ModRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Entry point of the mod. NeoForge creates this class once when the game starts.
 *
 * Two "event buses" exist:
 *  - the MOD bus (passed in here) fires once-off setup events: registering items, entities, packets...
 *  - the GAME bus fires gameplay events: damage, deaths, ticks... (see classes marked @EventBusSubscriber)
 */
@Mod(Kakugane.MODID)
public final class Kakugane {
    public static final String MODID = "kakugane";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Kakugane(IEventBus modBus, ModContainer container) {
        ModRegistry.register(modBus);                              // items, entities, components, ...
        modBus.addListener(ModNetwork::register);                  // client <-> server packets
        modBus.addListener(ModEntities::registerAttributes);       // mob health/damage values
        modBus.addListener(ModEntities::registerSpawnPlacements);  // where mobs may spawn
        container.registerConfig(ModConfig.Type.COMMON, KakuganeConfig.SPEC);
    }

    /** Shorthand for "kakugane:<path>". */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
