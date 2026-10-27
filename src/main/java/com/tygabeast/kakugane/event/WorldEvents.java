package com.tygabeast.kakugane.event;

import com.tygabeast.kakugane.Kakugane;
import com.tygabeast.kakugane.world.LabTracker;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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

    private WorldEvents() {}
}
