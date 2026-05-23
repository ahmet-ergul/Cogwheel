package dev.kima.cogwheel.client.ui.canvas;

import dev.kima.cogwheel.model.Design;
import dev.kima.cogwheel.model.Edge;
import dev.kima.cogwheel.model.Node;
import dev.kima.cogwheel.model.Vec2;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Renders edges as cubic bezier curves between port centers, tessellated into ~24 line segments.
 *
 * <p>Control point math: pull the curve horizontally so it forms a soft S-curve regardless of
 * vertical offset. {@code bend = max(40, |dx| * 0.5)} works at any zoom and any node spacing.
 */
public final class EdgeRenderer {
    public static final int SEGMENTS = 24;
    public static final int EDGE_COLOR = 0xFFE8B86E;
    public static final int PENDING_EDGE_COLOR = 0x88E8B86E; // faded for the in-progress drag
    public static final float EDGE_WIDTH = 2.0f;

    private EdgeRenderer() {}

    public static void renderAll(GuiGraphics graphics, Design design) {
        for (Edge edge : design.edges()) {
            renderOne(graphics, design, edge);
        }
    }

    /** Renders a faded preview bezier from a port to the cursor while the user is drag-creating an edge. */
    public static void renderPending(GuiGraphics graphics, HitTest.PortHit from, double cursorWorldX, double cursorWorldY) {
        Vec2 start = NodeRenderer.portCenter(from.node(), from.port().index(), from.output());
        Vec2 end = new Vec2(cursorWorldX, cursorWorldY);
        drawCurve(graphics, start, end, PENDING_EDGE_COLOR);
    }

    private static void renderOne(GuiGraphics graphics, Design design, Edge edge) {
        Node from = design.findNode(edge.from());
        Node to = design.findNode(edge.to());
        if (from == null || to == null) return;
        if (edge.fromPort() >= from.outputs().size() || edge.toPort() >= to.inputs().size()) return;

        Vec2 start = NodeRenderer.portCenter(from, edge.fromPort(), true);
        Vec2 end = NodeRenderer.portCenter(to, edge.toPort(), false);
        drawCurve(graphics, start, end, EDGE_COLOR);
    }

    private static void drawCurve(GuiGraphics graphics, Vec2 start, Vec2 end, int color) {
        double dx = end.x() - start.x();
        double bend = Math.max(40, Math.abs(dx) * 0.5);
        Vec2 c1 = new Vec2(start.x() + bend, start.y());
        Vec2 c2 = new Vec2(end.x() - bend, end.y());

        float[] xs = new float[SEGMENTS + 1];
        float[] ys = new float[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            double t = (double) i / SEGMENTS;
            double mt = 1 - t;
            double mt2 = mt * mt;
            double t2 = t * t;
            double x = mt2 * mt * start.x() + 3 * mt2 * t * c1.x() + 3 * mt * t2 * c2.x() + t2 * t * end.x();
            double y = mt2 * mt * start.y() + 3 * mt2 * t * c1.y() + 3 * mt * t2 * c2.y() + t2 * t * end.y();
            xs[i] = (float) x;
            ys[i] = (float) y;
        }
        LineRenderer.drawPolyline(graphics, xs, ys, color, EDGE_WIDTH);
    }
}
