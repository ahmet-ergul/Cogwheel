package dev.kima.cogwheel.client.ui.canvas;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * Draws thick lines in GUI space by submitting a 4-vertex quad to {@link RenderType#gui()}.
 * Vanilla {@code GuiGraphics} only supports horizontal/vertical primitive lines; diagonal lines
 * need a buffer-level approach.
 *
 * <p>The line width is in world units (i.e. the current pose stack's local space). When the canvas
 * is zoomed, lines visually scale with it — desired behavior.
 */
public final class LineRenderer {
    private LineRenderer() {}

    public static void drawLine(GuiGraphics graphics,
                                float x1, float y1, float x2, float y2,
                                int argb, float width) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) return;

        float halfW = width * 0.5f;
        // Perpendicular (rotate (dx,dy) by 90°, scale to halfW).
        float nx = -dy / len * halfW;
        float ny = dx / len * halfW;

        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        Matrix4f matrix = graphics.pose().last().pose();
        MultiBufferSource.BufferSource source = graphics.bufferSource();
        VertexConsumer consumer = source.getBuffer(RenderType.gui());

        consumer.addVertex(matrix, x1 + nx, y1 + ny, 0).setColor(r, g, b, a);
        consumer.addVertex(matrix, x2 + nx, y2 + ny, 0).setColor(r, g, b, a);
        consumer.addVertex(matrix, x2 - nx, y2 - ny, 0).setColor(r, g, b, a);
        consumer.addVertex(matrix, x1 - nx, y1 - ny, 0).setColor(r, g, b, a);
        // Quad winding: CCW so it's not back-culled in GUI render type.
        // RenderType.gui has no culling so order doesn't matter here, but be consistent.
    }

    /**
     * Convenience to draw a polyline (sequence of line segments). Caller is responsible for
     * eventually calling {@code graphics.flush()} or letting the GUI render pipeline flush.
     */
    public static void drawPolyline(GuiGraphics graphics, float[] xs, float[] ys, int argb, float width) {
        if (xs.length != ys.length || xs.length < 2) return;
        for (int i = 1; i < xs.length; i++) {
            drawLine(graphics, xs[i - 1], ys[i - 1], xs[i], ys[i], argb, width);
        }
    }
}
