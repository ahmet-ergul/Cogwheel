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
    public static final int EDGE_SELECTED_COLOR = 0xFFFFE9A8;
    public static final int PENDING_EDGE_COLOR = 0x88E8B86E; // faded for the in-progress drag
    public static final int LOOP_EDGE_COLOR = 0xFFB89AE8;   // violet matches loop port dot
    public static final int LOOP_EDGE_PENDING = 0x88B89AE8;
    public static final float EDGE_WIDTH = 2.0f;
    public static final float EDGE_SELECTED_WIDTH = 3.5f;

    private EdgeRenderer() {}

    public static void renderAll(GuiGraphics graphics, Design design, Canvas canvas) {
        for (Edge edge : design.edges()) {
            renderOne(graphics, design, edge, canvas.isSelectedEdge(edge));
        }
    }

    /** Renders a faded preview bezier from a port to the cursor while the user is drag-creating an edge. */
    public static void renderPending(GuiGraphics graphics, HitTest.PortHit from, double cursorWorldX, double cursorWorldY) {
        Vec2 start = from.bottom()
                ? NodeRenderer.bottomPortCenter(from.node(), from.port().index())
                : NodeRenderer.portCenter(from.node(), from.port().index(), from.output());
        if (start == null) return;
        Vec2 end = new Vec2(cursorWorldX, cursorWorldY);
        int color = from.bottom() ? LOOP_EDGE_PENDING : PENDING_EDGE_COLOR;
        if (from.bottom()) drawLoopLine(graphics, start, end, color, EDGE_WIDTH);
        else drawCurve(graphics, start, end, color);
    }

    private static void renderOne(GuiGraphics graphics, Design design, Edge edge, boolean selected) {
        Node from = design.findNode(edge.from());
        Node to = design.findNode(edge.to());
        if (from == null || to == null) return;
        // Validate index/bottom presence on each side before we look up port centers.
        if (!edge.fromBottom() && edge.fromPort() >= from.outputs().size()) return;
        if (!edge.toBottom() && edge.toPort() >= to.inputs().size()) return;
        if (edge.fromBottom() && edge.fromPort() >= from.bottomPorts().size()) return;
        if (edge.toBottom() && edge.toPort() >= to.bottomPorts().size()) return;

        Vec2 start = edge.fromBottom()
                ? NodeRenderer.bottomPortCenter(from, edge.fromPort())
                : NodeRenderer.portCenter(from, edge.fromPort(), true);
        Vec2 end = edge.toBottom()
                ? NodeRenderer.bottomPortCenter(to, edge.toPort())
                : NodeRenderer.portCenter(to, edge.toPort(), false);
        if (start == null || end == null) return;
        boolean loopEdge = edge.isLoopEdge();
        int color = selected ? EDGE_SELECTED_COLOR : (loopEdge ? LOOP_EDGE_COLOR : EDGE_COLOR);
        float width = selected ? EDGE_SELECTED_WIDTH : EDGE_WIDTH;
        if (loopEdge) drawLoopLine(graphics, start, end, color, width);
        else drawCurve(graphics, start, end, color, width);
    }

    /** Used by RenderPending — same signature as before, default width. */
    private static void drawCurve(GuiGraphics graphics, Vec2 start, Vec2 end, int color) {
        drawCurve(graphics, start, end, color, EDGE_WIDTH);
    }

    private static void drawCurve(GuiGraphics graphics, Vec2 start, Vec2 end, int color, float width) {
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
        LineRenderer.drawPolyline(graphics, xs, ys, color, width);
    }

    /** Loop edges (LoopNode top-port ↔ gated node bottom-port) are simple diagonal straight lines.
     *  The natural geometry — LoopNode below, gated node above — produces a clean line drop. */
    private static void drawLoopLine(GuiGraphics graphics, Vec2 start, Vec2 end, int color, float width) {
        float[] xs = { (float) start.x(), (float) end.x() };
        float[] ys = { (float) start.y(), (float) end.y() };
        LineRenderer.drawPolyline(graphics, xs, ys, color, width);
    }
}
