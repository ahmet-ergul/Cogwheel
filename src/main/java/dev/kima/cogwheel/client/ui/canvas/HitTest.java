package dev.kima.cogwheel.client.ui.canvas;

import dev.kima.cogwheel.model.Design;
import dev.kima.cogwheel.model.Edge;
import dev.kima.cogwheel.model.Node;
import dev.kima.cogwheel.model.Port;
import dev.kima.cogwheel.model.Vec2;

import java.util.List;
import java.util.Optional;

/**
 * Hit-tests world-space points against nodes, ports, and edges. All inputs and outputs are in
 * world space — callers convert from screen via {@link Canvas#screenToWorld(double, double)}.
 *
 * <p>Walk order (caller's responsibility): ports → edges → nodes → background. Ports come first
 * because a port dot sits visually on a node's edge — without port priority, you can never grab
 * the port to drag-create an edge.
 */
public final class HitTest {
    private HitTest() {}

    public static Optional<Node> nodeAt(Design design, double wx, double wy) {
        // Iterate in reverse so visually-on-top (last-rendered) nodes win.
        List<Node> nodes = design.nodes();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node n = nodes.get(i);
            if (inNodeRect(n, wx, wy)) {
                return Optional.of(n);
            }
        }
        return Optional.empty();
    }

    private static boolean inNodeRect(Node node, double wx, double wy) {
        double x = node.position().x();
        double y = node.position().y();
        double w = NodeRenderer.NODE_WIDTH;
        double h = NodeRenderer.heightOf(node);
        return wx >= x && wx <= x + w && wy >= y && wy <= y + h;
    }

    /**
     * Hit-test all ports on all nodes. Returns the closest port within {@code radius} world units
     * of the query point.
     */
    public static Optional<PortHit> portAt(Design design, double wx, double wy, double radius) {
        double bestDist2 = radius * radius;
        PortHit best = null;
        for (Node n : design.nodes()) {
            for (Port p : n.inputs()) {
                Vec2 c = NodeRenderer.portCenter(n, p.index(), false);
                double d2 = sqDist(c.x(), c.y(), wx, wy);
                if (d2 <= bestDist2) {
                    bestDist2 = d2;
                    best = new PortHit(n, p, false);
                }
            }
            for (Port p : n.outputs()) {
                Vec2 c = NodeRenderer.portCenter(n, p.index(), true);
                double d2 = sqDist(c.x(), c.y(), wx, wy);
                if (d2 <= bestDist2) {
                    bestDist2 = d2;
                    best = new PortHit(n, p, true);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Hit-test edges: sample 24 bezier points and check distance to each segment. Returns the
     * closest edge within {@code radius} world units.
     */
    public static Optional<Edge> edgeAt(Design design, double wx, double wy, double radius) {
        double bestDist2 = radius * radius;
        Edge best = null;
        for (Edge edge : design.edges()) {
            Node from = design.findNode(edge.from());
            Node to = design.findNode(edge.to());
            if (from == null || to == null) continue;
            if (edge.fromPort() >= from.outputs().size() || edge.toPort() >= to.inputs().size()) continue;

            Vec2 start = NodeRenderer.portCenter(from, edge.fromPort(), true);
            Vec2 end = NodeRenderer.portCenter(to, edge.toPort(), false);
            double dx = end.x() - start.x();
            double bend = Math.max(40, Math.abs(dx) * 0.5);
            Vec2 c1 = new Vec2(start.x() + bend, start.y());
            Vec2 c2 = new Vec2(end.x() - bend, end.y());

            double prevX = start.x(), prevY = start.y();
            for (int i = 1; i <= EdgeRenderer.SEGMENTS; i++) {
                double t = (double) i / EdgeRenderer.SEGMENTS;
                double mt = 1 - t;
                double mt2 = mt * mt;
                double t2 = t * t;
                double x = mt2 * mt * start.x() + 3 * mt2 * t * c1.x() + 3 * mt * t2 * c2.x() + t2 * t * end.x();
                double y = mt2 * mt * start.y() + 3 * mt2 * t * c1.y() + 3 * mt * t2 * c2.y() + t2 * t * end.y();
                double d2 = pointSegmentDistSq(wx, wy, prevX, prevY, x, y);
                if (d2 < bestDist2) {
                    bestDist2 = d2;
                    best = edge;
                }
                prevX = x;
                prevY = y;
            }
        }
        return Optional.ofNullable(best);
    }

    private static double sqDist(double ax, double ay, double bx, double by) {
        double dx = ax - bx;
        double dy = ay - by;
        return dx * dx + dy * dy;
    }

    private static double pointSegmentDistSq(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double lenSq = dx * dx + dy * dy;
        if (lenSq < 1e-9) return sqDist(px, py, x1, y1);
        double t = ((px - x1) * dx + (py - y1) * dy) / lenSq;
        t = Math.max(0, Math.min(1, t));
        double projX = x1 + t * dx;
        double projY = y1 + t * dy;
        return sqDist(px, py, projX, projY);
    }

    /** Result record for {@link #portAt}: which node owns the port, the port itself, and whether it's an output. */
    public record PortHit(Node node, Port port, boolean output) {}
}
