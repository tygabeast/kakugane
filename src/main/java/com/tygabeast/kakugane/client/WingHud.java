package com.tygabeast.kakugane.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The wing reserve bar (Alice in Wonderland), drawn just above the hotbar while the reserve isn't full.
 * The server sends the value at most 4 times a second while it changes; the bar is hidden 3 seconds after a
 * full reserve, so nothing is drawn while the wings are idle.
 */
public final class WingHud {
    private static float reserve = -1, max = 1;
    private static long fullSince;

    public static void update(float r, float m) {
        reserve = r;
        max = Math.max(1, m);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && r < m) fullSince = 0;
        else if (mc.level != null && fullSince == 0) fullSince = mc.level.getGameTime();
    }

    public static void reset() {
        reserve = -1;
    }

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (reserve < 0 || mc.level == null || mc.options.hideGui) return;
        if (fullSince > 0 && mc.level.getGameTime() - fullSince > 60) return;
        int w = 81, x = g.guiWidth() / 2 + 10, y = g.guiHeight() - 49 - (mc.player != null && mc.player.isUnderWater() ? 10 : 0);
        int fill = Math.round(w * Math.min(1f, reserve / max));
        boolean low = reserve < max * 0.2f;
        g.fill(x - 1, y - 1, x + w + 1, y + 4, 0xA0000000);
        g.fill(x, y, x + fill, y + 3, low ? 0xFFE05050 : 0xFFD8E6F0);
        g.fill(x, y, x + fill, y + 1, low ? 0xFFFF9090 : 0xFFFFFFFF);
    }

    private WingHud() {}
}
