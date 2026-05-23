package dev.kima.cogwheel.client.ui.canvas;

import dev.kima.cogwheel.model.Design;
import dev.kima.cogwheel.model.Edge;
import dev.kima.cogwheel.model.PortType;

/**
 * Rules that decide whether the user's drag-from-port-to-port gesture is allowed to materialize
 * into an {@link Edge}.
 *
 * <ul>
 *   <li>Source must be an output, target must be an input.</li>
 *   <li>Source and target must be on different nodes (no self-loops in v1).</li>
 *   <li>Port types must match: ITEM↔ITEM, FLUID↔FLUID — never cross-type.</li>
 *   <li>The exact same edge (same from/fromPort/to/toPort) must not already exist.</li>
 * </ul>
 */
public final class EdgeValidation {
    private EdgeValidation() {}

    public static boolean canConnect(Design design, HitTest.PortHit from, HitTest.PortHit to) {
        if (!from.output()) return false;
        if (to.output()) return false;
        if (from.node().id().equals(to.node().id())) return false;
        if (from.port().type() != to.port().type()) return false;
        // Edge equality is by from/fromPort/to/toPort; a 5-arg Edge record compares rates too,
        // but identical endpoints + 0 rate would still be a duplicate.
        for (Edge existing : design.edges()) {
            if (existing.from().equals(from.node().id())
                    && existing.fromPort() == from.port().index()
                    && existing.to().equals(to.node().id())
                    && existing.toPort() == to.port().index()) {
                return false;
            }
        }
        return true;
    }

    public static boolean isItemPort(HitTest.PortHit hit) {
        return hit.port().type() == PortType.ITEM;
    }
}
