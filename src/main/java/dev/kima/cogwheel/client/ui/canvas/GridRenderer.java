package dev.kima.cogwheel.client.ui.canvas;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Dotted grid in world space. Renders just the dots visible inside the given viewport rectangle
 * (screen-space). Computing visible bounds keeps the dot count bounded regardless of pan/zoom.
 */
public final class GridRenderer {
    /** World-space distance between adjacent dots. */
    public static final int GRID_STEP = 32;
    public static final int DOT_COLOR = 0x33FFFFFF; // semi-transparent white
    public static final int DOT_SIZE = 1;

    private GridRenderer() {}

    public static void render(GuiGraphics graphics, Canvas canvas,
                              int viewportX, int viewportY, int viewportW, int viewportH) {
        // Visible world bounds.
        double minWorldX = (viewportX - canvas.panX()) / canvas.zoom();
        double minWorldY = (viewportY - canvas.panY()) / canvas.zoom();
        double maxWorldX = (viewportX + viewportW - canvas.panX()) / canvas.zoom();
        double maxWorldY = (viewportY + viewportH - canvas.panY()) / canvas.zoom();

        int firstX = (int) Math.floor(minWorldX / GRID_STEP) * GRID_STEP;
        int firstY = (int) Math.floor(minWorldY / GRID_STEP) * GRID_STEP;

        for (int wx = firstX; wx <= maxWorldX; wx += GRID_STEP) {
            for (int wy = firstY; wy <= maxWorldY; wy += GRID_STEP) {
                graphics.fill(wx, wy, wx + DOT_SIZE, wy + DOT_SIZE, DOT_COLOR);
            }
        }
    }
}
