package com.tygabeast.kakugane.event;

import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.world.LabTracker;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.tygabeast.kakugane.KakuganeConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** World-level timers. Labs are checked every 10 seconds (see LabTracker for what that costs). */
@EventBusSubscriber(modid = Kakugane.MODID)
public final class WorldEvents {
    /** Ticks between lab checks (10 seconds). */
    public static final int LAB_TICK_INTERVAL = 200;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % LAB_TICK_INTERVAL == 0) LabTracker.tick(event.getServer().overworld());
    }

    /**
     * ALWAYS lab growth: a lab's clock starts when the chunk holding its start loads. The check is one map lookup
     * per loaded chunk, and does nothing in the other growth modes. Run on the server thread to be safe.
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        if (KakuganeConfig.LAB_GROWTH.get() != KakuganeConfig.LabGrowth.ALWAYS) return;
        level.getServer().execute(() -> LabTracker.onChunkLoaded(level, chunk));
    }

    private WorldEvents() {}
}
